# Phase 0 go/no-go 게이트 결과: GO (조건부)

- 판정 시각(UTC): 2026-06-14
- 대상: gajae-code(gjc) Bridge protocol v2 클라이언트 컴패니언 앱 (CMP)
- 게이트 위치: 모든 빌드/구현보다 선행
- 결과: **GO** — 단, gjc 측 opt-in 패치 전제(아래)

## 전제: gjc bridge fail-closed 해제 패치 (사용자 승인, "v1 gjc 무변경" 제약 해제)

현재 gjc 런타임(`src/modes/bridge/bridge-mode.ts`의 `runBridgeMode`)은 `createBridgeFetchHandler`에 `endpointMatrix`를 전달하지 않아 **항상 fail-closed**다(events/commands/control/uiResponses/hostToolResults/hostUriResults 전부 `403 endpoint_disabled`). env/플래그로 해제 불가.

적용한 최소 opt-in 패치(기본값은 fail-closed 유지, backward-compatible):
- `parseBridgeEndpoints(GJC_BRIDGE_ENDPOINTS)` 추가 → `createBridgeFetchHandler({ …, endpointMatrix })`로 전달.
- `GJC_BRIDGE_ENDPOINTS="all"` 또는 `"events,commands,control,uiResponses,hostToolResults,hostUriResults"`로 엔드포인트 opt-in.
- 패치 위치(설치본): `node_modules/@gajae-code/coding-agent/src/modes/bridge/bridge-mode.ts`. 업스트림 기여 또는 포크로 영속화 필요(레포 외부 의존성).

## Phase 0A — bridge 활성화 증명 (GO)

기동: `GJC_BRIDGE_TOKEN`, `GJC_BRIDGE_HOST=127.0.0.1`, `GJC_BRIDGE_PORT=4077`, `GJC_BRIDGE_ENDPOINTS=all`, `GJC_BRIDGE_SCOPES=prompt,control,bash,export,session,model,message:read,host_tools,host_uri,admin`, `GJC_BRIDGE_TLS_CERT/KEY`(self-signed) → `gjc --mode bridge`.

증거(redacted):
- `GET /healthz` → `200 {"status":"ok"}`.
- `GET /v1/help` → `200`, `endpoints` 전부 `true`(패치 동작 확인).
- `POST /v1/handshake`(Authorization Bearer) → `200 {"status":"accepted","protocol_version":2, session_id, accepted_capabilities[8], accepted_scopes, endpoints{...}}`.
- `GET /v1/sessions/{id}/events?last_seq=0` → `200`, 스트림 open(403 아님).
- `POST /v1/sessions/{id}/commands` `{"type":"get_session_stats"}`(Authorization + Idempotency-Key) → `200 {"type":"response","success":true,...}` (disposable read-only, 모델 미호출).
- 음성 제어: 잘못된 토큰 command POST → `401 {"error":"unauthorized"}`.

## Phase 0B — Web 인증 스트리밍 topology 증명 (GO, 핵심 사실 확정)

- bridge는 **CORS 헤더를 보내지 않음**: cross-origin `OPTIONS /v1/handshake` → `401`, `/healthz`에 `Access-Control-Allow-Origin` 없음. ⇒ 브라우저 직접 cross-origin 접근 불가 → **same-origin 리버스 프록시 필수**(계획과 일치).
- Caddy(v2.11.4) same-origin 프록시 검증: `localhost:8443`에서 `tls internal`, `/healthz`·`/v1/*`를 `https://127.0.0.1:4077`로 reverse_proxy(`tls_insecure_skip_verify`), `/`는 정적 파일. curl로 same-origin `GET /healthz`(200), `GET /v1/help`(200, endpoints true), `GET /`(200), `POST /v1/handshake`(200, v2, Authorization 헤더) 확인.
- 프로덕션 신뢰 TLS: Caddy auto-HTTPS(실도메인/Tailscale 인증서)로 충족(알려진 기능).

### follow-up (구현 중 닫을 것)
- 헤드리스 브라우저가 Caddy 내부 CA를 신뢰하지 않아(ERR_CERT_AUTHORITY_INVALID) 브라우저 ReadableStream 증분 수신 화면 증명은 보류. 신뢰 CA(또는 `caddy trust`/실인증서) 환경에서 브라우저 fetch 증분 스트리밍 스크린샷 증거를 G005에서 확보.
- Ktor Wasm 스트림이 버퍼링되면 Web actual을 직접 Fetch ReadableStream으로 교체 후 Phase 0B 재검증.

## Stop 조건 점검
모두 통과(endpoint 활성, protocol v2, events open, command 수락, 인증 강제). Web은 same-origin 프록시 전제로 통과.

## 결론
GO. 후속 구현(G002~) 착수 가능. 단 bridge 활성화는 gjc 패치 전제이며, 이는 레포 외부(`node_modules`/업스트림) 의존성으로 추적 필요.
