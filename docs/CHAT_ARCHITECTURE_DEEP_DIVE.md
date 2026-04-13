# Recruitable Chat Service - Architecture Deep Dive

## Purpose of This Document

This document is a deep technical explanation of the chat/messaging system in `recruitable-chat-service`.

It is written to help you:

- understand the architecture end-to-end
- explain design decisions to other engineers
- onboard new team members quickly
- reason about failures, debugging, and scaling

This is implementation-oriented: every concept maps to the current codebase and runtime behavior.

---

## 1) System Context

The chat service is a standalone microservice in the Recruitable platform.

- Main API service handles user auth, business logic, and token issuance.
- Chat service handles chat rooms, messages, presence, typing, and file metadata.
- Frontend talks directly to chat service for REST + WebSocket.

### Why separate service?

A dedicated chat service allows:

- independent scaling for real-time traffic
- isolated schema and domain model
- simpler ownership of messaging features
- separate deployment cadence from main API

### Runtime role in platform

- Exposes REST endpoints on `/api/v1/chat/*`
- Exposes WebSocket STOMP endpoint on `/ws`
- Uses JWT from main auth system (shared secret, issuer, audience)

---

## 2) Technology Stack and Why Each Is Used

### Spring Boot 3 + Spring MVC

Used for REST API development, validation, exception handling, and integration with security.

Why:

- mature ecosystem
- quick controller/service/repository layering
- robust production behavior

### Spring Security + JWT validation

Used to authenticate every request without server sessions.

Why:

- stateless authentication
- easy microservice-to-microservice consistency
- low overhead for horizontal scaling

### Spring WebSocket + STOMP

Used for real-time pub/sub events.

Why STOMP:

- simple topic model for frontend
- clear separation between send destinations (`/app/*`) and subscribe destinations (`/topic/*`)
- works well with SockJS fallback

### MongoDB

Used for durable chat data:

- rooms (with embedded membership)
- messages
- read state metadata

Why document DB:

- flexible schema for evolving chat features
- embedded documents for room members (no JOINs needed)
- natural fit for message collections with compound indexes
- simple horizontal scaling

### Redis

Used for transient real-time state:

- online presence sets
- typing indicators with TTL

Why:

- low-latency ephemeral storage
- natural fit for fast-changing session-like status

### S3-compatible object storage

Used for chat file uploads (actual binary objects), while DB stores message metadata and URL.

Why:

- avoids storing large blobs in relational DB
- scalable and CDN-friendly

### RabbitMQ (AMQP event bus)

Used for broadcasting chat events across all application instances.

Every REST/WebSocket write publishes to a RabbitMQ topic exchange (`chat.exchange`).
Each instance creates its own anonymous queue and relays events to its locally-connected
WebSocket clients via `SimpMessagingTemplate`.

Why:

- enables horizontal scaling (multiple instances)
- decouples event production from WebSocket delivery
- guaranteed delivery to all connected clients across all nodes

### Optional: RabbitMQ STOMP relay

Can be enabled (`app.websocket.broker-relay-enabled: true`) to replace the in-memory
STOMP broker with a RabbitMQ STOMP plugin relay. Currently unused — the AMQP fan-out
pattern above is the primary cross-instance mechanism.

---

## 3) High-Level Architecture

## Components

- **Controllers**: transport layer (HTTP/STOMP)
- **Service layer**: business rules, authorization checks, transaction boundaries
- **Repositories**: persistence operations
- **Security layer**: JWT extraction + validation + principal creation
- **Config layer**: security policy, websocket broker, S3 clients

## Request flow (REST)

1. Request enters security filter chain.
2. JWT filter validates token and stores auth principal.
3. Controller method runs with current user context.
4. Service enforces org boundaries and room/member permissions.
5. Repository reads/writes entities.
6. Controller optionally emits WebSocket events to topics.
7. Response serialized in snake_case JSON.

## Event flow (WebSocket)

1. Client connects to `/ws` with JWT.
2. `WebSocketAuthInterceptor` validates CONNECT token.
3. Client subscribes to org/room topics.
4. Interceptor enforces org and room-level subscribe authorization.
5. On publish (`/app/chat.send`, etc.), controller validates + persists via service.
6. Server broadcasts to destination topics (`/topic/org.{orgId}...`).

---

## 4) Data Model and Domain Design

Schema is managed as MongoDB collections with Spring Data MongoDB annotations.

### `chat_rooms` (collection)

Represents conversation containers with embedded membership.

Key fields:

- `organizationId`: multi-tenant partition key
- `type`: `PRIVATE`, `GROUP`, `PUBLIC`
- `name/description`: mostly for group/public rooms
- `members`: embedded array of `ChatRoomMember` documents

### `ChatRoomMember` (embedded document within `chat_rooms`)

Represents user-room membership and read state.

Key fields:

