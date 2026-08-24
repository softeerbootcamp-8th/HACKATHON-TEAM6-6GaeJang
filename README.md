# Delipot

육개장(Softeer 8기 6팀) 해커톤 프로젝트. 백엔드(Spring Boot) + 프론트(React) 모노레포.

```
.
├── backend/     Spring Boot 4.1 · Java 21 · Gradle 9   ← IntelliJ 로 backend/ 를 연다
├── frontend/    React 19 · Vite 8 · TS · pnpm        ← VS Code 로 저장소 루트를 연다
├── docker-compose.yml   로컬 MySQL 8.4 (호스트 포트 3307, DB/계정 모두 delipot)
├── .claude/skills/      Claude Code 스킬 (feature-impl)
├── infra/               서버 초기 세팅 스크립트 + 배포 문서
└── .github/workflows/   CI 2개 (PR 검증) + CD 2개 (main 배포)
```

## 처음 세팅

필요한 것: JDK 21, Node 24, pnpm 11, Docker.

> 백엔드는 Spring Boot 4 라인이다. Boot 3 예제와 스타터/패키지 이름이 다르다 —
> `.claude/skills/feature-impl/references/backend.md` 의 "Spring Boot 4 에서 달라진 것" 참고.

```bash
docker compose up -d
```

```bash
pnpm install
```

## 실행

백엔드 (8080):

```bash
cd backend && ./gradlew bootRun
```

프론트 (5173, `/api` → 8080 프록시):

```bash
pnpm -C frontend dev
```

http://localhost:5173 에서 헬스체크 카드로 프론트 → 백엔드 → DB 연결을 확인할 수 있다.

- 헬스체크 API: http://localhost:8080/api/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI 스펙: http://localhost:8080/v3/api-docs

MySQL 없이 백엔드만 띄우려면:

```bash
cd backend && SPRING_PROFILES_ACTIVE=h2 ./gradlew bootRun
```

## API 클라이언트 재생성

백엔드 API를 바꿨으면 백엔드를 띄운 상태에서:

```bash
pnpm -C frontend generate:api
```

`frontend/src/api/generated/`는 자동 생성물이다. 손으로 고치지 않는다.

## 검사

```bash
cd backend && ./gradlew test
```

```bash
pnpm -C frontend lint && pnpm -C frontend typecheck
```

## IDE

- IntelliJ: `backend/` 를 Gradle 프로젝트로 열고 JDK 21 지정. 애노테이션 프로세싱(Lombok) 활성화.
  루트 프로젝트명은 `delipot-backend`, 베이스 패키지는 `com.delipot`.
- VS Code: 저장소 루트를 열면 `.vscode/settings.json`이 eslint/prettier 경로를 프론트로 잡아준다.
  권장 확장은 `.vscode/extensions.json` 참고.

## 배포

`main` 브랜치에 머지되면 변경된 쪽만 자동 배포된다 (백엔드는 systemd 재시작 + 헬스체크, 프론트는 nginx 심링크 교체).
서버 초기 세팅, GitHub Secrets 등록, 롤백 방법은 [infra/README.md](infra/README.md) 참고.
