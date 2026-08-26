// 오프라인 캐싱 없음. fetch 핸들러를 두지 않는다 —
// respondWith 를 부르지 않는 빈 핸들러는 하는 일이 없으면서 페이지를 SW 제어 상태로 만들어
// 모든 요청을 SW 스레드로 경유시킨다. Chrome 의 설치 요건은 "오프라인 동작"으로 바뀌어
// no-op 핸들러로는 어차피 충족되지 않고, iOS 는 홈 화면 추가에 SW 를 요구하지 않는다.
self.addEventListener('install', () => {
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})
