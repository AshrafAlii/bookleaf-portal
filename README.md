# BookLeaf — Author Support Portal

A support portal for BookLeaf Publishing. Authors can see their books, royalties and every query
they raise, in real time. The admin team gets a ticket queue with **automatic AI triage and
prioritisation**, plus an AI-generated reply draft for every ticket.

**Live demo:** https://bookleaf-portal-figx.onrender.com
(loading the first page after some idle time can take ~40 seconds — the free hosting tier sleeps the app)

---

## Login details

| Role  | Email                  | Password    |
|-------|------------------------|-------------|
| Author | `priya.sharma@email.com` | `Author@1234` |
| Admin  | `ops@bookleaf.in`        | `Admin@1234`  |

All 10 seeded authors use the password `Author@1234` (e.g. `rohit.kapoor@email.com`).
Two accounts can be open side by side in the same browser, one per tab.

---

## How to run it locally

**Easiest — Docker:**

```bash
git clone <your-repo> bookleaf && cd bookleaf
docker compose up -d --build
```

Then open http://localhost:8080. This one command builds everything, starts PostgreSQL,
runs the database migrations and loads the demo data (10 authors, 18 books, 8 tickets).

**For development (live UI updates):** needs Java 21, Maven, Node 20 and PostgreSQL.

```bash
./scripts/setup.sh          # creates .env, the databases, installs npm packages
./scripts/dev-backend.sh    # terminal 1 → http://localhost:8080
./scripts/dev-frontend.sh   # terminal 2 → http://localhost:5173
```

Using IntelliJ? Follow **[docs/INTELLIJ.md](docs/INTELLIJ.md)** for a step-by-step setup.

---

## What it does

**Author portal**
- Dashboard with royalty totals and book list
- Raise a query, then track it live (updates appear instantly, no refresh)

**Admin portal**
- Ticket queue with filters, sorting, bulk actions and saved views
- Ticket detail with AI-drafted reply (edit before sending), priority override, internal notes
- Audit log of every action, and AI usage/cost logging

**AI features**
- Every new ticket is auto-classified: category, priority, and whether it needs escalation
  (AI runs in the background, so ticket creation stays fast)
- Reply drafts grounded in BookLeaf's own policy and the author's real royalty data
- Works with any AI provider (currently Groq; Gemini, OpenAI, Claude, Ollama all supported
  through a config change). If the AI is down, the app still works — tickets get classified
  with built-in rules and replies are written manually.

---

## Tech stack

- **Backend:** Spring Boot 3.3, Java 21, PostgreSQL 16, Flyway migrations
- **Frontend:** React 18, TypeScript, Vite, Tailwind
- **Real-time:** Server-Sent Events
- **AI:** one adapter, any OpenAI-compatible provider (set with 4 environment variables)
- Ships as one container: the API and the web app run on the same origin.

---

## Testing

```bash
cd backend && mvn test      # 71 backend tests
cd frontend && npm test     # 13 frontend tests
```

---

## More docs

| File | What's inside |
|---|---|
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | Assignment requirements mapped to the code that implements them |
| [docs/API.md](docs/API.md) | Every REST endpoint explained |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | How it is deployed on Render + AI provider setup |
| [docs/WRITEUP.md](docs/WRITEUP.md) | Design decisions and trade-offs |
| [docs/INTELLIJ.md](docs/INTELLIJ.md) | Local setup in IntelliJ |

Swagger UI: **http://localhost:8080/swagger-ui.html**
