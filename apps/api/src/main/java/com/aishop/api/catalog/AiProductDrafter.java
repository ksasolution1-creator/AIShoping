package com.aishop.api.catalog;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.aishop.api.catalog.CatalogViews.Category;
import com.aishop.api.common.web.ApiException;

/**
 * 상품 사진을 Claude에게 보여주고 상품 등록 화면에 채울 초안을 받는다.
 * 저장은 하지 않는다. 운영자가 초안을 확인·수정한 뒤 기존 상품 등록 API로 저장한다.
 * ANTHROPIC_API_KEY가 비어 있으면 이 기능은 꺼진다.
 */
@Component
public class AiProductDrafter {

	private static final Logger log = LoggerFactory.getLogger(AiProductDrafter.class);

	public static final int MAX_PHOTOS = 6;
	/** Claude API 이미지 한 장 한도 (5MB). 화면에서 줄여서 보내므로 보통 1MB 안쪽이다 */
	private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
	private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

	private static final String SYSTEM_PROMPT = """
			당신은 한국 재고·반품·땡처리 쇼핑몰 "떨고래 마켓"의 상품 등록 담당자입니다.
			운영자가 올린 상품 사진을 보고 상품 등록 화면에 채울 초안을 만듭니다. 운영자가 확인한 뒤 저장하므로,
			사진에서 확인되지 않는 내용을 지어내지 마세요. 모르는 값은 빈 문자열(숫자는 0)로 둡니다.

			- name: 쇼핑몰 검색에 잘 걸리도록 "브랜드 + 상품 종류 + 모델/핵심 특징" 순서로 60자 안쪽. 과장 표현 금지.
			- brand, modelName: 사진(로고·라벨·박스)에 보이는 그대로. 추측이면 빈 문자열.
			- categoryId: 주어진 카테고리 목록에서 가장 알맞은 id. 맞는 것이 없으면 0.
			- conditionGrade: 사진으로 판단한 상태등급.
			  NEW(새상품, 미개봉 정품), RETURN_UNOPENED(반품이지만 미개봉), RETURN_OPENED(반품, 개봉됨),
			  REFURB(리퍼), USED_A(사용감 거의 없음), USED_B(사용감 있음), USED_C(흠집·오염 뚜렷), DISPLAY(전시상품).
			  포장이 뜯겼거나 비닐이 없으면 개봉으로 봅니다. 확실하지 않으면 더 낮은 등급을 고르고 warnings에 적습니다.
			- conditionNote: 반품·중고·전시상품이면 고객이 알아야 할 상태(흠집 위치, 오염, 박스 훼손, 구성품 누락 의심)를
			  사실대로 한두 문장. 새상품이면 빈 문자열.
			- options: 색상·사이즈 등이 보이면 "색상=블랙, 사이즈=M" 형식. 없으면 빈 문자열.
			- description: 고객에게 보여줄 상품 설명. 2~4개 짧은 문단, 특징·구성품·사용처 위주. 상태 설명과 겹치지 않게.
			  가격·할인·배송 이야기와 "최고", "완벽" 같은 과장 표현은 쓰지 않습니다.
			- listPrice: 가격표·박스에 정가가 인쇄되어 보일 때만 그 금액(원). 아니면 0. 시세를 추정하지 마세요.
			- barcode: 바코드 아래 숫자가 또렷이 읽힐 때만. kcCertNo: KC 인증번호가 보일 때만.
			- expiryDate: 식품·건강기능식품·화장품 등에 소비기한(유통기한)이 인쇄되어 또렷이 보일 때만 yyyy-MM-dd.
			  안 보이거나 흐리면 빈 문자열이고, 먹는 상품인데 안 보이면 warnings에 적습니다.
			- notice: 라벨에서 읽히는 상품정보 제공고시 항목만 (예: 제조국=중국, 제조사(수입자)=OO, 소재=면 100%).
			- warnings: 운영자가 저장 전에 직접 확인해야 할 점 (예: "구성품 전체가 사진에 없음", "모델명 일부가 가려짐").
			""";

	private final Optional<AnthropicClient> client;
	private final String model;

	public AiProductDrafter(@Value("${aishop.ai.api-key:}") String apiKey,
			@Value("${aishop.ai.model:claude-opus-5-5}") String model) {
		this.client = apiKey.isBlank() ? Optional.empty()
				: Optional.of(AnthropicOkHttpClient.builder().apiKey(apiKey).build());
		this.model = model;
	}

	public boolean enabled() {
		return client.isPresent();
	}

	/** 사진에서 읽은 상품 정보 초안. 구조화 출력 스키마로 그대로 쓰인다 */
	public record AiDraft(String name, String brand, String modelName, long categoryId, ConditionGrade conditionGrade,
			String conditionNote, String options, String description, long listPrice, String barcode,
			String kcCertNo, String expiryDate, List<NoticeItem> notice, List<String> warnings) {
	}

	public record NoticeItem(String key, String value) {
	}

