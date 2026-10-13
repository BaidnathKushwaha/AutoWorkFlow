# AutoWorkflow Entity Relationships

This is a logical relationship guide based on Flyway foreign keys and JPA model fields. It does not imply that every relationship is represented by a JPA object association.

## Relationship map

```text
User (users)
 ├──< RefreshToken (refresh_tokens)
 ├──< Workflow (workflows)
 │     ├──< Execution (executions)
 │     └── 0..1 GmailTriggerState (gmail_trigger_states)
 ├──< Integration (integrations)
 ├──< AssistantConversation (assistant_conversations)
 │     └──< AssistantMessage (assistant_messages)
 ├──< OAuthState (oauth_states)
 └──< AuditLog (audit_logs; nullable user reference)

NodeDefinition (node_definitions)  [node catalogue]
Template (templates)               [reusable graph templates]
```

Legend: `1 ──< many` means one parent can have many children. `0..1` means zero or one child per parent.

## Relationships and constraints

| Parent | Child | Key | Database behavior |
|---|---|---|---|
| `users` | `refresh_tokens` | `refresh_tokens.user_id` | User deletion cascades |
| `users` | `workflows` | `workflows.user_id` | User deletion cascades |
| `workflows` | `executions` | `executions.workflow_id` | Workflow deletion cascades to execution history |
| `users` | `executions` | `executions.user_id` | User deletion cascades |
| `users` | `integrations` | `integrations.user_id` | User deletion cascades |
| `users` | `assistant_conversations` | `assistant_conversations.user_id` | User deletion cascades |
| `assistant_conversations` | `assistant_messages` | `assistant_messages.conversation_id` | Conversation deletion cascades |
| `users` | `oauth_states` | `oauth_states.user_id` | User deletion cascades |
| `workflows` | `gmail_trigger_states` | `gmail_trigger_states.workflow_id` | Workflow deletion cascades; workflow ID is unique |
| `users` | `audit_logs` | `audit_logs.user_id` | User deletion sets the reference to NULL |

Additional uniqueness rules:
- `users.email` is unique.
- `users.google_id` is unique when populated, through the Google OAuth migration's partial unique index.
- `refresh_tokens.token_hash` is unique.
- `node_definitions.type_key` is unique.
- `workflows.webhook_token` is unique.
- `integrations(user_id, provider)` is unique.
- `oauth_states.state_hash` is unique.
- `gmail_trigger_states.workflow_id` is unique.

## Why some relationships are scalar IDs

Several Java entities store related IDs as `UUID` fields rather than using `@ManyToOne` or `@OneToMany`. Application services/repositories therefore resolve related records explicitly. The SQL foreign keys remain important for integrity and delete behavior.

The node catalogue and templates are not joined to saved workflow nodes through a relational foreign key. A workflow's graph is stored as JSONB. The `type` field in a canvas node is resolved by node-type key at runtime; see [JSONB_WORKFLOW_FORMAT.md](JSONB_WORKFLOW_FORMAT.md) and [WORKFLOW_EXECUTION_ENGINE.md](WORKFLOW_EXECUTION_ENGINE.md).

## Data lifecycle implications

- Deleting a workflow also deletes its executions and Gmail trigger cursor because of database cascade constraints.
- Deleting a user cascades through workflows, executions, integrations, refresh tokens, assistant conversations, and OAuth states according to the migration constraints.
- Audit logs are designed to retain their row when a user is deleted; the user reference becomes null.
- OAuth state records are short-lived authorization state and should not be treated as permanent integration credentials.
- Integration tokens are stored in encrypted columns; raw tokens must not be added to logs or docs.

See [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) for table-level summaries and migration details.
