# Lobby 구현 계획

이 문서는 `docs/lobby-plan.md`를 기준으로 `kopic-lobby-k3s` 저장소에서 실제 구현을 시작하기 전에 작업 범위, 의존 구조, 책임 분리 규칙, 구현 금지 규칙, 테스트 기준을 정리한다. 문서에 없는 기능은 추가하지 않고, GE/WS 저장소와 맞춰야 하는 부분은 확인 항목으로 분리한다.

## 1. 현재 저장소 상태

- Spring Boot 4 기반 Java 21 애플리케이션이다.
- 현재 구현된 애플리케이션 코드는 `KopicLobbyApplication` 부트스트랩과 기본 context load 테스트뿐이다.
- `spring-boot-starter-web`, `spring-boot-starter-data-redis`, Jackson, Caffeine 의존성이 이미 있다.
- `application.yaml`에는 Redis host/port와 `kopic.node-id`만 설정되어 있다.
- Lobby API, Redis directory 조회, routeToken 발급, 요청/응답 DTO, 예외 응답 정책은 아직 구현되어 있지 않다.

## 2. 구현 범위

이 저장소에서 우선 구현할 범위는 Lobby 역할이다.

- `POST /routes/quick`
- `POST /routes/private`
- `POST /routes/private/join`
- Redis directory 조회 기반 GE 선택
- routeToken 발급
- routeToken 설정 및 Redis key 설정
- route 발급 실패 시 routeToken을 반환하지 않는 응답 처리
- Lobby 단위 테스트 또는 슬라이스 테스트

이 저장소에서 직접 구현하지 않는 범위는 다음과 같다.

- WS의 routeToken 로컬 검증 및 GE 라우팅
- GE의 room actor 처리
- GE의 `ge:{geId}`, `ge:load`, `quick:available`, `roomcode:{roomCode}` 갱신
- private roomCode 전역 중복 확보
- GE reconciliation 및 scale-in DRAINING 처리
- client retry 구현

위 항목은 `kopic-ws-k3s`, `kopic-ge-k3s`, client 저장소와의 연동 작업으로 분리한다.

## 3. 확인이 필요한 사항

구현 전에 아래 값은 기존 client/WS/GE 계약과 맞춰야 한다. 확정 전에는 임의의 값으로 구현하지 않는다.

- quick join routeToken에 넣을 기존 quick join `action` 값
- private create routeToken에 넣을 기존 private create `action` 값
- private join routeToken에 넣을 기존 private join `action` 값
- routeToken 서명 알고리즘
- routeToken `exp` payload 단위
  - 계획 문서 예시는 millisecond epoch 값을 사용한다.
  - JWT 표준 `exp`는 초 단위 NumericDate이므로, WS 구현과 맞춰 결정해야 한다.
- JWT 라이브러리 추가 여부
  - 현재 의존성에는 JWT 전용 라이브러리가 없다.
  - 새 라이브러리를 추가하지 않으려면 JWS 구현을 직접 해야 하므로 보안상 권장하지 않는다.
  - 구현 단계에서 임의로 라이브러리를 추가하지 않는다.
- route 발급 실패 시 사용할 HTTP status와 응답 body 형식
- `nickname`, `roomCode`의 입력 검증 기준

## 4. 설정 계획

`application.yaml`에 문서의 운영 설정을 추가한다.

```yaml
kopic:
  route-token:
    secret: ${KOPIC_ROUTE_TOKEN_SECRET}
    ttl: ${KOPIC_ROUTE_TOKEN_TTL:10s}
  redis:
    keys:
      ge-prefix: ${KOPIC_REDIS_KEY_GE_PREFIX:ge:}
      ge-load: ${KOPIC_REDIS_KEY_GE_LOAD:ge:load}
      quick-available: ${KOPIC_REDIS_KEY_QUICK_AVAILABLE:quick:available}
      room-code-prefix: ${KOPIC_REDIS_KEY_ROOM_CODE_PREFIX:roomcode:}
  lobby:
    quick-candidate-scan-limit: ${KOPIC_LOBBY_QUICK_CANDIDATE_SCAN_LIMIT:5}
```

