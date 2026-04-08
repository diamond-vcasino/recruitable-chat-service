# Chat Microservice — React Frontend Guide

## Overview

This guide explains how to integrate `recruitable-chat-service` from a React frontend.

Backend base URLs:

- REST: `http://localhost:8082/api/v1/chat`
- WebSocket: `http://localhost:8082/ws`

The service supports:

- direct messages (`PRIVATE` rooms)
- group rooms (`GROUP`)
- org-wide admin-created public rooms (`PUBLIC`)
- real-time message, presence, member, and room-change events

---

## 1. Install Dependencies

```bash
npm install @stomp/stompjs sockjs-client
npm install -D @types/sockjs-client
```

---

## 2. API Client Setup

```typescript
// src/config/chat.ts
export const CHAT_API_BASE = 'http://localhost:8082/api/v1/chat';
export const CHAT_WS_URL = 'http://localhost:8082/ws';
```

```typescript
// src/services/chatApi.ts
import axios from 'axios';
import { CHAT_API_BASE } from '../config/chat';

const chatApi = axios.create({
  baseURL: CHAT_API_BASE,
});

chatApi.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default chatApi;
```

---

## 3. Room Types and Access Rules

```ts
type RoomType = 'PRIVATE' | 'GROUP' | 'PUBLIC';
```

### `PRIVATE`
- 1-on-1 direct message room
- only the 2 members can access it
- reused automatically if it already exists

### `GROUP`
- normal multi-user room
- explicit members only
- users can be added/removed

### `PUBLIC`
- org-wide room
- all users in the same org can access it
- only users whose JWT role includes `ADMIN` can create it

---

## 4. REST Integration

### 4.1 Create rooms

```typescript
// PRIVATE room
export const createPrivateRoom = async (otherUserId: string) => {
  const res = await chatApi.post('/rooms', {
    type: 'PRIVATE',
    member_user_ids: [otherUserId],
  });
  return res.data;
};

// GROUP room
export const createGroupRoom = async (name: string, memberIds: string[] = []) => {
  const res = await chatApi.post('/rooms', {
    type: 'GROUP',
    name,
    member_user_ids: memberIds,
  });
  return res.data;
};

// PUBLIC room (ADMIN only)
export const createPublicRoom = async (name: string, description?: string) => {
  const res = await chatApi.post('/rooms', {
    type: 'PUBLIC',
    name,
    description,
  });
  return res.data;
};
```

If a non-admin tries to create a `PUBLIC` room, backend returns `403`.

---

### 4.2 Get my accessible rooms

```typescript
export const getRooms = async () => {
  const res = await chatApi.get('/rooms');
  return res.data;
};
```

This returns:

- all `PRIVATE` rooms I belong to
- all `GROUP` rooms I belong to
- all `PUBLIC` rooms in my org

---

### 4.3 Resolve or create private room before chatting

```typescript
export const getOrCreatePrivateRoom = async (targetUserId: string) => {
  const res = await chatApi.post(`/users/${targetUserId}/rooms/private`);
  return res.data;
};
```

Use this when the user clicks “Message user”.

The response includes the `room_id` to open.

---

### 4.4 Send direct private message to a user

```typescript
export const sendPrivateMessage = async (targetUserId: string, body: string) => {
  const res = await chatApi.post(`/users/${targetUserId}/messages`, {
    body,
    type: 'TEXT',
  });
  return res.data;
};
```

This is the fastest way to DM a user if you do not already know the room id.

The backend creates or reuses the `PRIVATE` room automatically.
Do not send `target_user_id` in the JSON body; it is taken from the URL path.

---

### 4.5 Add / remove members in a group room

```typescript
export const addMemberToRoom = async (roomId: string, userId: string) => {
  const res = await chatApi.post(`/rooms/${roomId}/members/${userId}`);
  return res.data;
};

export const removeMemberFromRoom = async (roomId: string, userId: string) => {
  await chatApi.delete(`/rooms/${roomId}/members/${userId}`);
};
```

Allowed by backend for:

- room `OWNER`
- room `ADMIN`
- org `ADMIN`

Only works on `GROUP` rooms.

---

### 4.6 Get room messages

```typescript
export const getMessages = async (roomId: string, page = 0, size = 50) => {
  const res = await chatApi.get(`/rooms/${roomId}/messages`, {
    params: { page, size },
  });
  return res.data;
};
```

---

### 4.7 Send message to an existing room

```typescript
export const sendRoomMessage = async (roomId: string, body: string) => {
  const res = await chatApi.post(`/rooms/${roomId}/messages`, {
    body,
    type: 'TEXT',
  });
  return res.data;
};
```

For file messages:

```typescript
export const sendFileMessage = async (roomId: string, upload: FileUploadResponse) => {
  const res = await chatApi.post(`/rooms/${roomId}/messages`, {
    type: 'FILE',
    body: upload.file_name,
    file_url: upload.file_url,
    file_name: upload.file_name,
    file_size: upload.file_size,
    file_content_type: upload.content_type,
  });
  return res.data;
};
```

---

