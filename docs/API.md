<!-- GSD:docs-update -->
# API Reference

Base URL: `http://localhost:8080`

All endpoints require `Authorization: Bearer <accessToken>` unless marked as **public**.

Error responses use [RFC 7807 Problem Detail](https://www.rfc-editor.org/rfc/rfc7807) format.

---

## Auth

### POST `/api/auth/register` (public)

Register a new user.

**Request:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```
- `email` — valid email address (required)
- `password` — minimum 8 characters (required)

**Response (201):**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "550e8400-..."
}
```

**Errors:** `409` duplicate email, `400` validation failure

### POST `/api/auth/login` (public)

**Request:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response (200):** same as register

**Errors:** `401` invalid credentials

### POST `/api/auth/refresh` (public)

Rotate a refresh token for a new token pair.

**Request:**
```json
{
  "refreshToken": "550e8400-..."
}
```

**Response (200):** same as register

**Errors:** `401` expired/invalid/reused token (reuse revokes all user tokens)

### GET `/api/auth/me`

Returns the current user's ID.

**Response (200):**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

## User Profile

### POST `/api/profile`

Create a user profile.

**Request:**
```json
{
  "displayName": "Cat Lover",
  "bio": "I love cats!",
  "dateOfBirth": "1995-06-15",
  "gender": "FEMALE",
  "genderPreference": "EVERYONE",
  "ageMin": 18,
  "ageMax": 40,
  "maxDistanceKm": 50
}
```

- `gender` — `MALE` or `FEMALE`
- `genderPreference` — `MALE`, `FEMALE`, or `EVERYONE`
- `dateOfBirth` — must be 18+ years ago

**Response (201):** `ProfileResponse`

**Errors:** `409` profile already exists, `400` validation failure (underage, invalid gender)

### GET `/api/profile`

Get the current user's profile.

**Response (200):**
```json
{
  "displayName": "Cat Lover",
  "bio": "I love cats!",
  "dateOfBirth": "1995-06-15",
  "gender": "FEMALE",
  "genderPreference": "EVERYONE",
  "ageMin": 18,
  "ageMax": 40,
  "maxDistanceKm": 50,
  "latitude": 51.5074,
  "longitude": -0.1278
}
```

### PUT `/api/profile`

Update profile fields (partial update — only include fields to change).

**Request:**
```json
{
  "displayName": "New Name",
  "bio": "Updated bio"
}
```

**Response (200):** `ProfileResponse`

### PUT `/api/profile/location`

Update the user's geolocation.

**Request:**
```json
{
  "latitude": 51.5074,
  "longitude": -0.1278
}
```

**Response (200):** `ProfileResponse`

### GET `/api/profile/completeness`

Check if the profile is complete enough for discovery.

**Response (200):**
```json
{
  "isComplete": false,
  "missingFields": ["location", "photo"]
}
```

---

## User Photos

### POST `/api/profile/photos/upload-url`

Request a presigned S3 upload URL.

**Request:**
```json
{
  "contentType": "image/jpeg"
}
```

- `contentType` — `image/jpeg` or `image/png` only

**Response (200):**
```json
{
  "photoId": "uuid",
  "uploadUrl": "https://s3...",
  "s3Key": "photos/{userId}/{uuid}.jpg"
}
```

**Errors:** `400` invalid content type, `400` max 6 photos reached

### POST `/api/profile/photos/{id}/confirm`

Confirm a photo upload. Server verifies the S3 object exists, generates a 200×200 thumbnail, and activates the photo.

**Response (200):**
```json
{
  "photoId": "uuid",
  "s3Key": "photos/...",
  "thumbnailS3Key": "thumbnails/...",
  "displayOrder": 0,
  "status": "ACTIVE"
}
```

### DELETE `/api/profile/photos/{id}`

Delete a photo. Removes from S3 and reorders remaining photos.

**Response (204)**

### PUT `/api/profile/photos/reorder`

Reorder photos.

**Request:**
```json
{
  "photoIds": ["uuid1", "uuid2", "uuid3"]
}
```

Must include all active photo IDs in the desired order.

**Response (200)**

### GET `/api/profile/photos`

List all active photos in display order.

**Response (200):** array of `PhotoResponse`

---

## Cat Profiles

### POST `/api/cats`

Create a cat profile (max 5 per user).

**Request:**
```json
{
  "name": "Whiskers",
  "age": 3,
  "ageUnit": "YEARS",
  "breed": "Persian",
  "bio": "Fluffy and friendly"
}
```

- `ageUnit` — `MONTHS` or `YEARS`

**Response (201):** `CatProfileResponse`

**Errors:** `409` max 5 cats reached

### GET `/api/cats`

List all cat profiles for the current user.

**Response (200):** array of `CatProfileResponse`

### GET `/api/cats/{catId}`

Get a specific cat profile.

**Response (200):** `CatProfileResponse`

### PUT `/api/cats/{catId}`

Update a cat profile (partial update).

**Response (200):** `CatProfileResponse`

### DELETE `/api/cats/{catId}`

Delete a cat profile. Cascades to all cat photos (S3 objects and DB records).

**Response (204)**

---

## Cat Photos

### POST `/api/cats/{catId}/photos/upload-url`

Request a presigned upload URL for a cat photo (max 10 per cat).

**Request:**
```json
{
  "contentType": "image/jpeg"
}
```

**Response (200):** `CatUploadUrlResponse`

### POST `/api/cats/{catId}/photos/{photoId}/confirm`

Confirm a cat photo upload.

**Response (200):** `CatConfirmUploadResponse`

### DELETE `/api/cats/{catId}/photos/{photoId}`

Delete a cat photo.

**Response (204)**

### PUT `/api/cats/{catId}/photos/reorder`

Reorder cat photos.

**Request:**
```json
{
  "photoIds": ["uuid1", "uuid2"]
}
```

**Response (200)**

### GET `/api/cats/{catId}/photos`

List all active photos for a cat.

**Response (200):** array of `CatPhotoResponse`

---

## Discovery

### GET `/api/discovery/feed`

Get the discovery feed — nearby cats from other users.

**Query params:**
- `cursor` (optional) — base64-encoded pagination cursor
- `pageSize` (optional, default `20`, max `50`)

**Response (200):**
```json
{
  "cats": [
    {
      "catId": "uuid",
      "name": "Whiskers",
      "age": 3,
      "ageUnit": "YEARS",
      "breed": "Persian",
      "bio": "Fluffy",
      "ownerId": "uuid",
      "ownerDisplayName": "Cat Lover",
      "catPhotoThumbnail": "thumbnails/...",
      "ownerPhotoThumbnail": "thumbnails/...",
      "distanceKm": 5.2
    }
  ],
  "cursor": {
    "seed": 0.42,
    "offset": 20,
    "hasMore": true
  }
}
```

**Errors:** `400` location required, `400` profile incomplete

### GET `/api/discovery/cats/{catId}/owner`

Get the full owner profile for a cat from the feed.

**Response (200):**
```json
{
  "userId": "uuid",
  "displayName": "Cat Lover",
  "bio": "I love cats!",
  "age": 29,
  "gender": "FEMALE",
  "photos": [{"s3Key": "...", "thumbnailS3Key": "..."}],
  "cats": [{"id": "uuid", "name": "Whiskers", "age": 3, "breed": "Persian", "photoThumbnail": "..."}]
}
```

### POST `/api/discovery/swipe`

Swipe on a cat (like or pass).

**Request:**
```json
{
  "catId": "uuid",
  "action": "LIKE"
}
```

- `action` — `LIKE` or `PASS`

**Response (200):**
```json
{
  "swipeId": "uuid",
  "matched": true,
  "matchId": "uuid"
}
```

`matched` is true when both users have liked each other's cats. `matchId` is present only when a new match is created.

**Errors:** `409` already swiped, `400` cannot swipe on own cat, `404` cat not found

---

## Matches

### GET `/api/matches`

List all matches for the current user.

**Response (200):**
```json
{
  "matches": [
    {
      "matchId": "uuid",
      "matchedAt": "2025-06-15T10:30:00Z",
      "otherUser": {
        "userId": "uuid",
        "displayName": "Cat Lover",
        "photoThumbnail": "thumbnails/..."
      },
      "otherUserCats": [
        {"name": "Whiskers", "photoThumbnail": "thumbnails/..."}
      ]
    }
  ]
}
```

---

## Conversations (Chat)

### GET `/api/conversations`

List all conversations with unread counts.

**Response (200):**
```json
{
  "conversations": [
    {
      "conversationId": "uuid",
      "matchId": "uuid",
      "otherUser": {"userId": "uuid", "displayName": "Cat Lover", "photoThumbnail": "..."},
      "otherUserCats": [{"name": "Whiskers", "photoThumbnail": "..."}],
      "lastMessage": {"content": "Hey!", "sentAt": "2025-06-15T10:30:00Z", "sentByMe": false},
      "unreadCount": 3
    }
  ]
}
```

### POST `/api/conversations/{id}/read`

Mark a conversation as read.

**Response (204)**

### GET `/api/conversations/{id}/messages`

Get message history for a conversation (cursor-paginated, newest first).

**Query params:**
- `cursor` (optional) — ISO timestamp for pagination
- `size` (optional, default `30`)

**Response (200):**
```json
{
  "messages": [
    {
      "messageId": "uuid",
      "conversationId": "uuid",
      "senderId": "uuid",
      "senderName": "Cat Lover",
      "content": "Hey!",
      "createdAt": "2025-06-15T10:30:00Z"
    }
  ],
  "nextCursor": "2025-06-15T10:29:00Z",
  "hasMore": true
}
```

---

## WebSocket (Chat)

Connect via STOMP over WebSocket at `ws://localhost:8080/ws`.

### Authentication

Pass the JWT as a STOMP `CONNECT` header. The `WebSocketAuthInterceptor` validates the token.

### Send a Message

**Destination:** `/app/chat.send`

**Payload:**
```json
{
  "conversationId": "uuid",
  "content": "Hello!"
}
```

Or start a new conversation from a match:
```json
{
  "matchId": "uuid",
  "content": "Hello!"
}
```

Either `conversationId` or `matchId` must be provided (not both).

### Subscriptions

- **`/topic/chat/{conversationId}`** — receive messages for a specific conversation
- **`/user/{userId}/queue/notifications`** — receive push notifications (new message previews)

### Notification Format

```json
{
  "conversationId": "uuid",
  "messageId": "uuid",
  "senderName": "Cat Lover",
  "preview": "Hey! How are you..."
}
```

---

## OpenAPI

Machine-readable API docs are available at `/v3/api-docs` (public). Grouped endpoints:

| Group | Paths |
|-------|-------|
| `auth` | `/api/auth/**` |
| `user` | `/api/profile/**`, `/api/photos/**` |
| `cats` | `/api/cats/**` |
| `discovery` | `/api/discovery/**`, `/api/swipe/**`, `/api/matches/**` |
| `chat` | `/api/chat/**`, `/api/conversations/**` |
