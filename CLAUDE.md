# IssueFlow — Claude Code Agent Instructions

## Project Identity
- **Name:** IssueFlow – Ticket Management Backend Platform
- **Assignment:** AT&T TDP 2026 Home Assignment
- **Stack:** Java 21, Spring Boot 3.4.2, PostgreSQL, Maven
- **Model used:** claude-sonnet-4-5 (via Claude Code)

---

## Project Structure (MANDATORY — always follow this)

```
src/main/java/com/att/tdp/issueflow/
├── controller/       ← REST controllers only, no business logic
├── service/          ← All business logic lives here
├── repository/       ← JPA repositories (interfaces only)
├── entity/           ← JPA entity classes
├── dto/
│   ├── request/      ← Incoming request bodies
│   └── response/     ← Outgoing response bodies
├── exception/        ← Custom exceptions + GlobalExceptionHandler
├── security/         ← JWT filter, UserDetailsService, SecurityConfig
├── scheduler/        ← @Scheduled jobs (escalation, etc.)
└── audit/            ← AuditLog entity + AuditService
```

---

## Code Rules (NEVER violate these)

### Java Style
- Use **Lombok** — it's in the pom.xml already. Use `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@RequiredArgsConstructor`
- Use **constructor injection** for services (Lombok `@RequiredArgsConstructor` on service classes)
- All enums stored as `@Enumerated(EnumType.STRING)` — never EnumType.ORDINAL
- DTOs use **Java records** where no mutation needed; use Lombok `@Data` classes for request bodies with `@Valid`
- Use `Optional` properly — always handle the empty case, never call `.get()` without `.isPresent()`

### Controller Rules
- Controllers return `ResponseEntity<?>` always
- No business logic in controllers — delegate everything to service layer
- Use `@Valid` on all `@RequestBody` parameters
- Use `@RestController` + `@RequestMapping` on class level

### Service Rules
- All write operations must be `@Transactional`
- Throw custom exceptions — never return null to indicate "not found"
- Every `findById` that expects a result throws `ResourceNotFoundException` if not found

### Database Rules
- All entities have `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;`
- Soft delete uses `private LocalDateTime deletedAt;` — null means active, non-null means deleted
- Use `@CreationTimestamp` and `@UpdateTimestamp` from Hibernate for audit timestamps
- All relationships use `fetch = FetchType.LAZY` unless explicitly needed otherwise
- Use `@Version private Long version;` on Ticket and Comment entities (optimistic locking)

### Validation Rules
- All request DTOs have Bean Validation annotations (`@NotBlank`, `@NotNull`, `@Email`, `@Size`, etc.)
- Enums validated via custom validator or `@Pattern` — reject invalid values with 400
- Never let invalid data reach the service layer

---

## Exception Handling

### Custom Exceptions to Create
```java
ResourceNotFoundException     // 404 - entity not found
ConflictException             // 409 - concurrent update, duplicate, etc.
ValidationException           // 400 - business rule violation
ForbiddenException            // 403 - not allowed (e.g., ADMIN-only action)
```

### Error Response Format (always return this shape)
```json
{
  "status": 400,
  "error": "Validation failed",
  "message": "Ticket status cannot move backward",
  "timestamp": "2026-01-01T12:00:00Z",
  "path": "/tickets/5"
}
```

---

## Security (JWT)
- JWT secret and expiry in `application.properties` (not hardcoded)
- All endpoints require authentication **except** `POST /auth/login`
- Token blacklist for logout: store invalidated JTIs in a DB table or in-memory Set
- `GET /auth/me` returns the currently authenticated user from SecurityContext
- Use Spring Security's `UsernamePasswordAuthenticationFilter` pattern

---

## Audit Log Rules
- Every state-changing action must write to `audit_logs` table
- Audit entry fields: `id`, `entityType`, `entityId`, `action`, `actorId`, `actorType` (USER/SYSTEM), `details` (JSON string), `createdAt`
- Auto-assignment writes audit with `actorType = SYSTEM`, `action = AUTO_ASSIGN`
- AuditService must be called from within `@Transactional` blocks in services

