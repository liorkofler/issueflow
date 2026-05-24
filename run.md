# IssueFlow — Running the Application

## Prerequisites

- **Java 21** — [Download](https://adoptium.net/) or install via `brew install --cask temurin@21`
- **Maven** — bundled as `./mvnw` (Maven Wrapper), no separate install needed
- **Docker** — [Download Docker Desktop](https://www.docker.com/products/docker-desktop/)

---

## 1. Start the Database

```bash
docker compose up -d
```

This starts a PostgreSQL 16 container on `localhost:5432` with:
- database: `issueflow`
- username: `issueflow`
- password: `issueflow`

---

## 2. Build

```bash
./mvnw clean package -DskipTests
```

The compiled JAR will be at `target/issueflow-0.0.1-SNAPSHOT.jar`.

---

## 3. Run the Application

```bash
./mvnw spring-boot:run
```

The server starts on **http://localhost:8080**.

---

## 4. Run All Tests

```bash
./mvnw test
```

Tests use an H2 in-memory database — no running Docker container needed.

---

## 5. Run a Specific Test Class

```bash
./mvnw test -Dtest=TicketServiceTest
```

---

## Example curl Commands

### Create a User

```bash
curl -s -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "secret123",
    "fullName": "Alice Smith",
    "role": "ADMIN"
  }' | jq .
```

### Login and Get a JWT

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"secret123"}' \
  | jq -r '.token')

echo "Token: $TOKEN"
```

### Call a Protected Endpoint

```bash
curl -s http://localhost:8080/auth/me \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### Create a Project

```bash
# Replace 1 with the actual user id returned from POST /users
curl -s -X POST http://localhost:8080/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My First Project",
    "description": "A demo project",
    "ownerId": 1
  }' | jq .
```

### Create a Ticket

```bash
# Replace projectId with the actual id returned from POST /projects
curl -s -X POST http://localhost:8080/tickets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Fix login bug",
    "description": "Login fails for OAuth users",
    "status": "TODO",
    "priority": "HIGH",
    "type": "BUG",
    "projectId": 1
  }' | jq .
```

### Logout

```bash
curl -s -X POST http://localhost:8080/auth/logout \
  -H "Authorization: Bearer $TOKEN"
```
