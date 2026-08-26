package com.delipot.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 프로필 수정(본인 제외 중복확인)과 로그인(탈퇴 회원 제외)이 기대는 두 파생 쿼리가
 * 실제 DB에서 맞게 동작하는지 확인한다. 단위 테스트는 리포지토리를 목으로 대체하므로
 * 메서드 이름 자체가 틀려도(런타임 전까지는) 잡히지 않는다.
 */
@DataJpaTest
@ActiveProfiles("h2")
class MemberRepositoryIntegrationTest {

	@Autowired
	private MemberRepository memberRepository;

	private Member save(String phoneNumber, String nickname) {
		return memberRepository.save(Member.register(phoneNumber, "HASH", nickname, "학동로 171"));
	}

	@Test
	@DisplayName("existsByNicknameAndIdNot: 본인의 현재 닉네임은 중복으로 잡지 않는다")
	void existsByNicknameAndIdNotExcludesSelf() {
		Member me = save("01011111111", "치킨조아");

		assertThat(memberRepository.existsByNicknameAndIdNot("치킨조아", me.getId())).isFalse();
	}

	@Test
	@DisplayName("existsByNicknameAndIdNot: 다른 회원이 쓰는 닉네임은 중복으로 잡는다")
	void existsByNicknameAndIdNotDetectsOthers() {
		Member me = save("01011111111", "치킨조아");
		save("01022222222", "피자조아");

		assertThat(memberRepository.existsByNicknameAndIdNot("피자조아", me.getId())).isTrue();
	}

	@Test
	@DisplayName("findByPhoneNumberAndWithdrawnAtIsNull: 탈퇴한 회원은 조회되지 않는다")
	void findByPhoneNumberExcludesWithdrawn() {
		Member member = save("01033333333", "탈퇴회원");
		member.withdraw();
		memberRepository.saveAndFlush(member);

		assertThat(memberRepository.findByPhoneNumberAndWithdrawnAtIsNull("01033333333")).isEmpty();
	}

	@Test
	@DisplayName("findByPhoneNumberAndWithdrawnAtIsNull: 정상 회원은 그대로 조회된다")
	void findByPhoneNumberFindsActiveMember() {
		save("01044444444", "정상회원");

		assertThat(memberRepository.findByPhoneNumberAndWithdrawnAtIsNull("01044444444")).isPresent();
	}

	@Test
	@DisplayName("탈퇴 후 익명화: 같은 phoneNumber/nickname으로 재가입해도 unique 제약에 걸리지 않는다")
	void withdrawnMemberFreesUpPhoneNumberAndNickname() {
		Member withdrawn = save("01055555555", "탈퇴예정");
		withdrawn.withdraw();
		memberRepository.saveAndFlush(withdrawn);

		Member rejoined = save("01055555555", "탈퇴예정");

		assertThat(memberRepository.existsByPhoneNumber("01055555555")).isTrue();
		assertThat(rejoined.getPhoneNumber()).isEqualTo("01055555555");
		assertThat(rejoined.getNickname()).isEqualTo("탈퇴예정");
		assertThat(memberRepository.findById(withdrawn.getId()).orElseThrow().getPhoneNumber())
			.isEqualTo("DEL" + withdrawn.getId());
	}
}
