package com.aishop.api.auth;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Component;

/** 로그인 세션 시작·종료 (이메일 로그인과 간편로그인이 같이 쓴다) */
@Component
public class LoginSessions {

	private final SecurityContextRepository contextRepository;
	private final CookieCsrfTokenRepository csrfTokenRepository;
	private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

	public LoginSessions(SecurityContextRepository contextRepository, CookieCsrfTokenRepository csrfTokenRepository) {
		this.contextRepository = contextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
	}

	public void start(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
		// 세션 고정 공격 방지: 로그인 전에 쓰던 세션 ID를 바꾼다
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		List<SimpleGrantedAuthority> authorities = user.isStaff()
				? user.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()
				: List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
		SecurityContext context = holder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
		holder.setContext(context);
		contextRepository.saveContext(context, request, response);
		// 로그인 전 CSRF 토큰은 버리고, 다음 변경 요청 전에 새로 받게 한다
		csrfTokenRepository.saveToken(null, request, response);
	}

	public void end(HttpServletRequest request, HttpServletResponse response) {
		holder.clearContext();
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		csrfTokenRepository.saveToken(null, request, response);
	}

}
