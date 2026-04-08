# Recruitable Chat Service — Backend Documentation

## Overview

`recruitable-chat-service` is a Spring Boot chat microservice for Recruitable.
It provides:

- REST APIs for room and message operations
- STOMP/WebSocket for real-time updates
- org-isolated access using JWT `organizationId`
- Redis-backed presence/typing support
- S3-compatible file uploads

Service default port: `8082`

---

## Core Rules

### Multi-tenant isolation
Every room and message belongs to one organization.
The authenticated user's `organizationId` is taken from the JWT and used to isolate data.

### Auth model
The service does **not** have its own user table.
Identity comes from JWT claims:

- `sub` → current user id
- `organizationId` → org id
- `roles` → role list
- `permissions` → optional permission list
- `type` must be `access`

### Supported room types

| Type | Meaning | Who can access | Notes |
|---|---|---|---|
| `PRIVATE` | 1-on-1 direct message room | only the 2 members | created/reused automatically for direct messaging |
| `GROUP` | normal group chat | explicit members only | users can be added/removed |
| `PUBLIC` | org-wide room | all users in same org | only JWT role `ADMIN` can create |

### Room management permissions

| Action | Allowed |
|---|---|
| Create `PRIVATE` room | any authenticated user |
| Create `GROUP` room | any authenticated user |
| Create `PUBLIC` room | only org users whose JWT role includes `ADMIN` |
| Add/remove members in `GROUP` room | room `OWNER`, room `ADMIN`, or org `ADMIN` |
| Add/remove members in `PRIVATE` room | not allowed |
| Add/remove members in `PUBLIC` room | not supported because access is org-wide |

### Public room behavior

`PUBLIC` rooms are visible from `GET /rooms` for every user in the organization.
A user does not need to be manually added before:

- viewing the room
- subscribing to its WebSocket topic
- reading messages
- sending messages

A lightweight membership row may be created automatically when the user first marks the room as read, so unread tracking can work per user.

---

## Main Backend Files

Important files in the service:

- `src/main/java/com/af/recruitable/chat/controller/ChatRestController.java`
- `src/main/java/com/af/recruitable/chat/controller/ChatWebSocketController.java`
- `src/main/java/com/af/recruitable/chat/service/impl/ChatServiceImpl.java`
- `src/main/java/com/af/recruitable/chat/config/WebSocketAuthInterceptor.java`
- `src/main/java/com/af/recruitable/chat/security/SecurityUtils.java`
- `src/main/java/com/af/recruitable/chat/constant/RoomType.java`

---

## Data Model Summary

### `chat_rooms`
Stores room metadata:

- `id`
- `organization_id`
- `type` → `PRIVATE`, `GROUP`, `PUBLIC`
- `name`
- `description`
- `avatar_url`
- `created_at`
- `updated_at`

### `chat_room_members`
Stores membership and per-user read state:

- `room_id`
- `user_id`
- `role` → `OWNER`, `ADMIN`, `MEMBER`
- `last_read_at`
- `joined_at`

### `chat_messages`
Stores messages:

- text messages
- file messages
- reply/thread parent reference with `parent_message_id`
- edit/delete flags

---

## REST API

Base URL:

`http://localhost:8082/api/v1/chat`

All endpoints require:

`Authorization: Bearer <access_token>`

---

## Room APIs

### 1. Create room

`POST /rooms`

Creates `PRIVATE`, `GROUP`, or `PUBLIC` rooms.

#### Example: create direct room

```json
{
  "type": "PRIVATE",
  "member_user_ids": ["3d5d1c3f-6b68-46e8-b2d0-c3f67f2ed001"]
}
```

#### Example: create group room

```json
{
  "type": "GROUP",
  "name": "Engineering",
  "description": "Internal engineering chat",
  "member_user_ids": [
    "11111111-1111-1111-1111-111111111111",
    "22222222-2222-2222-2222-222222222222"
  ]
}
```

#### Example: create public room

```json
{
  "type": "PUBLIC",
  "name": "Announcements",
  "description": "Org-wide updates"
}
```

#### Public-room restriction
If the authenticated user's JWT roles do not include `ADMIN`, `POST /rooms` with `type = PUBLIC` returns `403`.

---

### 2. Resolve or create private room with a user

`POST /users/{targetUserId}/rooms/private`

