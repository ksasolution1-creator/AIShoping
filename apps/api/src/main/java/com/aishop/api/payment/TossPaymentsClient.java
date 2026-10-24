package com.aishop.api.payment;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 토스페이먼츠 결제 API (승인·조회·취소).
 * 시크릿 키는 서버에만 두고, 인증 헤더는 Basic base64(시크릿키 + ":").
 */
@Component
public class TossPaymentsClient {

	private final RestClient restClient;
	private final JsonMapper jsonMapper;
	private final String clientKey;
	private final boolean configured;

	public TossPaymentsClient(JsonMapper jsonMapper,
			@Value("${aishop.toss.base-url:https://api.tosspayments.com}") String baseUrl,
			@Value("${aishop.toss.client-key:}") String clientKey,
			@Value("${aishop.toss.secret-key:}") String secretKey) {
		this.jsonMapper = jsonMapper;
		this.clientKey = clientKey;
		this.configured = !clientKey.isBlank() && !secretKey.isBlank();
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(30));
		String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
		this.restClient = RestClient.builder()
			.baseUrl(baseUrl)
			.requestFactory(factory)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
			.build();
	}

	public boolean isConfigured() {
		return configured;
	}

	public String clientKey() {
		return clientKey;
	}

	public TossPayment confirm(String paymentKey, String orderId, long amount, String idempotencyKey) {
		return post("/v1/payments/confirm", idempotencyKey,
				Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount));
	}

	public TossPayment cancel(String paymentKey, String reason, Long cancelAmount, String idempotencyKey) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("cancelReason", reason);
		if (cancelAmount != null) {
			body.put("cancelAmount", cancelAmount);
		}
		return post("/v1/payments/" + paymentKey + "/cancel", idempotencyKey, body);
	}

	public TossPayment findByOrderId(String orderId) {
		return execute(() -> restClient.get().uri("/v1/payments/orders/{orderId}", orderId).retrieve().body(String.class));
	}

	public TossPayment findByPaymentKey(String paymentKey) {
		return execute(() -> restClient.get().uri("/v1/payments/{paymentKey}", paymentKey).retrieve().body(String.class));
	}

	private TossPayment post(String path, String idempotencyKey, Map<String, Object> body) {
		return execute(() -> restClient.post()
			.uri(path)
			.header("Idempotency-Key", idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.body(String.class));
	}

	private TossPayment execute(java.util.function.Supplier<String> call) {
		if (!configured) {
			throw new TossApiException("NOT_CONFIGURED", "토스페이먼츠 키가 설정되지 않았습니다.", false);
		}
		try {
			return parse(call.get());
		}
		catch (RestClientResponseException ex) {
			JsonNode error = readTree(ex.getResponseBodyAsString());
			String code = error.path("code").asString("UNKNOWN");
			String message = error.path("message").asString("결제 처리 중 오류가 발생했습니다.");
			throw new TossApiException(code, message, false);
		}
		catch (ResourceAccessException ex) {
			// 응답을 못 받았다: 실제로 처리됐는지 알 수 없으므로 호출한 쪽에서 조회로 확인한다
			throw new TossApiException("NETWORK_ERROR", "토스페이먼츠와 통신하지 못했습니다.", true);
		}
	}

	private TossPayment parse(String raw) {
		JsonNode node = readTree(raw);
		String approvedAt = node.path("approvedAt").asString(null);
		String easyPay = node.path("easyPay").isObject()
				? node.path("easyPay").path("provider").asString(null)
				: node.path("easyPay").asString(null);
		return new TossPayment(
				node.path("paymentKey").asString(),
				node.path("orderId").asString(),
				node.path("status").asString(),
				node.path("method").asString(null),
				easyPay,
				node.path("totalAmount").asLong(),
				node.path("balanceAmount").asLong(),
				approvedAt == null ? null : OffsetDateTime.parse(approvedAt),
				node.path("lastTransactionKey").asString(null),
				raw);
	}

	private JsonNode readTree(String raw) {
		try {
			return jsonMapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
		}
		catch (RuntimeException ex) {
			return jsonMapper.createObjectNode();
		}
	}

	/** 토스 결제 객체 중 우리가 쓰는 값 */
	public record TossPayment(String paymentKey, String orderId, String status, String method, String easyPayProvider,
			long totalAmount, long balanceAmount, OffsetDateTime approvedAt, String lastTransactionKey, String raw) {

		public boolean isDone() {
			return "DONE".equals(status);
		}

	}

	/** 토스 API 오류. unknownResult가 true면 처리 결과를 알 수 없음(통신 실패) */
	public static class TossApiException extends RuntimeException {

		private final String code;
		private final boolean unknownResult;

		public TossApiException(String code, String message, boolean unknownResult) {
			super(message);
			this.code = code;
			this.unknownResult = unknownResult;
		}

		public String getCode() {
			return code;
		}

		public boolean isUnknownResult() {
			return unknownResult;
		}

	}

}
