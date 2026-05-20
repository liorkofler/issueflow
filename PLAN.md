# IssueFlow — Execution Plan

## How to Use This Plan
- Each session = one Claude Code conversation
- When a session is done: close the terminal, open a new one
- Start each new session with: "Continue from PLAN.md. Session X is done. Now do Session Y."
- Check off items as you go

---

## Pre-Work (Do This Before Any Claude Code Session)

- [ ] Create a public GitHub repository named `issueflow`
- [ ] Clone the skeleton files into it (pom.xml, compose.yml, mvnw, etc.)
- [ ] Place `CLAUDE.md` in the root of the repo
- [ ] Place `.claude/skills/` folder in the root of the repo
- [ ] Open the repo folder in VSCode
- [ ] Run `docker compose up -d` to start PostgreSQL
- [ ] Verify DB is running: `docker ps`

---

## Session 1 — Foundation & Infrastructure

**Goal:** Project compiles, connects to DB, has proper structure

**Prompt to use:**
```
Read CLAUDE.md fully before starting.

Set up the project foundation:
1. Add spring-boot-starter-security, jjwt-api, jjwt-impl, jjwt-jackson (0.12.x) to pom.xml
2. Create application.properties with:
   - PostgreSQL connection (host: localhost, db: issueflow, user: issueflow, pass: issueflow)
   - JPA: ddl-auto=update, show-sql=false, dialect=PostgreSQL
   - JWT secret and expiration (3600 seconds)
   - File upload max size: 10MB
3. Create the GlobalExceptionHandler with @RestControllerAdvice handling:
   ResourceNotFoundException (404), ConflictException (409), ValidationException (400),
   MethodArgumentNotValidException (400), ConstraintViolationException (400)
4. Create the ErrorResponse DTO (status, error, message, timestamp, path)
5. Create all custom exception classes
6. Create a placeholder SecurityConfig that permits all requests (we'll secure it in Session 3)
7. Verify: ./mvnw clean package -DskipTests succeeds
```

**Done when:** `./mvnw clean package -DskipTests` succeeds with no errors.

---

## Session 2 — All Entities

**Goal:** All database tables created via JPA, relationships correct

**Prompt to use:**
```
Read CLAUDE.md fully before starting.
Session 1 is complete: project compiles, GlobalExceptionHandler exists, exceptions exist.

Create ALL JPA entities (no service/controller yet, just entities):

1. User: id, username (unique), email (unique), passwordHash, fullName, role (enum: ADMIN/DEVELOPER), createdAt, updatedAt
2. Project: id, name, description, owner (ManyToOne → User), deletedAt, createdAt, updatedAt
3. Ticket: id, title, description, status (enum), priority (enum), type (enum), project (ManyToOne), assignee (ManyToOne → User, nullable), dueDate, isOverdue, version (@Version), deletedAt, createdAt, updatedAt
4. Comment: id, content, ticket (ManyToOne), author (ManyToOne → User), version (@Version), createdAt, updatedAt
5. AuditLog: id, entityType, entityId, action, actorId, actorType (USER/SYSTEM), details, createdAt
6. TokenBlacklist: id, jti (unique), expiresAt, createdAt
7. TicketDependency: id, ticket (ManyToOne), blockedBy (ManyToOne → Ticket), createdAt
8. Attachment: id, ticket (ManyToOne), filename, storagePath, contentType, fileSize, createdAt
9. CommentMention: id, comment (ManyToOne), mentionedUser (ManyToOne → User), createdAt

Create all enum classes:
- UserRole: ADMIN, DEVELOPER
- TicketStatus: TODO, IN_PROGRESS, IN_REVIEW, DONE
- TicketPriority: LOW, MEDIUM, HIGH, CRITICAL
- TicketType: BUG, FEATURE, TECHNICAL
- AuditActorType: USER, SYSTEM

Run ./mvnw spring-boot:run and verify all tables are created in PostgreSQL.
```

**Done when:** App starts, all tables visible in DB.

---

## Session 3 — User CRUD + Authentication

**Goal:** `/users` and `/auth` endpoints fully working

