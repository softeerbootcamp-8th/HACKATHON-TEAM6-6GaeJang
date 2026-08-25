package com.delipot.pot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PotRepository extends JpaRepository<Pot, Long> {

	/**
	 * 홈 목록의 후보를 뽑는다. 정확한 반경 판정은 서비스에서 구면 거리로 한 번 더 거른다.
	 *
	 * <p>native query 대신 JPQL인 이유는 MySQL의 {@code ST_Distance_Sphere}를 쓰면
	 * h2로 도는 테스트에서 실행되지 않기 때문이다. 사각형 조건은 두 DB 모두에서 같게 동작하고
	 * {@code idx_pots_lat_lng} 인덱스도 그대로 탄다.
	 *
	 * <p>{@code keyword}는 빈 문자열이면 검색 없이 전체를 뜻한다. null 대신 빈 문자열로 받는 이유는
	 * JPQL 파라미터의 null 타입 추론에 기대지 않기 위해서다. 호출부에서 {@code %}, {@code _}를
	 * 이스케이프해 넘기므로 여기서는 {@code escape '\\'}만 선언한다.
	 *
	 * <p>{@link org.springframework.data.domain.Limit}으로 상한을 건다. 반경이 좁아 보통 수십 건이지만
	 * 밀집 지역에서 결과가 폭증하면 TEXT 컬럼까지 전부 직렬화되므로 방어적으로 자른다.
	 */
	@Query("""
		select p from Pot p
		where p.status = :status
		  and p.deadline > :now
		  and p.currentMemberCount < p.capacity
		  and p.latitude between :minLatitude and :maxLatitude
		  and p.longitude between :minLongitude and :maxLongitude
		  and (:keyword = '' or lower(p.storeName) like lower(concat('%', :keyword, '%')) escape '\\')
		order by p.deadline asc, p.id asc
		""")
	List<Pot> findOpenPotsInBox(
		@Param("status") PotStatus status,
		@Param("now") OffsetDateTime now,
		@Param("minLatitude") BigDecimal minLatitude,
		@Param("maxLatitude") BigDecimal maxLatitude,
		@Param("minLongitude") BigDecimal minLongitude,
		@Param("maxLongitude") BigDecimal maxLongitude,
		@Param("keyword") String keyword,
		Limit limit
	);
}
