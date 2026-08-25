package com.delipot.pot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeoTest {

	/** 강남 학동로 부근. 목업 좌측 상단의 "학동로 171"에 해당하는 기준점. */
	private static final BigDecimal BASE_LAT = new BigDecimal("37.5172000");
	private static final BigDecimal BASE_LNG = new BigDecimal("127.0286000");

	@Test
	@DisplayName("같은 좌표의 거리는 0이다")
	void zeroDistanceForSamePoint() {
		assertThat(Geo.distanceMeters(BASE_LAT, BASE_LNG, BASE_LAT, BASE_LNG)).isZero();
	}

	@Test
	@DisplayName("위도 0.0009도 차이는 약 100m다")
	void latitudeDeltaMatchesKnownDistance() {
		BigDecimal north = BASE_LAT.add(new BigDecimal("0.0009000"));

		assertThat(Geo.distanceMeters(BASE_LAT, BASE_LNG, north, BASE_LNG))
			.isCloseTo(100.0, within(2.0));
	}

	/** 경도 보정이 빠지면 깨진다. 위도 37.5도에서 경도 1도는 적도의 약 79%다. */
	@Test
	@DisplayName("같은 각도라도 경도 방향이 위도 방향보다 가깝다")
	void longitudeDegreeIsShorterAtKoreanLatitude() {
		BigDecimal delta = new BigDecimal("0.0010000");

		double northward = Geo.distanceMeters(BASE_LAT, BASE_LNG, BASE_LAT.add(delta), BASE_LNG);
		double eastward = Geo.distanceMeters(BASE_LAT, BASE_LNG, BASE_LAT, BASE_LNG.add(delta));

		assertThat(eastward).isLessThan(northward);
		assertThat(eastward / northward).isCloseTo(Math.cos(Math.toRadians(37.5)), within(0.01));
	}

	@Test
	@DisplayName("사각형은 반경을 완전히 감싼다 — 정북·정동 300m 지점이 안에 들어온다")
	void boxContainsRadiusEdges() {
		Geo.Box box = Geo.boxAround(BASE_LAT, BASE_LNG, 300);

		BigDecimal northEdge = BASE_LAT.add(new BigDecimal("0.0026900"));
		assertThat(Geo.distanceMeters(BASE_LAT, BASE_LNG, northEdge, BASE_LNG)).isCloseTo(300.0, within(5.0));
		assertThat(northEdge).isLessThanOrEqualTo(box.maxLatitude());

		BigDecimal eastEdge = BASE_LNG.add(new BigDecimal("0.0033900"));
		assertThat(Geo.distanceMeters(BASE_LAT, BASE_LNG, BASE_LAT, eastEdge)).isCloseTo(300.0, within(5.0));
		assertThat(eastEdge).isLessThanOrEqualTo(box.maxLongitude());
	}

	/**
	 * 사각형만으로 판정을 끝내면 안 되는 근거. 모서리는 변보다 √2배 멀다.
	 * 이 테스트가 통과한다는 것은 구면 거리 재검증이 필요하다는 뜻이다.
	 */
	@Test
	@DisplayName("사각형 모서리는 300m를 넘는다 — 거리 재검증이 필요한 이유")
	void boxCornerExceedsRadius() {
		Geo.Box box = Geo.boxAround(BASE_LAT, BASE_LNG, 300);

		double corner = Geo.distanceMeters(BASE_LAT, BASE_LNG, box.maxLatitude(), box.maxLongitude());

		assertThat(corner).isGreaterThan(300.0);
		assertThat(corner).isCloseTo(300.0 * Math.sqrt(2), within(10.0));
	}

	@Test
	@DisplayName("사각형은 중심을 기준으로 대칭이다")
	void boxIsSymmetricAroundCenter() {
		Geo.Box box = Geo.boxAround(BASE_LAT, BASE_LNG, 300);

		assertThat(box.maxLatitude().subtract(BASE_LAT))
			.isEqualByComparingTo(BASE_LAT.subtract(box.minLatitude()));
		assertThat(box.maxLongitude().subtract(BASE_LNG))
			.isEqualByComparingTo(BASE_LNG.subtract(box.minLongitude()));
	}
}
