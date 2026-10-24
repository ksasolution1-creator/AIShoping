package com.aishop.api.auth;

import java.time.Duration;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.aishop.api.common.web.ApiException;

/** 같은 이메일로 로그인을 연속 실패하면 일정 시간 막는다 (비밀번호 대입 공격 방지) */
@Component
public class LoginAttemptGuard {

	private static final String KEY_PREFIX = "aishop:login-fail:";

	private final StringRedisTemplate redis;
	private final int maxFailures;
	private final Duration lockDuration;

	public LoginAttemptGuard(StringRedisTemplate redis,
			@Value("${aishop.auth.max-login-failures:5}") int maxFailures,
			@Value("${aishop.auth.login-lock-minutes:15}") long lockMinutes) {
		this.redis = redis;
		this.maxFailures = maxFailures;
		this.lockDuration = Duration.ofMinutes(lockMinutes);
	}

	public void checkNotLocked(String email) {
		String count = redis.opsForValue().get(key(email));
		if (count != null && Integer.parseInt(count) >= maxFailures) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED",
					"로그인을 " + maxFailures + "번 실패해서 " + lockDuration.toMinutes() + "분 동안 로그인할 수 없습니다.");
		}
	}

	public void recordFailure(String email) {
		String key = key(email);
		Long count = redis.opsForValue().increment(key);
		if (count != null && count == 1) {
			redis.expire(key, lockDuration);
		}
	}

	public void reset(String email) {
		redis.delete(key(email));
	}

	private static String key(String email) {
		return KEY_PREFIX + email.toLowerCase(Locale.ROOT);
	}

}
