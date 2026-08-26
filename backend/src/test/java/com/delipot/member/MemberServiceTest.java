package com.delipot.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.member.dto.ProfileUpdateRequest;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

	private static final Long MEMBER_ID = 1L;

	@Mock
	private MemberRepository memberRepository;

	@InjectMocks
	private MemberService memberService;

	private Member member(String nickname) {
		return Member.register("01012345678", "HASH", nickname, "학동로 171");
	}

	@Test
	@DisplayName("프로필 수정: 닉네임을 바꾸면 본인을 제외한 중복만 확인한다")
	void updateProfileChangesNickname() {
		Member member = member("치킨조아");
		given(memberRepository.findById(MEMBER_ID)).willReturn(java.util.Optional.of(member));
		given(memberRepository.existsByNicknameAndIdNot("피자조아", MEMBER_ID)).willReturn(false);

		Member updated = memberService.updateProfile(MEMBER_ID,
			new ProfileUpdateRequest("피자조아", null, null, null, null, null));

		assertThat(updated.getNickname()).isEqualTo("피자조아");
	}

	@Test
	@DisplayName("프로필 수정: 본인의 현재 닉네임과 같으면 중복확인을 건너뛰고 그대로 통과한다")
	void updateProfileSameNicknameSkipsDuplicateCheck() {
		Member member = member("치킨조아");
		given(memberRepository.findById(MEMBER_ID)).willReturn(java.util.Optional.of(member));

		Member updated = memberService.updateProfile(MEMBER_ID,
			new ProfileUpdateRequest("치킨조아", null, null, null, null, null));

		assertThat(updated.getNickname()).isEqualTo("치킨조아");
		verify(memberRepository, never()).existsByNicknameAndIdNot("치킨조아", MEMBER_ID);
	}

	@Test
	@DisplayName("프로필 수정: 다른 회원이 쓰는 닉네임이면 DUPLICATE_NICKNAME")
	void updateProfileDuplicateNickname() {
		Member member = member("치킨조아");
		given(memberRepository.findById(MEMBER_ID)).willReturn(java.util.Optional.of(member));
		given(memberRepository.existsByNicknameAndIdNot("피자조아", MEMBER_ID)).willReturn(true);

		assertThatThrownBy(() -> memberService.updateProfile(MEMBER_ID,
			new ProfileUpdateRequest("피자조아", null, null, null, null, null)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.DUPLICATE_NICKNAME);

		assertThat(member.getNickname()).isEqualTo("치킨조아");
	}

	@Test
	@DisplayName("프로필 수정: 주소만 보내면 닉네임은 그대로 두고 주소만 바뀐다")
	void updateProfileChangesAddressOnly() {
		Member member = member("치킨조아");
		given(memberRepository.findById(MEMBER_ID)).willReturn(java.util.Optional.of(member));

		Member updated = memberService.updateProfile(MEMBER_ID,
			new ProfileUpdateRequest(null, "역삼로 1", "역삼로 1", "역삼동 1", new BigDecimal("37.5"), new BigDecimal("127.0")));

		assertThat(updated.getNickname()).isEqualTo("치킨조아");
		assertThat(updated.getAddress()).isEqualTo("역삼로 1");
		assertThat(updated.getRoadAddress()).isEqualTo("역삼로 1");
	}

	@Test
	@DisplayName("회원 탈퇴: withdraw()를 호출하면 탈퇴 시각이 세팅되고 phoneNumber/nickname이 익명화된다")
	void withdrawSetsWithdrawnAt() {
		Member member = member("치킨조아");
		ReflectionTestUtils.setField(member, "id", MEMBER_ID);
		given(memberRepository.findById(MEMBER_ID)).willReturn(java.util.Optional.of(member));

		memberService.withdraw(MEMBER_ID);

		assertThat(member.getWithdrawnAt()).isNotNull();
		assertThat(member.getPhoneNumber()).isEqualTo("DEL" + MEMBER_ID);
		assertThat(member.getNickname()).isEqualTo("탈퇴" + MEMBER_ID);
	}

	@Test
	@DisplayName("로그인 조회: 탈퇴 회원은 제외하는 리포지토리 메서드를 그대로 위임한다")
	void findByPhoneNumberDelegatesWithdrawnExclusion() {
		Member member = member("치킨조아");
		given(memberRepository.findByPhoneNumberAndWithdrawnAtIsNull("01012345678"))
			.willReturn(java.util.Optional.of(member));

		assertThat(memberService.findByPhoneNumber("01012345678")).contains(member);
	}
}