---

## Business Logic Constraints (CRITICAL)

### Ticket Status Machine
Valid transitions ONLY:
- TODO → IN_PROGRESS
- IN_PROGRESS → IN_REVIEW
- IN_REVIEW → DONE
- Any backward transition → throw `ValidationException` with message "Status cannot move backward"
- Any update on DONE ticket → throw `ValidationException` with message "Cannot update a completed ticket"

### Ticket Concurrency
- Ticket entity has `@Version Long version` field
- On `OptimisticLockingFailureException` → catch and throw `ConflictException("Ticket is being updated by another user")`

### Auto-Assignment Logic
- Only triggered on ticket CREATE when `assigneeId` is null
- Candidates: users with role DEVELOPER (no ADMIN)
- Workload = count of non-DONE tickets assigned to that user within same project
- Tie-breaking: lowest `id` (oldest registered) wins
- If no DEVELOPERs exist in project → leave `assigneeId = null`, no error

### Soft Delete
- `DELETE /tickets/:id` and `DELETE /projects/:id` set `deletedAt = now()`, do NOT remove the row
- All standard GET endpoints filter by `deletedAt IS NULL`
- `GET /tickets/deleted` and `GET /projects/deleted` filter by `deletedAt IS NOT NULL` — ADMIN only
- `POST /tickets/:id/restore` and `POST /projects/:id/restore` set `deletedAt = null` — ADMIN only

### Ticket Dependencies
- Both tickets must belong to same project — else `ValidationException`
- Ticket cannot transition to DONE if any blocker ticket is not DONE
- No circular dependencies (A blocks B blocks A) — validate on add

### Auto-Escalation Scheduler
- Runs every minute (`@Scheduled(fixedRate = 60000)`)
- For each non-DONE, non-CRITICAL ticket where `dueDate < now()`:
  - Promote priority: LOW→MEDIUM, MEDIUM→HIGH, HIGH→CRITICAL
  - If already CRITICAL: set `isOverdue = true`, do not change priority further
- Manual priority change via PATCH clears `isOverdue = false`
- Escalation is idempotent — safe to run repeatedly

---

## API Contract
Follow the API table in README.md exactly. Endpoint paths, request bodies, and response shapes are the contract. Do not invent new fields or change endpoint paths.

---

## Testing Requirements
- Unit tests for: TicketService (status machine, auto-assign, soft delete), CommentService (mention parsing), EscalationScheduler
- Integration tests using H2 in-memory DB (already in pom.xml) for: User CRUD, Auth flow, Ticket lifecycle
- Test class naming: `XxxServiceTest`, `XxxControllerTest`
- Use `@SpringBootTest` + `MockMvc` for controller tests
- Use `@ExtendWith(MockitoExtension.class)` for pure unit tests

---

## What NOT to Do
- Do NOT put `@Autowired` on fields — use constructor injection always
- Do NOT expose JPA entities directly in API responses — always use DTOs
- Do NOT use `System.out.println` — use SLF4J `@Slf4j` logger
- Do NOT skip validation — every user input must be validated
- Do NOT use `FetchType.EAGER` unless you have a specific reason
- Do NOT hardcode credentials or secrets

---

## Build & Run Commands
```bash
# Start database
docker compose up -d

# Build (skip tests for speed)
./mvnw clean package -DskipTests

# Run application
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=TicketServiceTest
```

---

## Dependencies Already in pom.xml
- `spring-boot-starter-data-jpa` ✅
- `spring-boot-starter-web` ✅
- `spring-boot-starter-validation` ✅
- `postgresql` ✅
- `lombok` ✅
- `spring-boot-starter-test` ✅
- `h2` (test scope) ✅
- `commons-csv` ✅

### Still Need to Add to pom.xml
- `spring-boot-starter-security`
- `jjwt-api` + `jjwt-impl` + `jjwt-jackson` (io.jsonwebtoken, version 0.12.x)
