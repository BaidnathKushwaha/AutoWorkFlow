# AutoWorkflow

> **An AI assisted visual workflow automation platform for building, testing, and executing intelligent workflows.**

AutoWorkflow is a full stack workflow automation platform built around a visual canvas. Users can compose workflows by connecting triggers, AI powered nodes, logic nodes, and integrations, then manually test or deploy those workflows for event driven execution.

The project is intentionally designed to go beyond a basic drag and drop automation clone by combining **visual workflow orchestration, AI processing, intelligent routing, standalone node testing, and execution visibility**.

---

## ✨ Current Highlights

- 🎨 Visual workflow builder powered by React Flow
- 🤖 AI powered workflow nodes
- 🧠 AI completion and summarization capabilities
- 🔀 Conditional Switch based workflow branching
- 🧪 Standalone manual execution without requiring a trigger
- 🚀 Trigger based deployment with strict deployment validation
- 🔗 Webhook based workflow triggering
- 🐙 GitHub webhook/event integration
- 📊 Execution history and execution console
- ⚡ Real time execution state feedback
- 🧩 Modular node strategy based backend execution engine
- 🔐 Authentication and protected workflow operations
- 🗄️ Persistent workflow and execution data
- 📦 PostgreSQL based persistence
- 🌐 OpenAI, Gemini, and OpenRouter provider support
- 🛡️ Workflow validation before deployment
- 🔄 Automatic invalidation of deployed workflows when their graph changes

---

# 🧠 What Problem Does AutoWorkflow Solve?

Traditional automation platforms generally require users to manually understand which trigger, action, and integration should be connected.

AutoWorkflow aims to make intelligent automation more approachable by combining:

```text
Visual Workflow
      +
AI Processing
      +
Conditional Routing
      +
External Integrations
      +
Execution Visibility
```

A workflow can therefore look like:

```text
Trigger
   ↓
AI Processing
   ↓
Switch / Logic
   ├── Condition A → Action
   ├── Condition B → Action
   └── Default     → Action
```

The goal is to let users focus on **what should happen**, rather than implementing the orchestration logic themselves.

---

# 🏗️ High Level Architecture

```text
                    ┌─────────────────────┐
                    │      Frontend       │
                    │ React + React Flow  │
                    └──────────┬──────────┘
                               │
                         REST / HTTP
                               │
                               ▼
                    ┌─────────────────────┐
                    │       Backend       │
                    │    Spring Boot      │
                    └──────────┬──────────┘
                               │
             ┌─────────────────┼─────────────────┐
             │                 │                 │
             ▼                 ▼                 ▼
       Workflow Engine    AI Provider Layer   Integrations
             │                 │                 │
             │          ┌──────┼──────┐          │
             │          │      │      │          │
             │        OpenAI Gemini OpenRouter    │
             │                                     
             ▼
       Node Strategy Registry
             │
       ┌─────┼──────────────┐
       ▼     ▼              ▼
    Trigger  AI           Logic
                       / Switch / Router
             │
             ▼
       Execution Engine
             │
             ▼
          Database
```

---

# 🎨 Visual Workflow Builder

The frontend provides a visual canvas where nodes can be dragged, configured, connected, and executed.

### Current node categories include

- Triggers
- AI nodes
- Logic nodes
- Integration/action nodes
- Output related nodes

New nodes are created in a compact state to keep larger workflows readable.

---

# 🤖 AI Capabilities

AutoWorkflow supports a provider abstraction so workflow nodes do not need to be tightly coupled to a single AI vendor.

Current provider architecture includes:

```text
AI Provider
├── OpenAI
├── Gemini
└── OpenRouter
```

This allows AI based nodes to use the provider layer without changing the workflow execution architecture.

### Current AI use cases

#### Summarization

```text
Input Text
    ↓
Summarizer
    ↓
AI generated summary
```

#### AI Completion

```text
Input
   ↓
AI Completion
   ↓
Generated response
```

AI output can also become the input for downstream logic.

---

# 🔀 Intelligent Switch / Branching

One of the major capabilities currently implemented is the **Switch node**.

The Switch allows workflow execution to branch based on the value produced by an earlier node.

Example:

```text
                 AI Matcher
                     │
                     ▼
                  SWITCH
              ┌──────┼──────┐
              │      │      │
            Strong Moderate Weak
              │      │      │
              ▼      ▼      ▼
            Action Action Action
```

### Example: JD Resume Matcher

An AI node could classify a candidate:

```json
{
  "match": "Strong"
}
```

The Switch can then route the workflow:

