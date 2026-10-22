# API Reference

Base URL: `http://localhost:8080/api/v1`
Interactive documentation: `/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`

## Conventions

* **Authentication** — send `Authorization: Bearer <accessToken>`.
  `POST /auth/login` returns the access token in the body and sets an httpOnly
  `bookleaf_refresh` cookie on `/api/v1/auth/refresh`.
* **Dates** — ISO-8601 (`2024-05-22T00:00:00Z`).
* **Money** — decimal numbers in INR.
* **Errors** — `{ timestamp, status, error, message, path, fieldErrors?, correlationId? }`.

## Auth

### `POST /auth/login` — public

```json
{ "email": "priya.sharma@email.com", "password": "Author@1234" }
```

```json
{
  "accessToken": "eyJhbGciOi…",
  "tokenType": "Bearer",
  "expiresInSeconds": 1800,
  "user": {
    "id": "8f0f…", "email": "priya.sharma@email.com", "fullName": "Priya Sharma",
    "role": "AUTHOR", "authorId": "fca7…", "authorCode": "AUTH001"
  }
}
```

`401` when the email or password is wrong — the message is identical in both cases.

The response also carries `refreshToken`. The client keeps a copy in `sessionStorage` (scoped to
the browser tab) so two tabs can hold two different accounts at once; the httpOnly cookie remains
the shared fallback.

### `POST /auth/refresh` — public

Redeems a refresh token for a new access token and rotates it.

The token may be sent in the request body (a tab keeping its own copy) or in the httpOnly cookie
(the shared session). **The body wins** when both are present, so a tab signed in as one account
is never switched to another account's session by a different tab's refresh.

### `POST /auth/logout` — any

Clears the refresh cookie.

### `GET /auth/me` — authenticated

Returns the same `user` object as login.

---

## Author portal — `role = AUTHOR`

### `GET /author/dashboard`

```json
{
  "authorCode": "AUTH001", "name": "Priya Sharma", "email": "…", "city": "Mumbai",
  "phone": "+91-98765-43210", "joinedDate": "2023-03-15",
  "stats": {
    "bookCount": 2, "liveBookCount": 2, "inProductionBookCount": 0,
    "totalCopiesSold": 531, "totalRoyaltyEarned": 19908.0,
    "royaltyPaid": 16338.0, "royaltyPending": 3570.0,
    "lifetimeValue": 849.0, "openTicketCount": 1
  },
  "books": [ /* BookResponse */ ],
  "recentTickets": [ /* TicketSummary */ ]
}
```

### `GET /author/books` · `GET /author/books/{id}`

```json
{
  "id": "…", "bookCode": "BK001", "title": "Whispers of the Ganges",
  "isbn": "978-93-5XXXX-01-1", "genre": "Literary Fiction",
  "publicationDate": "2023-06-20", "status": "Published & Live", "inProduction": false,
  "mrp": 399.0, "authorRoyaltyPerCopy": 35.0,
  "totalCopiesSold": 342, "totalRoyaltyEarned": 11970.0,
  "royaltyPaid": 8400.0, "royaltyPending": 3570.0,
  "lastRoyaltyPayoutDate": "2025-10-15", "printPartner": "In-House",
  "availableOn": ["Amazon India", "Flipkart", "BookLeaf Store"]
}
```

`mrp`, `authorRoyaltyPerCopy`, `publicationDate` and `printPartner` are `null` for a title still in
production — the field is genuinely not applicable yet.

### `GET /author/tickets` · `GET /author/tickets/{id}`

`GET /author/tickets` returns a list of `TicketSummary`. `GET /author/tickets/{id}` adds
`description`, `attachmentName`, `book`, `messages` and `events`.

**Internal notes are never included.** A ticket belonging to another author returns `404`.

### `POST /author/tickets`

```json
{
  "bookId": "bc37…",            // null or omitted = General / Account Level
  "subject": "Royalty not received for six months",
  "description": "At least 20 characters…",
  "attachmentName": "statement.png"   // optional
}
```

`201` with the created ticket, including the automatic `category` and `priority`.

Classification happens twice: the response carries the **deterministic** classification
immediately (so the request never waits on the model), and the AI refines it in the background a
moment later. When the refinement lands, the ticket is updated and an `AI_CLASSIFIED` event is
published on the SSE stream. Set `app.ai.triage-async=false` to classify synchronously and get the
AI's answer in the response instead.