구현 시에는 `@ConfigurationProperties`로 바인딩한다. Redis key 이름과 prefix 조립은 설정 객체와 `RedisGeDirectory` 안에서만 처리하고, service/controller 계층에 문자열 조립을 노출하지 않는다.

## 5. 패키지 구조 계획

현재 코드가 거의 없으므로 Spring Boot 기본 구조 안에서 작게 나눈다.

```text
io.jhpark.kopic.lobby
  config
    LobbyProperties
    RedisKeyProperties
    RouteTokenProperties
  route
    RouteController
    RouteService
    RouteRequest
    RouteResponse
    RouteActionProperties
  route.token
    RouteTokenIssuer
    RouteTokenPayload
  redis
    GeDirectory
    RedisGeDirectory
  support
    LobbyException
    LobbyExceptionHandler
```

패키지는 구현 중 실제 클래스 수가 적으면 더 단순하게 합칠 수 있다. 단, Redis 조회, token 발급, HTTP controller 역할은 분리한다. 단순화를 하더라도 아래 의존 방향과 금지 규칙은 유지한다.

## 6. 클래스 간 의존 구조

기본 의존 방향은 다음과 같이 둔다.

```text
RouteController
  -> RouteService

RouteService
  -> GeDirectory
  -> RouteTokenIssuer

GeDirectory
  <- RedisGeDirectory

RedisGeDirectory
  -> StringRedisTemplate
  -> RedisKeyProperties
  -> LobbyProperties

RouteTokenIssuer
  -> RouteTokenProperties

LobbyExceptionHandler
  -> LobbyException
```

이 구조는 현재 프로젝트 상황에 맞다. 현재 저장소는 로비 서비스의 뼈대만 있으므로, HTTP 처리, 라우팅 결정, Redis 조회, token 발급을 최소 단위로 분리하는 것이 이후 구현에서 책임이 섞이는 것을 막는 가장 작은 구조다. 단, 구현 중 `RouteRequest`가 너무 작다면 quick/private 공용 요청 record와 private join 요청 record 정도로만 나누고, DTO 계층을 과하게 늘리지 않는다.

의존 방향 규칙:

- Controller는 service만 호출한다.
- Service는 GE 선택과 token 발급의 흐름만 조합한다.
- Redis 상세 구현은 `GeDirectory` 뒤에 숨긴다.
- routeToken 발급 상세 구현은 `RouteTokenIssuer` 뒤에 숨긴다.
- 예외 응답 변환은 `LobbyExceptionHandler`에서만 담당한다.

## 7. 클래스별 책임

### RouteController

책임:

- `POST /routes/quick`, `POST /routes/private`, `POST /routes/private/join` HTTP endpoint를 제공한다.
- 요청 body를 DTO로 받는다.
- `RouteService`를 호출하고 `RouteResponse`를 반환한다.
- Spring validation을 적용할 경우 validation annotation이 동작하는 진입점 역할을 한다.

하지 말아야 할 일:

- `StringRedisTemplate`을 직접 사용하지 않는다.
- `RouteTokenIssuer`를 직접 호출하지 않는다.
- Redis key, routeToken payload, GE 선택 규칙을 직접 알지 않는다.
- try-catch로 실패 응답을 직접 만들지 않는다.
- WS 연결, GE session routing, room 생성 요청을 직접 처리하지 않는다.

### RouteService

책임:

- route 종류별 application flow를 조합한다.
- quick route 요청 시 `GeDirectory`에서 quick route 후보 GE를 얻고 `RouteTokenIssuer`로 token을 발급한다.
- private create route 요청 시 load가 낮은 ACTIVE GE를 얻고 token을 발급한다.
- private join route 요청 시 roomCode로 GE를 얻고 token을 발급한다.
- route 발급 불가 상태를 `LobbyException`으로 변환한다.

