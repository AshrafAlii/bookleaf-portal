# Running the project in IntelliJ IDEA

Step-by-step for macOS. Works with **IntelliJ IDEA Ultimate** (full support for Spring Boot run
configurations and the Services window). For **Community Edition**, see the note at the bottom —
the only difference is you launch the main class instead of a Spring Boot run configuration.

Everything below assumes you are starting from a fresh clone.

---

## Step 0 — Prerequisites

Check these are installed before opening IntelliJ. IntelliJ can use its own bundled JDK and Maven,
so you may not need separate installs.

| Tool | Version | How to check | Notes |
|---|---|---|---|
| JDK | **21** | `java -version` | IntelliJ ships with JetBrains Runtime 21 — that works too. Set it in *Project Structure*. |
| Node.js | **20+** | `node -v` | Needed only for the frontend. |
| PostgreSQL | **14+** | `psql --version` | You have 17.10 installed and running. |
| Ollama *(optional)* | any | `ollama --version` | Only for the free local AI. Already installed on your machine. |

```bash
# all at once
java -version && node -v && psql --version && ollama --version
```

---

## Step 1 — Open the project

**Do not open the repo root as a plain folder** — open the `backend` folder so IntelliJ detects the
Maven project and imports the dependencies automatically.

```
File → Open…  →  select:  /Users/ashrafali/Downloads/BookLeafAssignment/bookleaf/backend
```

Click **OK** and choose **Trust Project** when IntelliJ asks.

IntelliJ will now:

- import the Maven dependencies (bottom-right progress bar, ~1–2 minutes the first time)
- detect `com.bookleaf.portal.BookLeafPortalApplication` and offer to create a run configuration

> **Where is the frontend then?** It lives one level up, in `../frontend`. IntelliJ only needs the
> backend; you run the frontend from IntelliJ's terminal (Step 6). You can also open the repo root
> in a second IntelliJ window and attach the frontend as a Node module — but it is not required.

---

## Step 2 — Set the JDK to 21

```
File → Project Structure…  (⌘ + ; on macOS)
  → Project
      SDK            → 21 (or "21 - Temurin" / "JetBrains Runtime 21")
      Language level → 21
      Compiler output → leave default
  → Apply → OK
```

Then make sure the Maven importer uses the same JDK:

```
Settings… (⌘ ,) → Build, Execution, Deployment → Build Tools → Maven → Runner
    → JRE: 21
```

The project ships a **Maven wrapper** (`mvnw`), so IntelliJ will use Maven 3.9.9 automatically —
you do not need Maven installed separately.

---

## Step 3 — Create the database

The backend needs a PostgreSQL database named `bookleaf`, owned by a role named `bookleaf`.

**Option A — the setup script (easiest).** Open IntelliJ's terminal
(*View → Tool Windows → Terminal*, or `⌥ + F12`) and run:

```bash
cd /Users/ashrafali/Downloads/BookLeafAssignment/bookleaf
./scripts/setup.sh
```

This creates the `.env` file, the role and both databases (`bookleaf` and `bookleaf_test`), and
runs `npm install` in the frontend.

**Option B — do it by hand.** In the terminal:

```bash
psql -d postgres -c "CREATE ROLE bookleaf LOGIN PASSWORD 'bookleaf';"
psql -d postgres -c "CREATE DATABASE bookleaf OWNER bookleaf;"
psql -d postgres -c "CREATE DATABASE bookleaf_test OWNER bookleaf;"
```

> Skip this if you already ran `setup.sh` — the role and databases already exist in your case.

**Verify** (optional): IntelliJ's **Database** tool window (`View → Tool Windows → Database`) → `+`
→ Data Source → PostgreSQL → host `localhost`, port `5432`, user `bookleaf`, password `bookleaf`
→ **Download** the driver → **Test Connection** → **OK**. You should see `bookleaf` and
`bookleaf_test` in the schema tree. You can also browse and query the data straight from IntelliJ.

---

## Step 4 — Configure the environment

The app reads a plain `.env` file (`KEY=value`) from the **repository root** — one level above
`backend/`. `setup.sh` already created it from `.env.example`.

