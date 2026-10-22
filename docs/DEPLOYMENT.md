# Deployment & the AI connection

The short answer to *"will the AI draft still work when I deploy it?"*:

> **Not with Ollama on your laptop.** Ollama listens on `localhost:11434` on *your* machine.
> A container on Render is a different machine and cannot reach it. You have three real options,
> and the first one is what you want.

| Option | AI works on the live site? | Cost | Setup |
|---|---|---|---|
| **A. A cloud API key** (Gemini / OpenRouter / Anthropic / OpenAI) | ✅ Yes, properly | Free tier available | Add one secret in Render — 2 minutes |
| **B. No provider — fallback mode** | ⚠️ Everything works *except* the AI draft button | Free | Nothing to do |
| **C. Tunnel your laptop's Ollama to the internet** | ✅ Yes, but fragile | Free | ngrok/Cloudflare tunnel + laptop must stay awake |

---

## Option A — a cloud API key (recommended)

The code is provider-agnostic. Set the key as an environment variable on the Render service and
the AI works exactly as it does locally.

### A1. Google Gemini — the most generous free tier

Get a key at <https://aistudio.google.com/apikey> (sign in with Google, no card needed).

```bash
# Render → the web service → Environment
OPENAI_API_KEY=<your gemini key>
OPENAI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai
OPENAI_CHAT_PATH=/chat/completions
OPENAI_MODEL=gemini-2.0-flash
```

### A2. OpenRouter — free models, one key for many models

Get a key at <https://openrouter.ai/keys>.

```bash
OPENAI_API_KEY=<your openrouter key>
OPENAI_BASE_URL=https://openrouter.ai/api/v1
OPENAI_CHAT_PATH=/chat/completions
OPENAI_MODEL=meta-llama/llama-3.1-8b-instruct:free
```

Other free models worth trying: `google/gemma-2-9b-it:free`, `mistralai/mistral-7b-instruct:free`.

### A3. Anthropic Claude — the best quality, smallest free tier

```bash
ANTHROPIC_API_KEY=sk-ant-...
ANTHROPIC_MODEL=claude-3-5-haiku-latest
```

### A4. OpenAI

```bash
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini
```

> **Why `OPENAI_CHAT_PATH` exists:** the OpenAI-compatible providers do not agree on where the API
> lives. OpenAI is `/v1/chat/completions`; OpenRouter and Gemini are `/chat/completions` on top of
> a base URL that already ends in the version. The path is configurable so all of them work with
> the same adapter — see `ProviderEndpointTest`.

### Verify it from the deployed site

As an admin, open the **Dashboard** and press **Test the AI connection** in the *AI usage* panel.
It reports the resolved provider, model and endpoint (never the key) and makes a live one-token
call to prove the provider is reachable from that machine.

The same information is available as JSON:

```
GET /api/v1/admin/ai-status
```

```json
{
  "enabled": true,
  "provider": "openai",
  "model": "gemini-2.0-flash",
  "endpoint": "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
  "triageAsync": true,
  "message": "Reachable in 412 ms."
}
```

---

## Option B — no provider (fallback mode)

Leave both keys empty. The deployed portal is **fully functional**:

- Tickets are still classified, by the deterministic rules (measured at 10/10 on category).
- The queue, bulk actions, saved views, real-time updates, replies, notes and the audit trail all
  work exactly as normal.
- The **Generate draft** button explains that no provider is configured and the admin writes the
  reply by hand — which is the designed behaviour, not a failure.

The admin dashboard shows a banner explaining the fallback, and the *AI usage* panel reports
`provider: none`.

This is a perfectly good demo, but a reviewer who clicks *Generate draft* and gets no AI reply
will notice. **Option A is worth the ten minutes.**

---

## Option C — tunnel your laptop's Ollama to the internet

Possible, but only for a short-lived demo:

```bash
# on your Mac
ollama serve
ngrok http 11434        # or: cloudflared tunnel --url http://localhost:11434
```

```bash
# on Render
OPENAI_API_KEY=ollama
OPENAI_BASE_URL=https://<subdomain>.ngrok-free.app
OPENAI_MODEL=qwen2.5:3b
```

**The catch:** your laptop must be awake, awake-connected and Ollama must be running for the whole
demo. If it sleeps, the deployed site silently falls back to the rules. Not something to rely on
for a review.

---

## What changes between local and deployed

Nothing in the code — the provider is selected purely from configuration:

| | Local | Deployed (Option A) |
|---|---|---|
| Provider | Ollama → `http://localhost:11434` | Gemini / OpenRouter / Anthropic |
| API key | `.env` (gitignored) | Render secret |
| Model in RAM | 1.9–4.9 GB **on your machine** | **0 MB** — the model runs at the provider |
| Response time | 3–50 s | ~0.4–1.5 s |
| `SECURE_COOKIES` | `false` | `true` (set by the blueprint) |

Because the model is remote in production, **the memory problem disappears entirely** — that is
one more reason to prefer a cloud key over a local model for the live demo.

---

## Render checklist

1. **New → Blueprint** → select the repository → **Apply**.
   The blueprint creates the web service and the Postgres database.
2. **Environment** on the web service:

   | Key | Value |
   |---|---|
   | `ANTHROPIC_API_KEY` *or* `OPENAI_API_KEY` | your key (the blueprint leaves these commented out) |
   | `OPENAI_BASE_URL` / `OPENAI_CHAT_PATH` / `OPENAI_MODEL` | only if using an OpenAI-compatible provider (see Option A) |
   | `SECURE_COOKIES` | `true` — already set by the blueprint |
   | `JWT_SECRET` | generated automatically |

3. **Deploy.** Check `https://<your-app>.onrender.com/actuator/health` → `{"status":"UP"}`.
4. Sign in as `ops@bookleaf.in` / `Admin@1234`, open **Dashboard**, press
   **Test the AI connection**.
5. Put the URL in the README under *Live demo*.

> **Render free tier sleeps.** An idle free instance goes to sleep after ~15 minutes and the
> first request after that takes 30–50 seconds to wake it. If the reviewer loads the page and it
> seems slow the first time, that is the free tier, not the app. A paid instance never sleeps.

---

## Security notes

- The API key is read from the environment on the server and **never sent to the browser**. The
  frontend only ever sees the provider *name* and *model*.
- `GET /api/v1/admin/ai-status` reports the endpoint URL but **not** the key.
- The key lives in Render's encrypted environment store, not in the repository or the image.
