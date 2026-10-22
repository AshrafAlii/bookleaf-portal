# Approach — BookLeaf Author Support & Communication Portal

*Ashraf Ali · Technical Assignment 1*

## What I built

A single deployable Spring Boot 3.3 / Java 21 application with a React + TypeScript SPA compiled
into the jar, serving both interfaces from one origin. PostgreSQL with Flyway-owned schema, JPA
with `ddl-auto=validate`. Stateless JWT auth with an httpOnly refresh cookie. Server-Sent Events
for live ticket updates. A provider-agnostic AI layer that classifies every new ticket, scores its
priority, and drafts replies grounded in the BookLeaf knowledge base and the author's live account
data.

The supplied sample dataset (10 authors, 18 books) is seeded verbatim, plus eight realistic demo
tickets so the queue and filters have something to show.

## What I prioritised, and why

**1. The AI integration, first and hardest.** It is the highest-weighted criterion (25%) and the
part of the brief that is most often faked. So the AI is not a wrapper around one API call: it has
a provider boundary (`AiClient`), a gateway that owns caching, usage accounting and failure
handling, a knowledge-base retrieval layer, separate structured prompts for triage and drafting, a
deterministic fallback path, and its own visibility in the admin UI.

The two decisions I would point at in a review:

- **Category and priority come from a single structured-output call.** Every inbound ticket
  triggers a classification, so halving the round trips there is the biggest cost lever in the
  system. The prompt asks for a rationale that must cite the specific phrase or number from the
  ticket, and that rationale is shown to the admin — a human can check the model in seconds
  instead of trusting a bare label.
- **The model is only allowed to state what it was given.** The draft prompt separates the ticket,
  the author's real account figures, the retrieved policy sections and a calibration example, and
  states the grounding rule explicitly. That is why drafts quote *“₹3,570 pending”* instead of
  *“your royalty is being processed”*, and why they own BookLeaf's mistakes rather than deflecting.

**2. Graceful degradation, as a designed path rather than a catch-all.** If the provider is down,
rate limited or simply not configured, the ticket is still created, the fallback classifier
decides the category and priority (and says so — the UI marks it *Fallback*), and the admin writes
the reply by hand. The failure is counted and visible in the AI usage panel. AI is an accelerant,
never a gate.

**3. Correctness of access control.** Authors only ever see their own data, enforced in the query
layer rather than by filtering responses. A ticket belonging to another author returns 404, not
403, so an author cannot probe for the existence of other authors' tickets. Internal notes are
filtered server side — the author-facing query never selects them at all.

**4. Real-time, genuinely real-time.** The brief says an author should see an admin reply without
refreshing. Server-Sent Events, with the authorisation decision made at publication time so an
author's stream only carries their own tickets.

## Trade-offs I made

**Java + Spring over a lighter stack.** It is not the fastest thing to write, but it is the stack
I am strongest in, and the brief explicitly says to pick what you are strongest in. Spring also
gives validation, security and OpenAPI generation essentially for free, which bought back time that
went into the AI layer instead.

**One deployable instead of two.** The SPA is compiled into the jar. That costs a slightly longer
build and a larger image, and buys no CORS configuration, working cookies, and one thing to
deploy and monitor.

**Keyword retrieval instead of embeddings.** A vector store would need infrastructure, an index
and money, and for a 1,450-token knowledge base with clearly named sections it would buy very
little. The lexical scorer is deterministic, free, testable and easy to explain. It is isolated
behind `KnowledgeBaseService` so swapping it later touches one class.

**Deliberately not built:** file upload (the brief says UI only), bulk queue actions, multi-book
tickets, a CMS for the knowledge base, and rate limiting on login. Each is a defensible omission
for the time available; each is listed in the README's limitations with the reasoning.

## Bugs the work surfaced

Worth noting, because they are the kind of thing that only shows up when you actually run the
thing:

- Two `LazyInitializationException`s caused by calling a `@Transactional` method from inside the
  same bean — the Spring proxy is bypassed, so the method ran without a transaction. Fixed by
  splitting the ticket services into a command service (writes), a query service (reads) and a
  non-transactional orchestrator. The same split keeps an LLM network call from holding a
  database transaction open.
- The admin queue's dynamic JPQL failed on PostgreSQL with *“could not determine data type of
  parameter”*. Replaced with composable JPA `Specification`s.
- The knowledge-base retrieval scorer was letting calibration examples crowd out the actual
  policy sections — found by a test, and a genuine prompt-quality bug.
- `@NotBlank` on an enum field (the validator rejected it at runtime) and a self-referencing test
  configuration that produced a circular bean reference.

## How I would evolve it

**With more time on the AI**, the biggest wins are evaluation and grounding rather than more
prompts: a small labelled set of historical tickets with the category a human gave them, scored
as precision/recall per category so regressions are caught before they reach authors; retrieval
measured the same way, so the knowledge base can be pruned of sections nobody needs; and a
structured "escalate to a human" signal so the model knows when *not* to answer.

**With more time on the platform**, the queue needs bulk triage and saved filters (an ops team
works in batches, not one ticket at a time), SLA policies that escalate automatically rather than
only reporting overdue counts, and attachments stored in object storage. The ticket list on the
author side needs pagination. The SSE hub needs a shared broker (Redis pub/sub) before it can run
on more than one instance, and the knowledge base wants a CMS so the ops team can edit policy
without a code review.

**With more time on the product**, the two things an author and an ops lead would both ask for are
a per-platform royalty breakdown (the knowledge base promises it; the sample data does not contain
it) and a self-service royalty statement the author can download.

## Honest notes

- The AI path was developed and verified against a deterministic mock provider and the fallback
  rules; it has been wired for Anthropic and OpenAI, and the provider boundary is tested, but I
  have not run it against a live key in this environment.
- The SSE "Live" indicator can show *Offline* if a browser has exhausted its six concurrent
  connections per origin — an artefact of opening many tabs, not a defect, but it is the kind of
  thing worth knowing before a demo.
- The seeded demo tickets are realistic but invented; the ten authors and eighteen books are the
  supplied data, verbatim.
