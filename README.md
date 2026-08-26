## 프로젝트 선택 주제

근거리 기반 나눔 / 교환 / 직거래 플랫폼
> ⚠️ **주제 지침:** 1~2인 가구의 공동구매 조율/결제/정산 번거로움 문제에 집중할 것

---

## 서비스 설명

> 💡 **근처에 있는 사람과 함께 배달하고 싶은 1인 주문자를 위한 위치 기반 배달 모임 플랫폼**

**델리팟(Delipot)** 은 혼자 배달을 시키기엔 배달비가 부담스럽고 최소주문금액을 채우기 어려운 1인 주문자를 위한 위치 기반 공동 배달 플랫폼입니다.

- **참여 및 개설:** 반경 300m 안의 근처 유저가 연 배달팟(공동 배달 모임)에 원하는 메뉴를 입력해 참여하거나 직접 배달팟을 만들 수 있습니다.
- **소통 및 정산:** 배달팟이 생성되면 공동 주문에 필요한 정보가 정리된 채팅방이 만들어지고, 유저는 채팅방에서 주문 진행 상황을 공유할 수 있습니다.

---

## 🛠️ Tech Stack

| 분류 | 기술 스택 |
| --- | --- |
| 프론트엔드 | ![React](https://img.shields.io/badge/React%2019-61DAFB?style=for-the-badge&logo=react&logoColor=black) ![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white) ![Orval](https://img.shields.io/badge/Orval-FF6B6B?style=for-the-badge) |
| 백엔드 | ![Java](https://img.shields.io/badge/Java%2021-007396?style=for-the-badge&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white) ![MySQL](https://img.shields.io/badge/MySQL_8.4-4479A1?style=for-the-badge&logo=mysql&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white) ![Swagger](https://img.shields.io/badge/springdoc_OpenAPI-85EA2D?style=for-the-badge&logo=swagger&logoColor=black) |
| 인프라 | ![AWS EC2](https://img.shields.io/badge/AWS%20EC2-FF9900?style=for-the-badge&logo=amazonec2&logoColor=white) ![AWS S3](https://img.shields.io/badge/AWS%20S3-569A31?style=for-the-badge&logo=amazons3&logoColor=white) ![AWS CloudFront](https://img.shields.io/badge/CloudFront-8C4FFF?style=for-the-badge&logo=amazonaws&logoColor=white) ![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white) ![systemd](https://img.shields.io/badge/systemd-000000?style=for-the-badge&logo=linux&logoColor=white) |
| 협업 | ![Slack](https://img.shields.io/badge/Slack-4A154B?style=for-the-badge&logo=slack&logoColor=white) ![GitHub](https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white) ![Notion](https://img.shields.io/badge/Notion-000000?style=for-the-badge&logo=notion&logoColor=white) ![Figma](https://img.shields.io/badge/Figma-F24E1E?style=for-the-badge&logo=figma&logoColor=white) |

---

## 🏗️ 서비스 아키텍처

<p align="center">
  <img src="docs/architecture.svg" alt="Delipot 배포 아키텍처" width="480" />
</p>

---

## 🗂️ ERD

`backend/src/main/java/com/delipot` 의 엔티티 6개를 옮긴 DDL 스냅샷. MySQL 8.4 / InnoDB / utf8mb4, 스키마는 `ddl-auto: update` 로 생성된다.

| | |
|---|---|
| 테이블 | 6 |
| 관계 | 9 |
| 실제 FK 제약 | 2 (둘 다 `chat_room` 을 향한다) |
| 논리적 FK | 7 (plain 컬럼, 앱이 정합성 보장) |

```mermaid
erDiagram
    member ||..o{ pots : "총대"
    member ||..o{ pot_members : "참여"
    pots ||..|{ pot_members : "참여자 명단"
    pots ||..o| chat_room : "팟 1 : 방 1"
    chat_room ||--o{ chat_room_member : "방 참여자"
    chat_room ||--o{ chat_message : "메시지"
    member ||..o{ chat_room_member : "입장"
    member |o..o{ chat_message : "작성"
    chat_message |o..o{ chat_room_member : "읽음 커서"
```

> 실선은 실제 FK, 점선은 앱이 정합성을 보장하는 논리적 관계다. 컬럼별 타입·제약·인덱스 등 상세는 **[docs/erd.md](docs/erd.md)** 에 정리했다.

---

## Known Issues

Delipot(3일 해커톤) 현재 시점의 알려진 제약 중 영향이 큰 항목만 추렸다. 전체 목록은 **[docs/known-issues.md](docs/known-issues.md)** 참고.

| 항목 | 현재 상태 |
|---|---|
| **결제·정산 연동 없음** | 총대 계좌 정보를 채팅방 배너에 텍스트로 띄워줄 뿐, 실제 송금·입금확인은 참여자가 각자 은행 앱으로 처리한다. |
| **본인인증(SMS) 없음** | 휴대폰번호 형식만 검사하고 인증번호를 보내지 않아, 남의 번호로 가입할 수 있다. |
| **실시간 GPS 미사용** | 홈 목록은 가입 시 등록한 주소 좌표 기준 반경 300m로 고정돼 있어, 지금 위치가 아니라 등록 주소 주변 팟만 보인다. |
| **채팅 서버 단일 인스턴스 한정** | STOMP 브로커가 인메모리 `SimpleBroker`라 서버를 2대 이상으로 늘릴 수 없다. |
| **무중단 배포 아님** | 단일 EC2에 `systemctl restart` 방식이라 배포 중 WebSocket 연결이 전부 끊긴다. |
| **동시 참여 경합 시 재시도 없음** | 정원 마지막 자리를 동시에 노리면 늦은 요청은 `CONFLICT`를 받고, 사용자가 직접 다시 눌러야 한다. |

---

## 👥 팀원 소개 및 맡은 일

| 이름 | 직무 | 담당 |
| --- | :---: | --- |
| [정상진](https://github.com/jsj3473) | 웹 백엔드 | 채팅기능 |
| [강민제](https://github.com/10000Je) | 웹 백엔드 | Auth, 인프라 |
| [박태은](https://github.com/ReusCap) | 웹 백엔드 | 배달팟 도메인 |
| 유하은 | 서비스 기획 | 피그마 |
| 조준희 | 서비스 기획 | 피그마 |
