# V3 In-App Notifications API

Status date: 2026-09-05

Status: implemented and locally regression-tested under the `v3` Spring
profile. This slice uses the existing central `notifications` table; it does
not require a database migration.

## Runtime And Authorization

- Local base URL: `http://localhost:8082`
- Authentication: `Authorization: Bearer <accessToken>`
- Allowed roles: active `principal` and active `teacher`
- The authenticated user must be associated with a school.
- Every read/update query derives `recipient_user_id` from the token. A client
  cannot request or mark another user's notifications.
- Request and response timestamps use ISO-8601 UTC values.

## Endpoint Matrix

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v3/notifications?unreadOnly=false&limit=20` | Return the authenticated user's newest notifications and current unread count. |
| `GET` | `/api/v3/notifications/unread-count` | Return only the authenticated user's unread count. |
| `POST` | `/api/v3/notifications/{notificationId}/read` | Mark one owned notification read. Repeating the request is idempotent. |
| `POST` | `/api/v3/notifications/read-all` | Mark all unread notifications owned by the authenticated user read. |

`limit` defaults to `20` and must be from `1` through `100`. The POST
endpoints have no request body.

## List Response

```json
{
  "success": true,
  "message": "Notifications retrieved successfully.",
  "data": {
    "unreadCount": 2,
    "notifications": [
      {
        "notificationId": 41,
        "notificationUuid": "e1019f8b-29b8-43ad-b453-b13f49247be8",
        "notificationType": "class_assignment_created",
        "title": "New class assignment",
        "message": "You were assigned to Grade 7 - Rizal for English (2025-2026).",
        "referenceType": "class_assignments",
        "referenceId": "910016",
        "isRead": false,
        "createdAt": "2026-09-05T01:00:00Z",
        "readAt": null
      }
    ]
  },
  "errors": null,
  "timestamp": "2026-09-05T01:00:01Z"
}
```

## Unread And Mark-Read Response

All three count/read operations return the same data shape:

```json
{
  "success": true,
  "message": "Notification marked as read.",
  "data": {
    "unreadCount": 1
  },
  "errors": null,
  "timestamp": "2026-09-05T01:01:00Z"
}
```

The success message differs by endpoint:

- `Unread notification count retrieved successfully.`
- `Notification marked as read.`
- `All notifications marked as read.`

## Error Examples

Invalid limit, HTTP `400`:

```json
{
  "success": false,
  "message": "Notification limit must be between 1 and 100.",
  "data": null,
  "errors": {
    "code": "INVALID_NOTIFICATION_LIMIT"
  },
  "timestamp": "2026-09-05T01:02:00Z"
}
```

Missing or non-owned notification, HTTP `404`:

```json
{
  "success": false,
  "message": "Notification was not found for the authenticated user.",
  "data": null,
  "errors": {
    "code": "NOTIFICATION_NOT_FOUND"
  },
  "timestamp": "2026-09-05T01:02:00Z"
}
```

Missing/invalid authentication returns HTTP `401`. An inactive account,
unsupported role, or account without a school returns HTTP `403`.

## Implemented Event Types

| `notificationType` | Recipient | Created when | Reference |
| --- | --- | --- | --- |
| `teacher_pending_approval` | Active principals in the applicant's school | Teacher email verification succeeds and status becomes `pending_approval` | `users`, teacher user ID |
| `teacher_account_approved` | Teacher | Principal approves the teacher | `users`, teacher user ID |
| `teacher_account_rejected` | Teacher | Principal rejects the teacher | `users`, teacher user ID |
| `class_assignment_created` | Teacher | Principal creates the assignment | `class_assignments`, assignment ID |
| `class_assignment_archived` | Teacher | Principal archives the assignment | `class_assignments`, assignment ID |
| `class_assignment_reactivated` | Teacher | Principal reactivates the assignment | `class_assignments`, assignment ID |

Event insertion participates in the same database transaction as its source
operation. The unique constraint on `(recipient_user_id, event_key)` and an
upsert-style insert prevent a retried event from creating a duplicate.

## Frontend Handoff

1. Use only `VITE_V3_API_URL` and `/api/v3/notifications/*`.
2. Add the Bearer token to every request.
3. Load `/unread-count` for the bell badge, then load the list when the panel
   opens. A modest poll interval or explicit refresh is sufficient for now.
4. Use `notificationType` for the icon/label and `referenceType` plus
   `referenceId` for navigation. Do not infer database IDs from message text.
5. After either mark-read request, replace the badge with the returned
   `unreadCount`.
6. Treat `404` from mark-read as missing/non-owned and remove stale local data.

## Deliberate Limits

- This is persistent in-app notification delivery only. It does not send SMS,
  push, or notification emails.
- Sync completion/failure and intervention events are not emitted because the
  corresponding V3 runtime slices are not implemented yet.
- A rejected teacher cannot authenticate as an active user. The rejection
  record is retained, but that user cannot open the protected in-app list.
  Login status remains authoritative until a future out-of-band rejection
  email/SMS contract is approved.
