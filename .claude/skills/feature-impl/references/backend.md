# 백엔드 구현 규칙

스택: Spring Boot, Lombok, JPA, MySQL. 모니터링 별도 구축 없음.

## 계층 순서

Repository → Service → DTO → Controller. 컨트롤러에 요청/응답 DTO 검증(`@Valid`) 붙인다.

## Lombok

- 엔티티: `@Getter` + `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + 빌더는 필요할 때만.
  `@Setter`는 엔티티에 쓰지 않는다 — 상태 변경은 의미 있는 메서드로 노출.
- DTO: `@Getter` + `@Builder` 또는 record 사용 가능하면 record 우선 (Lombok보다 불변성 보장).

## JPA / 쿼리 전략

- 기본은 JPA 메서드 쿼리.
- 조인·조건이 복잡해지면 JPQL로 명시적으로 작성 (`@Query`).
- 성능이 실제로 문제되는 곳(대량 집계, 인덱스 힌트 필요한 곳)만 native query.
  native query 쓸 땐 이유를 주석으로 한 줄 남긴다.
- N+1 의심되는 조회는 fetch join 또는 `@EntityGraph` 우선 고려.

## 동시성

여러 요청이 같은 자원(같은 라이딩 세션, 같은 이벤트 슬롯 등)을 동시에 건드릴 수 있으면
낙관적 락(`@Version`) 또는 조건부 UPDATE로 처리. 비관적 락은 정말 필요할 때만 (트래픽 적은 해커톤 규모면
보통 낙관적 락으로 충분).

## 에러 응답

공통 `ApiResponse<T>` 래핑 사용. 도메인 예외는 `GlobalExceptionHandler`에서 잡아서 일관된 코드로 변환.
새 에러 케이스면 계약 확정 게이트에서 사람에게 코드 물어본다.

## Flyway/스키마 변경

마이그레이션 쓰면 기존 파일 수정 말고 다음 버전 번호로 추가. (마이그레이션 도구 안 쓰면 `ddl-auto` 정책을
프로젝트 초기에 한 번 정해두고 CLAUDE.md에 고정 — 이 스킬에서 매번 묻지 않는다.)
## 패키지 구조 (확정)

```
backend/src/main/java/com/delipot/
├── DelipotApplication.java
├── global/
│   ├── config/      AppConfig(Clock), WebConfig(CORS), OpenApiConfig
│   ├── error/       ErrorCode, BusinessException, GlobalExceptionHandler
│   └── response/    ApiResponse
└── <도메인>/         Controller, Service, Repository, 엔티티
    └── dto/         요청/응답 record
```

새 기능은 `com.delipot.<도메인>` 패키지를 새로 만든다. `global/`은 전역 규약만 둔다.

## 응답 규약

- 성공: `ApiResponse.ok(data)` / 본문 없으면 `ApiResponse.ok()`
- 실패: 컨트롤러에서 직접 만들지 않고 `BusinessException(ErrorCode.X)`를 던진다.
  `GlobalExceptionHandler`가 `{success:false, error:{code,message}}` 로 변환한다.
- 새 에러 케이스는 `ErrorCode` enum에만 추가한다. enum 이름이 그대로 `error.code`로 나가므로
  프론트와의 계약이다 — 이름을 바꾸면 프론트가 깨진다.

## 프로파일 (확정, 다시 묻지 않는다)

- `local` (기본) — docker compose MySQL, 호스트 포트 **3307**, `ddl-auto: update`
- `h2` — MySQL 없이 띄울 때. `create-drop`. 스펙 생성/프론트 단독 작업용
- `prod` — `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` 환경변수, `ddl-auto: validate`

컨텍스트 로딩이 필요한 테스트는 `@ActiveProfiles("h2")`를 붙인다.

## 실행 명령

```bash
docker compose up -d
```

```bash
cd backend && ./gradlew bootRun
```

```bash
cd backend && ./gradlew test
```

## 스키마 변경

`ddl-auto: update`로 로컬은 자동 반영된다. 운영은 `validate`이므로
컬럼/테이블 추가 시 배포 전에 DDL을 사람이 적용해야 한다. 마이그레이션 도구는 아직 안 쓴다.

## Spring Boot 4 에서 달라진 것 (자주 틀리는 지점)

Boot 3 예제를 그대로 붙이면 컴파일이 깨진다. 아래만 기억한다.

- 웹 스타터: `spring-boot-starter-web` → **`spring-boot-starter-webmvc`** (webflux 와 분리)
- 테스트 스타터: `spring-boot-starter-test` 하나가 아니라 슬라이스별로 갈렸다.
  `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `...-validation-test`
- `@WebMvcTest` 패키지 이동:
  `org.springframework.boot.test.autoconfigure.web.servlet` → **`org.springframework.boot.webmvc.test.autoconfigure`**
- Jackson 3(`tools.jackson`)이 기본이다. `spring.jackson.serialization.write-dates-as-timestamps`
  같은 Jackson 2 전용 프로퍼티는 부팅 시 바인딩 실패로 죽는다. 날짜는 기본이 ISO-8601 문자열이다.
- springdoc 은 **3.x** 라인을 쓴다 (2.8.x 는 Boot 3 전용).
- 목 빈은 `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`).