### 4.8 Edit / delete message

```typescript
export const editMessage = async (messageId: string, body: string) => {
  const res = await chatApi.put(`/messages/${messageId}`, { body });
  return res.data;
};

export const deleteMessage = async (messageId: string) => {
  await chatApi.delete(`/messages/${messageId}`);
};
```

---

### 4.9 Mark room as read

```typescript
export const markAsRead = async (roomId: string) => {
  await chatApi.post(`/rooms/${roomId}/read`);
};
```

---

### 4.10 Upload file

```typescript
export const uploadFile = async (file: File) => {
  const formData = new FormData();
  formData.append('file', file);

  const res = await chatApi.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data;
};
```

---

### 4.11 Get online users

```typescript
export const getOnlineUsers = async () => {
  const res = await chatApi.get('/users/online');
  return res.data as string[];
};
```

---

## 5. WebSocket / STOMP Setup

```typescript
// src/services/chatWebSocket.ts
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { CHAT_WS_URL } from '../config/chat';

let stompClient: Client | null = null;

export const connectChat = (
  token: string,
  onConnect: () => void,
  onDisconnect: () => void
) => {
  stompClient = new Client({
    webSocketFactory: () => new SockJS(CHAT_WS_URL),
    connectHeaders: {
      Authorization: `Bearer ${token}`,
    },
    reconnectDelay: 5000,
    onConnect,
    onDisconnect,
    onStompError: (frame) => {
      console.error('STOMP error:', frame.headers['message']);
    },
  });

  stompClient.activate();
  return stompClient;
};

export const disconnectChat = async () => {
  if (stompClient) {
    await stompClient.deactivate();
    stompClient = null;
  }
};

export const getStompClient = () => stompClient;
```

Important:

- backend checks org-level subscription access
- backend also checks room-level subscription access for `PRIVATE` and `GROUP`
- `PUBLIC` room subscriptions are allowed for all users in that org

---

## 6. WebSocket Subscriptions

### 6.1 Subscribe to room messages

```typescript
export const subscribeToRoom = (
  orgId: string,
  roomId: string,
  onMessage: (payload: ChatMessageResponse | WebSocketEventDto) => void
): StompSubscription | null => {
  if (!stompClient || !stompClient.connected) return null;

  return stompClient.subscribe(`/topic/org.${orgId}.room.${roomId}`, (msg: IMessage) => {
    onMessage(JSON.parse(msg.body));
  });
};
```

Note: this channel may receive:

- `ChatMessageResponse` for new messages
- `WebSocketEventDto` with `event = MESSAGE_DELETED`

Frontend should branch on `payload.event` presence.

---

### 6.2 Subscribe to message edits

```typescript
export const subscribeToRoomEdits = (
  orgId: string,
  roomId: string,
  onEdit: (message: ChatMessageResponse) => void
): StompSubscription | null => {
  if (!stompClient || !stompClient.connected) return null;

  return stompClient.subscribe(`/topic/org.${orgId}.room.${roomId}.edit`, (msg: IMessage) => {
    onEdit(JSON.parse(msg.body));
  });
};
```

---

### 6.3 Subscribe to typing

```typescript
export const subscribeToTyping = (
  orgId: string,
  roomId: string,
  onTyping: (event: TypingEvent) => void
): StompSubscription | null => {
  if (!stompClient || !stompClient.connected) return null;

  return stompClient.subscribe(`/topic/org.${orgId}.room.${roomId}.typing`, (msg: IMessage) => {
    onTyping(JSON.parse(msg.body));
  });
};
```

---

### 6.4 Subscribe to read receipts

```typescript
export const subscribeToReadReceipts = (
  orgId: string,
  roomId: string,
  onEvent: (event: WebSocketEventDto) => void
): StompSubscription | null => {
  if (!stompClient || !stompClient.connected) return null;

  return stompClient.subscribe(`/topic/org.${orgId}.room.${roomId}.read`, (msg: IMessage) => {
    onEvent(JSON.parse(msg.body));
  });
};
```

---

### 6.5 Subscribe to room member changes

```typescript
export const subscribeToRoomMembers = (
  orgId: string,
  roomId: string,
  onEvent: (event: WebSocketEventDto) => void
): StompSubscription | null => {
  if (!stompClient || !stompClient.connected) return null;

  return stompClient.subscribe(`/topic/org.${orgId}.room.${roomId}.members`, (msg: IMessage) => {
    onEvent(JSON.parse(msg.body));
  });
};
```

Events include:

- `MEMBER_ADDED`
- `MEMBER_REMOVED`

---

### 6.6 Subscribe to room-list changes

```typescript
export const subscribeToRooms = (
  orgId: string,
  onEvent: (event: WebSocketEventDto) => void
): StompSubscription | null => {
  if (!stompClient || !stompClient.connected) return null;

  return stompClient.subscribe(`/topic/org.${orgId}.rooms`, (msg: IMessage) => {
    onEvent(JSON.parse(msg.body));
  });
};
```

Events include:

