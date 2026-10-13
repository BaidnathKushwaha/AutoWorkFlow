# AutoWorkflow Architecture

This document describes the architecture represented in the repository. It is a code-oriented overview, not a claim that every optional service is configured in every environment.

## System overview

```text
React / Vite / React Flow
          │ REST / HTTP
          ▼
Spring Boot backend
  ├── Auth and security
  ├── Workflow API and lifecycle
  ├── Workflow validation
  ├── Execution service
  │     └── WorkflowExecutor
  │            └── NodeStrategyRegistry
  │                    └── Node-specific strategies
  ├── AI provider routing
  ├── OAuth and integrations
  └── Persistence / execution logs
          │
          ▼
PostgreSQL (workflow graph JSON, records, migrations)
```

## Frontend

The frontend lives in `frontend/` and uses React, Vite, React Flow (`@xyflow/react`), Zustand, and Axios. It provides the visual workflow canvas and user-facing workflow and execution interactions. The API base URL can be overridden with `VITE_API_URL`. The externally reachable webhook origin can be configured with `VITE_WEBHOOK_BASE_URL`.

## Backend modules

The backend lives in `autoworkflow-backend/`. Important areas include:

- `auth/` and `security/`: authentication, JWT handling, and protected API access.
- `workflow/`: workflow persistence and lifecycle operations.
- `execution/`: execution service, graph executor, validation, and node strategies.
- `integration/`: integration records, OAuth authorization/callback handling, and external-service clients.
- `assistant/`: AI assistant-related functionality.
- `webhook/` and scheduling components: trigger entry points.
- `config/` and `common/`: application configuration, API support, shared types, and error handling.

Use the current source tree as the authority if package boundaries have changed since this document was written.

## Graph representation

The frontend canvas is represented by nodes and edges. The backend persists graph data in JSON/JSONB-compatible fields and passes it to the execution engine.

Conceptually, a node contains an identifier, a type, configuration data, and UI position. An edge identifies a source node and target node, and may include handle or branch metadata used by control-flow nodes.

Illustrative shape only:

```json
{
  "nodes": [
    {
      "id": "node-a",
      "type": "summarizer",
      "data": {},
      "position": { "x": 100, "y": 100 }
    }
  ],
  "edges": []
}
```

This is a conceptual illustration, not a complete API payload or schema contract. Use the actual frontend serialization and backend DTOs as the source of truth.

## Execution model

1. An API or trigger path requests execution.
2. The execution service performs the relevant validation and lifecycle checks.
3. `WorkflowExecutor` traverses the graph and resolves node behavior through `NodeStrategyRegistry`.
4. Each node strategy performs its operation and returns a result for downstream nodes.
5. Execution steps and terminal state are persisted for inspection.

The strategy approach keeps node-specific behavior separate from graph traversal. Control-flow strategies can select outgoing paths based on condition or branch metadata.

## Manual run versus deployment

The project distinguishes workflow testing from activation:

- **Manual execution** is for testing a saved workflow. In the implemented lifecycle, it does not inherently require a trigger node.
- **Deployment** is intended for event-driven execution and applies stricter trigger validation.
- **Graph edits after deployment** should invalidate the previous deployment so a changed graph is not silently treated as the previously deployed version.

Re-test these behaviors after changing validation or lifecycle code.

## AI provider layer

The repository includes provider configuration/routing for OpenAI, Gemini, and OpenRouter. Availability depends on valid API keys, account quota, model availability, provider-specific request formats, and network access. Provider configuration alone does not guarantee a successful completion.

Keep provider selection and node orchestration conceptually separate: the workflow executor decides when a node runs; the AI provider layer handles the configured AI request.

## Persistence and integrations

PostgreSQL stores application data, and Flyway is used for schema migrations. Workflow canvas data uses JSON-oriented persistence. External integrations rely on their own credentials, OAuth grants, scopes, and API availability. A successful OAuth callback does not prove every downstream action works, so integration verification must include an actual API operation.

## Security boundaries

- Protected API operations must be authorized for the authenticated user.
- Secrets must be supplied through environment configuration or a secret manager, not committed to source control.
- OAuth redirect URIs must match provider-console configuration exactly.
- Webhook endpoints exposed to third parties require a reachable HTTPS URL and appropriate secret/token handling.
- Development Compose credentials are not suitable for production.

## Design constraints

The architecture prioritizes visual orchestration, strategy-based node execution, explicit workflow lifecycle rules, and observable execution state. Production deployment, full integration reliability, and broad end-to-end coverage require separate verification and are not implied by this architecture description.
