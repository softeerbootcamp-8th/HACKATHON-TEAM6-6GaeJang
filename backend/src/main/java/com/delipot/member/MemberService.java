package com.delipot.member;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.member.dto.ProfileUpdateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository memberRepository;

	/** 온보딩 휴대폰 번호 실시간 중복확인. */
	public boolean isPhoneNumberAvailable(String phoneNumber) {
		return !memberRepository.existsByPhoneNumber(phoneNumber);
	}

	/** 온보딩 닉네임 실시간 중복확인. */
	public boolean isNicknameAvailable(String nickname) {
		return !memberRepository.existsByNickname(nickname);
	}

	/**
	 * 온보딩 완료 가입. 번호/닉네임 중복은 미리 거른다.
	 * (동시 가입 레이스는 DB unique 제약이 최종 방어선)
	 */
	@Transactional
	public Member register(String phoneNumber, String passwordHash, String nickname, String address) {
		return register(phoneNumber, passwordHash, nickname, address, null, null, null, null);
	}

	@Transactional
	public Member register(String phoneNumber, String passwordHash, String nickname, String address,
		String roadAddress, String jibunAddress, java.math.BigDecimal latitude, java.math.BigDecimal longitude) {
		if (memberRepository.existsByPhoneNumber(phoneNumber)) {
			throw new BusinessException(ErrorCode.DUPLICATE_PHONE);
		}
		if (memberRepository.existsByNickname(nickname)) {
			throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
		}
		return memberRepository.save(
			Member.register(phoneNumber, passwordHash, nickname, address, roadAddress, jibunAddress, latitude, longitude));
	}

	/**
	 * 여러 회원을 한 번에 읽는다. 팟 목록 카드의 참여자 아바타(닉네임)용.
	 * 팟마다 조회하면 목록 크기 × 참여자 수만큼 쿼리가 나가므로 호출부가 id를 모아 한 번에 부른다.
	 */
	public java.util.List<Member> findAllByIds(java.util.Collection<Long> ids) {
		return memberRepository.findAllById(ids);
	}

	public Member getById(Long id) {
		return memberRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	/**
	 * 로그인 검증용 — 없으면 empty 를 돌려주고, 실패 사유는 AuthService 가 통합 처리한다.
	 * 탈퇴 회원은 조회되지 않아 미가입과 동일하게 LOGIN_FAILED 로 흐른다.
	 */
	public Optional<Member> findByPhoneNumber(String phoneNumber) {
		return memberRepository.findByPhoneNumberAndWithdrawnAtIsNull(phoneNumber);
	}

	/**
	 * 프로필 수정. 닉네임은 보낸 경우에만, 주소는 보낸 경우에만 바꾼다.
	 * 닉네임이 본인의 현재 값과 같으면 중복확인을 건너뛴다(자기 자신은 중복이 아니다).
	 */
	@Transactional
	public Member updateProfile(Long memberId, ProfileUpdateRequest request) {
		Member member = getById(memberId);

		if (request.nickname() != null && !request.nickname().equals(member.getNickname())) {
			if (memberRepository.existsByNicknameAndIdNot(request.nickname(), memberId)) {
				throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
			}
			member.changeNickname(request.nickname());
		}
		if (request.address() != null) {
			member.changeAddress(request.address(), request.roadAddress(), request.jibunAddress(),
				request.latitude(), request.longitude());
		}
		return member;
	}

	/** 회원 탈퇴(soft delete). 탈퇴 가능 여부 검증과 참여 중인 팟 정리는 AuthService 가 먼저 처리한다. */
	@Transactional
	public void withdraw(Long memberId) {
		getById(memberId).withdraw();
	}
}
