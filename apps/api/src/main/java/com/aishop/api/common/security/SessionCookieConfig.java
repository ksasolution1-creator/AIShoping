package com.aishop.api.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/** 로그인 세션 쿠키 (Spring Session) */
@Configuration(proxyBeanMethods = false)
public class SessionCookieConfig {

	public static final String COOKIE_NAME = "AISHOP_SESSION";

	@Bean
	CookieSerializer cookieSerializer(@Value("${aishop.session.cookie-secure:false}") boolean secure) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(COOKIE_NAME);
		serializer.setCookiePath("/");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		// 운영(HTTPS)에서는 SESSION_COOKIE_SECURE=true
		serializer.setUseSecureCookie(secure);
		return serializer;
	}

}
