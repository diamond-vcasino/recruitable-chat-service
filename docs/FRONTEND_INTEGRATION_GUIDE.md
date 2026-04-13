# Recruitable Chat Service — Frontend React Integration Guide

> **Base URL**: `http://localhost:8082` (local) · `https://your-domain/chat-api` (production)
>
> **Authentication**: Every HTTP request must include `Authorization: Bearer <access_token>`.
> The access token is the `rct_at` cookie/localStorage value issued by the main auth service.

---

## Table of Contents

1. [Authentication & WebSocket Connection](#1-authentication--websocket-connection)
2. [REST API Reference](#2-rest-api-reference)
   - [Organization Members](#21-list-organization-members)
   - [Online Users](#22-get-online-users)
   - [Rooms](#23-rooms)
   - [Messages](#24-messages)
   - [Read Receipts](#25-read-receipts)
   - [File Upload](#26-file-upload)
3. [WebSocket (STOMP) Reference](#3-websocket-stomp-reference)
   - [Connection](#31-connecting)
   - [Subscriptions](#32-subscriptions)
   - [Sending via WebSocket](#33-sending-messages-via-websocket)
   - [Typing Indicator](#34-typing-indicator)
   - [Read Receipt via WS](#35-read-receipt-via-websocket)
4. [Real-Time Event Payloads](#4-real-time-event-payloads)
5. [Complete React Integration Example](#5-complete-react-integration-example)
6. [Enums & Constants](#6-enums--constants)
7. [Error Handling](#7-error-handling)
8. [Architecture Diagram](#8-architecture-diagram)

---

## 1. Authentication & WebSocket Connection

The chat service does **not** have its own login. It trusts the JWT issued by the main `recruitable-api` auth service. The same `rct_at` access token is used for both REST calls and WebSocket STOMP connections.

### Token flow

```
Browser (rct_at cookie/localStorage)
  │
  ├─── REST: Authorization: Bearer <rct_at>
  │
  └─── WebSocket STOMP CONNECT frame:
         header "Authorization": "Bearer <rct_at>"
```

### Token refresh

The access token (`rct_at`) is short-lived (~15 min). Before it expires, use your auth service's `/auth/refresh` endpoint with the `rct_rt` refresh token to get a new access token. If the WS connection drops due to an expired token, reconnect with the fresh token.

---

## 2. REST API Reference

All endpoints are under `/api/v1/chat`. Responses use **snake_case** JSON.

### 2.1 List Organization Members

Fetch org members for user picker (start DM, add to group).

```
GET /api/v1/chat/org-members?search=jane&page=0&size=20
```

| Param    | Type   | Default | Description                        |
|----------|--------|---------|------------------------------------|
| `search` | string | —       | Filter by name or email (optional) |
| `page`   | int    | 0       | Zero-based page number             |
| `size`   | int    | 20      | Page size                          |

**Response** `200 OK`:
```json
{
  "content": [
    {
      "user_id": "f05da3c9-d2c8-4901-ba2b-86966bfed1cd",
      "full_name": "Jane Doe",
      "email": "jane@example.com",
      "avatar_url": "https://cdn.example.com/avatars/jane.png",
      "role": "RECRUITER",
      "department": "Engineering",
      "status": "ACTIVE",
      "is_current_user": false
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 1,
  "total_pages": 1,
  "first": true,
  "last": true
}
```

### 2.2 Get Online Users

Returns enriched user profiles of all currently online users in the org.

```
GET /api/v1/chat/users/online
```

**Response** `200 OK`:
```json
[
  {
    "user_id": "f05da3c9-d2c8-4901-ba2b-86966bfed1cd",
    "full_name": "Jane Doe",
    "email": "jane@example.com",
    "avatar_url": "https://cdn.example.com/avatars/jane.png",
    "role": "RECRUITER",
    "department": "Engineering",
    "status": "ACTIVE",
    "is_current_user": false
  }
]
```

---

### 2.3 Rooms

#### 2.3.1 List My Rooms

```
GET /api/v1/chat/rooms
```

**Response** `200 OK`:
```json
[
  {
    "id": "cdc8bbda-390e-4718-976f-54551a8d66c7",
    "organization_id": "c50aa1b2-b4cd-4124-97da-71bb1458a4cb",
    "type": "PRIVATE",
    "name": null,
    "description": null,
    "avatar_url": null,
    "created_at": "2026-04-07T06:00:00Z",
    "updated_at": "2026-04-07T06:30:00Z",
    "members": [
      {
        "user_id": "e3732edd-d4b0-472a-ae18-d5d4cb292e26",
        "role": "MEMBER",
        "joined_at": "2026-04-07T06:00:00Z",
        "last_read_at": "2026-04-07T06:30:00Z"
      },
      {
        "user_id": "ec425a23-4551-4ceb-9237-52b505c48b11",
        "role": "MEMBER",
        "joined_at": "2026-04-07T06:00:00Z",
        "last_read_at": null
      }
    ],
    "unread_count": 3,
    "last_message": {
      "id": "37b6faaa-4eb1-42e5-b1e8-5bc4f3180d3e",
      "room_id": "cdc8bbda-390e-4718-976f-54551a8d66c7",
      "sender_id": "e3732edd-d4b0-472a-ae18-d5d4cb292e26",
      "sender_name": "John Doe",
      "type": "TEXT",
      "body": "Hello!",
      "parent_message_id": null,
      "file_url": null,
      "file_name": null,
      "file_size": null,
      "file_content_type": null,
      "edited": false,
      "deleted": false,
      "created_at": "2026-04-07T06:30:00Z",
      "edited_at": null
    }
  }
]
```

#### 2.3.2 Create a Room

```
POST /api/v1/chat/rooms
Content-Type: application/json
```

**Request body — PRIVATE room** (1-on-1 DM):
```json
{
  "type": "PRIVATE",
  "member_user_ids": ["ec425a23-4551-4ceb-9237-52b505c48b11"]
}
```

**Request body — GROUP room**:
```json
{
  "type": "GROUP",
  "name": "Engineering Team",
  "description": "All engineers",
  "member_user_ids": [
    "ec425a23-4551-4ceb-9237-52b505c48b11",
    "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  ]
}
```

**Request body — PUBLIC room** (admin only):
```json
{
  "type": "PUBLIC",
  "name": "Company Announcements",
  "description": "Org-wide channel"
}
```

| Field             | Type     | Required | Notes                                           |
|-------------------|----------|----------|-------------------------------------------------|
| `type`            | string   | ✅        | `PRIVATE`, `GROUP`, or `PUBLIC`                  |
| `name`            | string   | GROUP/PUBLIC | Ignored for PRIVATE                          |
| `description`     | string   | No       | Room description                                |
| `member_user_ids` | UUID[]   | PRIVATE: 1 | PRIVATE requires exactly 1; GROUP: 0+; PUBLIC: ignored |

**Response** `201 Created`: Same shape as room in [2.3.1](#231-list-my-rooms).

#### 2.3.3 Get or Create Private Room (Shortcut)

Resolves the 1-on-1 room with a user, creating it if it doesn't exist.

```
POST /api/v1/chat/users/{targetUserId}/rooms/private
```

**Response** `200 OK`: Room object.

#### 2.3.4 Add Member to Group Room

```
POST /api/v1/chat/rooms/{roomId}/members/{userId}
```

**Response** `200 OK`: Updated room object.

#### 2.3.5 Remove Member from Group Room

```
DELETE /api/v1/chat/rooms/{roomId}/members/{userId}
```

**Response** `204 No Content`.

---

### 2.4 Messages

#### 2.4.1 Get Message History (Paginated)

```
GET /api/v1/chat/rooms/{roomId}/messages?page=0&size=50
```

| Param  | Default | Description                     |
|--------|---------|---------------------------------|
| `page` | 0       | Zero-based page (newest first)  |
| `size` | 50      | Messages per page               |

**Response** `200 OK`:
```json
{
  "content": [
    {
      "id": "37b6faaa-4eb1-42e5-b1e8-5bc4f3180d3e",
      "room_id": "cdc8bbda-390e-4718-976f-54551a8d66c7",
      "sender_id": "e3732edd-d4b0-472a-ae18-d5d4cb292e26",
      "sender_name": "John Doe",
      "type": "TEXT",
      "body": "Hello!",
      "parent_message_id": null,
      "file_url": null,
      "file_name": null,
      "file_size": null,
      "file_content_type": null,
      "edited": false,
      "deleted": false,
      "created_at": "2026-04-07T06:30:00Z",
      "edited_at": null
    }
  ],
  "page": 0,
  "size": 50,
  "total_elements": 1,
  "total_pages": 1,
  "first": true,
  "last": true
}
```

> **⚠️ Important**: Messages are returned **newest first** (`created_at DESC`). Reverse them in the UI for chronological display.

#### 2.4.2 Send a Message (REST)

```
POST /api/v1/chat/rooms/{roomId}/messages
Content-Type: application/json
```

**Text message**:
```json
{
  "body": "Hello team!",
  "type": "TEXT",
  "sender_name": "John Doe"
}
```

**File message** (after uploading via [2.6](#26-file-upload)):
```json
{
  "type": "FILE",
  "file_url": "https://s3.example.com/chat/file.pdf",
  "file_name": "report.pdf",
  "file_size": 204800,
  "file_content_type": "application/pdf",
  "sender_name": "John Doe"
}
```

**Reply to a message**:
```json
{
  "body": "I agree!",
  "type": "TEXT",
  "parent_message_id": "37b6faaa-4eb1-42e5-b1e8-5bc4f3180d3e",
  "sender_name": "John Doe"
}
```

| Field               | Type   | Required | Notes                                      |
|---------------------|--------|----------|--------------------------------------------|
| `body`              | string | TEXT/SYSTEM | Required for TEXT type                  |
| `type`              | string | No       | Default: `TEXT`. Options: `TEXT`, `FILE`, `SYSTEM` |
| `sender_name`       | string | No       | Display name to store with message         |
| `parent_message_id` | UUID   | No       | For threaded replies                       |
| `file_url`          | string | FILE     | Required for FILE type                     |
| `file_name`         | string | No       | Original filename                          |
| `file_size`         | long   | No       | File size in bytes                         |
| `file_content_type` | string | No       | MIME type                                  |

**Response** `201 Created`: Message object (same shape as in history).

> **💡 Tip**: Always pass `sender_name` so other users see a display name. The chat service stores it with the message.

#### 2.4.3 Send a Direct Private Message (Shortcut)

Creates or reuses the PRIVATE room with the target user and sends the message in one call.

```
POST /api/v1/chat/users/{targetUserId}/messages
Content-Type: application/json
```

```json
{
  "body": "Hey, are you available?",
  "type": "TEXT",
  "sender_name": "John Doe"
}
```

**Response** `201 Created`: Message object (includes `room_id` of the resolved/created room).

#### 2.4.4 Edit a Message

Only the original sender can edit. Only the `body` can be changed.

```
PUT /api/v1/chat/messages/{messageId}
Content-Type: application/json
```

```json
{
  "body": "Updated message text"
}
```

**Response** `200 OK`: Updated message object with `edited: true` and `edited_at` timestamp.

#### 2.4.5 Delete a Message (Soft Delete)

Only the original sender can delete. The message body is cleared; `deleted: true` is set.

```
DELETE /api/v1/chat/messages/{messageId}
```

**Response** `204 No Content`.

---

### 2.5 Read Receipts

```
POST /api/v1/chat/rooms/{roomId}/read
```

No request body needed. Marks all messages in the room as read for the current user.

**Response** `200 OK`.

---

### 2.6 File Upload

```
POST /api/v1/chat/files/upload
Content-Type: multipart/form-data
```

| Param  | Type | Description          |
|--------|------|----------------------|
| `file` | file | The file to upload   |

**Limits**: Max file 15 MB, max request 20 MB.

**Response** `200 OK`:
```json
{
  "file_url": "https://s3.cloud.ideeza.com/recruitable-storage1/chat/orgId/uuid.pdf",
  "file_name": "report.pdf",
  "file_size": 204800,
  "content_type": "application/pdf"
}
```

**Usage flow**:
1. Upload the file → get `file_url`
2. Send a message with `type: "FILE"` and pass the `file_url`, `file_name`, etc.

---

## 3. WebSocket (STOMP) Reference

### 3.1 Connecting

Use **SockJS + STOMP.js** (`@stomp/stompjs`).

```
WebSocket endpoint: ws://<host>:8082/ws          (native WS)
SockJS fallback:    http://<host>:8082/ws         (SockJS)
```

**STOMP CONNECT headers**:
```
Authorization: Bearer <rct_at>
```

The token can also be passed as:
- STOMP header `token: <rct_at>`
- STOMP header `rct_at: <rct_at>`
- Cookie `rct_at=<token>` during HTTP handshake
- Query param during SockJS handshake

### 3.2 Subscriptions

After connecting, subscribe to these topics:

#### 3.2.1 Room Changes (new rooms, member changes)

```
/topic/org.{orgId}.rooms
```

**Payload**: `WebSocketEvent`
```json
{
  "event": "ROOM_CREATED",       // or ROOM_UPSERTED, MEMBER_ADDED, MEMBER_REMOVED
  "user_id": "f05da3c9-...",
  "room_id": "cdc8bbda-...",
  "message_id": null,
  "timestamp": 1712500000000
}
```

**When received**: Re-fetch room list with `GET /api/v1/chat/rooms`.

#### 3.2.2 New Messages in a Room

```
/topic/org.{orgId}.room.{roomId}
```

**Payload**: `ChatMessageResponse` (same shape as REST) **OR** `WebSocketEvent` for `MESSAGE_DELETED`:
```json
// New/edited message:
{
  "id": "37b6faaa-...",
  "room_id": "cdc8bbda-...",
  "sender_id": "e3732edd-...",
  "sender_name": "John Doe",
  "type": "TEXT",
  "body": "Hello!",
  "created_at": "2026-04-07T06:30:00Z",
  ...
}

// Deleted message event:
{
  "event": "MESSAGE_DELETED",
  "user_id": "e3732edd-...",
  "room_id": "cdc8bbda-...",
  "message_id": "37b6faaa-...",
  "timestamp": 1712500000000
}
```

**How to distinguish**: If the payload has an `event` field → it's a `WebSocketEvent`. Otherwise → it's a `ChatMessageResponse`.

#### 3.2.3 Message Edits

```
/topic/org.{orgId}.room.{roomId}.edit
```

**Payload**: `ChatMessageResponse` with `edited: true`.

#### 3.2.4 Typing Indicators

```
/topic/org.{orgId}.room.{roomId}.typing
```

**Payload**:
```json
{
  "room_id": "cdc8bbda-...",
  "user_id": "e3732edd-...",
  "user_name": "john@example.com",
  "typing": true
}
```

> **⚠️ Important**: The typing event is broadcast to **ALL** room subscribers, including the sender.
> **Frontend must filter**: Ignore typing events where `user_id === currentUserId`.

```tsx
// Example filter:
onTypingEvent(event) {
  if (event.user_id === currentUserId) return; // ignore own typing
  if (event.typing) {
    setTypingUsers(prev => [...prev.filter(u => u.user_id !== event.user_id), event]);
  } else {
    setTypingUsers(prev => prev.filter(u => u.user_id !== event.user_id));
  }
}
```

#### 3.2.5 Read Receipts

```
/topic/org.{orgId}.room.{roomId}.read
```

**Payload**:
```json
{
  "event": "READ_RECEIPT",
  "user_id": "e3732edd-...",
  "room_id": "cdc8bbda-...",
  "timestamp": 1712500000000
}
```

#### 3.2.6 Room Member Changes

```
/topic/org.{orgId}.room.{roomId}.members
```

**Payload**:
```json
{
  "event": "MEMBER_ADDED",      // or MEMBER_REMOVED
  "user_id": "ec425a23-...",
  "room_id": "cdc8bbda-...",
  "timestamp": 1712500000000
}
```

#### 3.2.7 Presence (Online/Offline)

```
/topic/org.{orgId}.presence
```

**Payload**:
```json
{
  "event": "USER_ONLINE",       // or USER_OFFLINE
  "user_id": "e3732edd-...",
  "timestamp": 1712500000000
}
```

---

### 3.3 Sending Messages via WebSocket

Instead of REST `POST`, you can send messages through the WebSocket for lower latency:

```
Destination: /app/chat.send
```

**Payload**:
```json
{
  "room_id": "cdc8bbda-390e-4718-976f-54551a8d66c7",
  "body": "Hello from WebSocket!",
  "type": "TEXT",
  "sender_name": "John Doe"
}
```

The message is saved to DB and broadcast to `/topic/org.{orgId}.room.{roomId}` automatically. You'll receive it back on your subscription (use the message `id` to deduplicate).

### 3.4 Typing Indicator

```
Destination: /app/chat.typing
```

**Payload**:
```json
{
  "room_id": "cdc8bbda-390e-4718-976f-54551a8d66c7",
  "typing": true
}
```

> You do NOT need to send `user_id` or `user_name` — the server populates them from your JWT.

**Best practice** — debounce typing events:
```ts
// Send typing=true on first keystroke, then every 2-3 seconds while typing.
// Send typing=false after 3 seconds of no input (or on blur/send).
let typingTimeout: ReturnType<typeof setTimeout>;

function onInputChange() {
  sendTyping(true);
  clearTimeout(typingTimeout);
  typingTimeout = setTimeout(() => sendTyping(false), 3000);
}

function onSendMessage() {
  clearTimeout(typingTimeout);
  sendTyping(false);
  // ... send the message
}
```

### 3.5 Read Receipt via WebSocket

```
Destination: /app/chat.read
```

**Payload**:
```json
{
  "room_id": "cdc8bbda-390e-4718-976f-54551a8d66c7"
}
```

---

## 4. Real-Time Event Payloads

### Summary of all WebSocket event types

| Topic Suffix         | Payload Type          | `event` Values                                   |
|----------------------|-----------------------|--------------------------------------------------|
| `.rooms`             | WebSocketEvent        | `ROOM_CREATED`, `ROOM_UPSERTED`, `MEMBER_ADDED`, `MEMBER_REMOVED` |
| `.room.{id}`         | ChatMessageResponse   | _(no `event` field — it's a new message)_        |
| `.room.{id}`         | WebSocketEvent        | `MESSAGE_DELETED`                                |
| `.room.{id}.edit`    | ChatMessageResponse   | _(edited message — check `edited: true`)_        |
| `.room.{id}.typing`  | TypingEvent           | _(check `typing: true/false`)_                   |
| `.room.{id}.read`    | WebSocketEvent        | `READ_RECEIPT`                                   |
| `.room.{id}.members` | WebSocketEvent        | `MEMBER_ADDED`, `MEMBER_REMOVED`                 |
| `.presence`          | WebSocketEvent        | `USER_ONLINE`, `USER_OFFLINE`                    |

---

## 5. Complete React Integration Example

### 5.1 TypeScript Types

```typescript
// types/chat.ts

export type RoomType = 'PRIVATE' | 'GROUP' | 'PUBLIC';
export type MessageType = 'TEXT' | 'FILE' | 'SYSTEM';
export type MemberRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export interface RoomMember {
  user_id: string;
  role: MemberRole;
  joined_at: string;
  last_read_at: string | null;
}

export interface ChatMessage {
  id: string;
  room_id: string;
  sender_id: string;
  sender_name: string | null;
  type: MessageType;
  body: string | null;
  parent_message_id: string | null;
  file_url: string | null;
  file_name: string | null;
  file_size: number | null;
  file_content_type: string | null;
  edited: boolean;
  deleted: boolean;
  created_at: string;    // ISO-8601
  edited_at: string | null;
}

export interface ChatRoom {
  id: string;
  organization_id: string;
  type: RoomType;
  name: string | null;
  description: string | null;
  avatar_url: string | null;
  created_at: string;
  updated_at: string;
  members: RoomMember[];
  unread_count: number;
  last_message: ChatMessage | null;
}

export interface OrgMember {
  user_id: string;
  full_name: string;
  email: string;
  avatar_url: string | null;
  role: string;
  department: string | null;
  status: string;
  is_current_user: boolean;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
  first: boolean;
  last: boolean;
}

export interface WebSocketEvent {
  event: string;
  user_id: string;
  room_id: string | null;
  message_id: string | null;
  timestamp: number;
}

export interface TypingEvent {
  room_id: string;
  user_id: string;
  user_name: string;
  typing: boolean;
}

export interface SendMessagePayload {
  room_id?: string;       // required for /app/chat.send
  body?: string;
  type?: MessageType;
  sender_name?: string;
  parent_message_id?: string;
  file_url?: string;
  file_name?: string;
  file_size?: number;
  file_content_type?: string;
}

export interface CreateRoomPayload {
  type: RoomType;
  name?: string;
  description?: string;
  member_user_ids?: string[];
}
```

### 5.2 Chat API Service

```typescript
// services/chatApi.ts
import axios from 'axios';
import type { ChatRoom, ChatMessage, PageResponse, OrgMember, CreateRoomPayload } from '../types/chat';

const CHAT_BASE = import.meta.env.VITE_CHAT_API_URL || 'http://localhost:8082';

const chatHttp = axios.create({ baseURL: `${CHAT_BASE}/api/v1/chat` });

// Attach token from localStorage
chatHttp.interceptors.request.use(config => {
  const token = localStorage.getItem('rct_at');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export const chatApi = {
  // Rooms
  getRooms: () => chatHttp.get<ChatRoom[]>('/rooms'),
  createRoom: (data: CreateRoomPayload) => chatHttp.post<ChatRoom>('/rooms', data),
  getOrCreatePrivateRoom: (targetUserId: string) =>
    chatHttp.post<ChatRoom>(`/users/${targetUserId}/rooms/private`),

  // Members
  addMember: (roomId: string, userId: string) =>
    chatHttp.post<ChatRoom>(`/rooms/${roomId}/members/${userId}`),
  removeMember: (roomId: string, userId: string) =>
    chatHttp.delete(`/rooms/${roomId}/members/${userId}`),

  // Messages
  getMessages: (roomId: string, page = 0, size = 50) =>
    chatHttp.get<PageResponse<ChatMessage>>(`/rooms/${roomId}/messages`, { params: { page, size } }),
  sendMessage: (roomId: string, body: string, senderName: string, type = 'TEXT') =>
    chatHttp.post<ChatMessage>(`/rooms/${roomId}/messages`, { body, type, sender_name: senderName }),
  sendPrivateMessage: (targetUserId: string, body: string, senderName: string) =>
    chatHttp.post<ChatMessage>(`/users/${targetUserId}/messages`, { body, type: 'TEXT', sender_name: senderName }),
  editMessage: (messageId: string, body: string) =>
    chatHttp.put<ChatMessage>(`/messages/${messageId}`, { body }),
  deleteMessage: (messageId: string) =>
    chatHttp.delete(`/messages/${messageId}`),

  // Read receipt
  markAsRead: (roomId: string) => chatHttp.post(`/rooms/${roomId}/read`),

  // File upload
  uploadFile: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return chatHttp.post('/files/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  // Org members & presence
  getOrgMembers: (search?: string, page = 0, size = 20) =>
    chatHttp.get<PageResponse<OrgMember>>('/org-members', { params: { search, page, size } }),
  getOnlineUsers: () => chatHttp.get<OrgMember[]>('/users/online'),
};
```

### 5.3 WebSocket Hook

```typescript
// hooks/useChatWebSocket.ts
import { useEffect, useRef, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { ChatMessage, WebSocketEvent, TypingEvent } from '../types/chat';

const CHAT_WS_URL = import.meta.env.VITE_CHAT_WS_URL || 'http://localhost:8082/ws';

interface UseChatWSOptions {
  orgId: string;
  currentUserId: string;
  token: string;
  onNewMessage?: (msg: ChatMessage) => void;
  onMessageDeleted?: (evt: WebSocketEvent) => void;
  onMessageEdited?: (msg: ChatMessage) => void;
  onRoomChange?: (evt: WebSocketEvent) => void;
  onTyping?: (evt: TypingEvent) => void;
  onReadReceipt?: (evt: WebSocketEvent) => void;
  onPresence?: (evt: WebSocketEvent) => void;
  onMemberChange?: (evt: WebSocketEvent) => void;
  onConnected?: () => void;
  onDisconnected?: () => void;
}

export function useChatWebSocket(roomIds: string[], options: UseChatWSOptions) {
  const clientRef = useRef<Client | null>(null);

  const connect = useCallback(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(CHAT_WS_URL),
      connectHeaders: { Authorization: `Bearer ${options.token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        options.onConnected?.();

        // Subscribe to org-level topics
        const orgPrefix = `/topic/org.${options.orgId}`;

        client.subscribe(`${orgPrefix}.rooms`, (msg: IMessage) => {
          const evt: WebSocketEvent = JSON.parse(msg.body);
          options.onRoomChange?.(evt);
        });

        client.subscribe(`${orgPrefix}.presence`, (msg: IMessage) => {
          const evt: WebSocketEvent = JSON.parse(msg.body);
          options.onPresence?.(evt);
        });

        // Subscribe to each room
        for (const roomId of roomIds) {
          const roomPrefix = `${orgPrefix}.room.${roomId}`;

          client.subscribe(roomPrefix, (msg: IMessage) => {
            const data = JSON.parse(msg.body);
            if (data.event === 'MESSAGE_DELETED') {
              options.onMessageDeleted?.(data);
            } else {
              options.onNewMessage?.(data);
            }
          });

          client.subscribe(`${roomPrefix}.edit`, (msg: IMessage) => {
            const edited: ChatMessage = JSON.parse(msg.body);
            options.onMessageEdited?.(edited);
          });

          client.subscribe(`${roomPrefix}.typing`, (msg: IMessage) => {
            const evt: TypingEvent = JSON.parse(msg.body);
            // ⚠️ FILTER OUT OWN TYPING EVENTS
            if (evt.user_id !== options.currentUserId) {
              options.onTyping?.(evt);
            }
          });

          client.subscribe(`${roomPrefix}.read`, (msg: IMessage) => {
            const evt: WebSocketEvent = JSON.parse(msg.body);
            options.onReadReceipt?.(evt);
          });

          client.subscribe(`${roomPrefix}.members`, (msg: IMessage) => {
            const evt: WebSocketEvent = JSON.parse(msg.body);
            options.onMemberChange?.(evt);
          });
        }
      },
      onDisconnect: () => options.onDisconnected?.(),
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message'], frame.body);
      },
    });

    client.activate();
    clientRef.current = client;
  }, [roomIds, options]);

  useEffect(() => {
    connect();
    return () => { clientRef.current?.deactivate(); };
  }, [connect]);

  // Expose send helpers
  const sendMessage = useCallback((roomId: string, body: string, senderName: string) => {
    clientRef.current?.publish({
      destination: '/app/chat.send',
      body: JSON.stringify({ room_id: roomId, body, type: 'TEXT', sender_name: senderName }),
    });
  }, []);

  const sendTyping = useCallback((roomId: string, typing: boolean) => {
    clientRef.current?.publish({
      destination: '/app/chat.typing',
      body: JSON.stringify({ room_id: roomId, typing }),
    });
  }, []);

  const sendReadReceipt = useCallback((roomId: string) => {
    clientRef.current?.publish({
      destination: '/app/chat.read',
      body: JSON.stringify({ room_id: roomId }),
    });
  }, []);

  return { sendMessage, sendTyping, sendReadReceipt, client: clientRef };
}
```

### 5.4 Typing Indicator Component

```tsx
// components/TypingIndicator.tsx
import { useState, useEffect } from 'react';
import type { TypingEvent } from '../types/chat';

interface Props {
  typingEvents: TypingEvent[];
}

export function TypingIndicator({ typingEvents }: Props) {
  const [activeTypers, setActiveTypers] = useState<TypingEvent[]>([]);

  useEffect(() => {
    // Auto-clear stale typing indicators after 4 seconds
    const timers = typingEvents
      .filter(e => e.typing)
      .map(e => {
        setActiveTypers(prev => [...prev.filter(p => p.user_id !== e.user_id), e]);
        return setTimeout(() => {
          setActiveTypers(prev => prev.filter(p => p.user_id !== e.user_id));
        }, 4000);
      });

    typingEvents.filter(e => !e.typing).forEach(e => {
      setActiveTypers(prev => prev.filter(p => p.user_id !== e.user_id));
    });

    return () => timers.forEach(clearTimeout);
  }, [typingEvents]);

  if (activeTypers.length === 0) return null;

  const names = activeTypers.map(t => t.user_name || 'Someone');
  const text = names.length === 1
    ? `${names[0]} is typing...`
    : names.length === 2
      ? `${names[0]} and ${names[1]} are typing...`
      : `${names[0]} and ${names.length - 1} others are typing...`;

  return <div className="text-xs text-muted-foreground animate-pulse">{text}</div>;
}
```

---

## 6. Enums & Constants

| Enum            | Values                       | Description                  |
|-----------------|------------------------------|------------------------------|
| `RoomType`      | `PRIVATE`, `GROUP`, `PUBLIC` | Type of chat room            |
| `MessageType`   | `TEXT`, `FILE`, `SYSTEM`     | Type of chat message         |
| `MemberRole`    | `OWNER`, `ADMIN`, `MEMBER`   | User's role within a room    |
| `DeliveryStatus`| `DELIVERED`, `READ`          | Message delivery status      |

---

## 7. Error Handling

All errors return JSON:

```json
{
  "message": "Room not found: cdc8bbda-...",
  "status_code": 404,
  "timestamp": "2026-04-07T06:30:00Z"
}
```

Validation errors include field details:

```json
{
  "message": "Validation failed",
  "status_code": 400,
  "timestamp": "2026-04-07T06:30:00Z",
  "errors": {
    "body": "body is required"
  }
}
```

| Status | Meaning                                    |
|--------|--------------------------------------------|
| 400    | Bad request / validation error             |
| 401    | Missing or invalid JWT                     |
| 403    | Not allowed (wrong org, not room member)   |
| 404    | Room or message not found                  |
| 409    | Conflict (user already member)             |
| 413    | File too large                             |
| 502    | Upstream service (api-backend) unreachable |

### WebSocket STOMP Errors

If WS auth fails, you receive a STOMP ERROR frame:

```
message: Authentication failed\c Invalid token\c JWT expired ...
```

Handle this in `onStompError` and trigger a token refresh + reconnect.

---

## 8. Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    React Frontend                        │
│                                                          │
│  REST calls ──────────►  Chat REST API (port 8082)       │
│  WS STOMP  ──────────►  /ws endpoint (SockJS)            │
│                                                          │
│  Token: rct_at from main auth service                    │
└──────────┬──────────────────────────────────┬────────────┘
           │                                  │
           ▼                                  ▼
┌─────────────────────┐          ┌─────────────────────────┐
│   Chat Service       │          │   Auth Service           │
│   (Spring Boot)      │          │   (recruitable-api)      │
│                      │          │                           │
│  ┌─ MongoDB ◄────┐  │          │  Issues JWT (rct_at)      │
│  │   (messages,   │  │          │  Provides /api/v1/profiles│
│  │    rooms)      │  │          └───────────────────────────┘
│  └────────────────┘  │
│                      │
│  ┌─ Redis ◄──────┐  │
│  │  (presence,    │  │
│  │   typing TTL)  │  │
│  └────────────────┘  │
│                      │
│  ┌─ RabbitMQ ◄───┐  │
│  │  (event fan-   │  │
│  │   out to all   │  │
│  │   instances)   │  │
│  └────────────────┘  │
│                      │
│  ┌─ S3 Storage ◄─┐  │
│  │  (file uploads)│  │
│  └────────────────┘  │
└──────────────────────┘
```

### Message Flow (Send via WebSocket)

```
1. Client sends to /app/chat.send
2. ChatWebSocketController saves to MongoDB
3. ChatEventPublisher sends to RabbitMQ (chat.exchange)
4. ChatEventListener receives from RabbitMQ
5. SimpMessagingTemplate sends to /topic/org.{orgId}.room.{roomId}
6. All subscribed WS clients receive the message
```

### Typing Flow

```
1. Client sends to /app/chat.typing { room_id, typing: true }
2. Server sets Redis key with 4s TTL
3. Server broadcasts to /topic/org.{orgId}.room.{roomId}.typing
4. Other clients receive { user_id, user_name, typing: true }
5. Frontend filters out own user_id and shows "X is typing..."
6. After 4s Redis key expires (auto-cleanup)
```

---

## Quick Start Checklist

1. ✅ Store `rct_at` token from login in localStorage
2. ✅ Create axios instance with `Authorization: Bearer` header
3. ✅ Connect STOMP with same token in connect headers
4. ✅ Subscribe to `/topic/org.{orgId}.rooms` and `.presence` immediately
5. ✅ On room select, subscribe to `.room.{roomId}`, `.edit`, `.typing`, `.read`, `.members`
6. ✅ Load message history via REST (reverse for chronological order)
7. ✅ Send messages via `/app/chat.send` (WS) or REST POST
8. ✅ Always pass `sender_name` when sending messages
9. ✅ Filter out own `user_id` from typing events
10. ✅ Send read receipts when user views a room
11. ✅ Handle token expiry → refresh → reconnect WS

