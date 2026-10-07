# Public Cover Images — Frontend Integration Guide

Two public endpoints that return **only book cover image URLs**, for landing pages, sliders and galleries.

- **Base URL:** `/api/v1` (all paths below are relative to it)
- **Auth:** none. Both endpoints are public: no login, no token, no cookie.
- **Which books:** only published, author-uploaded books, the same catalog as `GET /public/books/covers`. A book without a cover image is left out.

| Endpoint | Returns | Message key | Arabic message |
|---|---|---|---|
| `GET /public/books/top-reviewed/covers` | The cover images of the best-reviewed books | `book.top.reviewed.covers.fetched` | تم جلب أغلفة الكتب الأعلى تقييمًا. |
| `GET /public/books/cover-images` | The cover images of all books, page by page | `book.cover.images.fetched` | تم جلب صور أغلفة الكتب. |

Both answer in the standard envelope. The payload is in `data`, and `message` is the Arabic text above, ready to show.

> These return **URLs only**: no book id, title or any other detail. A cover image cannot be linked to its book from these responses. If the page needs to open the book on a click, use `GET /public/books/covers` instead, which returns the id, title and other details along with `coverImageUrl`.

---

## 1. Top reviewed books

`GET /public/books/top-reviewed/covers?limit=10` → **200**

| Query param | Default | Rules |
|---|---|---|
| `limit` | `10` | How many cover images you want. Values below 1 become 1 and values above **50** become 50. The request is never rejected for this |

### Response

```json
{
  "success": true,
  "statusCode": 200,
  "status": "OK",
  "messageStatus": "SUCCESS",
  "message": "تم جلب أغلفة الكتب الأعلى تقييمًا.",
  "data": [
    "https://…signed-url-of-the-best-rated-book-cover…",
    "https://…signed-url…",
    "https://…signed-url…"
  ]
}
```

`data` is a plain array of strings, **best first**.

### How the books are chosen

- Only books with **at least one review**.
- Ordered by **average rating** (highest first), then by **number of reviews** (most first). A book with a 5.0 average from one review ranks below a book with 5.0 from many.
- Books without a cover image are skipped, so you can get **fewer** images than `limit`, and an **empty array** when no book has been reviewed yet. That is a normal answer (`200`), not an error: hide the section or show an empty state.

---

## 2. All book covers

`GET /public/books/cover-images?page=0&size=50` → **200**

| Query param | Default | Rules |
|---|---|---|
| `page` | `0` | Zero-based page number. A negative value becomes 0 |
| `size` | `50` | Books per page. Values below 1 become 1 and values above **200** become 200 |

The list is paged on purpose: a public endpoint should not return the whole catalog in one answer.

### Response

```json
{
  "success": true,
  "statusCode": 200,
  "status": "OK",
  "messageStatus": "SUCCESS",
  "message": "تم جلب صور أغلفة الكتب.",
  "data": {
    "content": [
      "https://…signed-url…",
      "https://…signed-url…"
    ],
    "pageNumber": 0,
    "pageSize": 50,
    "totalElements": 137,
    "totalPages": 3,
    "last": false
  }
}
```

| Field | Meaning |
|---|---|
| `content` | The cover image URLs of this page, **newest books first** |
| `pageNumber` / `pageSize` | The page you got and the page size actually used (after the 200 cap) |
| `totalElements` / `totalPages` | Counted in **books**, not in images |
| `last` | `true` on the final page. Stop requesting when it is true |

### Paging notes

- **A page can hold fewer URLs than `pageSize`.** A book without a cover is skipped, but it still counts in `totalElements` and `totalPages`. Do not assume an empty or short page is the last one: use `last`.
- To load everything (for an infinite gallery), keep requesting `page + 1` until `last` is `true`.
- Ask for the size you will actually show. 50 is a sensible page for a grid; stay well under 200.

---

## 3. About the image links

- The URLs are **signed and expire**. The lifetime is a server setting, **10 hours** in the default configuration (it can differ per environment), so refetch the list when the page is opened again later, and do not store the URLs in long-lived storage.
- A new request returns **new URLs** for the same images, so the browser cannot reuse a cached image from an earlier response. Keep the list you already fetched in memory rather than requesting it again on every render.
- If an image stops loading (HTTP 403 from the image host), the link has expired: fetch the list again.
- Use the URLs directly as `<img src>` values. Give each image a fixed size or aspect ratio so the layout does not jump while they load, and use `loading="lazy"` for long galleries.

---

## 4. Errors

| Status | When | What to show |
|---|---|---|
| **200** with an empty list | Nothing to show yet | An empty state, or hide the section |
| **429** | Too many requests from this device | A "try again in a moment" message. The `Retry-After` header says how many seconds to wait |
| **500** | Unexpected server error | A generic "try again later" |

Bad `limit`, `page` or `size` values never produce a 400; they are corrected as described above. The limit for these endpoints is the general one: **100 requests per minute per IP address**.

---

## 5. Quick reference

| | Top reviewed | All covers |
|---|---|---|
| Endpoint | `GET /api/v1/public/books/top-reviewed/covers` | `GET /api/v1/public/books/cover-images` |
| Params | `limit` (default 10, max 50) | `page` (default 0), `size` (default 50, max 200) |
| `data` | `string[]` | `{ content: string[], pageNumber, pageSize, totalElements, totalPages, last }` |
| Order | Best average rating, then most reviews | Newest book first |
| Login needed | No | No |
| Empty case | `200` with `[]` | `200` with an empty `content` |
