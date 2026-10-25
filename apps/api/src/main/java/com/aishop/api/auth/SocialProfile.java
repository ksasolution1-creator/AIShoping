package com.aishop.api.auth;

import java.io.Serializable;

/**
 * 간편로그인 제공자에게 받은 회원 정보. 첫 가입 동의 화면까지 세션에 보관한다.
 *
 * @param email 제공자가 확인한 이메일만 넣는다 (없거나 확인 전이면 null)
 */
public record SocialProfile(SocialProvider provider, String providerUserId, String email, String name)
		implements Serializable {
}
