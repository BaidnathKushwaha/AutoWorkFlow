# Workflow Execution Engine

This document follows the current backend classes under `execution/`. It is an implementation guide, not a performance or production-readiness claim.

## Main components

| Component | Responsibility |
|---|---|
| `WorkflowController` | Workflow CRUD/lifecycle routes and manual trigger endpoint |
| `WorkflowService` | Saves/updates workflows and manages lifecycle operations |
| `ExecutionService` | Creates execution records, calls the executor, and persists final status/logs |
| `WorkflowExecutor` | Traverses the saved node/edge graph and orchestrates strategy execution |
| `NodeStrategyRegistry` | Resolves a node type key to its registered strategy; supports legacy aliases |
| `NodeStrategy` | Contract implemented by a specific node behavior |
| `NodeExecutionContext` | Passes user/workflow/execution IDs, node config, input, and loop/branch context |
| `NodeExecutionResult` | Returns success, output payload, error, and optional routing metadata |
| `LogStep` | Records per-node execution information for the execution detail UI |
| `WorkflowValidator` | Validates graph/lifecycle requirements before relevant operations |

## Execution flow

```text
Manual API / Webhook / Scheduled trigger
                |
                v
         ExecutionService
                |
                v
          WorkflowExecutor
                |
                v
        NodeStrategyRegistry
                |
                v
      NodeStrategy.execute(context)
                |
                v
    NodeExecutionResult + LogStep
                |
                v
       Persist execution outcome
```

## Manual run versus deployment

- Manual run is exposed at `POST /api/workflows/{id}/trigger`. If the request body is omitted, the controller constructs a sample payload containing `text`, `title`, `status`, and `action`.
- Deployment is exposed at `POST /api/workflows/{id}/deploy`. The workflow service/validator applies deployment lifecycle checks.
- The public webhook endpoint is `POST /api/webhooks/{token}`. It looks up the workflow by token and rejects workflows that are not deployed before invoking the execution service.
- Scheduled and Gmail polling components can invoke the execution service with their corresponding trigger source.
- Manual testing and deployed trigger execution are distinct lifecycle paths. Re-check the service and validator before changing these rules.

## Graph traversal

The executor builds lookups for nodes and outgoing edges and uses incoming-edge counts to determine eligible starting nodes. It resolves each node's `type` through the registry and passes `data` as node configuration. The input payload is built from upstream payloads.

The executor does not rely solely on a node being visually first. Registered trigger strategies are used to distinguish legitimate trigger start nodes from disconnected action nodes. Unknown node types fail strategy resolution instead of silently succeeding.

## Error handling

A node strategy can return a failed result or throw an exception. The executor converts thrown exceptions into failed node results, records a step, and stops the current scope unless the node config sets `continueOnFail` to true. The execution service persists the final execution status and an error summary.

Do not assume `continueOnFail` means the failed node's output is valid; downstream behavior should be checked against the result and strategy implementation.

## Control flow

### IF and AI Router

Boolean branch metadata can be returned as `branchTaken`. Edges are routed by comparing their branch label/handle with that boolean.

### Switch

Named branch metadata is returned as `branchKey`. The executor follows the outgoing edge whose branch value matches the key.

### Loop

The loop path expects the loop node output to contain an array at `items`. It runs the configured/inferred body once per item, passing `item`, `index`, and `count`. The continuation receives collected `items`, `count`, and `results`. Each step can carry iteration index/count, iteration ID, parent loop node ID, and branch path.

### Merge and Transform

These are separate node strategies. Their detailed input/output semantics are strategy-specific; inspect the implementation and tests before relying on a particular merge or mapping contract.

## Persistence and observability

An execution record includes workflow/user IDs, trigger source, status, start/finish times, duration, JSONB step logs, and an optional error message. The execution API exposes a paginated list and per-execution detail. Step logs are intended to be consumed by the frontend execution-detail view.

## Extension checklist for a new node type

1. Define the canonical node type key and node configuration shape.
2. Implement a `NodeStrategy` and its `getTypeKey()`/execution behavior.
3. Mark trigger behavior explicitly if it is a trigger strategy.
4. Register node metadata for the palette where required.
5. Align frontend node type/configuration and edge handles.
6. Add validation for configuration/graph constraints as needed.
7. Add unit tests for successful output, failure, invalid input, and routing behavior.
8. Add or update a seeded template only if the template is meant to use the node.
9. Update [JSONB_WORKFLOW_FORMAT.md](JSONB_WORKFLOW_FORMAT.md), API docs if relevant, and project status.

## Verification notes

The repository contains executor and loop tests, but this documentation pass did not run the build or test suite. Run the commands in [VERIFICATION_CHECKLIST.md](VERIFICATION_CHECKLIST.md) before asserting that the current baseline passes.
