package com.delipot.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delipot.auth.dto.LoginRequest;
import com.delipot.auth.dto.SignupRequest;
import com.delipot.auth.crypto.PasswordHasher;
import com.delipot.auth.session.RememberMeStore;
import com.delipot.auth.session.SessionStore;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.member.Member;
import com.delipot.member.MemberService;
import com.delipot.member.dto.MemberResponse;
import com.delipot.pot.PotService;

import lombok.RequiredArgsConstructor;

/**
 * 인증 흐름 조율. 회원 생성/조회는 {@link MemberService} 에, 세션 발급/삭제는 {@link SessionStore} 에 위임하고
 * 둘을 엮는다. 쿠키 세팅은 컨트롤러(웹 계층)가 담당해 서비스가 서블릿에 의존하지 않게 한다.
 *
 * <p>회원 탈퇴는 팟 도메인의 상태(총대인 진행 중 팟, 참여 중인 팟)를 함께 봐야 하는 교차 도메인
 * 작업이라 여기서 {@link PotService}까지 조율한다. 반대 방향 의존({@code PotService -> MemberService})은
 * 이미 있어서, {@code MemberService}가 {@code PotService}를 알게 하면 순환 참조가 생긴다 — 그래서
 * 이 조율은 이미 양쪽을 아는 이 서비스에 둔다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

	private final MemberService memberService;
	private final PotService potService;
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

	/** 내 정보 조회. "총대 N회" 배지는 매번 실제 카운트를 채운다. */
	public MemberResponse me(Long memberId) {
		Member member = memberService.getById(memberId);
		return MemberResponse.from(member, potService.countHostedPots(memberId));
	}

	/**
	 * 회원 탈퇴. 총대로 있는 진행 중인 팟이 있으면 막고, 참여 중인 팟은 자동으로 나가기 처리한 뒤
	 * soft delete하고 다른 기기에서 로그인된 것까지 포함해 이 회원의 모든 세션을 정리한다.
	 * 하나의 트랜잭션으로 묶어 "나가기는 됐는데 탈퇴는 실패" 같은 어중간한 상태를 막는다.
	 */
	@Transactional
	public void withdraw(Long memberId) {
		if (potService.hasActiveHostedPot(memberId)) {
			throw new BusinessException(ErrorCode.MEMBER_HAS_ACTIVE_POT);
		}
		potService.leaveAllActivePots(memberId);
		memberService.withdraw(memberId);
		sessionStore.deleteAllByMemberId(memberId);
		rememberMeStore.deleteAllByMemberId(memberId);
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
