# Claude Code — Quick Reference Card

## Starting a Session (Every Time)
1. Open VSCode in the project folder
2. Open Claude Code panel (left sidebar icon or Ctrl+Shift+P → "Claude Code")
3. First message in EVERY session:
   > "Read CLAUDE.md fully before starting. [Then describe your task]"

## Key Commands (type these in the Claude Code chat)
| Command | What it does |
|---------|-------------|
| `/clear` | Clear context but keep the session open |
| `/compact` | Summarize context to save space (use when conversation gets long) |
| `/cost` | See how many tokens you've used |
| `@filename` | Reference a specific file |

## Context Window Warning Signs
When you see these, do /compact or start a new session:
- Responses getting slower
- Claude forgetting things it knew earlier
- Repeating itself
- Making mistakes it wasn't making before

## Session Rules
- One goal per session (e.g., "build tickets CRUD" not "build everything")
- After each session ends: open a NEW terminal/chat window
- Start new session with: "Sessions 1-N are done. Now do Session N+1 from PLAN.md."

## How to Ask for Things (Best Practices)

### Too vague (BAD):
> "Make the tickets work"

### Just right (GOOD):
> "Implement TicketService.updateTicket with:
> - Status machine validation (see CLAUDE.md for valid transitions)
> - Optimistic locking handling
> - AuditLog on every change
> - Throw ValidationException for DONE ticket updates"

### Too much in one ask (BAD):
> "Build all services and controllers and tests for tickets, comments, auth, and projects"

## Prompt Templates

### Start a session:
```
Read CLAUDE.md fully before starting.
[Previous sessions done: X, Y, Z are complete.]

Now implement: [specific thing]
Requirements:
- [requirement 1]
- [requirement 2]
Constraints:
- [don't break X]
- [follow Y pattern]
Expected output: [list of files to create/modify]
```

### Fix a bug:
```
There's an error when I [do X]:
[paste the exact error message]

The relevant code is in [filename].
Fix it without changing [other thing].
```

### Ask for explanation:
```
Explain what the [ClassName] class does and why it's structured this way.
Focus on [specific part]. I need to understand it for the interview.
```

## Running the App
```bash
# Always start DB first
docker compose up -d

# Start app
./mvnw spring-boot:run

# Test with curl
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"secret"}'
```

## If Something Breaks
1. Copy the exact error message
2. New message: "This error occurred: [error]. Fix it."
3. If it can't fix it in 2 tries → start a new session and describe what you want to achieve differently