Returns the existing direct room or creates it if missing.
Useful when frontend wants a DM room first, before sending a message.

Response: `ChatRoomResponse`

---

### 3. List accessible rooms

`GET /rooms`

Returns:

- all `PRIVATE` rooms where the user is a member
- all `GROUP` rooms where the user is a member
- all `PUBLIC` rooms in the user's org

Response: `ChatRoomResponse[]`

---

### 4. Add member to group room

`POST /rooms/{roomId}/members/{userId}`

Allowed only for:

- room `OWNER`
- room `ADMIN`
- org `ADMIN`

Only works for `GROUP` rooms.

---

### 5. Remove member from group room

`DELETE /rooms/{roomId}/members/{userId}`

Allowed only for:

- room `OWNER`
- room `ADMIN`
- org `ADMIN`

Only works for `GROUP` rooms.
The last `OWNER` cannot be removed.

---

## Message APIs

### 6. Get room messages

`GET /rooms/{roomId}/messages?page=0&size=50`

Returns paginated room history.

Access rules:

- `PRIVATE` / `GROUP`: user must belong to room
- `PUBLIC`: any user in same org may access

---

### 7. Send message to room

`POST /rooms/{roomId}/messages`

Example:

```json
{
  "body": "hello team",
  "type": "TEXT"
}
```

For file messages:

```json
{
  "type": "FILE",
  "body": "project-plan.pdf",
  "file_url": "https://...",
  "file_name": "project-plan.pdf",
  "file_size": 14592,
  "file_content_type": "application/pdf"
}
```

Validation:

- `TEXT` requires non-empty `body`
- `FILE` requires `file_url`

---

### 8. Send private message directly to a user

`POST /users/{targetUserId}/messages`

This endpoint:

1. resolves or creates the `PRIVATE` room for the 2 users
2. stores the message
3. returns `ChatMessageResponse` including the real `room_id`

Example:

```json
{
  "body": "Hi, can we talk?",
  "type": "TEXT"
}
```

Response example:

```json
{
  "id": "message-uuid",
  "room_id": "private-room-uuid",
  "sender_id": "current-user-uuid",
  "type": "TEXT",
  "body": "Hi, can we talk?",
  "edited": false,
  "deleted": false,
  "created_at": "2026-04-06T06:00:00Z"
}
```

Frontend can use the returned `room_id` as the active DM room afterwards.

---

### 9. Edit message

`PUT /messages/{messageId}`

Only sender can edit.
Deleted messages cannot be edited.

---

### 10. Delete message

`DELETE /messages/{messageId}`

Soft delete only.
Only sender can delete.

---

## Read / Presence / File APIs

### 11. Mark room as read

`POST /rooms/{roomId}/read`

Updates `last_read_at` for the current user.
For `PUBLIC` rooms, a membership/read-tracking record can be created automatically if needed.

### 12. Get online users

`GET /users/online`

Returns `UUID[]` for online users in the current org.

### 13. Upload file

`POST /files/upload`

`multipart/form-data` with field name `file`.
Returns:

```json
{
  "file_url": "https://...",
  "file_name": "image.png",
  "file_size": 12345,
  "content_type": "image/png"
}
```

---

## Main DTOs Returned to Frontend

### `ChatRoomResponse`

```json
{
  "id": "uuid",
  "organization_id": "uuid",
  "type": "PRIVATE",
  "name": null,
  "description": null,
  "avatar_url": null,
  "created_at": "2026-04-06T06:00:00Z",
  "updated_at": "2026-04-06T06:05:00Z",
  "members": [
    {
      "user_id": "uuid",
      "role": "MEMBER",
      "joined_at": "2026-04-06T06:00:00Z",
      "last_read_at": null
    }
  ],
  "unread_count": 2,
  "last_message": null
}
```

### `ChatMessageResponse`

```json
{
  "id": "uuid",
  "room_id": "uuid",
  "sender_id": "uuid",
  "sender_name": null,
  "type": "TEXT",
  "body": "Hello",
  "parent_message_id": null,
  "file_url": null,
  "file_name": null,
  "file_size": null,
  "file_content_type": null,
  "edited": false,
  "deleted": false,
  "created_at": "2026-04-06T06:00:00Z",
  "edited_at": null
}
```

### `WebSocketEventDto`

