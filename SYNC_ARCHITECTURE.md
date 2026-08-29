# Multi-Tenant Sync Architecture

## Overview
The KMP Library Management System (Android, Desktop, Web) is a fully multi-tenant application. Data is fully isolated between different colleges/institutions.

## Canonical Data Structure (Firestore)
To align the Kotlin/Android and Python/Desktop apps without inventing a third structure, we use the root collection name `institutions`, as already implemented in the Desktop app's sync service.

### Root Path
`/institutions/{institutionId}/...`

### Sub-Collections
Under each `institutionId` document, the following sub-collections exist:
- `books`: Stores all book catalog entries (`syncId` as document ID).
- `members`: Stores all patron/student records (`syncId` as document ID).
- `issued_books`: Stores transaction logs for book issues and returns (`syncId` as document ID).
- `reservations`: Stores pending and fulfilled book reservations (`syncId` as document ID).
- `audit_log`: Stores the history of actions performed by staff/admins.
- `settings`: Stores global configurations for the college (e.g., `library_settings` document containing `fineRatePerDay`).

*Note: The metadata for the institution itself (name, invite code, creation date) will be stored directly on the `/institutions/{institutionId}` document.*

## Auth & Institution Resolution Flow
No app will hardcode an `institutionId`. The resolution mechanism is as follows:

1. **Authentication**: User logs in or signs up via Firebase Auth (Email/Password).
2. **User Profile**: A global `users/{uid}` document tracks which institution a user belongs to.
   - Example schema: `{ "email": "admin@college.edu", "institutionId": "uuid-1234", "role": "Owner" }`
3. **Onboarding**:
   - If `users/{uid}` does not have an `institutionId`, the app shows an Onboarding Screen.
   - **Create New**: Generates a new `institutionId`, creates the `/institutions/{institutionId}` document with a 6-digit `inviteCode`, and assigns the user as `Owner`.
   - **Join Existing**: User inputs an `inviteCode`. The app queries `/institutions` for that code, retrieves the `institutionId`, and assigns the user as `Staff`.
4. **Local Session**: Once resolved, the `institutionId` and `role` are saved in the local SQLite (Desktop) or SQLDelight / DataStore (Android) session.
5. **Runtime Queries**: All Firestore queries globally prefix paths with `/institutions/{local_session.institutionId}/`.
