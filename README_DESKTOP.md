# GDC Library50 - Windows Desktop & Director Dashboard

## Architecture Overview
The Windows application is built using **Compose Multiplatform (KMP)**, sharing the core business logic, database schema (SQLDelight), and sync interfaces with the Android application via the `:shared` module.

### Core Modules
- `:shared`: Common Main (Sync Interfaces, DTOs, SQLDelight Schema), Android Main, Desktop Main.
- `:desktopApp`: Windows-specific UI, DPAPI Security Layer, Local SQLite Encryption.
- `:app`: Existing Android Application (Unmodified).

## Features Implemented

### 1. Director Dashboard
- **Real-time Statistics**: Aggregated data for books, members, and circulation.
- **Reporting Center**: 
  - Circulation trends.
  - Member activity heatmaps.
  - Collection health analysis.
  - HEC NDLP Compliance reports.
- **Admin Settings**: Fine configurations, return deadlines, and system audit logs.

### 2. Desktop Mirror
- Full Catalog management.
- Member registration and management.
- Circulation (Issue/Return) hub.
- Reservation queue system.

### 3. Sync & Conflict Resolution
- **Two-way Sync**: Firestore ↔ Local SQLDelight.
- **UUID Mapping**: All entities use `syncId` for global document identification.
- **Soft Deletes**: `deleted` flag preserved for sync consistency.
- **Conflict Table**: Local `sync_conflicts` table for manual resolution of out-of-sync data.

## Security Implementation
- **Windows DPAPI**: Licensing keys and sensitive local tokens are encrypted using Windows Data Protection API, tied to the specific Windows user account.
- **SQLite Encryption**: The local database is encrypted using SQLCipher (via SQLDelight's JDBC driver) to prevent unauthorized access to the library records.
- **Role-Based Access Control (RBAC)**: Distinct permissions for `Librarian` vs `Director` roles.

## Sprint Deliverables (Day 1)
- [x] Windows Compose UI (Catalog, Members, Circulation).
- [x] Director Dashboard (Statistics, Reports).
- [x] Security Layer (DPAPI JNA Implementation).
- [x] Sync Architecture (Conflict Resolution Logic).
- [x] Shared Sync Interfaces for Mobile Integration.

---
*Developed for Government Degree College (GDC) Library Management.*