```text
Strong    → Google Sheets
Moderate  → Google Sheets
Weak      → Gmail
```

The Switch supports:

- Multiple named cases
- Default branch
- Dynamic output handles
- Duplicate case protection
- Branch specific edge metadata
- Backend branch execution
- Execution of only the selected branch

### Execution model

```text
Switch
   ↓
branchKey = "Strong"
   ↓
WorkflowExecutor
   ↓
edge.data.branch == "Strong"
   ↓
Strong branch executes
```

This keeps routing deterministic rather than asking an LLM to decide which execution path to follow.

---

# 🧪 Manual Execution vs Deployment

A key architectural decision in AutoWorkflow is separating **testing** from **deployment**.

## Manual Run

Manual execution is designed for testing individual workflows or nodes.

A trigger is **not required**.

```text
Manual Run
    ↓
Trigger not required
    ↓
Execute workflow
```

This makes it possible to test something such as:

```text
Summarizer
```

without first creating a Webhook or Schedule trigger.

---

## Deployment

Deployment is intentionally stricter.

A workflow must contain a valid trigger before it can become active.

```text
Deploy
  ↓
Deployment validation
  ↓
Trigger required
  ↓
ACTIVE
```

This prevents an event driven production workflow from being deployed without a mechanism capable of starting it.

The frontend also disables deployment for triggerless workflows, while the backend remains the final authority.

---

# 🔄 Workflow Deployment Invalidation

A deployed workflow should not silently continue using an old graph after its nodes or connections have been modified.

Therefore, changes to:

- Canvas nodes
- Canvas edges
- Trigger type

invalidate the existing deployment.

The workflow is returned to:

```text
DRAFT
```

and must be deployed again.

```text
ACTIVE
  │
  │ graph / trigger modified
  ▼
DRAFT
  │
  │ Deploy
  ▼
ACTIVE
```

This prevents accidental execution of an outdated workflow version.

---

# ⚡ Execution Experience

The workflow builder provides immediate execution feedback.

When the user starts a manual execution:

```text
▶ Run
   ↓
⏳ Running...
   ↓
Execution completes
   ↓
▶ Run
```

The Run action is disabled while execution is in progress, reducing accidental duplicate executions.

Execution results are displayed through the execution console and execution history.

---

# 🧾 Execution History

Execution history is persisted rather than being treated as temporary frontend state.

This allows users to inspect previous executions while the current UI avoids displaying stale execution results as if they belonged to a new run.

The architecture separates:

```text
Current execution
       +
Historical executions
```

so previous executions remain available without contaminating a new execution state.

---

# 🔗 Webhook & Event Driven Workflows

AutoWorkflow supports trigger based execution for deployed workflows.

Examples include:

- Webhook
- GitHub events
- Other configured trigger types

A typical flow:

```text
External Event
      ↓
Webhook / GitHub Event
      ↓
Workflow
      ↓
AI / Logic / Actions
      ↓
Execution Result
```

GitHub webhook integration is currently implemented and tested as part of the workflow system.

---

# 🧩 Backend Execution Architecture

The backend uses a strategy based execution model.

Conceptually:

```text
WorkflowExecutor
      ↓
NodeStrategyRegistry
      ↓
NodeStrategy
      ↓
Execute node
```

This keeps individual node behaviors isolated.

For example:

```text
SwitchStrategy
SummarizerStrategy
AiRouterStrategy
IfConditionStrategy
...
```

The executor is responsible for orchestration while individual strategies handle node specific behavior.

This architecture makes adding new node types significantly easier than putting every node implementation into one large execution class.

---

# 🔐 Validation Architecture

Workflow validation is intentionally split into two modes.

### Execution validation

Used for manual execution.

```text
validateForExecution()
```

A trigger is not required.

### Deployment validation

Used before deployment.

```text
validateForDeployment()
```

A trigger is required.

This distinction prevents the deployment rules from unnecessarily blocking standalone testing.

---

# 🛠️ Technology Stack

## Frontend

- React
- Vite
- React Flow
- Tailwind CSS
- JavaScript

## Backend

- Java
- Spring Boot
- Maven
- Spring Web
- Spring Data JPA
- Hibernate

## Security

- Spring Security
- JWT based authentication

## Database

- PostgreSQL

## AI

- OpenAI
- Google Gemini
- OpenRouter

## Workflow Execution

- Strategy pattern
- Node strategy registry
- Graph based execution
- Branch aware execution

## Development & Integration

- Git
- GitHub
- Webhooks
- REST APIs

---

# 📁 Project Structure

The project follows a separated frontend/backend architecture.