하지 말아야 할 일:

- `StringRedisTemplate`을 직접 사용하지 않는다.
- Redis key 문자열을 직접 조립하지 않는다.
- JWT/JWS 서명 세부 구현을 알지 않는다.
- GE room 선택, room 생성, roomCode 중복 확보, actor 처리, session 중복 검증을 구현하지 않는다.
- stale quick candidate를 삭제하지 않는다.

### GeDirectory

책임:

- Lobby가 필요한 GE 선택 기능을 추상화한다.
- quick route용 GE 선택, private create용 GE 선택, private join용 GE 조회 메서드를 제공한다.
- 구현체가 Redis인지 여부를 service 계층에서 숨긴다.

하지 말아야 할 일:

- routeToken을 발급하지 않는다.
- HTTP 요청/응답 DTO를 알지 않는다.
- WS/GE 내부 프로토콜이나 room actor 개념을 알지 않는다.
- Lobby 전용 상태 저장 API를 만들지 않는다.

### RedisGeDirectory

책임:

- `StringRedisTemplate`을 사용해 Redis directory를 조회한다.
- `quick:available`, `ge:load`, `roomcode:{roomCode}`, `ge:{geId}` key만 조회한다.
- Redis key 이름과 prefix를 `RedisKeyProperties`에서 가져온다.
- `quick-candidate-scan-limit`를 `LobbyProperties`에서 가져온다.
- `ge:{geId}` 값이 `ACTIVE`인 후보만 반환한다.
- Redis 조회 실패를 route 발급 실패로 전파할 수 있는 예외로 변환한다.

하지 말아야 할 일:

- routeToken을 발급하지 않는다.
- HTTP status나 응답 body 형식을 결정하지 않는다.
- stale `quick:available` member를 삭제하지 않는다.
- Redis에 Lobby 전용 상태를 새로 저장하지 않는다.
- `room:{roomId}`, `reservation:{id}`, `quick:{geId}` 같은 문서에서 제외한 key를 읽거나 쓰지 않는다.
- GE가 담당해야 하는 roomCode 등록/삭제, quick candidate 등록/삭제를 수행하지 않는다.

### RouteTokenIssuer

책임:

- `RouteTokenPayload`를 routeToken 문자열로 서명해 발급한다.
- `RouteTokenProperties`의 secret과 ttl을 사용한다.
- token payload에 문서에 명시된 최소 필드만 포함한다.
- private join route에서만 `roomCode`가 포함되도록 한다.

하지 말아야 할 일:

- Redis를 조회하지 않는다.
- GE 선택 규칙을 알지 않는다.
- HTTP 요청/응답 처리를 하지 않는다.
- client가 token payload를 읽는다는 전제로 기능을 만들지 않는다.
- JWT 라이브러리나 알고리즘이 확정되지 않은 상태에서 임의 구현을 추가하지 않는다.

### RouteTokenPayload

책임:

- routeToken에 들어갈 최소 payload를 표현한다.
- 필드는 `action`, `nickname`, `geId`, 선택적 `roomCode`, `exp`로 제한한다.

하지 말아야 할 일:

- Redis 조회 결과 전체나 room metadata를 담지 않는다.
- client 표시용 메시지, HTTP status, internal error code를 담지 않는다.
- GE roomId, participantCount, capacity 같은 Redis 모델에서 제외된 정보를 담지 않는다.

### RouteRequest

책임:

- route API 요청 body를 표현한다.
- quick/private create 요청은 `nickname`만 받는다.
- private join 요청은 `nickname`, `roomCode`를 받는다.
- 입력 검증 기준이 확정되면 DTO 또는 별도 validator에서 검증할 수 있게 한다.

하지 말아야 할 일:

- GE 선택 로직을 갖지 않는다.
- request DTO 안에서 Redis를 조회하지 않는다.
- action 값을 직접 결정하지 않는다.

### RouteResponse

책임:

- 성공 응답 body를 표현한다.
- 성공 시 `routeToken` 하나만 포함한다.

