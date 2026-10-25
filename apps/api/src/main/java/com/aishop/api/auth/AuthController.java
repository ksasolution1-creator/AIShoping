package com.aishop.api.auth;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.auth.AuthRequests.ChangePassword;
import com.aishop.api.auth.AuthRequests.Login;
import com.aishop.api.auth.AuthRequests.Signup;
import com.aishop.api.auth.AuthRequests.Withdraw;
import com.aishop.api.common.web.ApiException;

@RestController
public class AuthController {

	private final AuthService authService;
	private final LoginSessions sessions;

	public AuthController(AuthService authService, LoginSessions sessions) {
		this.authService = authService;
		this.sessions = sessions;
	}

	/** XSRF-TOKEN 쿠키를 발급한다. 변경 요청 전에 쿠키가 없으면 먼저 호출한다. */
	@GetMapping("/api/auth/csrf")
	public Map<String, String> csrf(CsrfToken token) {
		return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
	}

	@GetMapping("/api/auth/me")
	public AuthUser me(@AuthenticationPrincipal AuthUser user) {
		if (user == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다.");
		}
		return user;
	}

	@PostMapping("/api/auth/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthUser signup(@Valid @RequestBody Signup request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		AuthUser user = authService.signup(request, httpRequest.getRemoteAddr());
		sessions.start(user, httpRequest, httpResponse);
		return user;
	}

	@PostMapping("/api/auth/login")
	public AuthUser login(@Valid @RequestBody Login request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		AuthUser user = authService.login(request.email(), request.password());
		sessions.start(user, httpRequest, httpResponse);
		return user;
	}

	@PostMapping("/api/auth/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		sessions.end(request, response);
	}

	/** 비밀번호 변경. 세션 ID를 새로 발급해 변경 전 세션 값을 다시 쓸 수 없게 한다. */
	@PostMapping("/api/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ChangePassword body,
			HttpServletRequest request) {
		authService.changePassword(user.id(), body.currentPassword(), body.newPassword());
		request.changeSessionId();
	}

	/** 회원 탈퇴 (앱스토어 심사 요건: 앱 안에서 탈퇴 가능해야 함) */
	@PostMapping("/api/me/withdraw")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdraw(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody Withdraw body,
			HttpServletRequest request, HttpServletResponse response) {
		authService.withdraw(user.id(), body.password());
		sessions.end(request, response);
	}

}
