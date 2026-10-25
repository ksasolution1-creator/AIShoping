package com.aishop.api.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 이미 로그인해 있는 세션을 끊는다 (이용 정지, 운영자 권한 변경).
 * 회원별로 "이 시각 전에 시작한 세션은 무효"를 Redis에 적어 두고 요청마다 확인한다.
 * 정지된 회원은 로그인 자체가 막히므로, 정지를 풀면 새로 로그인한 세션은 그대로 쓴다.
 */
@Component
public class SessionRevoker {

	private static final String KEY = "aishop:session-revoked";

	private final StringRedisTemplate redis;

	public SessionRevoker(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** 지금까지 시작된 이 회원의 세션을 모두 끊는다 */
	public void revoke(long userId) {
		redis.opsForHash().put(KEY, Long.toString(userId), Long.toString(System.currentTimeMillis()));
	}

	private boolean revoked(long userId, long sessionCreatedAt) {
		Object at = redis.opsForHash().get(KEY, Long.toString(userId));
		return at != null && sessionCreatedAt <= Long.parseLong(at.toString());
	}

	/** SecurityConfig에서 권한 검사 전에 넣는다 */
	public OncePerRequestFilter sessionFilter() {
		return new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
					FilterChain chain) throws ServletException, IOException {
				Authentication auth = SecurityContextHolder.getContext().getAuthentication();
				HttpSession session = request.getSession(false);
				if (session != null && auth != null && auth.getPrincipal() instanceof AuthUser user
						&& revoked(user.id(), session.getCreationTime())) {
					SecurityContextHolder.clearContext();
					session.invalidate();
				}
				chain.doFilter(request, response);
			}
		};
	}

}
