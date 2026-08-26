# HACKATHON-TEAM6-6GaeJang
Softeer 8기 6팀 해커톤

## Architecture

![Delipot 배포 아키텍처](docs/architecture.svg)

- **프론트엔드**: `frontend-cd` 워크플로우가 빌드 산출물을 S3에 동기화하고 CloudFront 캐시를 무효화한다. 인증은 장기 액세스 키 대신 GitHub OIDC로 IAM Role을 그때그때 assume하는 방식을 쓴다. CloudFront는 정적 자산을 S3에서 서빙하고 `/api/*` 요청은 백엔드로 프록시해, 프론트는 항상 동일 오리진으로 API를 호출한다.
- **백엔드**: `backend-cd` 워크플로우가 `bootJar`를 빌드해 Public Subnet의 EC2로 SCP 전송한 뒤 SSH로 systemd 서비스를 재시작하고 헬스체크한다. DB는 Private Subnet의 EC2에 MySQL을 직접 설치해 운영하며, 외부에서 직접 접근할 수 없다.
- **이미지 업로드**: 채팅 이미지는 백엔드 EC2가 인스턴스에 붙은 IAM Role로 S3에 직접 업로드한다(CloudFront 경유 없음). 프론트 정적 자산과 같은 버킷을 쓰며, 버킷이 CloudFront 뒤에서만 공개되므로 반환 URL도 CloudFront 도메인을 사용한다.
- **지도**: 프론트엔드가 브라우저에서 Kakao Map API를 직접 호출한다. AWS 인프라를 거치지 않는 외부 연동이다.
- **알림**: PR이 열리면 `slack-pr-notify` 워크플로우가 Slack Webhook으로 팀 채널에 알림을 보낸다.
