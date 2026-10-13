# AutoWorkflow Data Model

This reference is derived from the JPA entities and checked-in Flyway migrations in the repository. It documents the current code model, not a promise that every database environment has applied every migration. For SQL types, constraints, and migration history, see [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md).

## Modeling conventions

- Primary identifiers are UUIDs.
- Timestamps use Java `Instant` and PostgreSQL `TIMESTAMPTZ` in the schema.
- Workflow canvas and structured assistant payloads use Jackson `JsonNode` mapped to PostgreSQL `JSONB`.
- Several entities store related UUIDs as scalar fields instead of declaring JPA `@ManyToOne` relationships. The database migration files define the actual foreign-key constraints.
- Secrets and OAuth tokens are intended to be encrypted before persistence. Never put real credentials in examples, logs, or documentation.

## Entity catalogue

### User — `users`

Source: `user/User.java`

| Field | Java type | Meaning |
|---|---|---|
| `id` | `UUID` | User primary key |
| `name` | `String` | Display name, required, max 150 |
| `email` | `String` | Unique email, required, max 255 |
| `passwordHash` | `String` | Password hash; nullable for Google-only accounts after migration V3 |
| `googleId` | `String` | Google subject identifier, unique when present |
| `avatarUrl` | `String` | Optional profile image URL |
| `apiKeyEncrypted` | `String` | Encrypted generated API key |
| `apiKeyLastFour` | `String` | Last four characters for display |
| `aiMode` | `AiMode` | `AUTO` or `SPECIFIC`, default `AUTO` |
| `aiProvider` | `String` | Optional provider preference |
| `aiModel` | `String` | Optional model preference |
| `role` | `UserRole` | Application role, default `USER` |
| `aiRequestsCount` | `Long` | AI request counter |
| `createdAt`, `updatedAt` | `Instant` | Creation/update timestamps |

The migrations V3, V6, and V7 evolve the initial user table to support Google identity and AI preferences. See the migration notes in [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md).

### Workflow — `workflows`

Source: `workflow/Workflow.java`

| Field | Java type | Meaning |
|---|---|---|
| `id` | `UUID` | Workflow primary key |
| `userId` | `UUID` | Owning user |
| `name` | `String` | Required name, max 200 |
| `description` | `String` | Optional description |
| `status` | `WorkflowStatus` | Lifecycle status; Java default is `DRAFT` |
| `deployed` | `boolean` | Whether the workflow is deployed |
| `triggerType` | `String` | Trigger type label/key |
| `triggerConfig` | `JsonNode` | Optional trigger configuration JSON |
| `canvasNodes` | `JsonNode` | React Flow node array, stored as JSONB |
| `canvasEdges` | `JsonNode` | React Flow edge array, stored as JSONB |
| `executionsCount` | `Long` | Execution count |
| `lastRunAt` | `Instant` | Last execution timestamp |
| `webhookToken` | `String` | Unique token used by the webhook route |
| `createdAt`, `updatedAt` | `Instant` | Timestamps |

The current Java entity uses UUID fields for `userId` and does not declare a JPA association. The database migration defines the user foreign key.

### Execution — `executions`

Source: `execution/Execution.java`

| Field | Java type | Meaning |
|---|---|---|
| `id` | `UUID` | Execution primary key |
| `workflowId` | `UUID` | Executed workflow |
| `userId` | `UUID` | User associated with execution |
| `status` | `ExecutionStatus` | Execution state; Java default is `RUNNING` |
| `triggeredBy` | `TriggeredBy` | Trigger source |
| `durationMs` | `Long` | Total duration in milliseconds |
| `stepsLogs` | `JsonNode` | JSONB step log array |
| `errorMessage` | `String` | Optional top-level error |
| `startedAt`, `finishedAt` | `Instant` | Start and finish timestamps |

The SQL migration comments list historical status/trigger values. Treat Java enums and current service logic as the runtime source of truth.

### Integration — `integrations`

Source: `integration/Integration.java`