**Prompt to use:**
```
Read CLAUDE.md fully before starting.
Sessions 1-2 complete. All entities exist, project starts cleanly.

Implement User management and JWT Authentication:

PART A — User CRUD:
- UserRepository (JpaRepository<User, Long>)
- UserService: createUser, getUserById, updateUser, deleteUser, getAllUsers
  - createUser must hash the password with BCryptPasswordEncoder before saving
  - deleteUser is a hard delete (users are not soft-deleted per requirements)
- UserController: follow README.md API table exactly for /users endpoints
- DTOs: CreateUserRequest, UpdateUserRequest, UserResponse

PART B — JWT Authentication:
- JwtUtil: generateToken(username), validateToken(token), extractUsername(token), extractJti(token)
- JwtAuthenticationFilter: extract Bearer token from header, validate, set SecurityContext
- CustomUserDetailsService: loads User by username for Spring Security
- AuthController:
  - POST /auth/login → validate credentials, return {accessToken, tokenType, expiresIn}
  - POST /auth/logout → add JTI to TokenBlacklist table
  - GET /auth/me → return current user from SecurityContext
- SecurityConfig: lock down all endpoints, permit only POST /auth/login

Write unit tests for UserService (createUser, getUserById not found, deleteUser).
```

**Done when:** Can login via Postman, get JWT, call GET /users with Bearer token.

---

## Session 4 — Project CRUD

**Goal:** `/projects` endpoints fully working

**Prompt to use:**
```
Read CLAUDE.md fully before starting.
Sessions 1-3 complete. Users and Auth fully working with JWT.

Implement Project management:
- ProjectRepository with custom query: findAllByDeletedAtIsNull(), findAllByDeletedAtIsNotNull()
- ProjectService: createProject, getProjectById, updateProject, softDeleteProject, getAllProjects, getDeletedProjects, restoreProject
  - getProjectById must filter out soft-deleted (throw 404 if deletedAt is set)
  - restoreProject is ADMIN only — throw ForbiddenException if current user is not ADMIN
- ProjectController: follow README.md API table exactly
- DTOs: CreateProjectRequest, UpdateProjectRequest, ProjectResponse

Write unit tests for ProjectService focusing on soft delete and restore logic.
```

---

## Session 5 — Ticket Core (CRUD + Status Machine + Concurrency)

**Goal:** `/tickets` CRUD with all business rules

**Prompt to use:**
```
Read CLAUDE.md fully before starting.
Sessions 1-4 complete. Projects working.

Implement Ticket management — this is the most complex part:

1. TicketRepository: findByProjectIdAndDeletedAtIsNull, findByProjectIdAndDeletedAtIsNotNull, findByIdAndDeletedAtIsNull
2. TicketService:
   - createTicket: validate projectId exists, auto-assign if no assigneeId (see CLAUDE.md logic), write AuditLog
   - getTicketById: 404 if not found or soft-deleted
   - updateTicket: 
     * DONE tickets cannot be updated → ValidationException
     * Status can only move forward (TODO→IN_PROGRESS→IN_REVIEW→DONE) → ValidationException on invalid transition
     * Handle OptimisticLockingFailureException → ConflictException
     * Manual priority change clears isOverdue flag
     * Write AuditLog on every change
   - softDeleteTicket, getAllByProject, getDeletedTickets, restoreTicket
3. TicketController: follow README.md API exactly
4. DTOs: CreateTicketRequest, UpdateTicketRequest, TicketResponse (include isOverdue field)

Write unit tests for:
- Status machine (valid transitions, invalid transitions, update-DONE)
- Auto-assignment (with developers, no developers, tie-breaking)
- Soft delete and restore
```

---

## Session 6 — Comments + Mentions

**Goal:** `/tickets/:id/comments` and `@mention` system

**Prompt to use:**
```
Read CLAUDE.md fully before starting.
Sessions 1-5 complete. Tickets working with all business logic.

Implement Comment management and @Mention system:

1. CommentRepository, CommentMentionRepository
2. CommentService:
   - addComment: parse @username mentions from content, validate each username exists,
     save CommentMention records, write AuditLog
   - getCommentsForTicket: include mentionedUsers in each CommentResponse
   - updateComment: handle OptimisticLockingFailureException → ConflictException,
     re-evaluate mentions (add new, remove stale), write AuditLog
   - deleteComment: write AuditLog
3. CommentController: follow README.md API table
4. UserController: add GET /users/:userId/mentions endpoint (newest first, with pagination)
5. DTOs: AddCommentRequest, UpdateCommentRequest, CommentResponse (with mentionedUsers array)

Write unit tests for mention parsing logic (case-insensitive, multiple mentions, mention on update).
```

---

## Session 7 — Audit Log + Ticket Dependencies

**Goal:** Audit log endpoint + dependency system

