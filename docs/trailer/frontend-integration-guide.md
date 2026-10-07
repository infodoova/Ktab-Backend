# Book Trailer — Frontend Integration Guide

Everything the frontend needs to let a user generate an AI video trailer for a book, follow its progress, and play or download the result.

- **Base URL:** `/api/v1` (all paths below are relative to it)
- **Auth:** the usual session: the `ACCESS_TOKEN` cookie, same as the rest of the API.
- **Feature flag:** the whole API only exists when the backend runs with `ktab.trailer.enabled=true`. If it is off, every trailer path returns 404.
- **Who can use it:** `ADMIN`, `AUTHOR`, `LIBRARIAN`, `ADMIN_LIBRARIAN` for everything below, except section 3.9 (`GET /books/{bookId}/trailer`), which readers can also call. Any other role gets 403.

---

## 1. How it works

A trailer is made in the background and is not instant. The frontend never waits on one request.

1. The user presses **Create trailer**: `POST /trailers/books/{bookId}`. The backend answers right away with status `QUEUED`.
2. The frontend **polls** `GET /trailers/{id}` until the status is final.
3. When the status is `READY`, call `GET /trailers/{id}/download` for the video links.

The user also gets an email when the trailer finishes. Do not rely on it for the UI; poll.

### Statuses

| Status | Meaning | Final? | What the UI shows |
|---|---|---|---|
| `QUEUED` | Waiting to start | no | "In the queue" + Cancel button |
| `RUNNING` | The agent is writing and generating the video | no | Progress state (no cancel for non-admins) |
| `HARVESTING` | Collecting and checking the output files | no | Progress state (no cancel for non-admins) |
| `READY` | Done and downloadable | yes | Player + download buttons |
| `NEEDS_REVIEW` | Finished but held for approval by the book's author, an admin librarian or an admin | yes (for now) | Reviewers: preview + Approve/Reject. Plain librarians: "Under review" |
| `FAILED` | Failed, or rejected in review | yes | Error message; admins get Retry |
| `CANCELLED` | Cancelled by the user | yes | Create a new one |

`QUEUED`, `RUNNING` and `HARVESTING` are the **active** statuses. A book can have **only one active trailer** at a time.

`NEEDS_REVIEW` can still change: approving moves it to `READY`, rejecting moves it to `FAILED`, and an admin's retry moves it to `QUEUED`.

---

## 2. Response format

Every response uses the standard envelope:

```json
{
  "success": true,
  "statusCode": 202,
  "status": "ACCEPTED",
  "messageStatus": "SUCCESS",
  "message": "تم بدء إنتاج الإعلان السينمائي للكتاب. ...",
  "correlationId": "…",
  "timestamp": "2026-10-07T08:53:10.886Z",
  "data": { }
}
```

`message` is a ready-to-show Arabic string. Read the payload from `data`.

### `TrailerView` (the trailer object)

```json
{
  "id": 12,
  "bookId": 345,
  "status": "RUNNING",
  "outcomeResult": null,
  "error": null,
  "higgsfieldGenerations": 3,
  "startedAt": "2026-10-07T08:40:00Z",
  "finishedAt": null,
  "notifyByEmail": false
}
```

| Field | Type | Notes |
|---|---|---|
| `id` | number | Trailer id. Use it for every call except create and list |
| `bookId` | number | |
| `status` | string | See the table above |
| `outcomeResult` | string \| null | The agent's own quality verdict. Mostly for admins; do not show to end users |
| `error` | string \| null | Set when the status is `FAILED`. Technical text, so do not show it raw to authors (see section 5) |
| `higgsfieldGenerations` | number | How many video clips were generated so far. Not a percentage |
| `startedAt` / `finishedAt` | ISO-8601 \| null | `finishedAt` is null while the trailer is active |
| `notifyByEmail` | boolean | `true` only in the create response (the user will be emailed). Always `false` on reads, so ignore it there |

There is **no progress percentage**. Show an indeterminate progress state and, if you like, the time since `startedAt`.

---

## 3. Endpoints

### 3.1 Create a trailer

`POST /trailers/books/{bookId}` → **202 Accepted**

No body. Returns a `TrailerView` with `status: "QUEUED"` and `notifyByEmail: true`.

