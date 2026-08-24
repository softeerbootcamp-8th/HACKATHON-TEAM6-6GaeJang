# 백엔드 작업 규칙

상세 규칙(계층 순서, Lombok/JPA, 프로파일, 응답 규약)은
`.claude/skills/feature-impl/references/backend.md` 한 곳에만 둔다. 백엔드 코드를 만지기 전에 그 파일을 읽는다.

빠른 참조:

- 실행: `docker compose up -d` → `./gradlew bootRun` (기본 프로파일 `local`, MySQL 호스트 포트 3307)
- MySQL 없이 띄우기: `SPRING_PROFILES_ACTIVE=h2 ./gradlew bootRun`
- 테스트: `./gradlew test`
- 응답은 항상 `ApiResponse.ok(...)`, 실패는 `BusinessException(ErrorCode.X)` throw
- 엔티티에 `@Setter` 금지