**Prompt to use:**
```
Read CLAUDE.md fully before starting.
Sessions 1-6 complete. Comments and mentions working.

PART A — Audit Log API:
- AuditLogRepository with queries: findAll(), findByEntityType, findByEntityId, findByActorId, findByAction
- AuditLogController: GET /audit-logs with optional query params (entityType, entityId, actorId, action)
- AuditLogResponse DTO

PART B — Ticket Dependencies:
- TicketDependencyRepository
- TicketDependencyService:
  - addDependency: both tickets must exist + same project, no circular deps, write AuditLog
  - getDependencies: return all blockers for a ticket
  - removeDependency: write AuditLog
  - (internally used by TicketService) hasPendingBlockers: returns true if any blocker is not DONE
- TicketDependencyController: POST/GET/DELETE /tickets/:id/dependencies
- Update TicketService.updateTicket: if status transition → DONE, check hasPendingBlockers, throw ValidationException if true
```

---

## Session 8 — Extended Features (Attachments + Export/Import)

**Goal:** File uploads, CSV export/import

**Prompt to use:**
```
Read CLAUDE.md fully before starting.
Sessions 1-7 complete. Dependencies and audit log working.

PART A — Attachments:
- Store files on local disk under /tmp/issueflow-attachments/ (or configurable path)
- AttachmentService: uploadAttachment (validate type + size), getAttachments, deleteAttachment
- Allowed types: image/png, image/jpeg, application/pdf, text/plain
- Max size: 10MB (already configured in application.properties)
- AttachmentController: POST/GET/DELETE /tickets/:id/attachments
- Write AuditLog on upload and delete

PART B — CSV Export/Import (commons-csv is already in pom.xml):
- TicketExportImportService:
  - exportToCSV: write tickets to CSV with fields: id, title, description, status, priority, type, assigneeId
  - importFromCSV: parse CSV, validate each row, create tickets, return {created, failed, errors:[]}
    handle commas/quotes inside fields correctly (RFC 4180)
- Add to TicketController:
  - GET /tickets/export?projectId=X → returns CSV file (Content-Type: text/csv)
  - POST /tickets/import → multipart/form-data (file + projectId field)
```

---

## Session 9 — Scheduler (Auto-Escalation) + Workload API

**Goal:** Background job + workload endpoint

**Prompt to use:**
```
Read CLAUDE.md fully before starting.
Sessions 1-8 complete.

PART A — Auto-Escalation Scheduler:
- Enable @EnableScheduling on main application class
- EscalationScheduler: @Scheduled(fixedRate = 60000)
  - Find all tickets where: deletedAt IS NULL, status != DONE, dueDate < now(), priority != CRITICAL
  - Promote priority one level (LOW→MEDIUM→HIGH→CRITICAL)
  - Set isOverdue = true when reaching CRITICAL
  - Write AuditLog for each escalation: actorType=SYSTEM, action=ESCALATE
  - Must be idempotent

PART B — Workload API:
- ProjectService: add getWorkload(projectId) — returns list of {userId, username, openTicketCount}
  sorted by openTicketCount ascending
  openTicketCount = non-DONE tickets assigned to that user in the project
- Add to ProjectController: GET /projects/:projectId/workload
- WorkloadResponse DTO
```

---

## Session 10 — Tests + Documentation

**Goal:** Tests pass, run.md and prompts.md written

**Prompt to use:**
```
Read CLAUDE.md fully before starting.
Sessions 1-9 complete. All features implemented.

PART A — Finalize Tests:
Write integration tests using @SpringBootTest + H2 database for:
1. Full auth flow: register user, login, get JWT, call protected endpoint, logout, verify token invalid
2. Ticket lifecycle: create → assign → progress through statuses → DONE, verify backward transition fails
3. Soft delete: delete project, verify not in list, restore, verify back in list

PART B — Documentation:
1. Create run.md with exact steps: prerequisites, docker compose, build, run, test, example curl commands
2. Create prompts.md with:
   - Model used: claude-sonnet-4-5
   - Summary of each session's prompt and outcome
   - Key decisions made during development

PART C — Final Check:
- ./mvnw clean package runs without errors
- ./mvnw test passes
- Application starts and all endpoints respond correctly
```

---

## Submission Checklist

- [ ] All code committed and pushed to public GitHub repo
- [ ] `CLAUDE.md` in repo root
- [ ] `.claude/skills/` folder in repo
- [ ] `run.md` with exact setup instructions
- [ ] `prompts.md` with model name + prompt history
- [ ] `docker compose up -d` starts DB successfully
- [ ] `./mvnw spring-boot:run` starts app successfully
- [ ] `./mvnw test` passes
- [ ] All API endpoints from README.md are implemented
- [ ] Repo is public and accessible
