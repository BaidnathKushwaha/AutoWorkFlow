# Workflow Node System

AutoWorkFlow separates the node palette/configuration model from runtime execution.

## Two related concepts

### Node definition

The node_definitions table stores palette metadata in NodeDefinition: canonical typeKey, display name, category, description, icon/color, optional JSONB config schema, optional required integration, active flag, and creation timestamp.

### Node strategy

A class implementing NodeStrategy provides runtime behavior. The strategy exposes a type key and executes with a NodeExecutionContext, returning a NodeExecutionResult. NodeStrategyRegistry maps strategy keys to strategy instances and normalizes a small set of legacy aliases.

These are related but not interchangeable. A node definition does not guarantee that a strategy exists; a strategy can exist even if the palette metadata is missing or inactive.

## Execution context

The context contains user, workflow, and execution UUIDs; node ID and type; node configuration (the data object from the saved canvas node); input payload; optional loop iteration index/count/ID; optional parent loop node ID; and branch path context.

Use the context rather than introducing hidden global state when a strategy needs workflow or iteration information.

## Execution result

The result record includes success, outputPayload, optional error, optional boolean branchTaken, and optional named branchKey.

IF/AI Router-style boolean paths use branch metadata; Switch uses a named branch key. A strategy should return clear failures for invalid configuration or missing integrations instead of silently claiming success.

## Strategy families represented in source

The strategy package includes AI processing and summarization, classification, AI routing, IF/Switch/Loop/Merge/Transform logic, webhook/cron trigger handling, HTTP requests, database/file/Redis operations, and several external actions/integrations including Slack, Discord, email/SMS, GitHub/Google Sheets-related actions, and Notion-related operations. This is a source-family overview, not a promise that every strategy is fully configured in every environment. Inspect execution/strategy/ and NodeStrategyRegistry for canonical keys and aliases.

## Adding or changing a node

1. Choose a stable canonical type key.
2. Define the expected data configuration and input/output payload.
3. Implement or update a NodeStrategy.
4. Declare trigger semantics if the node is a trigger.
5. Add or update node definition and palette presentation.
6. Align frontend node type, fields, and edge handles.
7. Add validation where configuration or graph structure requires it.
8. Add unit tests for valid input, invalid config, failures, and routing.
9. Update templates that use the node.
10. Update JSONB_WORKFLOW_FORMAT.md, WORKFLOW_EXECUTION_ENGINE.md, and PROJECT_STATUS.md if capability/status changed.

## Compatibility and safety

- Keep frontend node type strings and backend strategy keys aligned.
- Treat legacy aliases as compatibility support, not preferred new values.
- Avoid logging tokens, secrets, raw private documents, or sensitive provider payloads.
- Use explicit timeouts and bounded work for network integrations.
- Do not describe a node as end-to-end verified unless a real or automated test covers the relevant path.