```text
AutoWorkflow/
│
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── services/
│   │   ├── store/
│   │   └── ...
│   └── package.json
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   └── java/
│   │   │       └── com.autoworkflow/
│   │   │           ├── execution/
│   │   │           ├── workflow/
│   │   │           ├── common/
│   │   │           └── ...
│   │   └── test/
│   └── pom.xml
│
└── README.md
```

---

# 🚀 Core Workflow Lifecycle

```text
Create Workflow
      ↓
Build graph
      ↓
Configure nodes
      ↓
Save
      ↓
Manual Run
      ↓
Inspect execution
      ↓
Add trigger
      ↓
Deploy
      ↓
External event
      ↓
Workflow execution
      ↓
Execution history
```

---

# 🧪 Example Workflow

### AI Classification Workflow

```text
             ┌──────────────┐
             │   Webhook    │
             └──────┬───────┘
                    │
                    ▼
             ┌──────────────┐
             │ AI Completion│
             └──────┬───────┘
                    │
                    ▼
             ┌──────────────┐
             │    Switch    │
             └──────┬───────┘
               ┌────┼────┐
               │    │    │
               ▼    ▼    ▼
            Strong Moderate Weak
               │    │    │
               ▼    ▼    ▼
             Action Action Action
```

This demonstrates the core idea of AutoWorkflow:

> **AI determines the information. Logic determines the path. Actions perform the work.**

---

# 📌 Current Development Status

### Completed

- [x] Visual workflow builder
- [x] Drag and drop node creation
- [x] Compact initial node layout
- [x] Workflow persistence
- [x] Manual workflow execution
- [x] Trigger based deployment
- [x] Deployment validation
- [x] Deployment invalidation after graph changes
- [x] Execution console
- [x] Execution history
- [x] Immediate Running state
- [x] AI provider abstraction
- [x] OpenAI integration
- [x] Gemini integration
- [x] OpenRouter integration
- [x] Summarization workflow
- [x] AI Completion capability
- [x] GitHub webhook integration
- [x] Switch node
- [x] Multi branch execution
- [x] Default Switch branch
- [x] Branch aware execution engine

### In Progress / Future

- [ ] Additional integrations
- [ ] More advanced AI driven workflow construction
- [ ] Richer condition operators
- [ ] Nested branching
- [ ] Workflow templates
- [ ] Improved workflow observability
- [ ] Production deployment infrastructure
- [ ] More comprehensive automated integration testing

---

# 🎯 Design Principles

AutoWorkflow is being developed around a few core principles.

### 1. Testing should be independent from deployment

A developer should be able to test a node or workflow without first configuring a production trigger.

### 2. Production workflows must be validated strictly

A deployed workflow must have a valid trigger.

### 3. AI and deterministic logic should remain separate

AI should generate or classify information.

Deterministic nodes such as Switch should control execution.

### 4. The execution engine should be extensible

Adding a new node should primarily involve implementing its strategy rather than rewriting the entire executor.

### 5. The UI should expose execution state clearly

Users should always understand whether a workflow is idle, running, completed, or failed.

### 6. Graph modifications should invalidate deployment

A changed workflow should not silently execute an outdated deployed version.

---

# 📈 Project Direction

The long term direction is to evolve AutoWorkflow from a traditional visual automation builder into an **AI assisted workflow orchestration platform**.

The intended experience is:

```text
User Intent
     ↓
AI understands the task
     ↓
Workflow constructed / assisted
     ↓
User reviews the workflow
     ↓
AI + deterministic nodes execute it
     ↓
Results + execution history
```

The platform is therefore not intended to simply reproduce existing automation tools. The focus is on combining **AI understanding with controllable, observable, deterministic workflow execution**.

---

# 👨‍💻 Development

Clone the repository and configure the frontend and backend according to the environment configuration files.

Typical development flow:

```bash
# Backend
cd backend
./mvnw spring-boot:run

# Frontend
cd frontend
npm install
npm run dev
```

Production frontend build:

```bash
npm run build
```

Backend tests:

```bash
./mvnw test
```

> Exact environment variables and service configuration depend on the current deployment setup and should be kept outside the repository when they contain secrets.

---

# 🔒 Security

Never commit:

- API keys
- JWT secrets
- Database passwords
- OAuth credentials
- Webhook secrets
- `.env` files containing sensitive values

Use environment variables or the project's configuration mechanism for secrets.

---

# 📜 Project Status

AutoWorkflow is an actively developed project.

The current implementation focuses on establishing a reliable workflow execution foundation before expanding into more advanced AI assisted automation capabilities.