하지 말아야 할 일:

- 성공 응답에 `geId`, `action`, `roomCode`, Redis key 정보를 추가하지 않는다.
- 실패 응답 모델과 섞지 않는다.

### LobbyProperties

책임:

- Lobby route 선택 설정을 표현한다.
- 현재 범위에서는 `quick-candidate-scan-limit`를 제공한다.

하지 말아야 할 일:

- Redis key prefix를 직접 갖지 않는다.
- routeToken secret이나 ttl을 갖지 않는다.
- 런타임 GE/room 상태를 저장하지 않는다.

### RedisKeyProperties

책임:

- Redis key 이름과 prefix 설정을 표현한다.
- `ge-prefix`, `ge-load`, `quick-available`, `room-code-prefix`를 제공한다.

하지 말아야 할 일:

- Redis 조회를 직접 수행하지 않는다.
- GE 선택 규칙을 갖지 않는다.
- 문서에 없는 Redis key를 추가하지 않는다.

### RouteTokenProperties

책임:

- routeToken secret과 ttl 설정을 표현한다.
- Lobby와 WS가 공유해야 하는 token 설정의 Lobby 쪽 바인딩 지점이다.

하지 말아야 할 일:

- token을 직접 발급하지 않는다.
- Redis 설정이나 route 선택 설정을 갖지 않는다.
- action 값을 확정된 계약 없이 임의로 포함하지 않는다.

### LobbyException

책임:

- route 발급 실패, 입력 검증 실패 등 Lobby 도메인 실패를 표현한다.
- 실패 분류를 담아 `LobbyExceptionHandler`가 일관된 응답으로 변환할 수 있게 한다.

하지 말아야 할 일:

- HTTP 응답 body를 직접 만들지 않는다.
- Redis client 예외의 상세 구현 타입을 외부 계층에 노출하지 않는다.
- GE/WS 내부 실패를 표현하는 범용 예외로 확장하지 않는다.

### LobbyExceptionHandler

책임:

- `LobbyException`과 validation 실패를 HTTP 응답으로 변환한다.
- 실패 응답 형식을 한 곳에서 통일한다.
- route 발급 실패 시 `routeToken`을 반환하지 않도록 보장한다.

하지 말아야 할 일:

- GE 선택 로직을 수행하지 않는다.
- Redis를 조회하지 않는다.
- routeToken을 발급하지 않는다.
- 확정되지 않은 HTTP status/body 계약을 임의로 고정하지 않는다.

## 8. 의존 금지 규칙

구현 중 아래 의존 관계는 만들지 않는다.

- `RouteController`에서 `StringRedisTemplate`을 직접 사용하지 않는다.
- `RouteController`에서 `RouteTokenIssuer`를 직접 호출하지 않는다.
- `RedisGeDirectory`에서 routeToken을 발급하지 않는다.
- `RouteTokenIssuer`에서 Redis를 조회하지 않는다.
- `RouteService`에서 Redis key 문자열을 직접 조립하지 않는다.
- `RouteService`에서 `StringRedisTemplate`을 직접 사용하지 않는다.
- `RouteTokenPayload`에 Redis directory 전체 상태나 room metadata를 넣지 않는다.
- `LobbyExceptionHandler`에서 Redis 조회나 token 발급을 하지 않는다.
- Lobby에서 WS/GE 책임인 room 생성, actor 처리, session routing을 구현하지 않는다.
- Lobby에서 `room:{roomId}`, `reservation:{id}`, `quick:{geId}` 등 문서에서 제외한 Redis 모델을 도입하지 않는다.

## 9. 데이터 흐름

### 9.1 `POST /routes/quick`

```text
Client
  -> RouteController
  -> RouteService
  -> GeDirectory
  -> RedisGeDirectory
  -> GeDirectory
  -> RouteService
  -> RouteTokenIssuer
  -> RouteService
  -> RouteController
  -> Client
```

Redis 조회 key:

