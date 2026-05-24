# IssueFlow — Prompt History & Technical Decisions

## Model Used

**claude-sonnet-4-5** via Claude Code (VSCode extension)

---

## Session Summaries

### Session 1 — Foundation & Infrastructure

**What was built:**
- Added security and JWT dependencies to `pom.xml` (`spring-boot-starter-security`, `jjwt-api/impl/jackson 0.12.3`)
- Created `application.yaml` with PostgreSQL connection, JPA settings, JWT config, and multipart upload limits
- Created all four custom exception classes: `ResourceNotFoundException`, `ConflictException`, `ValidationException`, `ForbiddenException`
- Created `ErrorResponse` DTO (status, error, message, timestamp, path)
- Created `GlobalExceptionHandler` with `@RestControllerAdvice` handling all exceptions uniformly
- Created a placeholder `SecurityConfig` permitting all requests
- Verified `./mvnw clean package -DskipTests` succeeds

**Key decision:** Use a placeholder permissive `SecurityConfig` in Session 1 so the app compiles and starts before JWT is wired up.

---

### Session 2 — All Entities

**What was built:**
- All nine JPA entity classes: `User`, `Project`, `Ticket`, `Comment`, `AuditLog`, `TokenBlacklist`, `TicketDependency`, `Attachment`, `CommentMention`
- All enum types: `UserRole`, `TicketStatus`, `TicketPriority`, `TicketType`, `AuditActorType`
- `Ticket` and `Comment` got `@Version Long version` for optimistic locking
- Soft-delete columns (`deletedAt`) on `Project` and `Ticket`
- All timestamps via `@CreationTimestamp` / `@UpdateTimestamp`
- Verified all tables created in PostgreSQL on startup

**Key decision:** Enums stored as `EnumType.STRING` everywhere, never `ORDINAL`, so DB values are human-readable and migration-safe.

---

### Session 3 — User CRUD + Authentication

**What was built:**
- `UserRepository`, `UserService`, `UserController` — full CRUD for `/users`
- Password hashing with `BCryptPasswordEncoder` in `createUser`
- `JwtUtil` — generates tokens with UUID JTI, validates, extracts claims
- `JwtAuthenticationFilter` — reads `Authorization: Bearer` header, validates token, checks blacklist, sets `SecurityContext`
- `CustomUserDetailsService` — loads user by username for Spring Security
- `AuthController` — `POST /auth/login`, `POST /auth/logout` (blacklists JTI), `GET /auth/me`
- `SecurityConfig` — stateless JWT setup; only `POST /auth/login` and `POST /users` are public

**Key decision:** Token blacklist stores JTI + expiry in a DB table (`token_blacklist`) rather than an in-memory set, so the blacklist survives application restarts and works across multiple instances.

---

### Session 4 — Project CRUD

**What was built:**
- `ProjectRepository` with `findAllByDeletedAtIsNull()` and `findAllByDeletedAtIsNotNull()`
- `ProjectService` — create, get, update, soft-delete, restore, list active, list deleted
- `restoreProject` is ADMIN-only: throws `ForbiddenException` if caller is not ADMIN
- `getProjectById` throws 404 if `deletedAt` is set
- `ProjectController` with all endpoints per the README API table
- Unit tests for `ProjectService` covering soft-delete and restore logic

**Key decision:** Restore checks role at the service layer, not with Spring Security annotations, so the logic is visible and testable without full security context.

---

### Session 5 — Ticket Core

**What was built:**
- `TicketRepository` with filtered queries for active/deleted tickets
- `TicketService` with full business logic:
  - Status machine: only forward transitions (TODO→IN_PROGRESS→IN_REVIEW→DONE), backward throws `ValidationException`
  - DONE tickets cannot be updated — throws `ValidationException`
  - `OptimisticLockingFailureException` caught and rethrown as `ConflictException`
  - Auto-assignment on create: picks the DEVELOPER with the fewest open tickets in the project; tie-breaking by lowest user ID
  - Manual priority change clears the `isOverdue` flag
  - Audit log written on every state change
- `TicketController`, `CreateTicketRequest`, `UpdateTicketRequest`, `TicketResponse`
- Comprehensive unit tests for the status machine, auto-assignment, and soft-delete

**Key decision:** Status machine logic is a simple `switch` on current status rather than a strategy pattern — the transition table is small and stable, so the extra abstraction would be premature.

---

### Session 6 — Comments + Mentions

