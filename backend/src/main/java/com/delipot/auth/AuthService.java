package com.delipot.auth;

import org.springframework.stereotype.Service;

import com.delipot.auth.dto.LoginRequest;
import com.delipot.auth.dto.SignupRequest;
import com.delipot.auth.crypto.PasswordHasher;
import com.delipot.auth.session.RememberMeStore;
import com.delipot.auth.session.SessionStore;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.member.Member;
import com.delipot.member.MemberService;

import lombok.RequiredArgsConstructor;

/**
 * 인증 흐름 조율. 회원 생성/조회는 {@link MemberService} 에, 세션 발급/삭제는 {@link SessionStore} 에 위임하고
 * 둘을 엮는다. 쿠키 세팅은 컨트롤러(웹 계층)가 담당해 서비스가 서블릿에 의존하지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

	private final MemberService memberService;
	private final SessionStore sessionStore;
	private final RememberMeStore rememberMeStore;
	private final PasswordHasher passwordHasher;

	public SessionIssued signup(SignupRequest request) {
		String passwordHash = passwordHasher.encode(request.password());
		Member member = memberService.register(
			request.phoneNumber(),
			passwordHash,
			request.nickname(),
			request.address(),
			request.roadAddress(),
			request.jibunAddress(),
			request.latitude(),
			request.longitude()
		);
		return issueFor(member, request.rememberMe());
	}

	public SessionIssued login(LoginRequest request) {
		// 번호가 없거나 비번이 틀리거나 — 사유를 구분하지 않고 동일하게 LOGIN_FAILED.
		Member member = memberService.findByPhoneNumber(request.phoneNumber())
			.filter(m -> passwordHasher.matches(request.password(), m.getPassword()))
			.orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
		return issueFor(member, request.rememberMe());
	}

	public void logout(String sessionId, String rememberMeToken) {
		if (sessionId != null) {
			sessionStore.delete(sessionId);
		}
		if (rememberMeToken != null) {
			rememberMeStore.delete(rememberMeToken);
		}
	}

	/** 세션은 항상 발급하고, remember-me 토큰은 '자동 로그인' 을 선택한 경우에만 발급한다. */
	private SessionIssued issueFor(Member member, boolean rememberMe) {
		String sessionId = sessionStore.create(member.getId());
		String rememberMeToken = rememberMe ? rememberMeStore.issue(member.getId()) : null;
		return new SessionIssued(member, sessionId, rememberMeToken);
	}

	/** 발급 결과 — 응답 바디(member) + 쿠키에 실을 세션 키/remember-me 토큰. */
	public record SessionIssued(Member member, String sessionId, String rememberMeToken) {
	}
}
