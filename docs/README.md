# AutoWorkflow Documentation Index

This directory contains implementation-oriented documentation for the repository. The Java source, frontend source, DTOs, tests, and Flyway migrations remain the source of truth when behavior changes.

## Start here

| Document | What it covers |
|---|---|
| [Architecture](ARCHITECTURE.md) | System components, responsibilities, and lifecycle |
| [Local setup](LOCAL_SETUP.md) | Development environment and configuration |
| [Project status](PROJECT_STATUS.md) | Current baseline, known gaps, and handover |
| [Verification checklist](VERIFICATION_CHECKLIST.md) | Build, tests, and manual integration checks |

## Data and persistence

| Document | What it covers |
|---|---|
| [Data model](DATA_MODEL.md) | JPA entities and their fields |
| [Database schema](DATABASE_SCHEMA.md) | PostgreSQL tables, indexes, constraints, and migrations |
| [Entity relationships](ENTITY_RELATIONSHIPS.md) | Foreign keys, ownership, cascades, and uniqueness |
| [Workflow JSONB format](JSONB_WORKFLOW_FORMAT.md) | React Flow node/edge structures, runtime payloads, branch and loop metadata |

## Runtime and APIs

| Document | What it covers |
|---|---|
| [Workflow execution engine](WORKFLOW_EXECUTION_ENGINE.md) | Execution service, graph traversal, strategies, failures, control flow |
| [API reference](API_REFERENCE.md) | Controller route inventory, request behavior, authentication notes |

## Keeping documentation accurate

- Document observed implementation, not future plans as completed features.
- When a schema changes, update the Flyway migration and the data/schema/relationship docs together.
- When a node or graph contract changes, update the frontend, strategy, validator, tests, and JSONB format doc together.
- When a controller or DTO changes, update the API inventory and use the generated OpenAPI schema for exact payload definitions.
- Never include actual credentials, tokens, API keys, private webhook URLs, or populated environment files.
- The documentation pass does not itself prove the project builds or passes tests. Use the verification checklist.
