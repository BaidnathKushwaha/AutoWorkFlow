# AutoWorkflow Database Schema

This document describes the PostgreSQL schema from the checked-in Flyway migrations. The migration scripts, not this document, are authoritative for database creation and upgrades.

## Database conventions

- PostgreSQL is the primary relational database.
- UUID primary keys use `gen_random_uuid()` in the initial schema.
- Timestamp columns generally use `TIMESTAMPTZ`.
- Flexible graph/configuration/metadata payloads use `JSONB`.
- Flyway migration scripts live under `autoworkflow-backend/src/main/resources/db/migration/`.
- Foreign-key columns are sometimes represented as scalar UUID fields in JPA entities rather than object relationships.

## Tables

### `users`

Core account fields: `id` UUID PK, `name`, unique `email`, `password_hash`, encrypted API key metadata, `role`, AI request counter, and timestamps. Later migrations add Google identity/profile fields and AI preferences. Migration V3 makes `password_hash` nullable for Google-only accounts.

### `refresh_tokens`

Fields: `id` UUID PK, `user_id` FK to `users` with cascade delete, unique `token_hash`, `expires_at`, `revoked`, and `created_at`. Index: `idx_refresh_tokens_user`.

### `node_definitions`

Fields: `id`, unique `type_key`, `display_name`, `category`, `description`, `icon`, `color`, JSONB `config_schema`, `requires_integration`, `is_active`, and `created_at`. Index: `idx_node_definitions_category`.

### `workflows`

Fields: `id`, `user_id` FK to users (cascade delete), `name`, `description`, `status`, `is_deployed`, `trigger_type`, JSONB `trigger_config`, JSONB `canvas_nodes`, JSONB `canvas_edges`, `executions_count`, `last_run_at`, unique `webhook_token`, and timestamps. Indexes: `idx_workflows_user`, `idx_workflows_status`.

### `executions`

Fields: `id`, `workflow_id` FK to workflows (cascade delete), `user_id` FK to users (cascade delete), `status`, `triggered_by`, `duration_ms`, JSONB `steps_logs`, `error_message`, `started_at`, and `finished_at`. Indexes: `idx_executions_workflow`, `idx_executions_user`, `idx_executions_status`, and descending `idx_executions_started_at`.

### `integrations`

Fields: `id`, `user_id` FK to users (cascade delete), `provider`, `account_label`, encrypted access/refresh token columns, text `scopes`, `status`, `last_checked_at`, `token_expires_at`, and timestamps. Unique constraint: `(user_id, provider)`. Index: `idx_integrations_user`.

### `templates`

Initial fields: `id`, `name`, `description`, `trigger_icon_key`, `target_icon_key`, JSONB `canvas_nodes`, JSONB `canvas_edges`, `is_active`, and `created_at`. Migration V9 adds `category`, `difficulty`, and text `integration_requirements`.

### `assistant_conversations`

Fields: `id`, `user_id` FK to users (cascade delete), `title`, `created_at`, `updated_at`. Index: `idx_assistant_conv_user`.

### `assistant_messages`

Fields: `id`, `conversation_id` FK to assistant conversations (cascade delete), `role`, `content`, JSONB `generated_workflow_json`, and `created_at`. Migration V8 adds JSONB `workflow_proposal_json` and `workflow_proposal_validation_json`. Index: `idx_assistant_messages_conv`.

### `audit_logs`

Fields: `id`, nullable `user_id` FK to users with `ON DELETE SET NULL`, `action`, `entity_type`, `entity_id`, JSONB `metadata`, and `created_at`. Index: `idx_audit_logs_user`.

### `oauth_states`

Created by migration V9. Fields: `id`, unique `state_hash`, `user_id` FK to users (cascade delete), `provider`, `expires_at`, and nullable `used_at`. Index: `idx_oauth_states_expires`; a unique hash index is also declared in the entity mapping.

### `gmail_trigger_states`

Created by migration V9. Fields: `id`, unique `workflow_id` FK to workflows (cascade delete), `history_id`, and `updated_at`. It stores a per-workflow Gmail history cursor.

## Relationship summary

```text
users
 ├── refresh_tokens
 ├── workflows
 │    ├── executions
 │    └── gmail_trigger_states (zero or one per workflow)
 ├── integrations
 ├── assistant_conversations
 │    └── assistant_messages
 ├── oauth_states
 └── audit_logs (user reference becomes NULL on user deletion)

node_definitions     (catalog metadata; not a per-workflow FK)
templates            (reusable node/edge JSON; separate from saved workflows)
```

The diagram is logical. For exact referential constraints, consult the migration files.

## Migration inventory confirmed during documentation

| Migration | Purpose visible from the filename/content |
|---|---|
| `V1__init_schema.sql` | Initial application tables and indexes |
| `V2__seed_nodes.sql` | Seed node definitions/templates |
| `V3__add_google_oauth.sql` | Make password nullable; add Google ID and avatar URL |
| `V4__update_gemini_models.sql` | Update model identifiers in stored canvas JSON |
| `V6__add_ai_preferences_to_users.sql` | Add AI provider/model preference columns |
| `V7__add_ai_mode_and_normalize_ai_preferences.sql` | Add AI mode and enforce preference-state consistency |
| `V8__persist_workflow_proposal.sql` | Add assistant workflow proposal JSONB fields |
| `V9__phase4_oauth_templates_and_gmail_trigger.sql` | OAuth state, Gmail trigger cursor, template metadata, and seed updates |

The inventory above lists migrations whose names/content were directly inspected. Confirm the complete directory before assuming no other migration exists; do not rename or edit an already-applied Flyway migration as a casual schema change.

## Schema and entity drift to watch

1. `V1` initially declares `password_hash NOT NULL`; V3 explicitly relaxes this constraint for Google-only accounts.
2. `User.java` includes `google_id`, `avatar_url`, `ai_mode`, `ai_provider`, and `ai_model`, which are introduced/changed by later migrations.
3. `AssistantMessage.java` includes two proposal JSON fields added in V8.
4. `templates` gains category/difficulty/integration requirement columns in V9.
5. The `audit_logs` table exists in V1, but a mapped JPA entity was not confirmed during the source search.

When preparing a fresh database, run the configured Flyway migrations. Do not manually create a schema from a partial table summary.
