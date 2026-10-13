# AutoWorkflow

**AI-assisted visual workflow automation built with React, React Flow, Spring Boot, and PostgreSQL.**

AutoWorkflow is a full-stack application for building workflows visually, configuring AI and integration nodes, and executing connected steps through a backend workflow engine. It separates workflow editing and manual testing from deployment and event-triggered execution.

> **Project status:** Development is paused at the current committed baseline while documentation and handover notes are finalized. New features are intentionally deferred. This is a project snapshot, not a claim that every provider, integration, or deployment path is production-ready.

[Repository](https://github.com/BaidnathKushwaha/AutoWorkFlow) · [Architecture](docs/ARCHITECTURE.md) · [Local setup](docs/LOCAL_SETUP.md) · [Project status](docs/PROJECT_STATUS.md) · [Verification checklist](docs/VERIFICATION_CHECKLIST.md)

---

## Contents

- [What it does](#what-it-does)
- [Core capabilities in the repository](#core-capabilities-in-the-repository)
- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Repository layout](#repository-layout)
- [Run locally](#run-locally)
- [Configuration and secrets](#configuration-and-secrets)
- [Verification](#verification)
- [Current limitations](#current-limitations)
- [Resuming development](#resuming-development)

## What it does

The application lets a user arrange nodes on a visual canvas, connect them into a graph, save the workflow, and run it through the backend. The backend resolves each node to a dedicated strategy and passes outputs through the graph. AI nodes can process text, while logic nodes can control which path is executed.

The intended model is:

```text
Input / Trigger
      ↓
AI or Transformation
      ↓
Condition / Branch / Loop
      ↓
Integration or Action
      ↓
Execution result and logs
```

The goal is to make orchestration visible and configurable, while keeping execution decisions and validation in the backend rather than relying entirely on frontend behavior.

## Core capabilities in the repository

The following capabilities are represented in the current `main` branch. Actual runtime behavior depends on configuration, credentials, provider availability, and the specific workflow.

- **Visual workflow editor:** React and React Flow canvas with connected nodes and edges.
- **Workflow lifecycle:** workflow persistence, manual execution, deployment, and trigger-based execution paths.
- **Execution engine:** Spring Boot backend with a node strategy registry and graph-oriented execution.
- **AI provider layer:** OpenAI, Google Gemini, and OpenRouter configuration/routing.
- **AI operations:** summarization, completion/classification-related nodes, and AI-assisted workflow functionality.
- **Control flow:** IF/condition, Switch/AI routing, Loop, Merge, and Transform-related implementations. See the source and project status notes for current scope.
- **External integrations:** GitHub events/webhooks, Slack, Gmail, and Google Sheets integration code, plus other node strategies in the backend.
- **Authentication and protected operations:** Spring Security and JWT-based authentication.
- **Persistence:** PostgreSQL, with Flyway migrations and JSONB-backed workflow canvas data.
- **Execution visibility:** execution history/logging and UI execution-state feedback.
- **API exploration:** Springdoc OpenAPI/Swagger UI configuration.

This list describes repository implementation, not a guarantee that every integration has valid credentials or has been recently re-tested end to end.

## Architecture

```text
React + React Flow frontend
        │ REST/HTTP
        ▼
Spring Boot API
        ├── Authentication and authorization
        ├── Workflow CRUD and lifecycle
        ├── Workflow validation
        ├── Execution service
        │      └── WorkflowExecutor
        │              └── NodeStrategyRegistry
        │                      └── Node-specific strategies
        ├── AI provider routing
        ├── Integration / OAuth services
        └── Execution and workflow persistence
                        │
                        ▼
                   PostgreSQL
```

Workflow node and edge data is stored as graph-shaped JSON. The executor uses node strategies to isolate node behavior and keep graph orchestration separate from individual integrations.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design overview and lifecycle distinctions.

## Technology stack

| Area | Technology |
|---|---|
| Frontend | React 19, Vite, JavaScript |
| Workflow canvas | `@xyflow/react` |
| UI styling | Tailwind CSS |
| Client state / requests | Zustand, Axios |
| Backend | Java 21, Spring Boot 3 |
| API and persistence | Spring Web, Spring Data JPA, Hibernate |
| Security | Spring Security, JWT |
| Database / migrations | PostgreSQL, Flyway, JSONB |
| AI providers | OpenAI, Google Gemini, OpenRouter |
| API documentation | Springdoc OpenAPI |
| Scheduling / cache-related components | Quartz; Redis node support is configuration-dependent |
| Testing tools | JUnit / Spring Boot Test, Vitest, Testing Library |

## Repository layout

```text
AutoWorkFlow/
├── frontend/                       # React/Vite client
├── autoworkflow-backend/           # Spring Boot API and workflow engine
│   ├── src/main/java/com/autoworkflow/
│   ├── src/main/resources/
│   │   ├── application.example.yml
│   │   └── db/migration/
│   ├── src/test/
│   ├── pom.xml
│   ├── Dockerfile
│   └── docker-compose.yml
├── docker-compose.yml              # Root-level PostgreSQL service
├── docs/
│   ├── ARCHITECTURE.md
│   ├── LOCAL_SETUP.md
│   ├── PROJECT_STATUS.md
│   └── VERIFICATION_CHECKLIST.md
└── README.md
```

## Run locally

The steps below are the development path. Detailed configuration notes and caveats are in [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md).

### 1. Start PostgreSQL

From the repository root:

```bash
docker compose up -d postgres
```

The root Compose file creates a local PostgreSQL database. Its credentials are development-only and must not be reused for a public or production deployment.

### 2. Start the backend

```bash
cd autoworkflow-backend
./mvnw spring-boot:run
```

Configure the required environment variables and OAuth/AI provider credentials before expecting protected or external-service functionality to work. See the backend's `application.example.yml` and [Local setup](docs/LOCAL_SETUP.md).

### 3. Start the frontend

In a separate terminal:

```bash
cd frontend
npm install
npm run dev
```

By default, the frontend API base URL is `http://localhost:8080`. Override it with `VITE_API_URL` when your backend uses a different URL.

### Docker option

A backend Compose file also exists at `autoworkflow-backend/docker-compose.yml`. Review its environment values before using it. The supplied fallback secrets and database credentials are for local development only, not production.

## Configuration and secrets

The backend example configuration references environment variables for database access, JWT signing, encryption, AI provider keys, and OAuth credentials. Common variables include:

- Database: `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- Security: `JWT_SECRET`, `ENCRYPTION_SECRET`
- AI providers: `OPENAI_API_KEY`, `GEMINI_API_KEY`, `OPENROUTER_API_KEY`
- OAuth: `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`, `SLACK_CLIENT_ID`, `SLACK_CLIENT_SECRET`
- Frontend / webhooks: `FRONTEND_URL`, `VITE_API_URL`, `VITE_WEBHOOK_BASE_URL`

Use the exact names and profile-specific requirements in the checked-in example configuration and the relevant integration setup. Do not commit real credentials, access tokens, production secrets, or populated `.env` files.

## Verification

Commands available in the repository:

```bash
# Frontend
cd frontend
npm test
npm run lint
npm run build

# Backend, from repository root
cd autoworkflow-backend
./mvnw test
./mvnw clean package
```

These are instructions to run the checks, not a statement that they have all passed against the current commit. Before calling this snapshot release-ready, run the [verification checklist](docs/VERIFICATION_CHECKLIST.md), including OAuth and live-provider checks that cannot be proven by compilation alone.

## Current limitations

- AI calls require valid provider keys, supported model identifiers, and available quota. A configured provider does not mean every model is currently available.
- OAuth-backed integrations require correctly configured client credentials, redirect URIs, scopes, and user authorization.
- Public webhook providers such as GitHub cannot call a local-only `localhost` endpoint. Use a controlled public HTTPS endpoint for external webhook testing.
- The root Compose file contains development credentials. Do not expose it as a production deployment configuration.
- A checked-in example configuration is provided, but local environment configuration may be intentionally absent from version control.
- Automated tests and live end-to-end integrations must be re-run on the current baseline before making a release-readiness claim.

## Resuming development

This project is being paused at the current baseline. No new feature is required to close this milestone.

When development resumes:

1. Pull the latest `main` branch and inspect the recent commit history.
2. Read [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) and [docs/VERIFICATION_CHECKLIST.md](docs/VERIFICATION_CHECKLIST.md).
3. Run the frontend and backend checks before changing code.
4. Re-test critical workflows and provider credentials.
5. Add new features only after the baseline is understood and verified.

For now, the priority is to preserve a clear, honest record of what is in the repository and what still needs verification.
