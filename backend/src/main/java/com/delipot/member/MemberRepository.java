package com.delipot.member;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

	/** 온보딩 닉네임 실시간 중복확인용. */
	boolean existsByNickname(String nickname);

	/** 프로필 수정 시 닉네임 중복확인. 본인의 현재 닉네임은 중복으로 치지 않는다. */
	boolean existsByNicknameAndIdNot(String nickname, Long id);

	/** 가입 시 번호 중복(=이미 가입된 회원) 판별용. */
	boolean existsByPhoneNumber(String phoneNumber);

	/** 재방문 로그인: 번호로 기존 회원 조회. 탈퇴 회원은 로그인 대상에서 제외한다. */
	Optional<Member> findByPhoneNumberAndWithdrawnAtIsNull(String phoneNumber);
}
