package com.aishop.api.auth;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.aishop.api.common.web.ApiException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카카오·네이버 로그인 (OAuth 2.0 인가 코드 방식).
 * 앱 키는 각 개발자센터에서 발급받아 서버 환경변수로만 둔다. 키가 없는 제공자는 버튼을 숨긴다.
 */
@Component
public class SocialOAuthClient {

	private record Keys(String clientId, String clientSecret) {
	}

	private final RestClient restClient;
	private final JsonMapper jsonMapper;
	private final String redirectBase;
	private final Map<SocialProvider, Keys> keys = new EnumMap<>(SocialProvider.class);

	public SocialOAuthClient(JsonMapper jsonMapper,
			@Value("${aishop.social.redirect-base:http://localhost:3000}") String redirectBase,
			@Value("${aishop.social.kakao.client-id:}") String kakaoId,
			@Value("${aishop.social.kakao.client-secret:}") String kakaoSecret,
			@Value("${aishop.social.naver.client-id:}") String naverId,
			@Value("${aishop.social.naver.client-secret:}") String naverSecret) {
		this.jsonMapper = jsonMapper;
		this.redirectBase = redirectBase.replaceAll("/+$", "");
		// 카카오는 클라이언트 시크릿을 켜지 않았으면 REST API 키만으로 된다. 네이버는 시크릿이 꼭 필요하다
		if (!kakaoId.isBlank()) {
			keys.put(SocialProvider.KAKAO, new Keys(kakaoId, kakaoSecret));
		}
		if (!naverId.isBlank() && !naverSecret.isBlank()) {
			keys.put(SocialProvider.NAVER, new Keys(naverId, naverSecret));
		}
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(10));
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	public List<SocialProvider> enabledProviders() {
		return List.copyOf(keys.keySet());
	}

	public boolean isEnabled(SocialProvider provider) {
		return keys.containsKey(provider);
	}

	/** 제공자 로그인 화면 주소 */
	public String authorizeUrl(SocialProvider provider, String state) {
		Keys k = requireKeys(provider);
		return UriComponentsBuilder.fromUriString(provider.authorizeUrl())
			.queryParam("response_type", "code")
			.queryParam("client_id", k.clientId())
			.queryParam("redirect_uri", redirectUri(provider))
			.queryParam("state", state)
			.encode()
			.build()
			.toUriString();
	}

	/** 인가 코드로 토큰을 받고 회원 정보를 읽는다 */
	public SocialProfile fetchProfile(SocialProvider provider, String code, String state) {
		Keys k = requireKeys(provider);
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", k.clientId());
		if (!k.clientSecret().isBlank()) {
			form.add("client_secret", k.clientSecret());
		}
		form.add("redirect_uri", redirectUri(provider));
		form.add("code", code);
		form.add("state", state);
		try {
			JsonNode token = jsonMapper.readTree(restClient.post()
				.uri(provider.tokenUrl())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(String.class));
			String accessToken = token.path("access_token").asString("");
			if (accessToken.isBlank()) {
				throw failed(provider);
			}
			JsonNode me = jsonMapper.readTree(restClient.get()
				.uri(provider.profileUrl())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(String.class));
			return switch (provider) {
				case KAKAO -> kakao(me);
				case NAVER -> naver(me);
			};
		}
		catch (RestClientException | JacksonException ex) {
			throw failed(provider);
		}
	}

	String redirectUri(SocialProvider provider) {
		return redirectBase + "/api/auth/social/" + provider.path() + "/callback";
	}

	private static SocialProfile kakao(JsonNode me) {
		String id = me.path("id").asString("");
		JsonNode account = me.path("kakao_account");
		String email = account.path("is_email_verified").asBoolean(false) && account.path("is_email_valid").asBoolean(true)
				? blankToNull(account.path("email").asString(""))
				: null;
		String name = blankToNull(account.path("name").asString(""));
		if (name == null) {
			name = blankToNull(account.path("profile").path("nickname").asString(""));
		}
		return profile(SocialProvider.KAKAO, id, email, name);
	}

	private static SocialProfile naver(JsonNode me) {
		if (!"00".equals(me.path("resultcode").asString(""))) {
			throw failed(SocialProvider.NAVER);
		}
		JsonNode r = me.path("response");
		String name = blankToNull(r.path("name").asString(""));
		if (name == null) {
			name = blankToNull(r.path("nickname").asString(""));
		}
		// 네이버는 본인 확인을 거친 계정 이메일을 준다
		return profile(SocialProvider.NAVER, r.path("id").asString(""), blankToNull(r.path("email").asString("")), name);
	}

	private static SocialProfile profile(SocialProvider provider, String id, String email, String name) {
		if (id.isBlank()) {
			throw failed(provider);
		}
		return new SocialProfile(provider, id, email, name);
	}

	private Keys requireKeys(SocialProvider provider) {
		Keys k = keys.get(provider);
		if (k == null) {
			throw ApiException.badRequest("SOCIAL_DISABLED", provider.label() + " 로그인은 아직 준비 중입니다.");
		}
		return k;
	}

	private static ApiException failed(SocialProvider provider) {
		return ApiException.badRequest("SOCIAL_LOGIN_FAILED",
				provider.label() + " 로그인 정보를 받지 못했습니다. 잠시 후 다시 시도해 주세요.");
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
