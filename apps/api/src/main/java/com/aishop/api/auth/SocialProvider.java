package com.aishop.api.auth;

import java.util.Locale;
import java.util.Optional;

/** 간편로그인 제공자. social_accounts.provider 값과 같다 */
public enum SocialProvider {

	KAKAO("카카오", "https://kauth.kakao.com/oauth/authorize", "https://kauth.kakao.com/oauth/token",
			"https://kapi.kakao.com/v2/user/me"),
	NAVER("네이버", "https://nid.naver.com/oauth2.0/authorize", "https://nid.naver.com/oauth2.0/token",
			"https://openapi.naver.com/v1/nid/me");

	private final String label;
	private final String authorizeUrl;
	private final String tokenUrl;
	private final String profileUrl;

	SocialProvider(String label, String authorizeUrl, String tokenUrl, String profileUrl) {
		this.label = label;
		this.authorizeUrl = authorizeUrl;
		this.tokenUrl = tokenUrl;
		this.profileUrl = profileUrl;
	}

	public String label() {
		return label;
	}

	String authorizeUrl() {
		return authorizeUrl;
	}

	String tokenUrl() {
		return tokenUrl;
	}

	String profileUrl() {
		return profileUrl;
	}

	/** 주소의 소문자 이름 (kakao, naver) */
	public String path() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<SocialProvider> fromPath(String path) {
		for (SocialProvider p : values()) {
			if (p.path().equals(path)) {
				return Optional.of(p);
			}
		}
		return Optional.empty();
	}

}