| Error | Status | When |
|---|---|---|
| `trailer.already.running` | 409 | The book already has a `QUEUED`, `RUNNING` or `HARVESTING` trailer |
| `trailer.limit.reached` | 400 | The book already has 3 trailers in the last 30 days (failed and cancelled ones don't count). **Admins are exempt** |
| `trailer.not.found` | 404 | The book doesn't exist or the user may not use it (see "Who sees what") |
| 400 | 400 | Librarian is not linked to an organization |

### 3.2 List a book's trailers

`GET /trailers/books/{bookId}` → **200**

Returns `TrailerView[]`, newest first. Use it to render the trailer section of a book page and to find the active one on load.

### 3.3 Get one trailer (polling)

`GET /trailers/{id}` → **200**

Returns a `TrailerView`. **This is the endpoint to poll.**

### 3.4 Get download links

`GET /trailers/{id}/download` → **200**

```json
{
  "data": {
    "video": "https://…signed-url…",
    "videoClean": "https://…signed-url…",
    "captions": "https://…signed-url…"
  }
}
```

| Key | What it is | Always present? |
|---|---|---|
| `video` | The MP4 with Arabic captions burned in | yes |
| `videoClean` | The MP4 without captions | no, only if it exists |
| `captions` | An `.srt` subtitle file (Arabic) | no, only if it exists |

- Links are **pre-signed and expire**. Call this endpoint when the user opens the player or presses download. Do not store the links.
- Only works when the status is `READY`. In `NEEDS_REVIEW` it also works for the people who can review (admin, the book's author, admin librarian of the book's organization), so they can preview before deciding.
- Otherwise: **409** `trailer.not.ready`.

### 3.5 Cancel

`POST /trailers/{id}/cancel` → **200**, no data

Only a trailer that is still **`QUEUED`** can be cancelled. Once it is `RUNNING` or `HARVESTING` the generation has started and costs money, so it can no longer be stopped: **409** `trailer.cancel.not.allowed`. Admins are the exception and can also cancel `RUNNING` and `HARVESTING`. Any non-active status (`READY`, `NEEDS_REVIEW`, `FAILED`, `CANCELLED`) returns **409** `trailer.not.ready`. After cancelling, the status is `CANCELLED` and a new trailer can be created.

Show the Cancel button only when `status === "QUEUED"` (or for admins while active), and hide it as soon as polling shows `RUNNING`.

### 3.6 Review (admin, the book's author, or an admin librarian)

`POST /trailers/{id}/review?approve=true|false` → **200**, returns a `TrailerView`

Only when the status is `NEEDS_REVIEW`, otherwise **409**.

Who may call it: `ADMIN` (any book), `AUTHOR` (only their own books) and `ADMIN_LIBRARIAN` (only books of their own organization). A plain `LIBRARIAN` gets **403**. An author or admin librarian calling it for a book that is not theirs gets **404**, the same as a missing trailer.
- `approve=true` → `READY`
- `approve=false` → `FAILED`

The author is notified by email on the result. A second path does the same: `POST /admin/trailer-agent/trailers/{id}/review?approve=…`. Use either one.

### 3.7 Retry (admin only)

`POST /trailers/{id}/retry` → **202**, returns a `TrailerView` with `status: "QUEUED"`

Only for `FAILED` or `NEEDS_REVIEW`, otherwise **409** `trailer.not.ready`. Also **409** `trailer.already.running` if the book already has an active trailer. The old error and results are cleared.

### 3.8 Admin: connect Higgsfield (one-time setup, admin only)

`POST /admin/trailer-agent/higgsfield/connect` → **200**

Returns a map with the Higgsfield authorize URL. Open that URL in the browser. The admin approves, and Higgsfield redirects to the backend callback, which shows a confirmation page. Nothing else is needed from the frontend. Generation depends on this connection, so do it once before the first trailer.

The webhook (`/public/trailer-agent/webhook`) and the OAuth callback are backend-to-backend. **The frontend never calls them.**

---

### 3.9 Reader: show a book's trailer

`GET /books/{bookId}/trailer` → **200**

For the book page that readers see. Allowed for `READER` and for the four staff roles.

```json
{
  "data": {
    "trailerId": 12,
    "video": "https://…signed-url…",
    "videoClean": "https://…signed-url…",
    "captions": "https://…signed-url…",
    "expiresInSeconds": 600
  }
}
```

- It returns the **newest `READY` trailer** of the book. `videoClean` and `captions` are `null` when they don't exist. Use `video` for the player.
- It is **404** when the book isn't published, when it has no finished trailer, or when the book doesn't exist. Treat 404 as "no trailer": hide the trailer section, don't show an error.
- Trailers still under review, failed or cancelled are never returned here.
- The links expire after `expiresInSeconds` (10 minutes). Call this endpoint when the page opens or the user presses play, not earlier, and call it again if the video stops loading.
- There is no status, progress or error in this response. Readers never poll.

---

## 4. Who sees what

| Role | Can create / view / cancel for |
|---|---|
| `ADMIN` | Any book |
| `AUTHOR` | Only their own books |
| `LIBRARIAN`, `ADMIN_LIBRARIAN` | Books of their own organization |

A book the user has no access to returns **404**, the same as a missing one. Handle it as "not found".

`review` is for `ADMIN`, `AUTHOR` (own books) and `ADMIN_LIBRARIAN` (own organization's books); `retry` is `ADMIN` only. Hide those buttons for other roles, and expect 403 if called anyway.

---

## 5. Suggested UI

**Book page, trailer section**
1. On load, call `GET /trailers/books/{bookId}`.
2. If the newest trailer is active, show the progress state and start polling it.
3. If it is `READY`, show the player.
4. Otherwise show a **Create trailer** button, disabled while any trailer is active.

**Polling**
- Call `GET /trailers/{id}` every **10–15 seconds** while the status is `QUEUED`, `RUNNING` or `HARVESTING`.
- Stop on `READY`, `NEEDS_REVIEW`, `FAILED` or `CANCELLED`.
- Stop when the page is closed or hidden, and resume when it is visible again.
- Generation is slow (a video is rendered), so a trailer is not instant. Don't poll faster than every 5 seconds.

**Player**
- Call `/download` when the player opens and use `video` as the `<video>` source, `controls` on.
- Offer `videoClean` and `captions` as extra downloads when they are present.
- If the video link returns 403 or fails to load, the link expired. Call `/download` again.

**Errors**
- Show the response `message` for 4xx errors; it is already user-ready Arabic.
- For a `FAILED` trailer, show a generic "Trailer generation failed, please try again" to authors and librarians. `error` is technical. Show it to admins only.
- On `trailer.limit.reached`, tell the user the book hit its limit of 3 trailers per 30 days.

**Admin extras**
- On `NEEDS_REVIEW`: for reviewers (admin, the book's author, admin librarian) show a preview from `/download`, then **Approve** and **Reject** buttons. Everyone else sees "Under review".
- On `FAILED` or `NEEDS_REVIEW`: show **Retry** to admins only.
- `outcomeResult` and `higgsfieldGenerations` can be shown in an admin details panel.

---

## 6. Quick reference

| Action | Call | Success | Roles |
|---|---|---|---|
| Create | `POST /trailers/books/{bookId}` | 202 | all four |
| List for a book | `GET /trailers/books/{bookId}` | 200 | all four |
| Get / poll | `GET /trailers/{id}` | 200 | all four |
| Download links | `GET /trailers/{id}/download` | 200 | all four when `READY`; reviewers also in `NEEDS_REVIEW` |
| Cancel | `POST /trailers/{id}/cancel` | 200 | all four while `QUEUED`; admin also while `RUNNING`/`HARVESTING` |
| Review | `POST /trailers/{id}/review?approve=` | 200 | admin, author (own books), admin librarian (own organization) |
| Retry | `POST /trailers/{id}/retry` | 202 | admin |
| Connect Higgsfield | `POST /admin/trailer-agent/higgsfield/connect` | 200 | admin |
| Reader: latest trailer | `GET /books/{bookId}/trailer` | 200 (404 = none) | reader and staff |

### Error codes

| Status | Key | Meaning |
|---|---|---|
| 400 | `trailer.limit.reached` | 3 trailers per book per 30 days used |
| 403 | — | Role not allowed (for example, a plain librarian calling review, or a non-admin calling retry) |
| 404 | `trailer.not.found` | Missing trailer or book, or no access |
| 409 | `trailer.already.running` | The book already has an active trailer |
| 409 | `trailer.not.ready` | The action isn't allowed in the trailer's current status |
| 409 | `trailer.cancel.not.allowed` | Cancel attempted after the trailer left the queue (non-admin) |
