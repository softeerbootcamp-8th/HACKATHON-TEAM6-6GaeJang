package com.delipot.pot;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 근거리 조회에 필요한 좌표 계산.
 *
 * <p>거리 판정은 두 단계다.
 * <ol>
 *   <li>{@link #boxAround} 로 위경도 사각형을 만들어 인덱스({@code idx_pots_lat_lng})로 후보를 줄인다.</li>
 *   <li>후보에만 {@link #distanceMeters} 로 정확한 구면 거리를 계산해 반경 밖을 버린다.</li>
 * </ol>
 *
 * <p>사각형만 쓰면 모서리가 반경을 넘어(정사각형 대각선 = 변 × √2) 300m 요청에 최대 424m가 섞인다.
 * 반대로 구면 거리만 쓰면 계산식이 컬럼을 감싸 인덱스를 못 타고 풀스캔한다. 그래서 둘을 겹쳐 쓴다.
 */
final class Geo {

	/** 지구 평균 반지름(m). WGS84 기준. */
	private static final double EARTH_RADIUS_METERS = 6_371_008.8;

	/** 위도 1도의 거리(m). 경선을 따라가므로 위치와 무관하게 거의 일정하다. */
	private static final double METERS_PER_LATITUDE_DEGREE = 111_320.0;

	/** 좌표 컬럼이 DECIMAL(10,7)이라 비교값도 같은 스케일로 맞춘다. */
	private static final int COORDINATE_SCALE = 7;

	private Geo() {
	}

	/** 위경도 사각형. JPQL의 between 조건에 그대로 들어간다. */
	record Box(BigDecimal minLatitude, BigDecimal maxLatitude, BigDecimal minLongitude, BigDecimal maxLongitude) {
	}

	/**
	 * 중심에서 반경을 감싸는 최소 사각형을 만든다.
	 *
	 * <p>경도 1도의 거리는 위도에 따라 줄어든다(고위도로 갈수록 경선 간격이 좁아진다).
	 * 그래서 {@code cos(위도)}로 나눠 보정하지 않으면 고위도에서 사각형이 실제 반경보다 좁아져
	 * 반경 안에 있는 팟을 놓친다.
	 */
	static Box boxAround(BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
		double latitudeDelta = radiusMeters / METERS_PER_LATITUDE_DEGREE;

		// 극 근처에서 cos가 0에 수렴해 폭이 발산하는 것을 막는다. 한국 위도에서는 걸리지 않는다.
		double cosLatitude = Math.max(Math.cos(Math.toRadians(latitude.doubleValue())), 1e-6);
		double longitudeDelta = radiusMeters / (METERS_PER_LATITUDE_DEGREE * cosLatitude);

		return new Box(
			scaled(latitude.doubleValue() - latitudeDelta),
			scaled(latitude.doubleValue() + latitudeDelta),
			scaled(longitude.doubleValue() - longitudeDelta),
			scaled(longitude.doubleValue() + longitudeDelta)
		);
	}

	/**
	 * 두 좌표 사이의 구면 거리(m). 하버사인 공식.
	 *
	 * <p>평면 피타고라스를 쓰지 않는 이유는 위도에 따라 경도 1도의 실제 거리가 달라져서다.
	 * 300m 규모에서는 오차가 크지 않지만 보정 없이 쓰면 동서 방향 거리가 과대평가된다.
	 */
	static double distanceMeters(BigDecimal latitude1, BigDecimal longitude1,
		BigDecimal latitude2, BigDecimal longitude2) {

		double lat1 = Math.toRadians(latitude1.doubleValue());
		double lat2 = Math.toRadians(latitude2.doubleValue());
		double deltaLat = lat2 - lat1;
		double deltaLng = Math.toRadians(longitude2.doubleValue() - longitude1.doubleValue());

		double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
			+ Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);

		return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}

	private static BigDecimal scaled(double value) {
		return BigDecimal.valueOf(value).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
	}
}
