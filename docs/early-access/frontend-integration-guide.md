# Early Access Signup — Frontend Integration Guide

How the frontend lets a visitor ask for early access. One public endpoint, three kinds of visitor: **readers**, **authors** and **admin librarians**.

- **Base URL:** `/api/v1` (all paths below are relative to it)
- **Auth:** none. The endpoint is public: no login, no token, no cookie.
- **Not an account.** A signup is only a request. It has no password and cannot be used to log in. An admin grants early access later, and the signup becomes a real user when it goes live.

---

## 1. The flow

1. The visitor scans a **QR code**. Each QR code (or link) points to the form for **one role**.
2. The frontend opens the form for that role and collects the fields in section 3.
3. The frontend sends `POST /public/early-access` with that role.
4. On `201`, show the success message and a confirmation screen.

The QR codes themselves are made outside the backend. The backend only needs the page the QR code opens to know which role it is for.

### Suggested pages, one per role

| Page (suggested) | Role to send | Extra section |
|---|---|---|
| `/early-access/reader` | `READER` | none |
| `/early-access/author` | `AUTHOR` | none |
| `/early-access/admin-librarian` | `ADMIN_LIBRARIAN` | **library organization** (required) |

The page decides the role. **Do not** let the visitor pick their role in the form, and do not accept it from the URL without checking it is one of these three.

---

## 2. The endpoint

`POST /public/early-access` → **201 Created**

Headers: `Content-Type: application/json`

### Request body

```json
{
  "email": "sami@example.com",
  "fullName": "Sami Ali",
  "role": "READER",
  "phoneNumber": "+961 70 123 456",
  "gender": "MALE",
  "plan": "premium"
}
```

For an admin librarian add the `organization` block:

```json
{
  "email": "lina@example.com",
  "fullName": "Lina Haddad",
  "role": "ADMIN_LIBRARIAN",
  "phoneNumber": "+961 70 123 456",
  "gender": "FEMALE",
  "organization": {
    "name": "Beirut Public Library",
    "description": "A city library with a children's section.",
    "city": "Beirut",
    "country": "Lebanon",
    "address": "Hamra Street 12",
    "website": "https://library.example.org",
    "email": "info@library.example.org",
    "phone": "+961 1 234 567"
  }
}
```

### Response (201)

```json
{
  "success": true,
  "statusCode": 201,
  "status": "CREATED",
  "messageStatus": "SUCCESS",
  "message": "تم تسجيلك في قائمة الوصول المبكر. سنتواصل معك قريبًا.",
  "data": {
    "email": "sami@example.com",
    "fullName": "Sami Ali",
    "role": "READER",
    "organizationName": null,
    "plan": "premium",
    "earlyAccess": false
  }
}
```

`message` is ready-to-show Arabic text. `earlyAccess` is always `false` here: the visitor asks, an admin grants.

---

## 3. Fields

### Person (every role)

| Field | Required | Rules |
|---|---|---|
| `email` | yes | A valid email, up to 255 characters. Stored lower-case. **One signup per email**, whatever the capitalization |
| `fullName` | yes | Not blank, up to 200 characters |
| `role` | yes | Exactly `READER`, `AUTHOR` or `ADMIN_LIBRARIAN`. Anything else, including `ADMIN`, `LIBRARIAN` and `PUBLISHER`, is rejected |
| `phoneNumber` | no | 6 to 31 characters: digits, spaces, `( )`, `-`, with an optional leading `+`. Example `+961 70 123 456` |
| `gender` | no | `MALE` or `FEMALE` |
| `plan` | no | A plan key: letters, digits, `_` and `-`, up to 50 characters. Leave it out if the form has no plan choice |

### Library organization (only for `ADMIN_LIBRARIAN`)

The block must be present for an admin librarian, and **must be left out** for readers and authors (sending it with another role is a 400). The limits match the library organization record the signup will be turned into, so what you accept here can be stored there.

| Field | Required | Rules |
|---|---|---|
| `organization.name` | yes | Not blank, up to 255 characters |
| `organization.description` | no | Up to 5000 characters |
| `organization.city` | no | Up to 100 characters |
| `organization.country` | no | Up to 100 characters |
| `organization.address` | no | Up to 255 characters |
| `organization.website` | no | Must start with `http://` or `https://`, no spaces, up to 255 characters |
| `organization.email` | no | A valid email, up to 255 characters. The organization's contact email, not the person's |
| `organization.phone` | no | Same format as `phoneNumber` |

Do **not** send `earlyAccess`, a password, an id or an organization slug. The backend ignores or rejects anything that isn't listed here.

---

## 4. Errors

All errors use the same envelope as the rest of the API, with `success: false`.

| Status | When | What to show |
|---|---|---|
| **400** | A field is missing or invalid, or the role/organization combination is wrong | The `message` (the first problem, in Arabic), and the per-field texts from `errors` |
| **409** | This email is already registered | `message`: "this email is already registered in the early access list". Treat it as "you're already on the list", not as a failure of the form |
| **429** | Too many requests from this device | Ask the visitor to wait. See the `Retry-After` header |
| **500** | Unexpected server error | A generic "try again later" |

### 400 body

```json
{
  "success": false,
  "statusCode": 400,
  "messageStatus": "ERROR",
  "message": "رقم الهاتف غير صحيح.",
  "errors": {
    "phoneNumber": "رقم الهاتف غير صحيح.",
    "organization.name": "اسم المؤسسة المكتبية مطلوب ولا يمكن أن يكون فارغًا."
  }
}
```

`errors` maps a field name to its Arabic message, so you can show each message under its field. Nested fields use a dotted name (`organization.name`). Two checks are about the whole request, not one field, and come back under their own names; show those as a general form error:

| `errors` key | Meaning |
|---|---|
| `roleAllowed` | The role is not one of the three allowed |
| `organizationConsistent` | An admin librarian sent no organization, or another role sent one |

If the body cannot be read at all (for example an unknown `role` or `gender` value, or broken JSON) the answer is a **400** with only the generic message "there is an error in one of the entered fields" and no `errors` map.

### 429

The signup is held to the strict limit also used by login and registration: **10 requests per minute per IP address**. The response carries `Retry-After` (seconds) and `X-RateLimit-*` headers. Disable the submit button while sending so a double click does not use two requests, and show a "try again in a moment" message on 429.

---

## 5. Form behavior

- **Trim** the text fields before sending. The backend trims too, but the length rules are checked on what you send.
- **Validate on the client** with the rules in section 3 so most mistakes never reach the server. The server stays the source of truth.
- **Show the 409 message** and keep the visitor on a friendly confirmation: the email is already on the list.
- **Do not reveal more than the message says.** The response intentionally has no id and no other visitor's data.
- **Admin librarian form:** show the organization section with a heading such as "your library organization", and make the name field required.
- The page for readers and authors must not render the organization section at all.

---

## 6. What this does not do (yet)

- No endpoint to list signups, check a signup's status, or grant early access. Those are admin tasks and are not built.
- No confirmation email.
- No QR code generation. The QR code only has to open the correct role page.

---

## 7. Quick reference

| | |
|---|---|
| Endpoint | `POST /api/v1/public/early-access` |
| Login needed | No |
| Success | `201` |
| Duplicate email | `409` |
| Bad input | `400` (with `errors` per field) |
| Rate limit | 10 requests per minute per IP, then `429` |
| Roles | `READER`, `AUTHOR`, `ADMIN_LIBRARIAN` |
| Organization block | required for `ADMIN_LIBRARIAN`, forbidden for the others |