```
/Users/ashrafali/Downloads/BookLeafAssignment/bookleaf/.env
```

Open it in IntelliJ (it is hidden — use `⌘ + ⇧ + .` to toggle hidden files in the Project view, or
just `File → Open` and navigate to it).

The lines that matter:

```bash
DB_URL=jdbc:postgresql://localhost:5432/bookleaf
DB_USERNAME=bookleaf
DB_PASSWORD=bookleaf
JWT_SECRET=change-me-to-a-long-random-string-at-least-32-chars

# free local AI (Ollama) — leave as is if you want the AI features
OPENAI_API_KEY=ollama
OPENAI_BASE_URL=http://localhost:11434
OPENAI_MODEL=llama3.1
```

You do **not** need to export any of these in your shell — `application.yml` imports the file
automatically:

```yaml
spring:
  config:
    import: "optional:file:.env[.properties],optional:file:../.env[.properties]"
```

Both relative paths are listed, so the file is found whether IntelliJ's working directory is the
`backend` folder or the repository root.

> **If you prefer environment variables:** skip the file and set them in the run configuration
> (Step 5 → *Environment variables*). Real environment variables always win over the file.

---

## Step 5 — Create the backend run configuration

```
Run → Edit Configurations…  (⌃ + A, or the top-right dropdown → Edit Configurations)
  → +  →  Spring Boot
```

Fill it in:

| Field | Value |
|---|---|
| **Name** | `BookLeaf Portal` |
| **Main class** | `com.bookleaf.portal.BookLeafPortalApplication` |
| **Use classpath of module** | `bookleaf-portal` (your Maven module) |
| **JRE** | `21` |
| **Working directory** | `/Users/ashrafali/Downloads/BookLeafAssignment/bookleaf` ← **set this** |
| **Environment variables** | leave empty (the `.env` file is picked up) |
| **Active profiles** | leave empty |
| **VM options** | `-Dspring.profiles.active=local` *(optional)* |

> **Why the working directory matters:** it is what Spring resolves the relative `.env` path
> against. Setting it to the repository root makes `file:.env` resolve. (The `../.env` fallback
> covers the other case, but being explicit removes all doubt.)

Click **Apply → OK**.

### Optional: add the local AI as an environment variable instead

If you would rather not rely on the `.env` file, set these in the same screen under
**Environment variables**:

```
OPENAI_API_KEY=ollama;OPENAI_BASE_URL=http://localhost:11434;OPENAI_MODEL=llama3.1
```

---

## Step 6 — Start the backend

Click the green **▶** next to `BookLeaf Portal` in the top-right, or `Run → Run 'BookLeaf Portal'`.

Watch the **Run** console. You want to see:

```
Started BookLeafPortalApplication in 4.1 seconds
Seeded 10 authors and 18 books from the sample dataset
Seeded 8 demo tickets
```

If you see `Seed skipped — 10 authors already present`, that is fine — the data is already there.

Check it is alive:

```
http://localhost:8080/actuator/health   →  {"status":"UP"}
http://localhost:8080/swagger-ui.html   →  the API docs
```

Leave it running.

---

## Step 7 — Start the frontend

Open IntelliJ's terminal (*View → Tool Windows → Terminal*, `⌥ + F12`) and make sure you are in the
**frontend** folder:

```bash
cd /Users/ashrafali/Downloads/BookLeafAssignment/bookleaf/frontend
npm install        # first time only
npm run dev
```

You should see:

```
  VITE v5.4.21  ready in 178 ms
  ➜  Local:   http://localhost:5173/
```

The Vite dev server proxies every `/api` call to `http://localhost:8080`, so the browser only ever
talks to one origin — that is why the login cookie works with no CORS setup.

> **Tip — a Node.js run configuration instead of the terminal:**
> `Run → Edit Configurations…` → `+` → **npm** → *package.json* → `.../frontend/package.json` →
> *Command*: `run` → *Scripts*: `dev`. Then both the backend and the frontend are startable from
> the same run-configuration dropdown, and the **Services** window (Ultimate) shows both.

---

## Step 8 — Sign in

Open **http://localhost:5173**

