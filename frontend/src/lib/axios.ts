import Axios, { type AxiosError, type AxiosRequestConfig } from 'axios'

/**
 * Orval mutator. 생성된 모든 훅이 이 인스턴스를 통해 호출된다.
 * 인증 헤더, 공통 에러 처리는 전부 여기서만 손댄다.
 */
export const axiosInstance = Axios.create({
  // dev 에서는 vite proxy(/api → localhost:8080), 배포에서는 VITE_API_BASE_URL 사용
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 10_000,
})

axiosInstance.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    // 백엔드 ApiResponse 규약: { success: false, error: { code, message } }
    const body = error.response?.data
    if (body?.error) {
      error.message = body.error.message
    }
    return Promise.reject(error)
  },
)

export interface ApiErrorBody {
  success: false
  error?: { code: string; message: string }
}

export const customInstance = async <T>(
  config: AxiosRequestConfig,
  options?: AxiosRequestConfig,
): Promise<T> => {
  const controller = new AbortController()
  const promise = axiosInstance({
    ...config,
    ...options,
    signal: options?.signal ?? controller.signal,
  }).then(({ data }) => data as T)

  // TanStack Query가 요청을 취소할 수 있게 cancel을 붙여둔다.
  ;(promise as Promise<T> & { cancel?: () => void }).cancel = () => {
    controller.abort('Query was cancelled')
  }

  return promise
}

export type ErrorType<Error> = AxiosError<Error>
export type BodyType<BodyData> = BodyData