- `quick:available`
- `ge:{geId}`
- fallback 시 `ge:load`
- fallback 후보 확인 시 `ge:{geId}`

흐름:

1. Controller가 `nickname` 요청을 받는다.
2. Service가 quick route용 GE 선택을 `GeDirectory`에 요청한다.
3. `RedisGeDirectory`가 `quick:available` 앞쪽 후보를 `scanLimit`까지 조회한다.
4. 각 후보의 `ge:{geId}`가 `ACTIVE`인지 확인한다.
5. 후보가 없으면 `ge:load`에서 낮은 score 순으로 ACTIVE GE를 찾는다.
6. Service가 quick join action, nickname, geId로 token 발급을 요청한다.
7. Controller가 `{ "routeToken": "..." }`만 반환한다.

### 9.2 `POST /routes/private`

```text
Client
  -> RouteController
  -> RouteService
  -> GeDirectory
  -> RedisGeDirectory
  -> GeDirectory
  -> RouteService
  -> RouteTokenIssuer
  -> RouteService
  -> RouteController
  -> Client
```

Redis 조회 key:

- `ge:load`
- 후보 확인 시 `ge:{geId}`

흐름:

1. Controller가 `nickname` 요청을 받는다.
2. Service가 private create용 GE 선택을 `GeDirectory`에 요청한다.
3. `RedisGeDirectory`가 `ge:load`에서 낮은 score 순으로 후보를 조회한다.
4. 후보의 `ge:{geId}`가 `ACTIVE`인 첫 GE를 선택한다.
5. Service가 private create action, nickname, geId로 token 발급을 요청한다.
6. Controller가 `{ "routeToken": "..." }`만 반환한다.

### 9.3 `POST /routes/private/join`

```text
Client
  -> RouteController
  -> RouteService
  -> GeDirectory
  -> RedisGeDirectory
  -> GeDirectory
  -> RouteService
  -> RouteTokenIssuer
  -> RouteService
  -> RouteController
  -> Client
```

Redis 조회 key:

- `roomcode:{roomCode}`
- `ge:{geId}`

흐름:

1. Controller가 `nickname`, `roomCode` 요청을 받는다.
2. Service가 roomCode route용 GE 조회를 `GeDirectory`에 요청한다.
3. `RedisGeDirectory`가 `roomcode:{roomCode}`를 조회해 geId를 얻는다.
4. 해당 `ge:{geId}`가 `ACTIVE`인지 확인한다.
5. Service가 private join action, nickname, roomCode, geId로 token 발급을 요청한다.
6. Controller가 `{ "routeToken": "..." }`만 반환한다.

## 10. Redis 접근 규칙

- Redis 접근은 `RedisGeDirectory`로 제한한다.
- Redis key 이름과 prefix는 `RedisKeyProperties`에서 가져온다.
- `quick:available` 후보 조회 개수는 `LobbyProperties.quick-candidate-scan-limit`를 사용한다.
- `quick:available`의 stale member는 Lobby에서 삭제하지 않는다.
- `ge:{geId}` 값이 `ACTIVE`인 경우에만 후보로 인정한다.
- `DRAINING` 또는 key 없음 상태는 후보에서 제외한다.
- Redis 조회 실패 시 route 발급 실패로 처리한다.
- Redis에 Lobby 전용 상태를 새로 저장하지 않는다.
- Lobby는 `ge:{geId}`, `ge:load`, `quick:available`, `roomcode:{roomCode}` 외의 Redis 모델을 사용하지 않는다.
- Redis directory는 source of truth가 아니며, GE 로컬 room state에서 재생성 가능한 파생 인덱스로 본다.

## 11. routeToken 발급 규칙

