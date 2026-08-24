# 프론트엔드 작업 규칙

상세 규칙(Route-based 구조, Orval 연동, 토큰)은
`.claude/skills/feature-impl/references/frontend.md` 한 곳에만 둔다. 화면 코드를 만지기 전에 그 파일을 읽는다.

빠른 참조:

- 실행: `pnpm dev` (5173, `/api`는 8080으로 proxy)
- 스펙 재생성: 백엔드 띄운 뒤 `pnpm generate:api`
- 검사: `pnpm typecheck`, `pnpm lint`
- `src/api/generated/`, `src/routeTree.gen.ts`는 손으로 고치지 않는다
- 새 화면 컴포넌트는 그 라우트 옆 `-components/`에 둔다
