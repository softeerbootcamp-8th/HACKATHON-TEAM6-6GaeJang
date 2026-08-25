// 개발 서버(HMR)에서 서비스워커가 끼어들지 않도록 프로덕션 빌드에서만 등록한다.
export function registerServiceWorker() {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return

  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js')
  })
}
