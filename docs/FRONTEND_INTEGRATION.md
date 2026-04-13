# Chat Service — Frontend Integration Guide

> **Base URL**: `http://localhost:8082/api/v1/chat`
> **WebSocket**: `ws://localhost:8082/ws`
> **Auth**: All REST endpoints accept JWT via:
> - `Authorization: Bearer <JWT>` header, **or**
> - `rct_at` cookie (set by the main app on login), **or**
> - `access_token` cookie (fallback)

---

## Table of Contents

1. [JSON Format](#json-format)
2. [API Endpoints Reference](#api-endpoints-reference)
3. [Flows](#flows)
   - [Initialize Chat](#flow-1-initialize-chat)
   - [Send Direct Message (DM)](#flow-2-send-a-direct-message-dm)
   - [Create a Group Chat](#flow-3-create-a-group-chat)
   - [Send Message to Room](#flow-4-send-a-message-to-a-room)
   - [Send a File](#flow-5-send-a-file)
   - [Edit / Delete Messages](#flow-6-edit--delete-messages)
   - [Manage Group Members](#flow-7-manage-group-members)
4. [WebSocket Events](#websocket-events)
5. [TypeScript Types](#typescript-types)
6. [Frontend API Functions (chatApi.ts)](#frontend-api-functions-chatapits)

---

## JSON Format

The backend uses **snake_case** for all JSON property names.

| Java field | JSON key | Example |
|---|---|---|
| `roomId` | `room_id` | `"room_id": "abc-123"` |
| `senderId` | `sender_id` | `"sender_id": "def-456"` |
| `memberUserIds` | `member_user_ids` | `"member_user_ids": ["id1","id2"]` |
| `unreadCount` | `unread_count` | `"unread_count": 5` |
| `lastMessage` | `last_message` | `{ ... }` |
| `createdAt` | `created_at` | `"2026-04-13T10:30:00Z"` |
| `fileContentType` | `file_content_type` | `"image/png"` |

---

## API Endpoints Reference

### 1. Organization Members

#### `GET /api/v1/chat/org-members`
List all org members for user search (DM, group add).

| Param | Type | Default | Description |
|---|---|---|---|
| `search` | string | — | Filter by name or email |
| `page` | int | 0 | Page number (0-based) |
| `size` | int | 20 | Page size |

**Response**: Paginated `OrgMemberResponse`
```json
{
  "content": [
    {
      "user_id": "7c9e6679-...",
      "full_name": "Jane Doe",
      "email": "jane@example.com",
      "avatar_url": "https://...",
      "role": "RECRUITER",
      "department": "Engineering",
      "status": "ACTIVE",
      "is_current_user": false
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 42,
  "total_pages": 3,
  "first": true,
  "last": false
}
```

---

### 2. Rooms

#### `POST /api/v1/chat/rooms` — Create Room
Create a PRIVATE, GROUP, or PUBLIC room.

**Request body**:
```json
// GROUP example
{
  "type": "GROUP",
  "name": "Engineering Team",
  "description": "Channel for engineering",
  "member_user_ids": ["user-id-1", "user-id-2"]
}
```

```json
// PRIVATE example (exactly 1 member)
{
  "type": "PRIVATE",
  "member_user_ids": ["other-user-id"]
}
```

```json
// PUBLIC example (admin only)
{
  "type": "PUBLIC",
  "name": "Announcements",
  "description": "Company-wide announcements"
}
```

**Response** (`201 Created`): `ChatRoomResponse` with enriched members

---

#### `POST /api/v1/chat/users/{targetUserId}/rooms/private` — Get/Create DM Room
Get or create a 1-on-1 private room. No request body needed.

**Response** (`200 OK`): `ChatRoomResponse` — The PRIVATE room
- `name` is auto-set to the other user's display name
- `avatar_url` is set to the other user's avatar

---

#### `GET /api/v1/chat/rooms` — List My Rooms
Returns all accessible rooms with enriched member profiles, unread count, and last message.

**Response** (`200 OK`): `ChatRoomResponse[]`
```json
[
  {
    "id": "room-uuid",
    "organization_id": "org-uuid",
    "type": "PRIVATE",
    "name": "Jane Doe",
    "description": null,
    "avatar_url": "https://...",
    "created_at": "2026-04-13T10:00:00Z",
    "updated_at": "2026-04-13T12:30:00Z",
    "members": [
      {
        "user_id": "current-user-id",
        "full_name": "John Smith",
        "email": "john@example.com",
        "avatar_url": "https://...",
        "role": "MEMBER",
        "joined_at": "2026-04-13T10:00:00Z",
        "last_read_at": "2026-04-13T12:00:00Z"
      },
      {
        "user_id": "jane-user-id",
        "full_name": "Jane Doe",
        "email": "jane@example.com",
        "avatar_url": "https://...",
        "role": "MEMBER",
        "joined_at": "2026-04-13T10:00:00Z",
        "last_read_at": null
      }
    ],
    "unread_count": 3,
    "last_message": {
      "id": "msg-uuid",
      "room_id": "room-uuid",
      "sender_id": "jane-user-id",
      "sender_name": "jane@example.com",
      "type": "TEXT",
      "body": "Hey, check this out!",
      "edited": false,
      "deleted": false,
      "created_at": "2026-04-13T12:30:00Z"
    }
  }
]
```

**Key notes**:
- PRIVATE rooms: `name` = other user's display name, `avatar_url` = their avatar
- GROUP rooms: `name` = the group name you set on creation
- PUBLIC rooms: accessible to all org members even if not explicitly a member
- `members[].full_name`, `members[].email`, `members[].avatar_url` are enriched from the user profile service

---

### 3. Messages

#### `GET /api/v1/chat/rooms/{roomId}/messages` — Message History

| Param | Type | Default | Description |
|---|---|---|---|
| `page` | int | 0 | Page (0 = newest messages) |
| `size` | int | 50 | Messages per page |

**Response**: Paginated `ChatMessageResponse`

> **Ordering**: Within each page, messages are in **chronological order** (oldest first, newest last) — ready to render top-to-bottom in a chat UI.
> `page=0` contains the **most recent** messages. Increment `page` to load older messages.

```json
{
  "content": [
    {
      "id": "msg-uuid",
      "room_id": "room-uuid",
      "sender_id": "user-uuid",
      "sender_name": "jane@example.com",
      "type": "TEXT",
      "body": "Hello!",
      "parent_message_id": null,
      "file_url": null,
      "file_name": null,
      "file_size": null,
      "file_content_type": null,
      "edited": false,
      "deleted": false,
      "created_at": "2026-04-13T12:30:00Z",
      "edited_at": null
    }
  ],
  "page": 0,
  "size": 50,
  "total_elements": 128,
  "total_pages": 3,
  "first": true,
  "last": false
}
```

**Loading older messages**: increment `page` (0 → 1 → 2 …). When `last: true`, no more pages.

---

#### `POST /api/v1/chat/rooms/{roomId}/messages` — Send Message

**Request body** (TEXT message):
```json
{
  "body": "Hello team!"
}
```

**Request body** (FILE message — after uploading):
```json
{
  "type": "FILE",
  "file_url": "https://s3.../file.pdf",
  "file_name": "report.pdf",
  "file_size": 102400,
  "file_content_type": "application/pdf"
}
```

**Request body** (Reply to another message):
```json
{
  "body": "Great point!",
  "parent_message_id": "original-msg-uuid"
}
```

> **Note**: `sender_name` is auto-resolved from JWT. You do NOT need to send it.
> **Note**: `room_id` is taken from the URL path. Do NOT send it in the body.

**Response** (`201 Created`): `ChatMessageResponse`

---

#### `POST /api/v1/chat/users/{targetUserId}/messages` — Send Direct Message (shortcut)

Creates/reuses the PRIVATE room AND sends the message in one call.

**Request body**:
```json
{
  "body": "Hey, can we chat?"
}
```

**Response** (`201 Created`): `ChatMessageResponse` — check `room_id` to know which room it went to.

---

#### `PUT /api/v1/chat/messages/{messageId}` — Edit Message

Only the original sender can edit.

**Request body**:
```json
{
  "body": "Updated message text"
}
```

**Response** (`200 OK`): `ChatMessageResponse` with `edited: true`

---

#### `DELETE /api/v1/chat/messages/{messageId}` — Delete Message (soft)

Only the original sender can delete. Body is cleared and `deleted: true`.

**Response**: `204 No Content`

---

### 4. Read Receipts

#### `POST /api/v1/chat/rooms/{roomId}/read` — Mark as Read

No request body. Call this when the user opens/views a room.

**Response**: `200 OK`

---

### 5. File Upload

#### `POST /api/v1/chat/files/upload`

**Content-Type**: `multipart/form-data`
**Form field**: `file` (the file)

**Response** (`200 OK`):
```json
{
  "file_url": "https://s3.cloud.../chat/org-id/uuid/report.pdf",
  "file_name": "report.pdf",
  "file_size": 102400,
  "content_type": "application/pdf"
}
```

> After upload, send the file as a message (see [Send a File flow](#flow-5-send-a-file)).

---

### 6. Presence (Online Users)

#### `GET /api/v1/chat/users/online` — Get Online User IDs

Returns a JSON array of UUIDs (strings) of currently online users.

**Response** (`200 OK`):
```json
["7c9e6679-7425-40de-944b-e07fc1f90ae7", "550e8400-e29b-41d4-a716-446655440000"]
```

#### `GET /api/v1/chat/users/online/details` — Get Online Users with Profiles

Returns full profile info for online users. Heavier — use only when needed.

**Response** (`200 OK`): `OrgMemberResponse[]`

---

### 7. Room Members

#### `POST /api/v1/chat/rooms/{roomId}/members/{userId}` — Add Member

Add a user to a GROUP room. Requires room OWNER/ADMIN or org ADMIN role.

**No request body**. Response: `200 OK` with updated `ChatRoomResponse`

#### `DELETE /api/v1/chat/rooms/{roomId}/members/{userId}` — Remove Member

Remove a user from a GROUP room. Cannot remove the last OWNER.

**Response**: `204 No Content`

---

## Flows

### Flow 1: Initialize Chat

When the chat UI loads:

```
1. Connect WebSocket:  ws://localhost:8082/ws?token=<JWT>
2. GET /api/v1/chat/rooms          → Load room list (with member names, unread counts)
3. GET /api/v1/chat/users/online   → Load online user IDs
4. Subscribe to WebSocket topics:
   - /topic/org.{orgId}.rooms      → Room list changes (new rooms, member changes)
   - /topic/org.{orgId}.presence   → Online/offline events
```

```typescript
// Step 1: Connect WebSocket
connectChat(token, () => setIsConnected(true), () => setIsConnected(false));

// Step 2: Fetch rooms (already enriched with member names)
const rooms = await getRooms();

// Step 3: Fetch online users (returns UUID strings)
const onlineIds: string[] = await getOnlineUsers();
setOnlineUserIds(new Set(onlineIds));
```

---

### Flow 2: Send a Direct Message (DM)

**Option A: Quick DM (one call)**
```
POST /api/v1/chat/users/{targetUserId}/messages
Body: { "body": "Hey!" }
→ Returns ChatMessageResponse with room_id
→ Invalidate rooms query to see the new/updated room
```

> **⚠️ Important**: Use this endpoint only for the **first** message to a user.
> Once you have the `room_id` from the response, use `POST /rooms/{roomId}/messages` for all subsequent messages in that conversation.
> Calling this endpoint with your **own** user ID as `targetUserId` will return a 400 error.

```typescript
const msg = await sendPrivateMessage(targetUserId, 'Hey!');
// msg.room_id tells you which room it went to
queryClient.invalidateQueries({ queryKey: ['chat-rooms'] });
```

**Option B: Open DM room first, then send**
```
1. POST /api/v1/chat/users/{targetUserId}/rooms/private
   → Returns ChatRoomResponse (existing or new)
2. Select the room in UI
3. POST /api/v1/chat/rooms/{roomId}/messages
   Body: { "body": "Hey!" }
```

```typescript
// Step 1: Open/create the DM room
const room = await getOrCreatePrivateRoom(targetUserId);
selectRoom(room.id);

// Step 2: Send a message in the room
const msg = await sendRoomMessage(room.id, 'Hey!');
```

---

### Flow 3: Create a Group Chat

```
1. GET /api/v1/chat/org-members?search=jane    → Search for users to add
2. POST /api/v1/chat/rooms
   Body: {
     "type": "GROUP",
     "name": "Project Alpha",
     "description": "Discussion for Project Alpha",
     "member_user_ids": ["user-1-id", "user-2-id", "user-3-id"]
   }
   → Returns ChatRoomResponse (201 Created)
3. Select the new room in UI
4. Send messages as usual
```

```typescript
// Step 1: Search members
const members = await getOrgMembers({ search: 'jane' });

// Step 2: Create the group
const room = await createGroupRoom(
  'Project Alpha',
  ['user-1-id', 'user-2-id'],
  'Discussion for Project Alpha'
);

// Step 3: Select & start chatting
selectRoom(room.id);
```

**Adding members later**:
```
POST /api/v1/chat/rooms/{roomId}/members/{newUserId}
→ Returns updated ChatRoomResponse with the new member
```

**Removing members**:
```
DELETE /api/v1/chat/rooms/{roomId}/members/{userId}
→ 204 No Content
```

---

### Flow 4: Send a Message to a Room

```
POST /api/v1/chat/rooms/{roomId}/messages
Body: { "body": "Hello team!" }
→ Returns ChatMessageResponse (201 Created)
```

```typescript
export async function sendRoomMessage(roomId: string, body: string) {
  const res = await api.post(`/api/v1/chat/rooms/${roomId}/messages`, { body });
  return res.data;
}
```

> **Important**: Do NOT include `room_id` or `sender_name` in the body. They are set automatically.

---

### Flow 5: Send a File

**Two-step process:**

```
1. POST /api/v1/chat/files/upload   (multipart/form-data with field "file")
   → Returns: { file_url, file_name, file_size, content_type }

2. POST /api/v1/chat/rooms/{roomId}/messages
   Body: {
     "type": "FILE",
     "file_url": "<from step 1>",
     "file_name": "<from step 1>",
     "file_size": <from step 1>,
     "file_content_type": "<content_type from step 1>"
   }
   → Returns ChatMessageResponse
```

```typescript
export async function sendFileMessage(roomId: string, uploaded: FileUploadResponse) {
  const res = await api.post(`/api/v1/chat/rooms/${roomId}/messages`, {
    type: 'FILE',
    file_url: uploaded.file_url,
    file_name: uploaded.file_name,
    file_size: uploaded.file_size,
    file_content_type: uploaded.content_type,  // note: content_type → file_content_type
  });
  return res.data;
}
```

> **Note the field name mapping**: Upload returns `content_type`, but the send message endpoint expects `file_content_type`.

---

### Flow 6: Edit / Delete Messages

**Edit**:
```
PUT /api/v1/chat/messages/{messageId}
Body: { "body": "Updated text" }
→ Returns ChatMessageResponse with edited: true
```

**Delete** (soft):
```
DELETE /api/v1/chat/messages/{messageId}
→ 204 No Content
→ WebSocket broadcasts MESSAGE_DELETED event to room subscribers
```

---

### Flow 7: Manage Group Members

**Add member** (requires room OWNER/ADMIN or org ADMIN):
```
POST /api/v1/chat/rooms/{roomId}/members/{userId}
→ Returns updated ChatRoomResponse
```

**Remove member**:
```
DELETE /api/v1/chat/rooms/{roomId}/members/{userId}
→ 204 No Content
```

**Create a public room** (org ADMIN only):
```
POST /api/v1/chat/rooms
Body: { "type": "PUBLIC", "name": "Announcements" }
→ All org members can see and access this room automatically
```

---

## WebSocket Events

### Connection

```typescript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const CHAT_WS_URL = 'http://localhost:8082/ws';

let stompClient: Client | null = null;

export function connectChat(
  token: string,
  orgId: string,
  onConnected: () => void,
  onDisconnected: () => void,
) {
  stompClient = new Client({
    // Use SockJS as transport (fallback for browsers without native WS)
    webSocketFactory: () => new SockJS(CHAT_WS_URL),
    
    // Pass JWT in STOMP CONNECT frame
    connectHeaders: {
      Authorization: `Bearer ${token}`,
    },
    
    // Reconnect on disconnect (with new token if refreshed)
    reconnectDelay: 5000,

    onConnect: () => {
      console.log('STOMP connected');
      onConnected();

      // Subscribe to org-level events
      stompClient?.subscribe(`/topic/org.${orgId}.rooms`, (msg) => {
        const event = JSON.parse(msg.body);
        // Handle ROOM_CREATED, ROOM_UPSERTED, etc. → refetch rooms
      });

      stompClient?.subscribe(`/topic/org.${orgId}.presence`, (msg) => {
        const event = JSON.parse(msg.body);
        // Handle USER_ONLINE / USER_OFFLINE → update online set
      });
    },

    onDisconnect: () => {
      console.log('STOMP disconnected');
      onDisconnected();
    },

    onStompError: (frame) => {
      console.error('STOMP error:', frame.headers['message']);
    },
  });

  stompClient.activate();
}

// Subscribe to a room's messages (call when user selects a room)
export function subscribeToRoom(orgId: string, roomId: string, onMessage: (msg: any) => void) {
  return stompClient?.subscribe(`/topic/org.${orgId}.room.${roomId}`, (frame) => {
    onMessage(JSON.parse(frame.body));
  });
}

// Subscribe to typing in a room
export function subscribeToTyping(orgId: string, roomId: string, onTyping: (evt: any) => void) {
  return stompClient?.subscribe(`/topic/org.${orgId}.room.${roomId}.typing`, (frame) => {
    onTyping(JSON.parse(frame.body));
  });
}

// Send typing indicator
export function sendTyping(roomId: string, typing: boolean) {
  stompClient?.publish({
    destination: '/app/chat.typing',
    body: JSON.stringify({ room_id: roomId, typing }),
  });
}

// Disconnect
export function disconnectChat() {
  stompClient?.deactivate();
  stompClient = null;
}
```

**WebSocket URL**: `ws://localhost:8082/ws` (or with SockJS: `http://localhost:8082/ws`)
**Pass token** via one of these methods (in priority order):
1. STOMP CONNECT `Authorization: Bearer <token>` header (recommended)
2. STOMP CONNECT `token` header (plain JWT)
3. `rct_at` cookie (auto-captured during HTTP handshake)
4. `access_token` cookie (fallback, captured during handshake)

### STOMP Destinations

#### Subscribe (receive events)

| Destination | Payload | Description |
|---|---|---|
| `/topic/org.{orgId}.rooms` | `WebSocketEventDto` | Room list changes (ROOM_CREATED, ROOM_UPSERTED, MEMBER_ADDED, MEMBER_REMOVED) |
| `/topic/org.{orgId}.presence` | `WebSocketEventDto` | USER_ONLINE / USER_OFFLINE |
| `/topic/org.{orgId}.room.{roomId}` | `ChatMessageResponse` or `WebSocketEventDto` | New messages + MESSAGE_DELETED events |
| `/topic/org.{orgId}.room.{roomId}.edit` | `ChatMessageResponse` | Edited messages |
| `/topic/org.{orgId}.room.{roomId}.typing` | `TypingEvent` | Typing indicators |
| `/topic/org.{orgId}.room.{roomId}.read` | `WebSocketEventDto` | Read receipts |
| `/topic/org.{orgId}.room.{roomId}.members` | `WebSocketEventDto` | MEMBER_ADDED / MEMBER_REMOVED |

#### Send (client → server)

| Destination | Payload | Description |
|---|---|---|
| `/app/chat.send` | `SendMessageRequest` | Send a message via WebSocket |
| `/app/chat.typing` | `TypingEvent` | Typing indicator |
| `/app/chat.read` | `ReadReceiptRequest` | Mark room as read |

### Event Types (WebSocketEventDto)

```json
{
  "event": "ROOM_CREATED",     // or ROOM_UPSERTED, MEMBER_ADDED, MEMBER_REMOVED, 
                                // MESSAGE_DELETED, READ_RECEIPT, USER_ONLINE, USER_OFFLINE
  "user_id": "uuid",
  "room_id": "uuid",
  "message_id": "uuid",        // only for MESSAGE_DELETED
  "timestamp": 1681383000000
}
```

### TypingEvent

```json
{
  "room_id": "uuid",
  "user_id": "uuid",
  "user_name": "jane@example.com",
  "typing": true
}
```

---

## TypeScript Types

```typescript
// ── Room Types ──────────────────────────────────────────────────
type RoomType = 'PRIVATE' | 'GROUP' | 'PUBLIC';
type MemberRole = 'OWNER' | 'ADMIN' | 'MEMBER';
type MessageType = 'TEXT' | 'FILE' | 'SYSTEM';

interface RoomMemberResponse {
  user_id: string;
  full_name: string | null;
  email: string | null;
  avatar_url: string | null;
  role: MemberRole;
  joined_at: string;
  last_read_at: string | null;
}

interface ChatRoomResponse {
  id: string;
  organization_id: string;
  type: RoomType;
  name: string | null;          // Auto-set for PRIVATE rooms
  description: string | null;
  avatar_url: string | null;    // Auto-set for PRIVATE rooms
  created_at: string;
  updated_at: string;
  members: RoomMemberResponse[];
  unread_count: number;
  last_message: ChatMessageResponse | null;
}

// ── Message Types ───────────────────────────────────────────────
interface ChatMessageResponse {
  id: string;
  room_id: string;
  sender_id: string;
  sender_name: string;           // Auto-resolved from JWT email
  type: MessageType;
  body: string | null;
  parent_message_id: string | null;
  file_url: string | null;
  file_name: string | null;
  file_size: number | null;
  file_content_type: string | null;
  edited: boolean;
  deleted: boolean;
  created_at: string;
  edited_at: string | null;
}

// ── File Upload ─────────────────────────────────────────────────
interface FileUploadResponse {
  file_url: string;
  file_name: string;
  file_size: number;
  content_type: string;
}

// ── WebSocket Events ────────────────────────────────────────────
interface WebSocketEventDto {
  event: string;
  user_id: string;
  room_id: string | null;
  message_id: string | null;
  timestamp: number;
}

interface TypingEvent {
  room_id: string;
  user_id: string;
  user_name: string;
  typing: boolean;
}

// ── Org Members ─────────────────────────────────────────────────
interface OrgMemberResponse {
  user_id: string;
  full_name: string;
  email: string;
  avatar_url: string | null;
  role: string;
  department: string | null;
  status: string;
  is_current_user: boolean;
}

// ── Pagination ──────────────────────────────────────────────────
interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
  first: boolean;
  last: boolean;
}
```

---

## Frontend API Functions (chatApi.ts)

Here are the recommended API wrapper functions:

```typescript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8082/api/v1/chat',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true, // Send rct_at cookie automatically
});

// Optional: add Bearer header if you have the token in memory
// (not needed if using cookies — the rct_at cookie is sent automatically)
api.interceptors.request.use((config) => {
  const token = tokenService.getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// ── Rooms ─────────────────────────────────────────────────────────
export async function getRooms(): Promise<ChatRoomResponse[]> {
  const res = await api.get('/rooms');
  return res.data;
}

export async function createGroupRoom(
  name: string,
  memberIds: string[],
  description?: string,
): Promise<ChatRoomResponse> {
  const res = await api.post('/rooms', {
    type: 'GROUP',
    name,
    member_user_ids: memberIds,
    description,
  });
  return res.data;
}

export async function createPublicRoom(
  name: string,
  description?: string,
): Promise<ChatRoomResponse> {
  const res = await api.post('/rooms', {
    type: 'PUBLIC',
    name,
    description,
  });
  return res.data;
}

export async function getOrCreatePrivateRoom(
  targetUserId: string,
): Promise<ChatRoomResponse> {
  const res = await api.post(`/users/${targetUserId}/rooms/private`);
  return res.data;
}

// ── Messages ──────────────────────────────────────────────────────
export async function getMessages(
  roomId: string,
  page = 0,
  size = 50,
): Promise<PageResponse<ChatMessageResponse>> {
  const res = await api.get(`/rooms/${roomId}/messages`, {
    params: { page, size },
  });
  return res.data;
}

export async function sendRoomMessage(
  roomId: string,
  body: string,
): Promise<ChatMessageResponse> {
  const res = await api.post(`/rooms/${roomId}/messages`, { body });
  return res.data;
}

export async function sendPrivateMessage(
  targetUserId: string,
  body: string,
): Promise<ChatMessageResponse> {
  const res = await api.post(`/users/${targetUserId}/messages`, { body });
  return res.data;
}

export async function editMessage(
  messageId: string,
  body: string,
): Promise<ChatMessageResponse> {
  const res = await api.put(`/messages/${messageId}`, { body });
  return res.data;
}

export async function deleteMessage(messageId: string): Promise<void> {
  await api.delete(`/messages/${messageId}`);
}

// ── Files ─────────────────────────────────────────────────────────
export async function uploadFile(file: File): Promise<FileUploadResponse> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await api.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data;
}

export async function sendFileMessage(
  roomId: string,
  uploaded: FileUploadResponse,
): Promise<ChatMessageResponse> {
  const res = await api.post(`/rooms/${roomId}/messages`, {
    type: 'FILE',
    file_url: uploaded.file_url,
    file_name: uploaded.file_name,
    file_size: uploaded.file_size,
    file_content_type: uploaded.content_type, // ← map content_type → file_content_type
  });
  return res.data;
}

// ── Read Receipts ─────────────────────────────────────────────────
export async function markAsRead(roomId: string): Promise<void> {
  await api.post(`/rooms/${roomId}/read`);
}

// ── Presence ──────────────────────────────────────────────────────
export async function getOnlineUsers(): Promise<string[]> {
  const res = await api.get('/users/online');
  return res.data; // Returns array of UUID strings
}

export async function getOnlineUsersDetails(): Promise<OrgMemberResponse[]> {
  const res = await api.get('/users/online/details');
  return res.data;
}

// ── Room Members ──────────────────────────────────────────────────
export async function addMemberToRoom(
  roomId: string,
  userId: string,
): Promise<ChatRoomResponse> {
  const res = await api.post(`/rooms/${roomId}/members/${userId}`);
  return res.data;
}

export async function removeMemberFromRoom(
  roomId: string,
  userId: string,
): Promise<void> {
  await api.delete(`/rooms/${roomId}/members/${userId}`);
}

// ── Org Members Search ────────────────────────────────────────────
export async function getOrgMembers(params?: {
  search?: string;
  page?: number;
  size?: number;
}): Promise<PageResponse<OrgMemberResponse>> {
  const res = await api.get('/org-members', { params });
  return res.data;
}
```

---

## Error Responses

All errors follow this format:

```json
{
  "message": "Error description",
  "status_code": 400,
  "timestamp": "2026-04-13T12:00:00Z"
}
```

Validation errors include field details:
```json
{
  "message": "Validation failed",
  "status_code": 400,
  "timestamp": "2026-04-13T12:00:00Z",
  "errors": {
    "type": "Room type is required",
    "body": "body is required"
  }
}
```

| Status | Meaning |
|---|---|
| 400 | Bad request / validation error |
| 401 | JWT missing or expired |
| 403 | Not authorized (wrong org, not a member, not admin) |
| 404 | Room/message not found |
| 409 | Conflict (e.g. user already a member) |
| 413 | File too large |
| 502 | Backend API (profile service) unreachable |

---

## Quick Checklist

- [x] **Send message**: `POST /rooms/{roomId}/messages` — body only needs `{ "body": "..." }`
- [x] **DM a user**: `POST /users/{userId}/messages` — creates room automatically
- [x] **Create group**: `POST /rooms` with `type: "GROUP"`, `name`, `member_user_ids`
- [x] **Get member names**: Included in `GET /rooms` response under `members[].full_name`
- [x] **Online users**: `GET /users/online` returns UUID array
- [x] **Upload file**: `POST /files/upload` then `POST /rooms/{id}/messages` with FILE type
- [x] **Mark read**: `POST /rooms/{roomId}/read`
- [x] **Search users**: `GET /org-members?search=jane`

