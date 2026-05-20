# Skill: Create Full CRUD Module

## Purpose
Use this skill when asked to create a new entity with full CRUD operations in this project.

## Pre-Conditions
- GlobalExceptionHandler and custom exceptions already exist
- AuditService already exists
- SecurityConfig already in place

## Steps to Follow

### 1. Entity Class (`entity/XxxEntity.java`)
- Annotate with `@Entity`, `@Table(name = "xxx")`
- Use Lombok: `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;`
- Use `@Enumerated(EnumType.STRING)` for all enum fields
- Add `@CreationTimestamp private LocalDateTime createdAt;`
- Add `@UpdateTimestamp private LocalDateTime updatedAt;`
- If soft-deletable: add `private LocalDateTime deletedAt;`
- For concurrency: add `@Version private Long version;`

### 2. DTOs (`dto/request/` and `dto/response/`)
- `CreateXxxRequest` — with `@NotBlank`, `@NotNull`, `@Valid` annotations
- `UpdateXxxRequest` — all fields optional (nullable), validated if present
- `XxxResponse` — clean output, no sensitive fields, no JPA entity references

### 3. Repository (`repository/XxxRepository.java`)
- Extends `JpaRepository<XxxEntity, Long>`
- Add custom queries for soft-delete: `findAllByDeletedAtIsNull()`, `findAllByDeletedAtIsNotNull()`
- Use `@Query` annotation for complex queries

### 4. Service (`service/XxxService.java`)
- Annotate class with `@Service @RequiredArgsConstructor @Slf4j`
- Inject repository and AuditService via constructor (Lombok handles it)
- All write methods: `@Transactional`
- `findById` throws `ResourceNotFoundException` if missing
- Log important operations with `log.info()`
- Write to AuditLog on every state change

### 5. Controller (`controller/XxxController.java`)
- `@RestController @RequestMapping("/xxx") @RequiredArgsConstructor`
- Every method returns `ResponseEntity<?>`
- Use `@Valid` on all `@RequestBody`
- Inject only the service — no repository in controller
- Follow the README.md API table exactly for endpoint paths and HTTP methods

## Checklist Before Finishing
- [ ] Entity compiled and table created in DB
- [ ] All endpoints return correct HTTP status codes
- [ ] Invalid input returns 400 with error message
- [ ] Not found returns 404 with error message
- [ ] At least one unit test for the service
- [ ] AuditLog written for create/update/delete