- routeToken은 `RouteTokenIssuer`에서만 발급한다.
- token payload에는 문서에 명시된 최소 필드만 넣는다.
- 필드는 `action`, `nickname`, `geId`, `exp`, private join 전용 `roomCode`로 제한한다.
- `roomCode`는 private join에만 포함한다.
- client가 token을 해석한다는 전제로 구현하지 않는다.
- WS는 동일 secret으로 token을 로컬 검증해야 하므로 action 값, 알고리즘, exp 단위는 WS와 공유 계약이 필요하다.
- WS와 공유해야 하는 action 값, 알고리즘, exp 단위는 확인 필요 항목으로 둔다.
- JWT 라이브러리 추가 여부가 확정되지 않았다면 구현 단계에서 임의로 결정하지 않는다.
- token 발급 전에는 반드시 `RouteService`가 `GeDirectory`를 통해 geId를 결정해야 한다.

## 12. 예외 처리 규칙

- route 발급 실패 시 `routeToken`을 반환하지 않는다.
- 실패 응답은 `LobbyExceptionHandler`에서 통일한다.
- Controller에서 직접 try-catch로 응답을 만들지 않는다.
- Redis 후보 없음, roomCode 없음, ACTIVE GE 없음은 route 발급 실패로 분류한다.
- Redis 조회 실패도 route 발급 실패로 분류한다.
- nickname, roomCode 검증 기준은 확인 필요 항목으로 둔다.
- HTTP status와 실패 body 형식은 아직 확정하지 않는다.
- 입력 검증 실패와 route 발급 실패의 HTTP status를 분리할지는 확인 필요 항목으로 둔다.

## 13. API 구현 계획

### 13.1 `POST /routes/quick`

요청:

```json
{ "nickname": "pobi" }
```

처리:

- quick route용 `geId` 선택
- quick join action으로 routeToken 발급

응답:

```json
{ "routeToken": "..." }
```

### 13.2 `POST /routes/private`

요청:

```json
{ "nickname": "pobi" }
```

처리:

- load가 낮은 ACTIVE GE 선택
- private create action으로 routeToken 발급

응답:

```json
{ "routeToken": "..." }
```

### 13.3 `POST /routes/private/join`

요청:

```json
{ "nickname": "pobi", "roomCode": "ABCDEF" }
```

처리:

- `roomcode:{roomCode}`에서 `geId` 조회
- ACTIVE 확인
- private join action과 roomCode를 포함해 routeToken 발급

응답:

```json
{ "routeToken": "..." }
```

## 14. 테스트 작성 기준

### RouteService 테스트

확인할 조건:

- quick route에서 `GeDirectory`가 반환한 geId로 token을 발급한다.
- private create route에서 `GeDirectory`가 반환한 geId로 token을 발급한다.
- private join route에서 roomCode를 포함해 token을 발급한다.
- GE 후보가 없으면 `LobbyException`을 발생시킨다.
- Service가 Redis key 문자열을 직접 다루지 않는다.
- Service가 `RouteTokenIssuer` 외의 방식으로 token 문자열을 만들지 않는다.

### RedisGeDirectory 테스트

확인할 조건:

- `quick:available` 후보 중 `ge:{geId}`가 `ACTIVE`인 첫 후보를 반환한다.
- `DRAINING` GE와 key가 없는 GE는 제외한다.
- quick 후보가 없으면 `ge:load` fallback으로 ACTIVE GE를 찾는다.
- private create는 `ge:load`에서 낮은 score 순으로 ACTIVE GE를 찾는다.
- private join은 `roomcode:{roomCode}`로 geId를 찾고 ACTIVE 확인을 수행한다.
- roomCode가 없으면 route 발급 실패로 이어질 수 있는 결과를 반환한다.
- Redis 예외가 발생하면 route 발급 실패로 전파된다.
- stale `quick:available` member를 삭제하지 않는다.
- 문서에 없는 Redis key를 접근하지 않는다.

### RouteTokenIssuer 테스트

확인할 조건:

- payload에 `action`, `nickname`, `geId`, `exp`가 포함된다.
- private join payload에만 `roomCode`가 포함된다.
- configured ttl이 만료 시간 계산에 반영된다.
- secret이 설정되지 않았을 때의 처리 방식은 확인된 정책에 따른다.
- 알고리즘과 `exp` 단위는 확정된 계약에 맞춰 검증한다.

