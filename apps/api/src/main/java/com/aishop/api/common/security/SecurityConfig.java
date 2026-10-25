package com.aishop.api.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;

import jakarta.servlet.http.HttpServletResponse;

import com.aishop.api.auth.SessionRevoker;
import tools.jackson.databind.json.JsonMapper;

/**
 * 세션 쿠키 로그인 + CSRF 보호.
 * 웹(Next.js)은 같은 도메인의 /api로 프록시하므로 쿠키가 그대로 오간다.
 * 변경 요청(POST/PUT/PATCH/DELETE)은 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보내야 한다.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	public static final String[] STAFF_ROLES = { "ADMIN", "OPERATOR" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
			CookieCsrfTokenRepository csrfTokenRepository, JsonMapper jsonMapper, SessionRevoker sessionRevoker)
			throws Exception {
		http
			// 이용 정지·권한 변경된 계정은 이미 로그인해 있어도 권한 검사 전에 세션을 끊는다
			.addFilterBefore(sessionRevoker.sessionFilter(), AuthorizationFilter.class)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
				.requestMatchers("/files/**").permitAll()
				.requestMatchers("/api/auth/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories", "/api/condition-grades",
						"/api/search/popular")
				.permitAll()
				.requestMatchers("/api/admin/**").hasAnyRole(STAFF_ROLES)
				.anyRequest().authenticated())
			// 웹훅은 토스 서버가 호출하므로 CSRF 토큰이 없다 (내용 대신 토스 재조회 결과만 반영)
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository).ignoringRequestMatchers("/api/payments/webhook"))
			.securityContext(context -> context.securityContextRepository(contextRepository))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint((request, response, e) -> writeProblem(response, jsonMapper,
						HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다."))
				.accessDeniedHandler((request, response, e) -> {
					if (e instanceof CsrfException) {
						writeProblem(response, jsonMapper, HttpStatus.FORBIDDEN, "CSRF",
								"보안 토큰이 없거나 만료되었습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.");
					}
					else {
						writeProblem(response, jsonMapper, HttpStatus.FORBIDDEN, "FORBIDDEN", "이 기능을 사용할 권한이 없습니다.");
					}
				}));
		return http.build();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/** 로그인·로그아웃 때 토큰을 새로 발급하려고 저장소를 빈으로 꺼내 둔다 */
	@Bean
	CookieCsrfTokenRepository csrfTokenRepository(
			@Value("${aishop.session.cookie-secure:false}") boolean secure) {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").path("/").secure(secure));
		return repository;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		// 기본 bcrypt. 나중에 알고리즘을 바꿔도 기존 해시를 그대로 검증할 수 있다
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	private static void writeProblem(HttpServletResponse response, JsonMapper jsonMapper, HttpStatus status,
			String code, String detail) throws java.io.IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setProperty("code", code);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(), problem);
	}

}