```json
{
  "event": "ROOM_CREATED",
  "user_id": "uuid",
  "room_id": "uuid",
  "message_id": null,
  "timestamp": 1775450000000
}
```

---

## WebSocket / STOMP

### Connect

Endpoint:

- `ws://localhost:8082/ws`
- SockJS fallback also available on `/ws`

Headers:

```text
Authorization: Bearer <access_token>
```

or

```text
token: <access_token>
```

### Server-side subscription security

`WebSocketAuthInterceptor` now validates both:

1. org-level access
2. room-level access

That means:

- users cannot subscribe to other org topics
- users cannot subscribe to `PRIVATE`/`GROUP` rooms they do not belong to
- users **can** subscribe to `PUBLIC` room topics in their own org

---

## STOMP destinations

### Client -> Server

| Destination | Payload | Purpose |
|---|---|---|
| `/app/chat.send` | `SendMessageRequest` | send to an existing room |
| `/app/chat.typing` | `TypingEvent` | typing state |
| `/app/chat.read` | `ReadReceiptRequest` | mark room as read |

### Server -> Client

| Destination | Payload | Meaning |
|---|---|---|
| `/topic/org.{orgId}.room.{roomId}` | `ChatMessageResponse` or `WebSocketEventDto` | new message / delete event |
| `/topic/org.{orgId}.room.{roomId}.edit` | `ChatMessageResponse` | edited message |
| `/topic/org.{orgId}.room.{roomId}.typing` | `TypingEvent` | typing updates |
| `/topic/org.{orgId}.room.{roomId}.read` | `WebSocketEventDto` | read receipt |
| `/topic/org.{orgId}.room.{roomId}.members` | `WebSocketEventDto` | member added / removed |
| `/topic/org.{orgId}.rooms` | `WebSocketEventDto` | room created / room upserted / membership changes |
| `/topic/org.{orgId}.presence` | `WebSocketEventDto` | online / offline presence |

### Known `WebSocketEventDto.event` values

- `USER_ONLINE`
- `USER_OFFLINE`
- `ROOM_CREATED`
- `ROOM_UPSERTED`
- `MEMBER_ADDED`
- `MEMBER_REMOVED`
- `MESSAGE_DELETED`
- `READ_RECEIPT`

---

## Suggested Frontend Flows

### Direct message flow

Option A:

1. call `POST /users/{targetUserId}/rooms/private`
2. open returned `room_id`
3. subscribe to `/topic/org.{orgId}.room.{roomId}`
4. send via `/app/chat.send` or `POST /rooms/{roomId}/messages`

Option B:

1. call `POST /users/{targetUserId}/messages`
2. backend auto-creates/reuses the DM room
3. use returned `room_id` as active conversation

### Public room flow

1. Admin creates room with `POST /rooms` and `type = PUBLIC`
2. All org users receive room event on `/topic/org.{orgId}.rooms`
3. All org users can list the room through `GET /rooms`
4. Any org user can open, subscribe, read, and send

### Group member management flow

1. Authorized user calls `POST /rooms/{roomId}/members/{userId}` or `DELETE /rooms/{roomId}/members/{userId}`
2. Frontend listens on:
   - `/topic/org.{orgId}.rooms`
   - `/topic/org.{orgId}.room.{roomId}.members`
3. Refresh room details or room list after event

---

## Config Notes

Important properties in `application.yml`:

| Property | Meaning |
|---|---|
| `server.port` | service port |
| `app.jwt.secret` | shared JWT secret with main backend |
| `app.jwt.issuer` | expected issuer |
| `app.jwt.audience` | expected audience |
| `app.websocket.broker-relay-enabled` | use RabbitMQ STOMP relay |
| `spring.flyway.default-schema` | isolated chat schema |

---

## Validation Notes / Edge Cases

- self-DM is rejected
- `PRIVATE` room requires exactly one target user
- `PUBLIC` room creation by non-admin is rejected with `403`
- `GROUP` member add/remove on non-group room is rejected
- last `OWNER` of a group cannot be removed
- `FILE` messages require `file_url`
- blank message body is rejected for non-file messages
- cross-org room access is rejected for REST and WebSocket

---

## Verification

The backend was validated with a clean Maven compile/package during implementation.
If you want to re-run locally:

```powershell
cd "C:\Users\ACER\Documents\project\springboot\recruitable\recruitable-chat-service"
mvn clean package -DskipTests
```