### RouteController Web MVC 테스트

확인할 조건:

- `POST /routes/quick` 성공 시 응답 body가 `routeToken`만 포함한다.
- `POST /routes/private` 성공 시 응답 body가 `routeToken`만 포함한다.
- `POST /routes/private/join` 성공 시 응답 body가 `routeToken`만 포함한다.
- route 발급 실패 시 응답에 `routeToken`이 없다.
- Controller가 service 실패를 직접 catch하지 않고 exception handler 응답으로 처리한다.
- 입력 검증 기준이 확정되면 nickname, roomCode 누락/빈 값 테스트를 추가한다.

### Spring context load 테스트

확인할 조건:

- 설정 객체가 정상 바인딩된다.
- Controller, Service, RedisGeDirectory, RouteTokenIssuer bean이 정상 생성된다.
- test profile에서 token secret 등 필수 설정을 어떻게 제공할지 확인한다.

## 15. 구현 단계

### 1단계: 설정 객체 추가

수정/생성 파일:

- `src/main/java/io/jhpark/kopic/lobby/config/LobbyProperties.java`
- `src/main/java/io/jhpark/kopic/lobby/config/RedisKeyProperties.java`
- `src/main/java/io/jhpark/kopic/lobby/config/RouteTokenProperties.java`
- `src/main/resources/application.yaml`
- 필요 시 `KopicLobbyApplication.java` 또는 별도 configuration class

확인 방법:

- `./gradlew compileJava`
- context load 테스트

주의사항:

- 설정 객체는 런타임 상태를 저장하지 않는다.
- Redis key 설정과 token 설정을 섞지 않는다.
- 실제 설정 파일 수정은 구현 단계에서 진행한다. 이 문서 작업에서는 수정하지 않는다.

### 2단계: 요청/응답 DTO 추가

수정/생성 파일:

- `src/main/java/io/jhpark/kopic/lobby/route/RouteRequest.java`
- `src/main/java/io/jhpark/kopic/lobby/route/RouteResponse.java`

확인 방법:

- DTO 직렬화/역직렬화 테스트 또는 Web MVC 테스트에서 확인

주의사항:

- 성공 응답에는 `routeToken`만 둔다.
- private join 요청 외에는 `roomCode`를 요구하지 않는다.
- 입력 검증 기준은 확인 전까지 과하게 확정하지 않는다.

### 3단계: 예외 타입과 예외 핸들러 추가

수정/생성 파일:

- `src/main/java/io/jhpark/kopic/lobby/support/LobbyException.java`
- `src/main/java/io/jhpark/kopic/lobby/support/LobbyExceptionHandler.java`

확인 방법:

- Web MVC 테스트에서 실패 응답에 `routeToken`이 없는지 확인

주의사항:

- HTTP status와 body 형식은 확인 전까지 문서에 없는 형태로 고정하지 않는다.
- Controller에 try-catch 응답 생성을 넣지 않는다.

### 4단계: `GeDirectory` 인터페이스 정의

수정/생성 파일:

- `src/main/java/io/jhpark/kopic/lobby/redis/GeDirectory.java`

확인 방법:

- `RouteService` 테스트에서 mock으로 대체 가능한지 확인

주의사항:

- HTTP DTO나 Redis template 타입을 인터페이스에 노출하지 않는다.
- 메서드는 Lobby route에 필요한 세 가지 조회 기능으로 제한한다.

### 5단계: `RedisGeDirectory` 구현

수정/생성 파일:

- `src/main/java/io/jhpark/kopic/lobby/redis/RedisGeDirectory.java`

확인 방법:

- Redis operation mock 기반 단위 테스트
- local Redis 수동 key 기반 통합 확인

주의사항:

- Redis 접근은 이 클래스에만 둔다.
- stale quick candidate를 삭제하지 않는다.
- `ACTIVE`만 후보로 인정한다.
- 문서의 4종류 외 key를 사용하지 않는다.

