package com.delipot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.delipot.auth.AuthService.SessionIssued;
import com.delipot.auth.dto.LoginRequest;
import com.delipot.auth.dto.SignupRequest;
import com.delipot.auth.crypto.PasswordHasher;
import com.delipot.auth.session.RememberMeStore;
import com.delipot.auth.session.SessionStore;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.member.Member;
import com.delipot.member.MemberService;
import com.delipot.pot.PotService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final Long MEMBER_ID = 1L;

	@Mock
	private MemberService memberService;
	@Mock
	private PotService potService;
	@Mock
	private SessionStore sessionStore;
	@Mock
	private RememberMeStore rememberMeStore;
	@Mock
	private PasswordHasher passwordHasher;

	@InjectMocks
	private AuthService authService;

	@Test
	@DisplayName("가입: 비밀번호를 해싱해 저장하고, rememberMe=true면 remember-me도 발급한다")
	void signupWithRemember() {
		Member member = mock(Member.class);
		given(member.getId()).willReturn(1L);
		given(passwordHasher.encode("secret1234")).willReturn("HASH");
		given(memberService.register("01012345678", "HASH", "철수", "서울", null, null, null, null)).willReturn(member);
		given(sessionStore.create(1L)).willReturn("sid");
		given(rememberMeStore.issue(1L)).willReturn("rid");

		SessionIssued issued = authService.signup(
			new SignupRequest("01012345678", "secret1234", "철수", "서울", true));

		assertThat(issued.sessionId()).isEqualTo("sid");
		assertThat(issued.rememberMeToken()).isEqualTo("rid");
	}

	@Test
	@DisplayName("가입: rememberMe=false면 remember-me 토큰을 발급하지 않는다")
	void signupWithoutRemember() {
		Member member = mock(Member.class);
		given(member.getId()).willReturn(1L);
		given(passwordHasher.encode("secret1234")).willReturn("HASH");
		given(memberService.register("01012345678", "HASH", "철수", "서울", null, null, null, null)).willReturn(member);
		given(sessionStore.create(1L)).willReturn("sid");

		SessionIssued issued = authService.signup(
			new SignupRequest("01012345678", "secret1234", "철수", "서울", false));

		assertThat(issued.rememberMeToken()).isNull();
		verify(rememberMeStore, never()).issue(org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	@DisplayName("로그인: 비밀번호가 맞으면 세션을 발급한다")
	void loginSuccess() {
		Member member = mock(Member.class);
		given(member.getId()).willReturn(1L);
		given(member.getPassword()).willReturn("HASH");
		given(memberService.findByPhoneNumber("01012345678")).willReturn(Optional.of(member));
		given(passwordHasher.matches("secret1234", "HASH")).willReturn(true);
		given(sessionStore.create(1L)).willReturn("sid");

		SessionIssued issued = authService.login(
			new LoginRequest("01012345678", "secret1234", false));

		assertThat(issued.sessionId()).isEqualTo("sid");
		assertThat(issued.rememberMeToken()).isNull();
	}

	@Test
	@DisplayName("로그인: 비밀번호가 틀리면 LOGIN_FAILED")
	void loginWrongPassword() {
		Member member = mock(Member.class);
		given(member.getPassword()).willReturn("HASH");
		given(memberService.findByPhoneNumber("01012345678")).willReturn(Optional.of(member));
		given(passwordHasher.matches("wrongpass1", "HASH")).willReturn(false);

		assertThatThrownBy(() -> authService.login(
			new LoginRequest("01012345678", "wrongpass1", false)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.LOGIN_FAILED);
	}

	@Test
	@DisplayName("로그인: 없는 번호도 동일하게 LOGIN_FAILED (사유 구분 없음)")
	void loginUnknownPhone() {
		given(memberService.findByPhoneNumber("01000000000")).willReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(
			new LoginRequest("01000000000", "secret1234", false)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.LOGIN_FAILED);
	}

	@Test
	@DisplayName("로그아웃: 세션과 remember-me를 모두 삭제한다")
	void logoutDeletesBoth() {
		authService.logout("sid", "rid");

		verify(sessionStore).delete("sid");
		verify(rememberMeStore).delete("rid");
	}

	@Test
	@DisplayName("로그아웃: 토큰이 없으면 아무것도 삭제하지 않는다")
	void logoutNulls() {
		authService.logout(null, null);

		verifyNoInteractions(sessionStore, rememberMeStore);
	}

	@Test
	@DisplayName("탈퇴: 총대로 있는 진행 중인 팟이 있으면 막고, 아무것도 정리하지 않는다")
	void withdrawBlockedByActiveHostedPot() {
		given(potService.hasActiveHostedPot(MEMBER_ID)).willReturn(true);

		assertThatThrownBy(() -> authService.withdraw(MEMBER_ID))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.MEMBER_HAS_ACTIVE_POT);

		verify(potService, never()).leaveAllActivePots(MEMBER_ID);
		verify(memberService, never()).withdraw(MEMBER_ID);
		verifyNoInteractions(sessionStore, rememberMeStore);
	}

	@Test
	@DisplayName("탈퇴: 총대인 진행 중 팟이 없으면 참여 중인 팟을 나간 뒤 탈퇴하고, 다른 기기 세션까지 전부 정리한다")
	void withdrawSucceeds() {
		given(potService.hasActiveHostedPot(MEMBER_ID)).willReturn(false);

		authService.withdraw(MEMBER_ID);

		verify(potService).leaveAllActivePots(MEMBER_ID);
		verify(memberService).withdraw(MEMBER_ID);
		verify(sessionStore).deleteAllByMemberId(MEMBER_ID);
		verify(rememberMeStore).deleteAllByMemberId(MEMBER_ID);
	}
}
