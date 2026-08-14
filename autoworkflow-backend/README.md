# AutoWorkflow Backend

Java 21 + Spring Boot 3 backend for the AutoWorkflow frontend (React 19 / React Flow). Implements every screen shown in the UI: Dashboard, Workflows, Workflow Builder, Executions, Templates, Node Marketplace, Integrations, AI Assistant, and Settings.

## Stack

- **Java 21**, **Spring Boot 3.3**
- **PostgreSQL** (Flyway migrations, JSONB for React Flow canvas data)
- **Spring Security + JWT** (access + rotating refresh tokens)
- **WebClient (reactive)** for all outbound integration/HTTP calls
- **Quartz + cron-utils** for scheduled ("Cron Trigger") workflows
- **Redis** (optional) for the Redis node
- **springdoc-openapi** → Swagger UI at `/swagger-ui.html`

## Module Layout

```
com.autoworkflow/
├── auth/            signup, login, JWT refresh/rotation, logout
├── user/             profile, platform API key (generate/reveal)
├── workflow/          CRUD, save/deploy/trigger lifecycle
├── execution/
│   ├── engine/        WorkflowExecutor (topological graph runner), NodeStrategy contract
│   └── strategy/       one class per node type — 25 total, see below
├── node/               Node Marketplace registry (DB-backed, seeded via Flyway)
├── template/            Templates gallery + "import as workflow"
├── integration/         per-user OAuth credentials, health checks, encrypted at rest
├── assistant/            AI Assistant chat, conversation history, workflow-JSON generation
├── dashboard/            aggregated stats + execution-overview chart data
├── webhook/              public endpoint for `webhook` trigger nodes
├── scheduler/            cron polling for `cron_trigger` nodes
├── security/              JWT filter, CustomUserDetails, CurrentUserProvider
├── config/                Security, CORS, OpenAI WebClient, Swagger, Async
└── common/                ApiResponse envelope, exceptions, JSONB converter, enums
```

## The Execution Engine

`WorkflowExecutor` reads a workflow's `canvas_nodes` / `canvas_edges` (the exact React Flow JSON the frontend saves) and walks it as a graph:

1. Finds trigger node(s) — nodes with no incoming edge.
2. Executes each node via its registered `NodeStrategy` (looked up by the node's `type` field).
3. Threads each node's output forward as the next node's input payload.
4. `if_condition` / `ai_router` nodes only follow the outgoing edge whose `data.branch` matches their decision.
5. Every step (input, output, timing, success/failure) is appended to `Execution.steps_logs`, matching the frontend's `LogStep` interface field-for-field so Execution Detail needs zero transformation.

### All 25 node types are implemented (`execution/strategy/`)

| Category | Nodes |
|---|---|
| Trigger | Cron Trigger, Webhook, GitHub Event, Email Received |
| AI | OpenAI, AI Router, Summarizer, Classifier |
| Logic | IF Condition, Loop, Merge, Delay, Transform |
| Integration | HTTP Request, GitHub, Slack, Gmail, Notion, Google Sheets, Database, File, Redis |
| Action | Send Email, SMS, Discord |

## Setup

### 1. Local Postgres via Docker

```bash
docker compose up -d postgres
```

### 2. Configure environment

Copy the defaults in `application.yml` or export:

```bash
export JWT_SECRET="a-real-256-bit-secret"
export ENCRYPTION_SECRET="a-real-32-byte-secret"
export OPENAI_API_KEY="sk-..."          # platform fallback key for the AI Assistant / AI nodes
export FRONTEND_URL="http://localhost:5173"
```

### 3. Run

```bash
./mvnw spring-boot:run
```

Flyway runs automatically on boot (`V1__init_schema.sql`, `V2__seed_nodes.sql`) — the Node Marketplace and Templates gallery are populated immediately, matching the frontend screenshots exactly.

### 4. Full stack via Docker

```bash
docker compose up --build
```

API available at `http://localhost:8080`, Swagger UI at `http://localhost:8080/swagger-ui.html`.

## Wiring up the frontend

In the React app, replace the Zustand store actions in `authStore.js` / `workflowStore.js` / `executionStore.js` with calls to these endpoints (see `postman/AutoWorkflow.postman_collection.json` for ready-made requests):

| Frontend needs | Endpoint |
|---|---|
| Login/Signup | `POST /api/auth/login`, `POST /api/auth/signup` |
| Dashboard stat cards | `GET /api/dashboard/stats` |
| Dashboard execution chart | `GET /api/dashboard/execution-overview` |
| Workflows grid/list | `GET /api/workflows?search=&status=` |
| Workflow Builder load/save | `GET /api/workflows/{id}`, `PUT /api/workflows/{id}` |
| Deploy button | `POST /api/workflows/{id}/deploy` |
| Run button | `POST /api/workflows/{id}/trigger` |
| Node palette | `GET /api/nodes/grouped` |
| Execution History | `GET /api/executions` |
| Execution Detail | `GET /api/executions/{id}` |
| Templates gallery | `GET /api/templates`, `POST /api/templates/{id}/import` |
| Integrations page | `GET /api/integrations`, `GET /api/integrations/oauth/{provider}`, `DELETE /api/integrations/{provider}` |
| AI Assistant chat | `POST /api/assistant/chat` |
| Settings profile | `PUT /api/users/me` |
| Settings API key Reveal/Generate | `GET /api/users/me/api-key/reveal`, `POST /api/users/me/api-key` |

All authenticated endpoints expect `Authorization: Bearer <accessToken>`.

## Known scaffolding gaps (intentional, flagged inline with `TODO`)

- **OAuth token exchange** (`IntegrationController.oauthCallback`) — the authorization-URL building is real; the actual code→token exchange call per provider (GitHub/Slack/Google/Notion/Discord each have a different token endpoint contract) needs the client secret wiring finished per provider.
- **Integration health checks** currently just refresh `lastCheckedAt`; add a real lightweight "whoami" call per provider to flip status to `ERROR` on an expired token.
- **Redis node** requires `spring-boot-starter-data-redis` (already on the classpath) plus `spring.data.redis.host/port` config once Redis is provisioned.
- **SMS node** needs Twilio credentials (`app.twilio.account-sid/auth-token/from-number`) — currently returns a clear error if unset rather than silently failing.
- This project was written and organized in a sandboxed environment without network/Maven access, so it has **not been compiled**. Run `./mvnw clean compile` first thing and expect to fix minor issues (import ordering, occasional getter/setter naming from Lombok) before your first real run.