**What was built:**
- `CommentRepository`, `CommentMentionRepository`
- `CommentService`:
  - Parses `@username` mentions from comment content with regex
  - Validates each mentioned username exists (warns but doesn't fail on unknown)
  - Saves `CommentMention` records; re-evaluates on update (adds new, removes stale)
  - Handles `OptimisticLockingFailureException` → `ConflictException`
  - Writes audit log on add, update, delete
- `CommentController` for `/tickets/:id/comments`
- `GET /users/:userId/mentions` with pagination (page + pageSize query params), newest first
- Unit tests for mention parsing (case-insensitive, multiple mentions, update scenario)

**Key decision:** Mention parsing uses a simple regex (`@\\w+`) extracted at the service layer rather than a dedicated parser, keeping the footprint small. Unknown usernames are silently skipped rather than erroring, to allow mentioning future users.

---

### Session 7 — Audit Log + Ticket Dependencies

**What was built:**
- `AuditLogRepository` with optional filter queries by `entityType`, `entityId`, `actorId`, `action`
- `AuditLogController` — `GET /audit-logs` with optional query params
- `AuditLogResponse` DTO
- `TicketDependencyRepository`, `TicketDependencyService`:
  - `addDependency`: validates both tickets exist, belong to the same project, and that adding the dependency would not create a cycle (DFS check)
  - `hasPendingBlockers`: returns true if any blocker ticket is not DONE
  - Writes audit log on add/remove
- `TicketDependencyController` — `POST/GET/DELETE /tickets/:id/dependencies`
- `TicketService.updateTicket` extended: blocks transition to DONE if any blocker is not DONE

**Key decision:** Circular dependency detection uses DFS at the service layer on each add. The cycle check is bounded by ticket count within a project, which is acceptable given expected data sizes.

---

### Session 8 — Attachments + CSV Export/Import

**What was built:**
- `AttachmentService` — stores files to local disk under `/tmp/issueflow-attachments/`; validates MIME type (PNG, JPEG, PDF, plain text) and size (≤ 10 MB); writes audit log
- `AttachmentController` — `POST/GET/DELETE /tickets/:id/attachments`
- `TicketExportImportService` using Apache Commons CSV:
  - Export: writes all non-deleted tickets for a project to a CSV (id, title, description, status, priority, type, assigneeId)
  - Import: parses CSV, validates each row, creates tickets, returns `{created, failed, errors}` per row
- `GET /tickets/export?projectId=X` returns CSV file with proper `Content-Disposition` header
- `POST /tickets/import` accepts `multipart/form-data`

**Key decision:** Attachment files are stored on the local filesystem under `/tmp/` rather than a blob store to keep infrastructure requirements minimal (no S3/GCS dependency). The storage path is saved in the DB, making it easy to swap to a cloud store later.

---

### Session 9 — Auto-Escalation Scheduler + Workload API

**What was built:**
- `@EnableScheduling` on `IssueFlowApplication`
- `EscalationScheduler` — runs every 60 seconds:
  - Finds all non-DONE, non-CRITICAL, non-deleted tickets with `dueDate < now()`
  - Promotes priority one step (LOW→MEDIUM→HIGH→CRITICAL)
  - Sets `isOverdue = true` when priority is already CRITICAL
  - Writes audit log with `actorType = SYSTEM`, `action = ESCALATE`
  - Idempotent — safe to run repeatedly
- `ProjectService.getWorkload` — returns developers sorted by open (non-DONE) ticket count ascending
- `GET /projects/:projectId/workload` endpoint
- `WorkloadResponse` record DTO
- Unit tests for `EscalationScheduler`

**Key decision:** Scheduler runs as a fixed-rate Spring bean rather than a Quartz job to avoid additional infrastructure. The `CRITICAL` cap is an explicit guard rather than relying on the enum ordinal arithmetic, to make the rule visible in code.

---

### Session 10 — Integration Tests + Documentation

**What was built:**
- `AuthFlowIntegrationTest` (`@SpringBootTest` + `MockMvc` + H2): create user → login → get JWT → call `/auth/me` → logout → verify old token returns 401
- `TicketLifecycleIntegrationTest`: create ticket → advance through all four statuses → verify backward transition returns 400 → verify update on DONE ticket returns 400 → verify skipping a status returns 400
- `ProjectSoftDeleteIntegrationTest`: create project → soft delete → verify absent from `GET /projects` → `GET /projects/{id}` returns 404 → restore → verify back in list; also verifies a DEVELOPER cannot restore (403)
- `run.md` with prerequisites, Docker command, build, run, test steps, and example `curl` commands
- `prompts.md` (this file) with model name, per-session summaries, and key decisions

**Key decision:** Integration tests use `@DirtiesContext` per test method to reset the H2 database to a clean state, avoiding test-ordering dependencies at the cost of slightly longer test startup time.

---

## Key Technical Decisions (Cross-Cutting)

| Decision | Rationale |
|---|---|
| Java records for response DTOs | Immutable, compact, no boilerplate — appropriate for read-only response objects |
| Lombok `@Data` for request DTOs | Spring's `@Valid` + Jackson need mutable fields with getters/setters |
| Constructor injection everywhere | Enables immutability, simplifies unit testing, avoids field injection issues |
| `EnumType.STRING` for all enums | Human-readable DB values; adding/renaming enum constants won't corrupt existing rows |
| H2 for test profile | Fast, zero-infrastructure test execution; schema auto-created via `ddl-auto: create-drop` |
| Service-layer role checks | Business rules co-located with business logic, testable without Spring Security context wiring |
| Soft delete via `deletedAt` timestamp | Preserves history, enables restore, avoids cascading FK issues from hard deletes |
