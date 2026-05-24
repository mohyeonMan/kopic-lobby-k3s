# KOPIC

KOPIC는 여러 사용자가 같은 방에서 그림을 그리고 정답을 맞히는 실시간 그림 퀴즈 서비스입니다.

서비스 URL: `https://jhhomehub.gonetis.com/kopic`

## 소개

KOPIC는 웹 클라이언트, WebSocket 서버, 게임 엔진, 로비 서비스로 나뉘어 개발되는 프로젝트입니다.

현재 서비스 흐름은 브라우저에서 WebSocket 서버로 접속하고, WebSocket 서버와 게임 엔진이 RabbitMQ를 통해 이벤트를 주고받는 구조입니다. 로비 서비스는 빠른 입장, 방 조회, 엔진 라우팅 같은 진입 흐름을 분리하기 위한 서비스로 개발 예정입니다.

## 저장소 구성

| 저장소 | 역할 |
|---|---|
| `kopic-client` | React 기반 웹 클라이언트 |
| `kopic-ws-k3s` | WebSocket 연결과 클라이언트 이벤트 중계를 담당하는 서버 |
| `kopic-ge-k3s` | 방, 라운드, 턴, 점수 등 게임 진행을 처리하는 게임 엔진 |
| `kopic-lobby-k3s` | 로비, 매칭, 방 조회, 엔진 라우팅을 담당할 예정인 서비스 |
| `home-k3s-infra` | KOPIC 배포 매니페스트와 ArgoCD 애플리케이션을 관리하는 인프라 저장소 |

## 아키텍처

```text
Client
  ↓ WebSocket
WS Server
  ↓ RabbitMQ
GE Server
```

Lobby 도입 후에는 방 생성, 빠른 입장, 엔진 선택 같은 흐름이 Lobby를 거치도록 분리될 예정입니다.

```text
Client
  ↓
Lobby
  ↓
WS Server / GE Server
```

## 주요 기능

- 실시간 방 참여와 게임 진행
- WebSocket 기반 클라이언트 연결
- RabbitMQ 기반 서버 간 이벤트 전달
- 라운드, 턴, 출제자, 정답자, 점수 처리
- 캔버스 드로잉, 채팅, 힌트, 커스텀 단어 기능

## 기술 스택

- Backend: Java 21, Spring Boot, Spring WebSocket, Spring AMQP
- Frontend: React, TypeScript, Vite
- Runtime: k3s, Kubernetes
- GitOps: ArgoCD, Kustomize
- Ingress: Traefik
- Image: Docker, GHCR
- Database/Cache: Redis
- Messaging: RabbitMQ, WebSocket
- Observability: Spring Actuator, Micrometer, kube-prometheus-stack, Prometheus, Grafana, ServiceMonitor
- Logging: Loki, Grafana Alloy

## 배포 구성

KOPIC의 Kubernetes 배포 설정은 `home-k3s-infra` 저장소의 `apps/kopic` 아래에서 관리됩니다.

| 구성 | 내용 |
|---|---|
| Namespace | `kopic` |
| GitOps | ArgoCD Application |
| Manifest | Kustomize |
| Ingress | Traefik |
| Image Registry | GHCR |
| Message Broker | RabbitMQ |
| Monitoring | ServiceMonitor, Grafana dashboard |

## CI/CD

`kopic-client`, `kopic-ws-k3s`, `kopic-ge-k3s`는 GitHub Actions로 컨테이너 이미지를 빌드해 GHCR에 푸시합니다.

```text
dev/prod branch push
  ↓
GitHub Actions
  ↓
Docker image build & push to GHCR
  ↓
home-k3s-infra Kustomize image tag update
  ↓
ArgoCD sync
```

배포 매니페스트 변경은 `home-k3s-infra`의 `apps/kopic/{dev,prod}` 경로에 반영됩니다.
