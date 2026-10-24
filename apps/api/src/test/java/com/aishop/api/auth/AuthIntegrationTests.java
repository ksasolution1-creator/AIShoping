package com.aishop.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.aishop.api.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTests {

	private static final String SESSION_COOKIE = "AISHOP_SESSION";
	private static final String PASSWORD = "abcd1234!";

	@Autowired
	MockMvc mvc;

	@Autowired
	AuthService authService;

	@Test
	void signupLogsInAndMeReturnsUser() throws Exception {
		String email = uniqueEmail();
		Cookie session = signup(email);

		mvc.perform(get("/api/auth/me").cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.type").value("CUSTOMER"));
	}

	@Test
	void signupRequiresRequiredAgreementsAndStrongPassword() throws Exception {
		mvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"short","name":"홍길동",
						 "agreeAge14":false,"agreeTerms":true,"agreePrivacy":true,"agreeMarketing":false}
						""".formatted(uniqueEmail())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.fieldErrors.password").exists())
			.andExpect(jsonPath("$.fieldErrors.agreeAge14").value("만 14세 이상만 가입할 수 있습니다."));
	}

	@Test
	void duplicateEmailIsRejected() throws Exception {
		String email = uniqueEmail();
		signup(email);

		mvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(email.toUpperCase())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));
	}

	@Test
	void meWithoutLoginIs401() throws Exception {
		mvc.perform(get("/api/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void postWithoutCsrfTokenIsRejected() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"a@b.c\",\"password\":\"x\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF"));
	}

	@Test
	void loginLocksAfterFiveFailures() throws Exception {
		String email = uniqueEmail();
		signup(email);

		for (int i = 0; i < 5; i++) {
			login(email, "wrong-password1").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		}
		// 잠긴 뒤에는 올바른 비밀번호도 거부한다
		login(email, PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
	}

	@Test
	void unknownEmailGetsSameErrorAsWrongPassword() throws Exception {
		login(uniqueEmail(), PASSWORD).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void adminApiRequiresStaffRole() throws Exception {
		mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());

		Cookie customer = signup(uniqueEmail());
		mvc.perform(get("/api/admin/products").cookie(customer))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		String adminEmail = uniqueEmail();
		authService.createStaff(adminEmail, PASSWORD, "관리자", Set.of("ADMIN"), 1L);
		Cookie admin = sessionOf(login(adminEmail, PASSWORD).andExpect(status().isOk()).andReturn());
		mvc.perform(get("/api/admin/products").cookie(admin)).andExpect(status().isOk());
	}

	@Test
	void shopApiIsPublic() throws Exception {
		mvc.perform(get("/api/products")).andExpect(status().isOk());
		mvc.perform(get("/api/categories")).andExpect(status().isOk());
	}

	@Test
	void logoutEndsSession() throws Exception {
		Cookie session = signup(uniqueEmail());

		mvc.perform(post("/api/auth/logout").with(csrf()).cookie(session)).andExpect(status().isNoContent());

		mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void withdrawRemovesPersonalInfoAndBlocksLogin() throws Exception {
		String email = uniqueEmail();
		Cookie session = signup(email);

		mvc.perform(post("/api/me/withdraw").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isNoContent());

		login(email, PASSWORD).andExpect(status().isUnauthorized());
		// 같은 이메일로 다시 가입할 수 있다
		signup(email);
	}

	// ------------------------------------------------------------------

	private Cookie signup(String email) throws Exception {
		MvcResult result = mvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(email)))
			.andExpect(status().isCreated())
			.andReturn();
		return sessionOf(result);
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	private static Cookie sessionOf(MvcResult result) {
		Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
		assertThat(cookie).as("세션 쿠키").isNotNull();
		return cookie;
	}

	private static String signupJson(String email) {
		return """
				{"email":"%s","password":"%s","name":"홍길동",
				 "agreeAge14":true,"agreeTerms":true,"agreePrivacy":true,"agreeMarketing":true}
				""".formatted(email, PASSWORD);
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
	}

}
