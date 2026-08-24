import { defineConfig } from 'orval'

/**
 * 백엔드를 띄운 상태에서 `pnpm generate:api` 실행.
 * springdoc이 노출하는 /v3/api-docs 를 읽어 TanStack Query 훅을 생성한다.
 * 생성물(src/api/generated)은 손으로 고치지 않는다.
 */
export default defineConfig({
  api: {
    input: {
      target: process.env.OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs',
    },
    output: {
      mode: 'tags-split',
      target: './src/api/generated/endpoints.ts',
      schemas: './src/api/generated/model',
      client: 'react-query',
      httpClient: 'axios',
      clean: true,
      override: {
        mutator: {
          path: './src/lib/axios.ts',
          name: 'customInstance',
        },
        query: {
          useQuery: true,
          signal: true,
        },
      },
    },
  },
})
