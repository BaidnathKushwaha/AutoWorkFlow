# Workflow JSONB Format

AutoWorkFlow stores a React Flow graph in the workflow's `canvas_nodes` and `canvas_edges` JSONB columns. Templates use the same general graph representation. The frontend's saved node/edge objects are passed to the backend executor; they are not normalized into one relational row per node.

This guide documents the fields used by the current code paths and checked-in template seed examples. Node-specific `data` fields vary by strategy, so there is no single universal configuration object.

## 1. Node shape

A canvas node generally follows React Flow's shape:

```json
{
  "id": "2",
  "type": "ai",
  "position": { "x": 420, "y": 100 },
  "data": {
    "label": "Resume Matcher",
    "provider": "default",
    "structuredOutput": true,
    "prompt": "Match the resume in {{body}} against this job description."
  }
}
```

Fields used by the graph/executor:
- `id`: node identifier; edges refer to it.
- `type`: strategy type key used by `NodeStrategyRegistry`, with a small alias map for legacy keys.
- `position`: visual canvas coordinates.
- `data`: node-specific configuration. The executor passes this object as node configuration; absent data is treated as an empty object.
- `data.label`: optional display label used in execution logs, falling back to the node type.

This example is based on a seeded template pattern. It is not a full JSON Schema for all node types.

## 2. Edge shape

A basic edge looks like:

```json
{
  "id": "e1-2",
  "source": "1",
  "target": "2"
}
```

Control-flow edges may carry branch/handle metadata:

```json
{
  "id": "e2-3",
  "source": "2",
  "target": "3",
  "sourceHandle": "true",
  "data": { "branch": "true" }
}
```

The executor reads the source and target node IDs. For routing, it checks `data.branch` first and falls back to `sourceHandle`. Exact handle values depend on the source node strategy and the frontend's edge configuration.

## 3. Storage envelope

The graph is stored as two separate JSONB values, not as a wrapper object:

```text
workflows.canvas_nodes = [ node, node, ... ]
workflows.canvas_edges = [ edge, edge, ... ]
```

The same pair of fields exists on `templates`. The `trigger_config` field is a separate optional JSONB value on a workflow.

## 4. Runtime input/output

The executor builds an input payload for each node:
- If the node has zero or one incoming edge, the first available upstream payload is used, or an empty object if there is no payload.
- If a node has multiple incoming edges, inputs are wrapped as an object with an `inputs` array.
- A node strategy returns a `NodeExecutionResult` containing success, output payload, optional error, and optional routing metadata.
- If the result has no output payload, the executor falls back to the input payload for downstream propagation.

The exact input and output fields are strategy-specific. Do not assume every node emits the same JSON shape.

## 5. Branch routing

`NodeExecutionResult` supports:
- `branchTaken`: boolean routing metadata used by IF/AI Router-style paths.
- `branchKey`: named branch key used by Switch-style routing.

For a result with a named branch key, the executor compares that key to the edge branch value. For boolean branch results, it compares the edge branch string parsed as a boolean. If no routing metadata is present, outgoing edges are not filtered by these rules.

Illustrative named branch:

```json
{
  "id": "e2-4",
  "source": "switch-1",
  "target": "strong-match-action",
  "data": { "branch": "strong" }
}
```

The branch name must agree with the key returned by the Switch strategy. The example label `strong` is illustrative, not a universal built-in route.

## 6. Loop metadata

The Loop execution path uses node configuration fields `bodyStartNodeId` and `continuationNodeId`, or infers the body/continuation from outgoing edge handles `loop_body` and `loop_continuation`. A loop node's output must contain an array at `items`.

Each body iteration receives an object with `item`, `index`, and `count`. The executor records iteration metadata in the execution step log. After iteration, the continuation input contains `items`, `count`, and `results`.

A non-empty loop requires a resolvable body start and continuation. The executor fails clearly if these are missing or the body start/continuation ID does not exist.

## 7. Execution step log

The execution entity stores `steps_logs` as JSONB. Each step is built from the `LogStep` model and includes fields for the node ID/label, status, start/end timing, input/output, error, duration, and loop/branch context where applicable. Consult `execution/engine/LogStep.java` and the frontend execution-detail types for the exact current serialized property names.

## 8. Validation and compatibility

- Node `type` must resolve to a registered strategy at runtime.
- Node IDs and edge source/target IDs must agree.
- Branch labels/handles must match strategy output.
- Loop body/continuation metadata must refer to valid graph nodes.
- Legacy node type aliases are normalized by `NodeStrategyRegistry`; new node types should use canonical keys.
- Keep node type keys synchronized between the frontend palette, node definitions, and registered backend strategies.

When changing the canvas format, update frontend serialization, backend parsing/execution, validation, templates, tests, and this document together.