- `ROOM_CREATED`
- `ROOM_UPSERTED`
- `MEMBER_ADDED`
- `MEMBER_REMOVED`

Recommended behavior: when one of these arrives, re-fetch `GET /rooms`.

---

### 6.7 Subscribe to presence

```typescript
export const subscribeToPresence = (
  orgId: string,
  onEvent: (event: WebSocketEventDto) => void
): StompSubscription | null => {
  if (!stompClient || !stompClient.connected) return null;

  return stompClient.subscribe(`/topic/org.${orgId}.presence`, (msg: IMessage) => {
    onEvent(JSON.parse(msg.body));
  });
};
```

---

## 7. WebSocket Publish Helpers

### 7.1 Send to room

```typescript
export const sendMessageWs = (roomId: string, body: string, type: 'TEXT' | 'FILE' = 'TEXT') => {
  if (!stompClient || !stompClient.connected) return;

  stompClient.publish({
    destination: '/app/chat.send',
    body: JSON.stringify({
      room_id: roomId,
      body,
      type,
    }),
  });
};
```

### 7.2 Send typing

```typescript
export const sendTypingIndicator = (roomId: string, typing: boolean) => {
  if (!stompClient || !stompClient.connected) return;

  stompClient.publish({
    destination: '/app/chat.typing',
    body: JSON.stringify({
      room_id: roomId,
      typing,
    }),
  });
};
```

### 7.3 Send read receipt

```typescript
export const sendReadReceipt = (roomId: string) => {
  if (!stompClient || !stompClient.connected) return;

  stompClient.publish({
    destination: '/app/chat.read',
    body: JSON.stringify({
      room_id: roomId,
    }),
  });
};
```

---

## 8. Recommended Frontend Flows

### Start DM with a user

```typescript
const openDirectMessage = async (targetUserId: string) => {
  const room = await getOrCreatePrivateRoom(targetUserId);
  return room.id;
};
```

Or send immediately:

```typescript
const quickDirectMessage = async (targetUserId: string, body: string) => {
  const message = await sendPrivateMessage(targetUserId, body);
  return message.room_id;
};
```

### Create public announcements room

```typescript
const createAnnouncementsRoom = async () => {
  return createPublicRoom('Announcements', 'Visible to everyone in the org');
};
```

### Add user to a group room

```typescript
const inviteUser = async (roomId: string, userId: string) => {
  await addMemberToRoom(roomId, userId);
  // then optionally refetch room details
};
```

---

## 9. TypeScript Interfaces

```typescript
export interface ChatRoomResponse {
  id: string;
  organization_id: string;
  type: 'PRIVATE' | 'GROUP' | 'PUBLIC';
  name: string | null;
  description: string | null;
  avatar_url: string | null;
  created_at: string;
  updated_at: string;
  members: RoomMemberResponse[];
  unread_count: number;
  last_message: ChatMessageResponse | null;
}

export interface RoomMemberResponse {
  user_id: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER';
  joined_at: string;
  last_read_at: string | null;
}

export interface ChatMessageResponse {
  id: string;
  room_id: string;
  sender_id: string;
  sender_name: string | null;
  type: 'TEXT' | 'FILE' | 'SYSTEM';
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

export interface TypingEvent {
  room_id: string;
  user_id: string;
  user_name: string | null;
  typing: boolean;
}

export interface WebSocketEventDto {
  event:
    | 'USER_ONLINE'
    | 'USER_OFFLINE'
    | 'ROOM_CREATED'
    | 'ROOM_UPSERTED'
    | 'MEMBER_ADDED'
    | 'MEMBER_REMOVED'
    | 'MESSAGE_DELETED'
    | 'READ_RECEIPT';
  user_id: string;
  room_id: string | null;
  message_id: string | null;
  timestamp: number;
}

export interface CreateRoomRequest {
  type: 'PRIVATE' | 'GROUP' | 'PUBLIC';
  name?: string;
  description?: string;
  member_user_ids?: string[];
}

export interface SendMessageRequest {
  room_id: string;
  body?: string;
  type?: 'TEXT' | 'FILE';
  parent_message_id?: string;
  file_url?: string;
  file_name?: string;
  file_size?: number;
  file_content_type?: string;
}

export interface SendPrivateMessageRequest {
  body?: string;
  type?: 'TEXT' | 'FILE';
  parent_message_id?: string;
  file_url?: string;
  file_name?: string;
  file_size?: number;
  file_content_type?: string;
}

export interface FileUploadResponse {
  file_url: string;
  file_name: string;
  file_size: number;
  content_type: string;
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
```

---

## 10. Important Notes

1. The backend uses snake_case JSON for REST and WebSocket payloads.
2. There is no user table in this service; sender identity comes from JWT.
3. If you need profile names/avatars, fetch them from the main backend and join in frontend state.
4. For room-list accuracy, re-fetch `GET /rooms` after room-change events.
5. For deleted messages, listen on `/topic/org.{orgId}.room.{roomId}` and handle `event = MESSAGE_DELETED`.
6. Swagger UI is available at:
   - `http://localhost:8082/swagger-ui/index.html`
