package com.delipot.pot.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "팟 생성 요청")
public record PotCreateRequest(

	@Schema(description = "글 제목", example = "역삼역 호백반점 같이 시켜요")
	@NotBlank(message = "제목은 필수입니다.")
	@Size(max = 100, message = "제목은 100자 이하여야 합니다.")
	String title,

	@Schema(description = "가게 이름. 홈 검색 기준", example = "호백반점")
	@NotBlank(message = "가게 이름은 필수입니다.")
	@Size(max = 100, message = "가게 이름은 100자 이하여야 합니다.")
	String storeName,

	@Schema(description = "배달앱 가게 링크", example = "https://web.coupangeats.com/share?storeId=781313")
	@NotBlank(message = "가게 링크는 필수입니다.")
	@Size(max = 500, message = "가게 링크는 500자 이하여야 합니다.")
	@Pattern(regexp = "^https?://.+", message = "가게 링크는 http 또는 https로 시작해야 합니다.")
	String storeUrl,

	@Schema(description = "배달 받고 나눌 장소", example = "역삼 스타빌 1층 로비")
	@NotBlank(message = "만날 장소는 필수입니다.")
	@Size(max = 200, message = "만날 장소는 200자 이하여야 합니다.")
	String meetingPlace,

	@Schema(description = "만날 장소 도로명 주소. 지도에서 고른 값을 그대로 보낸다", example = "서울 강남구 학동로 171")
	@Size(max = 200, message = "도로명 주소는 200자 이하여야 합니다.")
	String meetingRoadAddress,

	@Schema(description = "만날 장소 지번 주소", example = "서울 강남구 논현동 58-3")
	@Size(max = 200, message = "지번 주소는 200자 이하여야 합니다.")
	String meetingJibunAddress,

	@Schema(description = "만날 장소 위도", example = "37.5006")
	@NotNull(message = "위도는 필수입니다.")
	@DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
	@DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
	BigDecimal latitude,

	@Schema(description = "만날 장소 경도", example = "127.0366")
	@NotNull(message = "경도는 필수입니다.")
	@DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
	@DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
	BigDecimal longitude,

	@Schema(description = "총대를 포함한 모집 정원", example = "4")
	@NotNull(message = "모집 인원은 필수입니다.")
	@Min(value = 2, message = "모집 인원은 2명 이상이어야 합니다.")
	@Max(value = 20, message = "모집 인원은 20명 이하여야 합니다.")
	Integer capacity,

	@Schema(description = "가게 최소주문금액(원)", example = "20000")
	@NotNull(message = "최소주문금액은 필수입니다.")
	@Min(value = 0, message = "최소주문금액은 0원 이상이어야 합니다.")
	Integer minOrderAmount,

	@Schema(description = "모집 마감시간. 오프셋을 반드시 포함한다", example = "2026-08-25T19:30:00+09:00")
	@NotNull(message = "마감시간은 필수입니다.")
	@Future(message = "마감시간은 현재 시각 이후여야 합니다.")
	OffsetDateTime deadline,

	@Schema(description = "상세 설명")
	@Size(max = 2000, message = "상세 설명은 2000자 이하여야 합니다.")
	String description,

	@Schema(description = "정산받을 은행", example = "카카오뱅크")
	@NotBlank(message = "은행명은 필수입니다.")
	@Size(max = 30, message = "은행명은 30자 이하여야 합니다.")
	String bankName,

	@Schema(description = "정산받을 계좌번호", example = "3333-01-1234567")
	@NotBlank(message = "계좌번호는 필수입니다.")
	@Pattern(regexp = "^[0-9-]{8,30}$", message = "계좌번호는 숫자와 하이픈만 8~30자로 입력해야 합니다.")
	String accountNumber,

	@Schema(description = "예금주", example = "김하나")
	@NotBlank(message = "예금주는 필수입니다.")
	@Size(max = 30, message = "예금주는 30자 이하여야 합니다.")
	String accountHolder
) {
}
