package com.aishop.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import com.aishop.api.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SocialLoginTests {

	private static final String SESSION_COOKIE = "AISHOP_SESSION";

	@Autowired
	MockMvc mvc;

	@MockitoBean
	SocialOAuthClient client;

	@BeforeEach
	void setUp() {
		when(client.isEnabled(any())).thenReturn(true);
		when(client.enabledProviders()).thenReturn(List.of(SocialProvider.KAKAO, SocialProvider.NAVER));
		when(client.authorizeUrl(any(), anyString()))
			.thenAnswer(inv -> "https://kauth.kakao.com/oauth/authorize?state=" + inv.getArgument(1));
	}

	@Test
	void firstLoginAsksAgreementThenSecondLoginGoesStraightIn() throws Exception {
		String id = uniq();
		String email = "kakao-" + id + "@example.test";
		when(client.fetchProfile(eq(SocialProvider.KAKAO), eq("code-1"), anyString()))
			.thenReturn(new SocialProfile(SocialProvider.KAKAO, id, email, "김카카오"));

		// 1) 처음: 약관 동의 화면으로
		Flow first = login("/orders");
		assertThat(first.location).isEqualTo("/signup/social?next=/orders");

		mvc.perform(get("/api/auth/social/pending").cookie(first.session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.providerLabel").value("카카오"))
			.andExpect(jsonPath("$.name").value("김카카오"))
			.andExpect(jsonPath("$.email").value(email));

		MvcResult signup = mvc.perform(post("/api/auth/social/signup").with(csrf()).cookie(first.session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"김고래","agreeAge14":true,"agreeTerms":true,"agreePrivacy":true,"agreeMarketing":false}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("김고래"))
			.andExpect(jsonPath("$.email").value(email))
			.andReturn();
		Cookie loggedIn = sessionOr(signup, first.session);

		mvc.perform(get("/api/me/login-methods").cookie(loggedIn))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasPassword").value(false))
			.andExpect(jsonPath("$.socialProviders[0]").value("KAKAO"));

		// 2) 다음부터는 바로 로그인돼 원래 가려던 곳으로
		Flow second = login("/orders");
		assertThat(second.location).isEqualTo("/orders");
		mvc.perform(get("/api/auth/me").cookie(second.session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("김고래"));
	}

	@Test
	void signupWithoutAgreementIsRejected() throws Exception {
		String id = uniq();
		when(client.fetchProfile(eq(SocialProvider.KAKAO), eq("code-1"), anyString()))
			.thenReturn(new SocialProfile(SocialProvider.KAKAO, id, null, "닉네임"));
		Flow first = login("/");

		mvc.perform(post("/api/auth/social/signup").with(csrf()).cookie(first.session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"닉네임","agreeAge14":true,"agreeTerms":false,"agreePrivacy":true}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.agreeTerms").exists());
	}

	@Test
	void emailOfExistingMemberIsNotLinkedAutomatically() throws Exception {
		String email = "dup-" + uniq() + "@example.test";
		mvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"abcd1234!","name":"기존회원",
						 "agreeAge14":true,"agreeTerms":true,"agreePrivacy":true}
						""".formatted(email)))
			.andExpect(status().isCreated());
		when(client.fetchProfile(eq(SocialProvider.KAKAO), eq("code-1"), anyString()))
			.thenReturn(new SocialProfile(SocialProvider.KAKAO, uniq(), email, "남"));

		Flow flow = login("/");
		assertThat(flow.location).startsWith("/login?socialError=EMAIL_IN_USE");
	}

	@Test
	void wrongStateIsRejected() throws Exception {
		MvcResult start = mvc.perform(get("/api/auth/social/kakao/start")).andExpect(status().isFound()).andReturn();
		mvc.perform(get("/api/auth/social/kakao/callback").param("code", "code-1").param("state", "forged")
				.cookie(start.getResponse().getCookie(SESSION_COOKIE)))
			.andExpect(status().isFound())
			.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
				.startsWith("/login?socialError=SOCIAL_STATE_MISMATCH"));
	}

	@Test
	void nextOnlyAllowsOurOwnPaths() {
		assertThat(SocialLoginController.safeNext("/cart")).isEqualTo("/cart");
		assertThat(SocialLoginController.safeNext("//evil.example")).isEqualTo("/");
		assertThat(SocialLoginController.safeNext("https://evil.example")).isEqualTo("/");
		assertThat(SocialLoginController.safeNext("/\\evil.example")).isEqualTo("/");
		assertThat(SocialLoginController.safeNext(null)).isEqualTo("/");
	}

	// ------------------------------------------------------------------

	private record Flow(String location, Cookie session) {
	}

	/** 시작 → (제공자 로그인) → 콜백 */
	private Flow login(String next) throws Exception {
		MvcResult start = mvc.perform(get("/api/auth/social/kakao/start").param("next", next))
			.andExpect(status().isFound())
			.andReturn();
		Cookie session = start.getResponse().getCookie(SESSION_COOKIE);
		String state = UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl())
			.build()
			.getQueryParams()
			.getFirst("state");

		MvcResult callback = mvc.perform(get("/api/auth/social/kakao/callback")
				.param("code", "code-1")
				.param("state", state)
				.cookie(session))
			.andExpect(status().isFound())
			.andReturn();
		return new Flow(callback.getResponse().getRedirectedUrl(), sessionOr(callback, session));
	}

	private static Cookie sessionOr(MvcResult result, Cookie previous) {
		Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
		return cookie != null && !cookie.getValue().isEmpty() ? cookie : previous;
	}

	private static String uniq() {
		return UUID.randomUUID().toString().substring(0, 12);
	}

}
