# AutoWorkflow API Reference

This is an endpoint inventory derived from controller annotations in the current backend source. It is not a generated OpenAPI specification and does not attempt to guess every DTO field. Use the running Springdoc/OpenAPI output and DTO source for exact request/response schemas.

## Conventions

- Backend API base URL defaults to `http://localhost:8080`.
- Most application routes use an `ApiResponse<T>` envelope. Paginated endpoints return a `PageResponse<T>` inside that envelope.
- Routes that use `CurrentUserProvider` require an authenticated user. Authentication rules should also be checked in the Spring Security configuration.
- The public webhook route is intentionally unauthenticated; its token in the URL is the secret.
- UUID path parameters must be valid UUID strings.
- Do not include real tokens, API keys, or personal data in examples.

## Authentication — `/api/auth`

| Method | Route | Purpose |
|---|---|---|
| POST | `/signup` | Create an account |
| POST | `/login` | Authenticate with credentials |
| POST | `/google` | Authenticate using a Google ID token supplied by the frontend |
| POST | `/refresh` | Refresh authentication tokens |
| POST | `/logout` | Revoke/logout using a refresh token |

Source: `auth/AuthController.java`.

## User profile and AI preferences — `/api/users`

| Method | Route | Purpose |
|---|---|---|
| GET | `/me` | Get current user's profile |
| PUT | `/me` | Update profile |
| POST | `/me/api-key` | Generate an API key |
| GET | `/me/api-key/reveal` | Reveal the current API key through the protected API |
| GET | `/me/ai-preferences` | Read AI preferences |
| PUT | `/me/ai-preferences` | Update AI preferences |

Source: `user/UserController.java`. API-key reveal should be treated as sensitive and must remain protected.

## Workflows — `/api/workflows`

| Method | Route | Purpose |
|---|---|---|
| GET | `/` | List workflows; supports optional `search`, `status`, `page` (default 0), `size` (default 20) |
| POST | `/` | Create workflow; returns 201 |
| GET | `/{id}` | Fetch a workflow by ID |
| PUT | `/{id}` | Save/update a workflow |
| POST | `/{id}/deploy` | Deploy workflow |
| PATCH | `/{id}/toggle` | Toggle active state |
| DELETE | `/{id}` | Delete workflow |
| POST | `/{id}/trigger` | Manually run a workflow, optionally with a JSON input payload |

Source: `workflow/WorkflowController.java`.

When `POST /{id}/trigger` receives no body or a JSON null body, the controller uses a sample input with `text`, `title`, `status`, and `action`. A successful trigger response is a `TriggerRunResponse` containing execution ID and status; inspect its DTO for exact field names.

## Executions — `/api/executions`

| Method | Route | Purpose |
|---|---|---|
| GET | `/` | List executions for the current user; supports `page` and `size` |
| GET | `/{id}` | Fetch execution detail and step logs |

Source: `execution/ExecutionController.java`.

## Webhooks — `/api/webhooks`

| Method | Route | Purpose |
|---|---|---|
| POST | `/{token}` | Receive an external trigger payload and execute the deployed workflow |

Source: `webhook/WebhookController.java`.

The controller looks up the workflow by webhook token, checks that it is deployed, then passes the request body to the execution service. The body is optional; an omitted body becomes an empty JSON object. Keep webhook tokens secret and use an externally reachable HTTPS endpoint for real external systems.

## Integrations — `/api/integrations`

| Method | Route | Purpose |
|---|---|---|
| GET | `/` | List current user's integration connections |
| GET | `/oauth/{provider}` | Start OAuth and return an authorization URL |
| POST | `/oauth/{provider}/callback` | Exchange an authorization code for tokens using a state value |
| GET | `/oauth/{provider}/callback` | Handle provider browser redirect and redirect back to frontend |
| POST | `/key/{provider}` | Connect a provider using an API key |
| DELETE | `/{provider}` | Disconnect a provider |

Source: `integration/IntegrationController.java`.

The Google browser callback can use the shared `google` transport route while recovering the actual provider from server-side OAuth state. Provider support depends on the configured OAuth client registry. See also the specialized integration controllers listed below.

## Assistant — `/api/assistant`

| Method | Route | Purpose |
|---|---|---|
| POST | `/chat` | Submit an assistant chat request |
| GET | `/conversations` | List current user's conversations |
| GET | `/conversations/{id}/messages` | Get conversation history |
| DELETE | `/conversations/{id}` | Delete a conversation |

Source: `assistant/AssistantController.java`.

## Other controller families

The repository also contains controllers for these route families. Inspect each controller for current operations and exact DTO schemas:

| Base route | Area |
|---|---|
| `/api/nodes` | Node definitions/palette |
| `/api/templates` | Workflow templates and template import |
| `/api/dashboard` | Dashboard metrics |
| `/api/integrations/github` | GitHub operations |
| `/api/integrations/slack` | Slack operations |

Do not assume every external integration has the same routes or authorization behavior. The generic integration connection routes and the provider-specific action routes serve different purposes.

## Error handling and response schemas

The backend uses shared exception handling and an `ApiResponse` wrapper. Exact error status codes/messages depend on exception type and handler configuration. Consult:
- request/response records under each module's `dto/` package;
- `common/response/ApiResponse.java` and `PageResponse.java`;
- the global exception handler;
- Springdoc output from the running backend.

## Generate or inspect the live OpenAPI document

With the backend running and Springdoc enabled, inspect the configured OpenAPI JSON/UI paths from the project's Springdoc configuration. The common Springdoc defaults are often `/v3/api-docs` and `/swagger-ui/index.html`, but confirm the active configuration before relying on those URLs.

This documentation pass inventoried controller routes; it did not start the backend or verify the live OpenAPI page.