```json
{
  "id": "…", "ticketCode": "BL-000042",
  "subject": "…", "status": "OPEN", "statusLabel": "Open",
  "category": "ROYALTY_PAYMENTS", "categoryLabel": "Royalty & Payments",
  "categorySource": "AI",            // AI | HEURISTIC | MANUAL
  "priority": "CRITICAL", "priorityLabel": "Critical",
  "prioritySource": "AI",
  "bookTitle": "Whispers of the Ganges", "authorName": "Priya Sharma",
  "createdAt": "…", "updatedAt": "…", "firstResponseAt": null,
  "assignedToName": null, "messageCount": 0,
  "awaitingAdmin": true, "ageHours": 0, "lastMessagePreview": null
}
```

`categorySource` is `HEURISTIC` when the AI provider was unavailable and the built-in rules decided.

### `POST /author/tickets/{id}/replies`

```json
{ "body": "Any additional detail that would help." }
```

---

## Admin portal — `role = ADMIN`

### `GET /admin/tickets`

Query parameters (all optional):

| Parameter | Values |
|---|---|
| `status` | `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED` |
| `category` | `ROYALTY_PAYMENTS`, `ISBN_METADATA`, `PRINTING_QUALITY`, `DISTRIBUTION_AVAILABILITY`, `PRODUCTION_UPDATES`, `GENERAL_INQUIRY` |
| `priority` | `CRITICAL`, `HIGH`, `MEDIUM`, `LOW` |
| `assignedTo` | user id |
| `from`, `to` | ISO-8601 instants |
| `search` | free text over subject, description, ticket code and author name/email |
| `sort` | `priorityRank,desc` (default), `createdAt,asc`, `createdAt,desc`, `updatedAt,desc` |
| `page`, `size` | 0-based page, max size 100 |

```json
{
  "content": [ /* TicketSummary */ ],
  "page": 0, "size": 25, "totalElements": 9, "totalPages": 1,
  "first": true, "last": true
}
```

An unknown filter value returns `400` with a message naming the offending value.

### `GET /admin/tickets/{id}`