- `userId`
- `role`: `OWNER`, `ADMIN`, `MEMBER`
- `lastReadAt`: per-user room read marker
- `joinedAt`: when the user was added

### `chat_messages` (collection)

Represents chat message records.

Key fields:

- `roomId` + `organizationId` (denormalized for access-control)
- `senderId`, `senderName`
- `type`: `TEXT`, `FILE`, `SYSTEM`
- file metadata (`fileUrl`, `fileName`, etc.)
- soft-delete flags (`deleted`)
- edit metadata (`edited`, `editedAt`)
- `createdAt` (compound index with `roomId` for efficient pagination)

### Why this model?

- Embedded membership eliminates JOIN queries for room access checks
- Compound indexes enable efficient room-scoped message pagination
- Document model naturally supports varying message types (TEXT/FILE/SYSTEM)

---

## 5) Room Types and Authorization Rules

## PRIVATE rooms

- exactly two users
- reused for the same pair within an org
- not subscribable/visible outside those members

## GROUP rooms

- explicit membership
- creator becomes `OWNER`
- add/remove members supported

## PUBLIC rooms

- org-wide visibility/access
- only JWT `ADMIN` can create
- all org users can read/send/subscribe

## Group management permissions

For add/remove member operations:

- room `OWNER`: allowed
- room `ADMIN`: allowed
- org `ADMIN` (from JWT): allowed

---

## 6) Security Architecture

## HTTP authentication

`JwtAuthenticationFilter`:

- reads `Authorization: Bearer <token>` header
- falls back to `rct_at` or `access_token` cookies
- validates signature, issuer, audience, expiry
- checks token revocation via Redis blacklist
- extracts claims into `JwtAuthenticationToken`
- stores auth in `SecurityContext`

## WebSocket authentication

`WebSocketAuthInterceptor` on STOMP inbound channel:

- validates CONNECT token (same JWT rules)
- sets authenticated principal on STOMP session

## WebSocket subscription authorization

On SUBSCRIBE:

1. validate topic org matches user org
2. if destination includes room id:
   - for `PRIVATE`/`GROUP`: user must be member
   - for `PUBLIC`: org match is enough

This is crucial to prevent unauthorized room snooping.

## Role normalization detail

Role checks normalize both `ADMIN` and `ROLE_ADMIN` to avoid JWT format mismatch bugs.

---

## 7) API Surface and Interaction Patterns

## Core REST endpoints

- `POST /rooms`
- `GET /rooms`
- `POST /rooms/{roomId}/members/{userId}`
- `DELETE /rooms/{roomId}/members/{userId}`
- `GET /rooms/{roomId}/messages`
- `POST /rooms/{roomId}/messages`
- `POST /users/{targetUserId}/rooms/private`
- `POST /users/{targetUserId}/messages`
- `PUT /messages/{messageId}`
- `DELETE /messages/{messageId}`
- `POST /rooms/{roomId}/read`
- `GET /users/online`
- `POST /files/upload`

## Direct message endpoint behavior

`POST /users/{targetUserId}/messages`:

1. resolve or create private room for sender + target
2. validate payload (`TEXT` body or `FILE` file_url)
3. persist message
4. return message DTO with resolved `room_id`
5. broadcast room/message events

Important contract:

- `targetUserId` comes from URL path
- body should not require `target_user_id`

---

## 8) WebSocket Topic Design

## Client publish destinations

- `/app/chat.send`
- `/app/chat.typing`
- `/app/chat.read`

## Server broadcast topics

- `/topic/org.{orgId}.room.{roomId}`
- `/topic/org.{orgId}.room.{roomId}.edit`
- `/topic/org.{orgId}.room.{roomId}.typing`
- `/topic/org.{orgId}.room.{roomId}.read`
- `/topic/org.{orgId}.room.{roomId}.members`
- `/topic/org.{orgId}.rooms`
- `/topic/org.{orgId}.presence`

## Why this topic shape?

- org namespace isolates tenants naturally
- room-level granularity keeps subscriber scopes small
- dedicated suffix channels reduce payload ambiguity

---

## 9) Presence and Typing Subsystem

## Presence

- Redis set key: `chat:presence:{orgId}`
- On connect: add user id
- On disconnect: remove user id
- Presence events broadcast to org presence topic

## Typing

- Redis key: `chat:typing:{roomId}:{userId}` with short TTL
- typing=true sets key
- typing=false deletes key

Why Redis here:

- these states are ephemeral and high-churn
- DB persistence would be noisy and expensive

---

## 10) Message Lifecycle

1. user sends (REST or STOMP)
2. service validates room access
3. service validates payload integrity
4. message row persisted
5. room `updatedAt` touched
6. broadcast to room topic
7. clients update UI

### Edit lifecycle

- sender-only edit
- sets `edited=true`, `editedAt`
- broadcasts to `.edit` topic

### Delete lifecycle

