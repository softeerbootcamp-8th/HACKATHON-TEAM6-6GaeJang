package com.delipot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

/**
 * 인증 전 구간 통합 테스트. 필터→인터셉터→리졸버 + 실제 InMemory 저장소 + 실제 BCrypt 를 h2 위에서 돈다.
 * 데이터가 클래스 내에서 공유되므로 테스트마다 번호/닉네임을 다르게 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AuthIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private String signupBody(String phone, String pw, String nick, String addr, boolean remember) {
		return """
			{"phoneNumber":"%s","password":"%s","nickname":"%s","address":"%s","rememberMe":%b}"""
			.formatted(phone, pw, nick, addr, remember);
	}

	private String loginBody(String phone, String pw, boolean remember) {
		return """
			{"phoneNumber":"%s","password":"%s","rememberMe":%b}""".formatted(phone, pw, remember);
	}

	private MvcResult signup(String phone, String nick, boolean remember) throws Exception {
		return mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupBody(phone, "secret1234", nick, "서울", remember)))
			.andExpect(status().isOk())
			.andReturn();
	}

	@Test
	@DisplayName("가입 → 세션 쿠키로 /me, 쿠키 없으면 401")
	void signupThenMe() throws Exception {
		MvcResult result = signup("01011110001", "철수", false);
		Cookie sid = result.getResponse().getCookie("SID");
		assertThat(sid).isNotNull();

		mockMvc.perform(get("/api/auth/me").cookie(sid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nickname").value("철수"));

		mockMvc.perform(get("/api/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	@DisplayName("rememberMe=false면 RID 쿠키가 만료(Max-Age=0)로 내려간다")
	void noRememberCookieWhenUnchecked() throws Exception {
		Cookie rid = signup("01011110002", "영희", false).getResponse().getCookie("RID");

		assertThat(rid).isNotNull();
		assertThat(rid.getMaxAge()).isZero();
	}

	@Test
	@DisplayName("rememberMe=true면 RID 쿠키가 장기(Max-Age>0)로 내려간다")
	void rememberCookieWhenChecked() throws Exception {
		Cookie rid = signup("01011110003", "민수", true).getResponse().getCookie("RID");

		assertThat(rid).isNotNull();
		assertThat(rid.getMaxAge()).isPositive();
	}

	@Test
	@DisplayName("틀린 비밀번호 로그인은 LOGIN_FAILED")
	void loginWrongPassword() throws Exception {
		signup("01011110004", "관리", false);

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginBody("01011110004", "wrongpass1", false)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
	}

	@Test
	@DisplayName("닉네임 중복확인 / 번호 중복 가입")
	void duplicates() throws Exception {
		signup("01011110005", "중복이", false);

		mockMvc.perform(get("/api/members/check-nickname").param("nickname", "중복이"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.available").value(false));

		mockMvc.perform(get("/api/members/check-nickname").param("nickname", "새이름"))
			.andExpect(jsonPath("$.data.available").value(true));

		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupBody("01011110005", "secret1234", "다른닉", "부산", false)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("DUPLICATE_PHONE"));
	}

	@Test
	@DisplayName("remember-me: RID만으로 새 세션 발급 + 옛 RID는 회전으로 무효화")
	void rememberMeSilentReLoginAndRotation() throws Exception {
		Cookie oldRid = signup("01011110006", "자동이", true).getResponse().getCookie("RID");
		assertThat(oldRid).isNotNull();

		// 세션 쿠키 없이 RID 만으로 요청 → 조용한 재로그인(200) + 새 SID/RID 발급
		MvcResult reLogin = mockMvc.perform(get("/api/auth/me").cookie(oldRid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nickname").value("자동이"))
			.andReturn();

		Cookie newSid = reLogin.getResponse().getCookie("SID");
		Cookie newRid = reLogin.getResponse().getCookie("RID");
		assertThat(newSid).isNotNull();
		assertThat(newRid).isNotNull();
		assertThat(newRid.getValue()).isNotEqualTo(oldRid.getValue());

		// 회전됐으므로 옛 RID 재사용은 실패(401)
		mockMvc.perform(get("/api/auth/me").cookie(new Cookie("RID", oldRid.getValue())))
			.andExpect(status().isUnauthorized());

		// 새 RID 는 유효
		mockMvc.perform(get("/api/auth/me").cookie(new Cookie("RID", newRid.getValue())))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("로그아웃하면 세션/remember-me가 모두 무효화된다")
	void logoutInvalidatesBoth() throws Exception {
		MvcResult signup = signup("01011110007", "나갈이", true);
		Cookie sid = signup.getResponse().getCookie("SID");
		Cookie rid = signup.getResponse().getCookie("RID");

		mockMvc.perform(post("/api/auth/logout").cookie(sid, rid))
			.andExpect(status().isOk());

		// 옛 세션 쿠키로는 401
		mockMvc.perform(get("/api/auth/me").cookie(new Cookie("SID", sid.getValue())))
			.andExpect(status().isUnauthorized());

		// 옛 remember-me 로도 재로그인 불가
		mockMvc.perform(get("/api/auth/me").cookie(new Cookie("RID", rid.getValue())))
			.andExpect(status().isUnauthorized());
	}
}
