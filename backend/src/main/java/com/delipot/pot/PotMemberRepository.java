package com.delipot.pot;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PotMemberRepository extends JpaRepository<PotMember, Long> {

	/** 홈 진입 시 "내가 연 팟 + 참여중인 팟"의 후보 id를 뽑는 출발점. */
	List<PotMember> findByMemberId(Long memberId);

	/**
	 * 카드 우측 아바타용. 팟마다 따로 조회하면 목록 크기만큼 쿼리가 늘어나(N+1)
	 * 한 번에 긁어 서비스에서 potId로 묶는다.
	 */
	List<PotMember> findByPotIdIn(Collection<Long> potIds);

	boolean existsByPotIdAndMemberId(Long potId, Long memberId);

	/** 나가기. 삭제된 행 수를 돌려주므로 "참여하지 않았는데 나가기"를 호출부에서 구분할 수 있다. */
	long deleteByPotIdAndMemberId(Long potId, Long memberId);
}
