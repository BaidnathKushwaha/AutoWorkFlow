# AutoWorkflow Backend

Spring Boot backend for AutoWorkflow, the React/React Flow workflow automation application.

## Technology

- Java 21 and Spring Boot 3.3
- Spring Web and WebFlux clients
- Spring Data JPA / Hibernate
- PostgreSQL and Flyway
- Spring Security and JWT
- Springdoc OpenAPI
- Quartz / cron-utils for scheduled workflow support
- Optional Redis dependency for Redis-related functionality
- OpenAI, Gemini, and OpenRouter provider configuration

## Main responsibilities

- Authentication and protected API operations
- Workflow CRUD and lifecycle operations
- Validation for execution and deployment
- Graph execution through `WorkflowExecutor`
- Node behavior through `NodeStrategyRegistry` and node-specific strategies
- AI provider routing and AI workflow nodes
- Integration records, OAuth flows, and external API clients
- Trigger handling, execution persistence, and execution logs

## Package layout

```text
com.autoworkflow/
├── auth/
├── user/
├── workflow/
├── execution/
│   ├── engine/
│   ├── strategy/
│   └── validation/
├── integration/
├── assistant/
├── webhook/
├── scheduler/
├── security/
├── config/
└── common/
```

Treat the source tree as authoritative if package names or module boundaries change.

## Local setup

Start PostgreSQL using the root Compose file or a local PostgreSQL instance. Configure environment variables according to `src/main/resources/application.example.yml`. At minimum, configure database access and strong application secrets. AI and OAuth keys are required only for the corresponding capabilities.

Run the backend:

```bash
./mvnw spring-boot:run
```

Run tests and package:

```bash
./mvnw test
./mvnw clean package
```

Springdoc OpenAPI is configured; Swagger UI is normally available at `/swagger-ui.html` when the application is running and access is permitted by the active security configuration.

## Execution model

The execution service passes the saved workflow graph to `WorkflowExecutor`. The executor resolves each node type through the node strategy registry and runs the corresponding strategy. Node results are passed through the graph and execution information is persisted for the frontend to display.

The project distinguishes manual testing from deployment validation. Manual execution is intended to support testing without requiring an event trigger; deployment applies trigger-oriented validation. Re-run lifecycle checks whenever this behavior changes.

## Configuration

See `src/main/resources/application.example.yml` for tracked variable names, including:

- `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `JWT_SECRET`, `ENCRYPTION_SECRET`
- `OPENAI_API_KEY`, `GEMINI_API_KEY`, `OPENROUTER_API_KEY`
- `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`
- `SLACK_CLIENT_ID`, `SLACK_CLIENT_SECRET`

Additional configuration can be required by an individual integration or active Spring profile. Keep all real credentials outside Git.

## Important limitations

- Configuring a provider does not guarantee model availability, quota, or a successful request.
- OAuth-backed integrations require valid credentials, exact callback URLs, required scopes, and working provider APIs.
- External webhook delivery requires a reachable endpoint, normally public HTTPS.
- Redis-related behavior depends on service configuration.
- Development Compose defaults are not production secrets.
- This README does not claim that the current baseline has passed all tests or live integration checks. Run the root [verification checklist](../docs/VERIFICATION_CHECKLIST.md) before release or major changes.
