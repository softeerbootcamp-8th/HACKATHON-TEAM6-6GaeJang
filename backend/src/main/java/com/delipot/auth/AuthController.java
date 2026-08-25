package com.delipot.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delipot.auth.AuthService.SessionIssued;
import com.delipot.auth.dto.LoginRequest;
import com.delipot.auth.dto.SignupRequest;
import com.delipot.auth.web.SessionCookieManager;
import com.delipot.global.response.ApiResponse;
import com.delipot.member.MemberService;
import com.delipot.member.dto.MemberResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth", description = "온보딩 가입 / 로그인 / 세션")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final MemberService memberService;
	private final SessionCookieManager cookieManager;

	@Operation(summary = "온보딩 가입", description = "번호/닉네임/주소로 가입하고 세션 쿠키를 발급한다.")
	@PostMapping("/signup")
	public ApiResponse<MemberResponse> signup(
		@Valid @RequestBody SignupRequest request,
		HttpServletResponse response
	) {
		SessionIssued issued = authService.signup(request);
		setAuthCookies(response, issued);
		return ApiResponse.ok(MemberResponse.from(issued.member()));
	}

	@Operation(summary = "로그인", description = "기존 회원 번호로 세션을 재발급한다.")
	@PostMapping("/login")
	public ApiResponse<MemberResponse> login(
		@Valid @RequestBody LoginRequest request,
		HttpServletResponse response
	) {
		SessionIssued issued = authService.login(request);
		setAuthCookies(response, issued);
		return ApiResponse.ok(MemberResponse.from(issued.member()));
	}

	@Operation(summary = "로그아웃", description = "세션/remember-me 를 제거하고 두 쿠키를 만료시킨다.")
	@RequireAuthenticate
	@PostMapping("/logout")
	public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		String sessionId = cookieManager.resolveSessionId(request).orElse(null);
		String rememberMe = cookieManager.resolveRememberMe(request).orElse(null);
		authService.logout(sessionId, rememberMe);
		response.addHeader(HttpHeaders.SET_COOKIE, cookieManager.expire().toString());
		response.addHeader(HttpHeaders.SET_COOKIE, cookieManager.expireRememberMe().toString());
		return ApiResponse.ok();
	}

	@Operation(summary = "내 정보", description = "현재 로그인한 회원 정보를 반환한다.")
	@RequireAuthenticate
	@GetMapping("/me")
	public ApiResponse<MemberResponse> me(@LoginMember Long memberId) {
		return ApiResponse.ok(MemberResponse.from(memberService.getById(memberId)));
	}

	/**
	 * 세션 쿠키는 항상 내려주고, remember-me 쿠키는 '자동 로그인' 을 선택한 경우에만 발급한다.
	 * 선택하지 않았다면 혹시 남아있을 RID 쿠키를 만료시켜 정리한다.
	 */
	private void setAuthCookies(HttpServletResponse response, SessionIssued issued) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookieManager.issue(issued.sessionId()).toString());
		if (issued.rememberMeToken() != null) {
			response.addHeader(HttpHeaders.SET_COOKIE,
				cookieManager.issueRememberMe(issued.rememberMeToken()).toString());
		} else {
			response.addHeader(HttpHeaders.SET_COOKIE, cookieManager.expireRememberMe().toString());
		}
	}
}