### 6단계: `RouteTokenIssuer` 구현

수정/생성 파일:

- `src/main/java/io/jhpark/kopic/lobby/route/token/RouteTokenIssuer.java`
- `src/main/java/io/jhpark/kopic/lobby/route/token/RouteTokenPayload.java`
- 필요 시 `build.gradle`

확인 방법:

- token payload 필드 테스트
- ttl 반영 테스트
- WS와 합의된 알고리즘/exp 단위 기준 테스트

주의사항:

- JWT 라이브러리 추가 여부는 확인 후 결정한다.
- Redis 조회를 하지 않는다.
- roomCode는 private join에서만 넣는다.

### 7단계: `RouteService` 구현

수정/생성 파일:

- `src/main/java/io/jhpark/kopic/lobby/route/RouteService.java`
- 필요 시 `src/main/java/io/jhpark/kopic/lobby/route/RouteActionProperties.java`

확인 방법:

- `RouteService` 단위 테스트

주의사항:

- `GeDirectory`와 `RouteTokenIssuer`만 조합한다.
- Redis key 문자열을 직접 만들지 않는다.
- room 생성, actor 처리, session routing을 구현하지 않는다.

### 8단계: `RouteController` 구현

수정/생성 파일:

- `src/main/java/io/jhpark/kopic/lobby/route/RouteController.java`

확인 방법:

- Web MVC 테스트
- 성공 응답 body 확인

주의사항:

- Controller는 `RouteService`만 호출한다.
- `StringRedisTemplate`이나 `RouteTokenIssuer`를 주입하지 않는다.
- 실패 응답은 exception handler에 맡긴다.

### 9단계: 단위 테스트 작성

수정/생성 파일:

- `src/test/java/io/jhpark/kopic/lobby/route/RouteServiceTest.java`
- `src/test/java/io/jhpark/kopic/lobby/redis/RedisGeDirectoryTest.java`
- `src/test/java/io/jhpark/kopic/lobby/route/token/RouteTokenIssuerTest.java`
- 필요 시 테스트 fixture/helper

확인 방법:

- `./gradlew test`

주의사항:

- 테스트를 위해 production 구조를 과하게 바꾸지 않는다.
- Redis 실제 서버가 필요한 테스트와 mock 기반 테스트를 분리한다.

### 10단계: API 테스트 및 통합 확인

수정/생성 파일:

- `src/test/java/io/jhpark/kopic/lobby/route/RouteControllerTest.java`
- 필요 시 통합 테스트 설정

확인 방법:

- `./gradlew test`
- 로컬 Redis에 아래 key를 수동으로 넣어 확인

```text
SET ge:ge-local ACTIVE EX 15
ZADD ge:load 1 ge-local
ZADD quick:available 1769270000000 ge-local:rid_a
SET roomcode:ABCDEF ge-local EX 3600
```

주의사항:

- quick candidate가 있으면 `quick:available`의 GE가 선택되는지 확인한다.
- quick candidate가 없으면 `ge:load` fallback이 동작하는지 확인한다.
- roomCode가 없으면 private join route 발급이 실패하는지 확인한다.
- GE/WS가 필요한 검증은 이 저장소 단독 테스트로 대체하지 않는다.

## 16. 완료 기준

- Lobby가 local memory에 GE/room 상태를 저장하지 않는다.
- Redis key는 문서의 4종류만 조회한다.
- quick route는 join 가능한 quick room GE를 우선 선택한다.
- quick 후보가 없으면 load가 낮은 ACTIVE GE를 선택한다.
- private create는 load가 낮은 ACTIVE GE를 선택한다.
- private join은 roomCode로 GE를 찾고 ACTIVE 확인 후 routeToken을 발급한다.
- 성공 응답은 `routeToken`만 반환한다.
- 실패 응답은 `routeToken`을 반환하지 않는다.
- WS나 GE 책임을 Lobby에 가져오지 않는다.
