# Skill: Write Tests for This Project

## Test Types Used in This Project

### Unit Tests (`@ExtendWith(MockitoExtension.class)`)
Use for: service layer business logic, pure functions, validators
- Mock all dependencies with `@Mock`
- Test class under test with `@InjectMocks`
- Test happy path + all exception paths
- Naming: `shouldDoX_whenY()`

### Integration Tests (`@SpringBootTest`)
Use for: controller endpoints, full request-response cycle
- Use H2 in-memory DB (already in pom.xml for test scope)
- Use `MockMvc` with `@AutoConfigureMockMvc`
- Test with real HTTP calls: status, response body, DB state

## Test File Location
- Unit: `src/test/java/com/att/tdp/issueflow/service/XxxServiceTest.java`
- Integration: `src/test/java/com/att/tdp/issueflow/controller/XxxControllerTest.java`

## application.properties for Tests
Create `src/test/resources/application.properties`:
```properties
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
```

## Service Test Template
```java
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private TicketService ticketService;

    @Test
    void shouldThrowException_whenStatusMovesBackward() {
        // given
        Ticket ticket = Ticket.builder()
            .id(1L).status(TicketStatus.IN_PROGRESS).build();
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setStatus(TicketStatus.TODO); // backward!

        // when + then
        assertThrows(ValidationException.class, 
            () -> ticketService.updateTicket(1L, request));
    }
}
```

## Checklist
- [ ] Tests are independent (no shared state between tests)
- [ ] Each test has a clear name describing what it tests
- [ ] All exception paths tested, not just happy path
- [ ] No real external services called in unit tests
- [ ] Tests actually fail if the logic is removed (meaningful assertions)
