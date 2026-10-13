# Local Development Setup

This guide covers the repository's local development path. Use development-only credentials and never expose these defaults to the internet.

## Prerequisites

- Git
- Java 21 for the backend
- Node.js and npm compatible with the checked-in Vite toolchain
- Docker with Docker Compose, or a separately managed PostgreSQL instance
- Provider/OAuth credentials only for features that call external services

## 1. Clone the repository

```bash
git clone https://github.com/BaidnathKushwaha/AutoWorkFlow.git
cd AutoWorkFlow
```

## 2. Start PostgreSQL

The root `docker-compose.yml` defines a local PostgreSQL service:

```bash
docker compose up -d postgres
docker compose ps
```

Review the Compose file before use. Its database credentials are for local development. If you use a different database configuration, set the matching backend environment variables.

## 3. Configure the backend

The example configuration is:

`autoworkflow-backend/src/main/resources/application.example.yml`

The tracked example references these variable groups:

| Purpose | Variables |
|---|---|
| Database | `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| Application security | `JWT_SECRET`, `ENCRYPTION_SECRET` |
| AI providers | `OPENAI_API_KEY`, `GEMINI_API_KEY`, `OPENROUTER_API_KEY` |
| GitHub OAuth | `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` |
| Google OAuth | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` |
| Slack OAuth | `SLACK_CLIENT_ID`, `SLACK_CLIENT_SECRET` |

Additional integration-specific settings may be needed by individual nodes or profiles. Consult the backend configuration classes and the relevant provider's setup screen.

Create a local configuration file from the example if that matches the application's active configuration mechanism, or export environment variables in your shell. The repository intentionally ignores real application YAML files and environment files. Never add secrets to Git.

Use strong, unique values for `JWT_SECRET` and `ENCRYPTION_SECRET`. The exact length/format must satisfy the backend implementation. Do not use example values from Compose for any shared or public environment.

## 4. Start the backend

```bash
cd autoworkflow-backend
./mvnw spring-boot:run
```

If the wrapper is not executable on Linux/macOS:

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

Confirm that the application starts and that database migrations complete. Swagger UI is configured through Springdoc; when the app is running and the active security configuration permits it, try `http://localhost:8080/swagger-ui.html`.

## 5. Start the frontend

Open another terminal from the repository root:

```bash
cd frontend
npm install
npm run dev
```

The frontend API URL defaults to `http://localhost:8080`. To override it, set `VITE_API_URL` in a local frontend environment file. For webhooks from an external service, configure `VITE_WEBHOOK_BASE_URL` to an externally reachable HTTPS URL.

## 6. Configure OAuth providers

For every provider you want to use:

1. Register or select the OAuth application in the provider console.
2. Set the client ID and client secret in the backend environment.
3. Set the exact callback URL expected by the backend.
4. Configure only the scopes required by the integration.
5. Connect the account in the application.
6. Run an actual workflow action and inspect its result.

A redirect URI mismatch, invalid client credentials, missing scope, expired token, or provider quota can cause integration failure even when the application itself starts correctly.

## 7. AI provider setup

Set the key for the provider you intend to test. Confirm that the chosen model is currently supported by the provider and that the account has available quota. A configured API key is not proof of successful provider compatibility. Check backend logs and the execution record for errors.

## 8. GitHub webhook setup

A GitHub server cannot call a bare `localhost` URL on your computer. For local testing, expose the backend through a temporary public HTTPS tunnel or use a controlled hosted endpoint. Set `VITE_WEBHOOK_BASE_URL` to the reachable base URL and configure the webhook URL and secret/token as required by the application's GitHub trigger implementation. Do not publish temporary tunnel URLs as permanent production endpoints.

## 9. Stop local services

```bash
# From repository root
docker compose down
```

This stops containers but normally preserves named volumes. To remove local database data too, explicitly remove the relevant volume after confirming that no needed data remains.

## Troubleshooting

- **Backend does not start:** check Java version, environment variables, active profile, database connectivity, and migration logs.
- **Database connection refused:** check `docker compose ps`, port 5432, and database settings.
- **Frontend cannot reach API:** check backend startup, `VITE_API_URL`, and CORS configuration.
- **OAuth redirect error:** compare the callback URL exactly with the provider console and verify client credentials.
- **AI quota/model errors:** verify account quota and the current provider model identifier.
- **Webhook never fires:** verify that the endpoint is publicly reachable over HTTPS and that the configured token/secret matches.
- **Integration authorization succeeds but an action fails:** inspect scopes, provider API responses, token state, and the execution log.

This guide documents the setup path; it does not certify the current environment as production-ready.