| Role | Email | Password |
|---|---|---|
| Author | `priya.sharma@email.com` | `Author@1234` |
| Author | `rohit.kapoor@email.com` | `Author@1234` |
| Admin | `ops@bookleaf.in` | `Admin@1234` |

All ten seeded authors share `Author@1234`. The demo data includes eight realistic tickets across
every category, priority and status.

---

## Step 9 — (Optional) Turn on the free local AI

Only needed if you want the **AI draft** button and AI auto-classification to use a real model.

```bash
ollama serve            # leave running in a terminal
ollama pull llama3.1   # ~5 GB, already on your machine
```

Then restart the backend (the **▶** button again) and confirm the provider is picked up:

```bash
curl -s http://localhost:8080/api/v1/meta | python3 -m json.tool
```

You want `"aiEnabled": true`. If it says `false`, the backend did not pick up the `.env` — check the
**Working directory** in Step 5.

Without the AI the app still works completely: tickets are classified by the built-in fallback
rules (marked **Fallback** in the UI) and replies are written by hand.

---

## Step 10 — Run the tests

From the terminal:

```bash
cd /Users/ashrafali/Downloads/BookLeafAssignment/bookleaf/backend
./mvnw test              # 41 backend tests
```

Or in IntelliJ: right-click `src/test/java` → **Run 'All Tests'**.

The frontend tests:

```bash
cd ../frontend && npm test
```

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Unsupported class file major version` or `invalid target release: 21` | Wrong JDK. *Project Structure → SDK → 21*, and *Settings → Maven → Runner → JRE → 21*. |
| `Connection refused` / `FATAL: password authentication failed for user "bookleaf"` | The database or role is missing. Re-run Step 3. Check `DB_USERNAME` / `DB_PASSWORD` in `.env`. |
| `FlywayException: Unable to obtain connection from database` | Postgres is not running. Check with `pg_isready`; start it with `brew services start postgresql@17`. |
| The AI says `aiEnabled: false` | The backend did not read `.env`. Confirm the run configuration's **Working directory** is the repository root, and that the file is at `<repo>/.env`. |
| `port 8080 already in use` | Another instance is running (possibly a previous `mvn spring-boot:run`). Stop it, or change `server.port` in `.env`. |
| `port 5173 already in use` | Vite is already running. Kill it with `lsof -ti:5173 | xargs kill`, or let Vite pick another port. |
| Login succeeds but every request returns 401 | The access token expired (30 min) and the refresh cookie is missing — usually a clock/timezone or cookie-domain issue. Sign out and back in. |
| IntelliJ shows red squiggles everywhere after the first import | The Maven project has not finished importing. Wait for the progress bar, then *Maven → Reload All Maven Projects*. |
| Changes to `.env` have no effect | The `.env` is read at **startup**. Restart the backend after editing it. |

---

## IntelliJ IDEA Community Edition

Community does not have the Spring Boot run configuration type or the Services window, but
everything else is identical:

1. Open `backend/` as a Maven project and set the JDK to 21 (Steps 1–2).
2. **Run → Edit Configurations… → + → Application**
   - **Main class:** `com.bookleaf.portal.BookLeafPortalApplication`
   - **Use classpath of module:** `bookleaf-portal`
   - **Working directory:** `/Users/ashrafali/Downloads/BookLeafAssignment/bookleaf`
   - **JRE:** 21
3. Run it, then run the frontend from the terminal as in Step 7.

---

## What is where

```
bookleaf/
├── backend/          ← the project you open in IntelliJ
│   ├── mvnw          ← Maven wrapper (no local Maven needed)
│   ├── pom.xml
│   └── src/main/java/com/bookleaf/portal/
│       ├── BookLeafPortalApplication.java   ← the main class
│       ├── ai/        ← AI gateway, prompts, knowledge base
│       ├── config/    ← security, OpenAPI, properties, data seeder
│       ├── domain/    ← JPA entities
│       ├── service/   ← ticket lifecycle
│       └── web/       ← REST controllers
├── frontend/         ← run with `npm run dev`
├── data/             ← the supplied sample dataset
├── docs/             ← README, API reference, write-up, screenshots
└── .env              ← local configuration (gitignored)
```
