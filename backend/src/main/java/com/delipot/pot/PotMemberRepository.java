package com.delipot.pot;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PotMemberRepository extends JpaRepository<PotMember, Long> {

	/**
	 * 회원 탈퇴 시 자동 나가기 대상 팟 id. 탈퇴 검증에서 "총대인 ACTIVE 팟 없음"을 먼저 확인하므로
	 * 여기 걸리는 potId는 전부 참여자로만 속한 팟이다.
	 */
	@Query("""
		select pm.potId from PotMember pm
		where pm.memberId = :memberId
		  and exists (
		    select 1 from Pot p
		    where p.id = pm.potId and p.status = com.delipot.pot.PotStatus.ACTIVE
		  )
		""")
	List<Long> findActivePotIdsByMemberId(@Param("memberId") Long memberId);

	/** 카드 우측 아바타용. 팟마다 따로 조회하면 목록 크기만큼 쿼리가 늘어나(N+1) 한 번에 긁는다. */
	List<PotMember> findByPotIdIn(Collection<Long> potIds);

	boolean existsByPotIdAndMemberId(Long potId, Long memberId);

	/** 나가기. 삭제된 행 수를 돌려주므로 "참여하지 않았는데 나가기"를 호출부에서 구분할 수 있다. */
	long deleteByPotIdAndMemberId(Long potId, Long memberId);
}