Adds `author`, `book`, `description`, `messages` (including internal notes),
`events` (the audit trail) and `priorityRationale` (the model's reasoning).

### `PUT /admin/tickets/{id}/status`

```json
{ "status": "IN_PROGRESS" }
```

Sets `firstResponseAt` on the first public reply and `resolvedAt` / `closedAt` as appropriate.

### `PUT /admin/tickets/{id}/category` · `PUT /admin/tickets/{id}/priority`

```json
{ "category": "PRINTING_QUALITY" }
{ "priority": "HIGH" }
```

The response reflects the override and flips `categorySource` / `prioritySource` to `MANUAL`.

### `POST /admin/tickets/{id}/assign`

Assigns the ticket to the signed-in admin.

### `POST /admin/tickets/{id}/replies` · `POST /admin/tickets/{id}/notes`

```json
{ "body": "We are looking into this and will come back to you within 48 hours." }
```

`/replies` is visible to the author; `/notes` is **never** exposed to them.

### `POST /admin/tickets/{id}/draft`

Query parameter: `refresh` (default `false`).

`refresh=false` serves the cached draft when there is one — free and instant.
`refresh=true` **bypasses the cache**: the model is asked again (at a slightly
higher temperature so the wording genuinely differs) and the fresh answer does not
overwrite the cached original. This is what the **Regenerate** button uses.

```json
{
  "available": true,
  "body": "Hi Priya, …",
  "provider": "anthropic",
  "model": "claude-3-5-haiku-latest",
  "contextTokens": 412,
  "note": null
}
```

When the provider is unavailable:

```json
{
  "available": false, "body": null, "provider": "none", "model": "none",
  "contextTokens": 412,
  "note": "No AI provider is configured, so no draft was generated. Write the reply manually — the ticket is fully usable."
}
```

The `note` explains where the draft came from — a cache hit, a fresh generation, or a
regeneration.

### `POST /admin/tickets/bulk`

Applies one action to many tickets. Every part of the request is optional — only the parts that
are supplied are applied, so the same endpoint covers "assign these 30 to me" and "close these 12
as resolved".

```json
{
  "ticketIds": ["8f58f29d-…", "3aad79c8-…"],
  "status": "IN_PROGRESS",
  "category": "ROYALTY_PAYMENTS",
  "priority": "HIGH",
  "assignToMe": true,
  "note": "Triaged in the morning batch."
}
```

```json
{
  "requested": 2, "updated": 2,
  "statusChanged": 2, "categoryChanged": 0, "priorityChanged": 1,
  "assigned": 2, "noted": 2,
  "ticketIds": ["8f58f29d-…", "3aad79c8-…"]
}
```

Tickets already in the target state are left alone and excluded from the change counts. Every
change writes its own audit event, so a bulk action is traceable ticket by ticket. A `note` is an
**internal note** and is never visible to the author.

`400` when no tickets are selected or no action is chosen; `403` for a non-admin.

### `GET /admin/filters` · `POST /admin/filters` · `DELETE /admin/filters/{id}`

Saved queue filters, private to the admin who created them.

```json
// POST /admin/filters
{ "name": "Overdue royalties", "filters": "{"status":"OPEN","category":"ROYALTY_PAYMENTS","sort":"createdAt,asc"}" }
```

```json
// GET /admin/filters
[
  { "id": "6c7ead2c-…", "name": "Overdue royalties",
    "filters": "{"status":"OPEN","category":"ROYALTY_PAYMENTS","sort":"createdAt,asc"}",
    "createdAt": "2026-10-22T12:30:47Z" }
]
```

`409` when a filter with the same name already exists.

### `GET /admin/dashboard`

```json
{
  "queue": {
    "open": 6, "inProgress": 3, "resolved": 1, "closed": 2,
    "awaitingFirstResponse": 4, "overdue": 1, "criticalOpen": 2,
    "averageFirstResponseHours": 6.0
  },
  "ai": {
    "totalCalls": 24, "cachedCalls": 6, "failedCalls": 1,
    "promptTokens": 18422, "completionTokens": 3106,
    "averageLatencyMs": 842.0, "provider": "anthropic", "model": "claude-3-5-haiku-latest"
  },
  "urgentTickets": [ /* TicketSummary */ ],
  "recentTickets": [ /* TicketSummary */ ],
  "totalAuthors": 10, "totalBooks": 18
}
```

`overdue` counts unresolved tickets older than 48 hours — the window BookLeaf's own tone guide
promises authors.

### `GET /admin/ai-usage` · `GET /admin/ai-usage/recent?limit=25`

The AI summary, and the individual calls behind it (purpose, provider, model, tokens, latency,
cached flag, success, error message).

---

## Real-time

### `GET /stream/tickets` — authenticated

`text/event-stream`. Admins receive every ticket event; authors receive only their own.

```
event:connected
data:{"type":"CONNECTED","message":"Live updates enabled","at":"2026-10-22T06:28:27Z"}

event:ticket
data:{"type":"REPLY_SENT","ticketId":"8f58…","ticketCode":"BL-000010","status":"OPEN","priority":"LOW","category":"GENERAL_INQUIRY","message":"BookLeaf replied to BL-000010","at":"2026-10-22T06:28:40Z"}
```

Event types: `TICKET_CREATED`, `STATUS_CHANGED`, `REPLY_SENT`, `NOTE_ADDED`, `AUTHOR_REPLIED`,
`ASSIGNED`, `CATEGORY_OVERRIDDEN`, `PRIORITY_OVERRIDDEN`.

The client should refetch the affected ticket rather than treat the payload as the full record.

---

## Meta

### `GET /meta` — public

```json
{
  "categories": ["Royalty & Payments", "ISBN & Metadata Issues", "Printing & Quality",
                 "Distribution & Availability", "Book Status & Production Updates", "General Inquiry"],
  "priorities": ["Critical", "High", "Medium", "Low"],
  "statuses": ["Open", "In Progress", "Resolved", "Closed"],
  "aiEnabled": true, "aiProvider": "anthropic", "aiModel": "claude-3-5-haiku-latest"
}
```

### `GET /meta/knowledge-base` — public

Every knowledge-base section with its approximate token cost and a content version hash.

---

## Status codes

| Code | Meaning |
|---|---|
| `200` / `201` | Success |
| `400` | Malformed request or failed validation (`fieldErrors` populated) |
| `401` | Missing, expired or invalid access token |
| `403` | Authenticated but not permitted (e.g. an author calling an admin route) |
| `404` | Not found — also returned when an author requests another author's ticket |
| `409` | Conflict |
| `500` | Unexpected server error — logged with a `correlationId`, never a stack trace |
