# 프론트엔드 구현 규칙

스택: React + Vite + TypeScript, pnpm, TanStack Router/Query, Orval, shadcn/ui + Tailwind v4.

## API 연동 순서

1. 백엔드를 로컬 프로파일로 띄운다.
2. `pnpm generate:api` 실행 — OpenAPI 스펙에서 Orval 훅 재생성 (`src/api/generated/`).
3. 화면에서는 생성된 훅만 소비한다. `src/api/generated/`, `routeTree.gen.ts`는 손으로 고치지 않는다.
4. 예외 처리(인증 헤더, 에러 인터셉터 등)는 `lib/axios.ts`의 customInstance/mutator 레이어에서.

## 디렉토리 구조 — Route-based

```
src/
├── routes/
│   ├── __root.tsx
│   ├── ridings/
│   │   ├── index.tsx          # /ridings
│   │   ├── -components/       # ← -prefix: 라우트 트리에서 제외
│   │   │   └── RidingMap.tsx
│   │   └── $ridingId/
│   │       ├── index.tsx      # /ridings/:ridingId
│   │       └── -components/
├── components/ui/             # shadcn (전역 공유)
└── api/generated/             # Orval 자동 생성
```

새 화면은 라우트 옆 `-components/`에 컴포넌트를 둔다. 전역 공유가 필요할 때만 `components/ui/`로 승격.

## 컴포넌트 요청 시 이 정도면 충분

```
routes/ridings/$ridingId/-components/StopEventBanner.tsx 를 만들어줘.

데이터: useSseStopEvents(ridingId) (또는 관련 Orval 훅)
레이아웃:
  - 화면 상단 고정 배너, 정지 이벤트 발생 시 노출
상태:
  - loading: 스켈레톤 없이 미노출
  - error: role="alert" 로 짧은 메시지
인터랙션:
  - 배너 탭하면 지도에서 해당 지점으로 이동
```

과한 공용화보다 그 화면에 맞는 코드 우선. `components/ui/` 바깥은 무리하게 추상화하지 않는다.

## 디자인 토큰

`src/styles/globals.css`의 `@theme` 토큰 우선 사용. 색상 하드코딩 지양, 반복되면 토큰으로 승격.

## 접근성

페이지 루트 `<main aria-label="...">`. 에러/상태 변화는 `role="alert"` 또는 `aria-live`.
## 실제 파일 배치 (확정)

```
frontend/
├── orval.config.ts          # /v3/api-docs → 훅 생성 설정
├── tsr.config.json          # 라우트 트리 생성 설정 (routeFileIgnorePrefix: "-")
├── components.json          # shadcn CLI 설정
└── src/
    ├── main.tsx             # QueryClientProvider + RouterProvider
    ├── routeTree.gen.ts     # 자동 생성. 손대지 않는다
    ├── api/generated/       # Orval 자동 생성. 손대지 않는다
    ├── lib/axios.ts         # customInstance(Orval mutator) + 에러 인터셉터
    ├── lib/queryClient.ts   # staleTime 30s, retry 1
    ├── lib/utils.ts         # cn()
    ├── components/ui/       # shadcn (button, card)
    ├── routes/              # 라우트 + -components/
    └── styles/globals.css   # @theme 토큰
```

## 명령

```bash
pnpm -C frontend dev
```

```bash
pnpm -C frontend generate:api
```

```bash
pnpm -C frontend typecheck
```

`generate:api`는 백엔드가 8080에서 떠 있어야 동작한다 (`OPENAPI_URL`로 덮어쓸 수 있다).
라우트 파일을 추가/삭제한 뒤 타입 에러가 나면 `pnpm -C frontend routes:gen`을 먼저 돌린다.

## 생성된 훅 사용법

- 훅 이름은 백엔드 `operationId`에서 나온다. 컨트롤러 메서드명이 곧 훅 이름이다
  (`health()` → `useHealth()`). 메서드명을 바꾸면 프론트 코드가 깨진다.
- 응답은 `ApiResponse` 래핑이므로 실데이터는 `data?.data`로 꺼낸다.
- 에러 메시지는 인터셉터가 백엔드 `error.message`로 바꿔둔다. 화면에서는 `error`만 보면 된다.

## API 주소

- dev: vite proxy가 `/api` → `http://localhost:8080`으로 넘긴다. baseURL은 비워둔다.
- 배포: `VITE_API_BASE_URL` 환경변수 사용.

## 디자인 토큰 (현재 정의된 것)

`bg`, `fg`, `muted`, `muted-fg`, `border`, `primary`, `primary-fg`, `up`, `down`, `radius-card`.
`text-up` / `text-down` 처럼 Tailwind 유틸로 바로 쓴다. 없는 색이 필요하면 `globals.css`의
`@theme`에 토큰을 추가하고 쓴다 — 클래스에 hex를 박지 않는다.
