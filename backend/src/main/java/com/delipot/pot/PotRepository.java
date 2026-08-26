package com.delipot.pot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PotRepository extends JpaRepository<Pot, Long> {

	/** 채팅방 헤더/배너의 roomId → potId 역조회({@link Pot#chatRoomId} 참고). */
	Optional<Pot> findByChatRoomId(Long chatRoomId);

	/**
	 * 총대가 나눔 완료를 누르지 않고 방치한 팟을 일괄 완료 처리한다. 목록 조회 앞에서 한 번 실행한다.
	 *
	 * <p>{@code countsAsHostExperience} 계산식이 {@link Pot#complete()}와 중복돼 있다 —
	 * 벌크 UPDATE는 엔티티를 거치지 않으므로 한쪽만 고치면 배지가 틀어진다.
	 * {@code clearAutomatically}는 벌크 UPDATE가 우회한 1차 캐시를 비워 바로 뒤 조회가
	 * 낡은 상태를 읽지 않게 한다.
	 *
	 * @param threshold 이 시각 이전에 마감된 팟을 완료 처리한다 (현재 시각 - 5시간)
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update Pot p
		   set p.status = com.delipot.pot.PotStatus.DONE,
		       p.countsAsHostExperience = case
		           when p.currentMemberCount > 1 or p.hasMemberLeft = true then true
		           else false
		       end
		 where p.status = com.delipot.pot.PotStatus.ACTIVE
		   and p.deadline <= :threshold
		""")
	int completeAbandoned(@Param("threshold") OffsetDateTime threshold);

	/**
	 * {@link #completeAbandoned}가 완료 처리할 팟들의 채팅방 id. 벌크 UPDATE는 엔티티를 읽지 않아
	 * "어느 방에 완료 공지를 남길지" 알 방법이 없으므로 같은 조건으로 UPDATE 직전에 먼저 조회한다.
	 * chatRoomId가 없는(채팅 연동 전) 팟은 공지를 남길 방이 없어 뺀다.
	 */
	@Query("""
		select p.chatRoomId from Pot p
		where p.status = com.delipot.pot.PotStatus.ACTIVE
		  and p.deadline <= :threshold
		  and p.chatRoomId is not null
		""")
	List<Long> findChatRoomIdsAbandoned(@Param("threshold") OffsetDateTime threshold);

	/**
	 * "전체 배달팟" 섹션의 후보. 정확한 반경 판정은 서비스에서 구면 거리로 한 번 더 거른다.
	 *
	 * <p>native query 대신 JPQL인 이유는 MySQL의 {@code ST_Distance_Sphere}가 h2 테스트에서
	 * 실행되지 않기 때문이다. 내가 총대거나 이미 참여한 팟은 위쪽 내 섹션과 중복되므로
	 * 상관 서브쿼리로 뺀다({@code uk_pot_members_pot_member} 인덱스를 탄다).
	 *
	 * <p>{@code keyword}는 빈 문자열이면 검색 없이 전체를 뜻한다. 호출부에서 {@code %}, {@code _}를
	 * 이스케이프해 넘기므로 여기서는 {@code escape '\\'}만 선언한다.
	 */
	@Query("""
		select p from Pot p
		where p.status = com.delipot.pot.PotStatus.ACTIVE
		  and p.deadline > :now
		  and p.currentMemberCount < p.capacity
		  and not exists (
		    select 1 from PotMember pm
		    where pm.potId = p.id and pm.memberId = :memberId
		  )
		  and p.latitude between :minLatitude and :maxLatitude
		  and p.longitude between :minLongitude and :maxLongitude
		  and (:keyword = '' or lower(p.storeName) like lower(concat('%', :keyword, '%')) escape '\\')
		order by p.deadline asc, p.id asc
		""")
	List<Pot> findOpenPotsInBox(
		@Param("now") OffsetDateTime now,
		@Param("memberId") Long memberId,
		@Param("minLatitude") BigDecimal minLatitude,
		@Param("maxLatitude") BigDecimal maxLatitude,
		@Param("minLongitude") BigDecimal minLongitude,
		@Param("maxLongitude") BigDecimal maxLongitude,
		@Param("keyword") String keyword,
		Limit limit
	);

	/**
	 * "총대 N회" 배지. 완료된 팟 중에서도 총대 외 참여자가 한 번이라도 있었던 팟만
	 * 센다({@link Pot#countsAsHostExperience} 참고).
	 */
	long countByHostIdAndCountsAsHostExperienceTrue(Long hostId);

	/** 회원 탈퇴 검증용 — 총대로 있는 살아있는(ACTIVE) 팟이 있는지. */
	boolean existsByHostIdAndStatus(Long hostId, PotStatus status);

	/**
	 * "내가 연 배달팟" / "참여중인 배달팟" 섹션. 내가 속한 팟만 찾고 반경도 마감시간도 보지 않는다 —
	 * 팟의 수령 위치는 내 등록 주소와 다를 수 있고, 참여자에게는 마감 후가 오히려 중요한 구간이다.
	 * 이 섹션에서 사라지는 유일한 조건은 나눔 완료({@code DONE})다.
	 */
	@Query("""
		select p from Pot p
		where p.status <> com.delipot.pot.PotStatus.DONE
		  and exists (
		    select 1 from PotMember pm
		    where pm.potId = p.id and pm.memberId = :memberId
		  )
		  and (:keyword = '' or lower(p.storeName) like lower(concat('%', :keyword, '%')) escape '\\')
		order by p.deadline asc, p.id asc
		""")
	List<Pot> findMyLivePots(
		@Param("memberId") Long memberId,
		@Param("keyword") String keyword
	);
}