| Field | Java type | Meaning |
|---|---|---|
| `id` | `UUID` | Integration record ID |
| `userId` | `UUID` | Owning user |
| `provider` | `String` | Provider key |
| `accountLabel` | `String` | Human-readable connected account |
| `encryptedAccessToken` | `String` | Encrypted access token or API key |
| `encryptedRefreshToken` | `String` | Encrypted refresh token when available |
| `scopes` | `List<String>` | Granted scopes, converted to/from text |
| `status` | `IntegrationStatus` | Connection state |
| `lastCheckedAt` | `Instant` | Last health check timestamp |
| `tokenExpiresAt` | `Instant` | Token expiry, when known |
| `createdAt`, `updatedAt` | `Instant` | Timestamps |

A database unique constraint prevents duplicate records for the same `(user_id, provider)` pair.

### NodeDefinition — `node_definitions`

Source: `node/NodeDefinition.java`

| Field | Java type | Meaning |
|---|---|---|
| `id` | `UUID` | Definition ID |
| `typeKey` | `String` | Unique node type key |
| `displayName` | `String` | Palette label |
| `category` | `NodeCategory` | Node category |
| `description` | `String` | Optional description |
| `icon`, `color` | `String` | Presentation metadata |
| `configSchema` | `JsonNode` | Optional JSONB configuration schema |
| `requiresIntegration` | `String` | Optional provider requirement |
| `active` | `boolean` | Whether definition is active |
| `createdAt` | `Instant` | Creation timestamp |

A node definition describes palette/configuration metadata. Runtime execution is separately resolved by `NodeStrategyRegistry`; a palette entry alone does not prove an execution strategy is registered.

### RefreshToken — `refresh_tokens`

Source: `auth/RefreshToken.java`

Fields: `id` UUID, `userId` UUID, `tokenHash` string, `expiresAt` Instant, `revoked` boolean, and `createdAt` Instant. The token value is stored as a hash, not as the raw refresh token. The schema enforces a unique token hash and a user foreign key.

### Template — `templates`

Source: `template/Template.java`

Fields: `id`, `name`, `description`, `category`, `difficulty`, `integrationRequirements`, `triggerIconKey`, `targetIconKey`, `canvasNodes`, `canvasEdges`, `active`, and `createdAt`. Canvas fields are JSONB. Integration requirements are represented as a list in Java and converted to text for persistence.

### AssistantConversation — `assistant_conversations`

Source: `assistant/AssistantConversation.java`

Fields: `id`, `userId`, `title`, `createdAt`, and `updatedAt`. A conversation belongs to a user.

### AssistantMessage — `assistant_messages`

Source: `assistant/AssistantMessage.java`

Fields: `id`, `conversationId`, `role`, `content`, `generatedWorkflowJson`, `workflowProposalJson`, `workflowProposalValidationJson`, and `createdAt`. The three workflow-related fields are JSONB. Proposal fields were added by a later migration rather than the initial schema.

### OAuthState — `oauth_states`

Source: `integration/oauth/OAuthState.java`

Fields: `id`, `stateHash`, `userId`, `provider`, `expiresAt`, and `usedAt`. The stored state is hashed, expires, and is intended to be consumed once. The user foreign key cascades on deletion.

### GmailTriggerState — `gmail_trigger_states`

Source: `scheduler/GmailTriggerState.java`

Fields: `id`, `workflowId`, `historyId`, and `updatedAt`. There is one cursor/state record per workflow; deleting a workflow cascades to its state.

### AuditLog — `audit_logs`

The table is created in the initial migration with fields `id`, nullable `userId`, `action`, `entityType`, `entityId`, `metadata` JSONB, and `createdAt`. The initial schema allows audit history to remain if a user is deleted by setting `user_id` to null. A dedicated JPA entity was not found in the inspected source search, so this is documented from SQL rather than as a confirmed mapped entity.

## Logical relationships

See [ENTITY_RELATIONSHIPS.md](ENTITY_RELATIONSHIPS.md) for the relationship map and delete behavior.

## JSON contracts

- [Workflow JSONB format](JSONB_WORKFLOW_FORMAT.md): node, edge, branch, loop, and step-log shapes.
- [API reference](API_REFERENCE.md): endpoint inventory from controller annotations.
- [Workflow execution engine](WORKFLOW_EXECUTION_ENGINE.md): graph traversal and strategy contracts.

## Maintenance rule

When an entity, DTO, enum, controller, or Flyway migration changes, update the corresponding document in the same change. If the JPA model and migration schema disagree, record the mismatch rather than guessing which one is deployed.