	public AiDraft draft(List<MultipartFile> photos, String hint, List<Category> categories) {
		AnthropicClient anthropic = client.orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
				"AI_DISABLED", "사진 자동 입력이 꺼져 있습니다. 서버에 ANTHROPIC_API_KEY를 설정해 주세요."));
		if (photos == null || photos.isEmpty()) {
			throw ApiException.badRequest("NO_PHOTO", "사진을 한 장 이상 올려 주세요.");
		}
		if (photos.size() > MAX_PHOTOS) {
			throw ApiException.badRequest("TOO_MANY_PHOTOS", "사진은 " + MAX_PHOTOS + "장까지 올릴 수 있습니다.");
		}

		List<ContentBlockParam> content = new ArrayList<>();
		for (MultipartFile photo : photos) {
			content.add(ContentBlockParam.ofImage(ImageBlockParam.builder().source(toImageSource(photo)).build()));
		}
		content.add(ContentBlockParam.ofText(TextBlockParam.builder().text(userPrompt(hint, categories)).build()));

		StructuredMessageCreateParams<AiDraft> params = MessageCreateParams.builder()
			.model(model)
			.maxTokens(16000L)
			.system(SYSTEM_PROMPT)
			.outputConfig(AiDraft.class)
			.addUserMessageOfBlockParams(content)
			// 안전 분류기가 거절하면 서버가 알맞은 다른 모델로 다시 처리한다
			.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
			.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
			.build();

		StructuredMessage<AiDraft> response;
		try {
			response = anthropic.messages().create(params);
		}
		catch (RateLimitException ex) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_BUSY", "요청이 많습니다. 잠시 뒤 다시 시도해 주세요.");
		}
		catch (AnthropicServiceException ex) {
			log.warn("Claude API 오류 status={} type={}", ex.statusCode(), ex.errorType().orElse(null), ex);
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_ERROR", "사진 분석에 실패했습니다. 잠시 뒤 다시 시도해 주세요.");
		}
		catch (AnthropicException ex) {
			log.warn("Claude API 연결 실패", ex);
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_ERROR", "사진 분석 서버에 연결하지 못했습니다.");
		}

		StopReason stop = response.stopReason().orElse(null);
		if (StopReason.REFUSAL.equals(stop)) {
			throw ApiException.badRequest("AI_REFUSED", "이 사진으로는 상품 정보를 만들 수 없습니다. 직접 입력해 주세요.");
		}
		if (StopReason.MAX_TOKENS.equals(stop)) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_ERROR", "사진 분석 결과가 너무 길어 끊겼습니다. 다시 시도해 주세요.");
		}
		return response.content().stream()
			.flatMap(block -> block.text().stream())
			.map(text -> text.text())
			.findFirst()
			.map(d -> sanitize(d, categories))
			.orElseThrow(() -> new ApiException(HttpStatus.BAD_GATEWAY, "AI_ERROR", "사진 분석 결과가 비어 있습니다."));
	}

	private static Base64ImageSource toImageSource(MultipartFile photo) {
		String type = photo.getContentType() == null ? "" : photo.getContentType().toLowerCase();
		if (!IMAGE_TYPES.contains(type)) {
			throw ApiException.badRequest("INVALID_IMAGE", "JPG, PNG, WEBP 사진만 올릴 수 있습니다.");
		}
		if (photo.getSize() > MAX_PHOTO_BYTES) {
			throw ApiException.badRequest("IMAGE_TOO_LARGE", "사진 한 장이 5MB를 넘습니다.");
		}
		try {
			return Base64ImageSource.builder()
				.mediaType(Base64ImageSource.MediaType.of(type))
				.data(Base64.getEncoder().encodeToString(photo.getBytes()))
				.build();
		}
		catch (IOException ex) {
			throw ApiException.badRequest("INVALID_IMAGE", "사진을 읽지 못했습니다.");
		}
	}

	private static String userPrompt(String hint, List<Category> categories) {
		String categoryList = categories.stream()
			.map(c -> c.id() + ": " + c.name() + (c.parentId() == null ? "" : " (상위 " + c.parentId() + ")"))
			.collect(Collectors.joining("\n"));
		StringBuilder prompt = new StringBuilder();
		prompt.append("카테고리 목록 (id: 이름):\n").append(categoryList).append("\n\n");
		if (hint != null && !hint.isBlank()) {
			prompt.append("운영자 메모 (사진보다 우선합니다):\n").append(hint.strip()).append("\n\n");
		}
		prompt.append("위 사진들은 모두 같은 상품입니다. 상품 등록 초안을 만들어 주세요.");
		return prompt.toString();
	}

	/** 목록에 없는 카테고리, 음수 금액 같은 값은 비운다 */
	private static AiDraft sanitize(AiDraft d, List<Category> categories) {
		boolean knownCategory = categories.stream().anyMatch(c -> c.id() == d.categoryId());
		return new AiDraft(trim(d.name()), trim(d.brand()), trim(d.modelName()), knownCategory ? d.categoryId() : 0,
				d.conditionGrade(), trim(d.conditionNote()), trim(d.options()), trim(d.description()),
				Math.max(d.listPrice(), 0), trim(d.barcode()), trim(d.kcCertNo()), isoDate(d.expiryDate()),
				d.notice() == null ? List.of() : d.notice().stream()
					.filter(n -> n != null && !trim(n.key()).isEmpty() && !trim(n.value()).isEmpty())
					.toList(),
				d.warnings() == null ? List.of() : d.warnings());
	}

	/** yyyy-MM-dd로 읽힌 날짜만 쓴다 (잘못 읽은 값으로 입력칸이 깨지지 않게) */
	private static String isoDate(String s) {
		try {
			return LocalDate.parse(trim(s)).toString();
		}
		catch (DateTimeException e) {
			return "";
		}
	}

	private static String trim(String s) {
		return s == null ? "" : s.strip();
	}

}