- sender-only soft delete
- marks `deleted=true`, nulls body
- broadcasts `MESSAGE_DELETED` event

### Read lifecycle

- updates `last_read_at`
- public rooms can auto-create lightweight membership/read-tracking row
- emits `READ_RECEIPT` event

---

## 11) Error Handling Strategy

`GlobalExceptionHandler` standardizes response envelopes:

- `ChatException` -> domain-aware status (400/403/404/409)
- validation errors -> field-level details
- malformed JSON/body -> 400 with parse detail
- missing endpoint/resource -> 404
- fallback -> 500

### Why this matters

- frontend gets predictable `message` and `status_code`
- easier observability and alerting
- fewer ambiguous failures

---

## 12) Configuration and Environment

Important config groups in `application.yml`:

- MongoDB connection (URI, database name, auto-index)
- Redis (presence, typing, token revocation)
- RabbitMQ (AMQP event bus for cross-instance fan-out)
- JWT secret/issuer/audience (must match main api-backend)
- S3 credentials and endpoint (file uploads)
- CORS settings (allowAllOrigins toggle)

## Deployment prerequisites

- MongoDB 7.0+
- Redis
- RabbitMQ (AMQP for event fan-out across instances)
- S3-compatible bucket

---

## 13) Scalability and Performance Notes

## Current scaling model

- app is stateless for auth (JWT)
- simple in-memory broker works for single instance
- relay mode recommended for multi-instance

## Hot paths

- room list retrieval
- message history pagination
- room-topic fan-out

## Existing optimizations

- indexed queries on room/message dimensions
- page-based history
- ephemeral data moved to Redis

## Future optimizations

- keyset pagination for very large rooms
- cached room metadata snapshots
- outbox/event relay for guaranteed broadcast durability

---

## 14) Common Failure Modes and Debug Playbook

## Failure: DM endpoint returns 400

Likely causes:

- missing/blank body for `TEXT`
- invalid payload type/file fields
- path/body mismatch in frontend contract

Check:

- response body from API
- controller mapping exists in running build

## Failure: `PUBLIC` enum rejected

Likely cause:

- stale runtime (old jar/classes) without `PUBLIC` enum

Fix:

- clean build + restart service process

## Failure: cannot subscribe to room over WS

Likely causes:

- expired token
- cross-org room/topic
- user not member of `PRIVATE`/`GROUP` room

## Failure: repeated WS expired-token warnings

Cause:

- frontend reconnect loop using stale token

Fix:

- refresh token, then reconnect WS with new token

---

## 15) Trade-Offs and Why This Design Was Chosen

## Chosen

- JWT stateless auth
- room membership model in relational DB
- STOMP topic routing with org namespacing
- Redis for transient status

## Trade-offs accepted

- no user profile table in chat service (must enrich from main API)
- room event payloads are lightweight (frontend may need follow-up fetch)
- some operations rely on service-layer authorization instead of DB policies

These choices optimize delivery speed and operational simplicity while keeping clear extension points.

---

## 16) Extension Roadmap (How to Grow This System)

Recommended next increments:

1. room role management endpoints (promote/demote room admins)
2. leave-room endpoint with owner transfer policy
3. delivery receipts per message/member
4. message reactions and pinned messages
5. search APIs (rooms/messages)
6. moderation and abuse reporting hooks
7. audit trail and compliance exports

---

## 17) Teaching Guide (How to Explain to Others)

When training teammates, use this progression:

1. System boundaries and why chat is a separate service
2. JWT/org isolation model
3. Room types + permission matrix
4. REST and WebSocket end-to-end flows
5. Data model and message lifecycle
6. Failure modes and debugging checklist
7. Scaling path from single instance to relay

This order mirrors real engineering decisions and helps people understand both design and operations.

---

## 18) Verification Commands

Use these to verify local build after any architecture-level changes:

```powershell
cd "C:\Users\ACER\Documents\project\springboot\recruitable\recruitable-chat-service"
mvn clean compile
mvn clean package -DskipTests
```

Optional run:

```powershell
java -jar ".\target\recruitable-chat-service-1.0-SNAPSHOT.jar"
```

---

## 19) Quick Reference

- Main service entry: `src/main/java/com/af/recruitable/chat/ChatServiceApplication.java`
- REST controller: `src/main/java/com/af/recruitable/chat/controller/ChatRestController.java`
- WebSocket controller: `src/main/java/com/af/recruitable/chat/controller/ChatWebSocketController.java`
- Auth interceptor: `src/main/java/com/af/recruitable/chat/config/WebSocketAuthInterceptor.java`
- Core business logic: `src/main/java/com/af/recruitable/chat/service/impl/ChatServiceImpl.java`
- Frontend guide: `docs/CHAT_REACT_FRONTEND_GUIDE.md`
- API docs: `docs/CHAT_SERVICE_DOCUMENTATION.md`

This deep-dive can be used as your internal architecture handbook for this service.

