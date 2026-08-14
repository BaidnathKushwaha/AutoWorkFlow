-- ============================================================
-- AutoWorkflow initial schema
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------
-- USERS
-- ---------------------------------------------------------
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(150)  NOT NULL,
    email               VARCHAR(255)  NOT NULL UNIQUE,
    password_hash       VARCHAR(255)  NOT NULL,
    api_key_encrypted    TEXT,
    api_key_last_four   VARCHAR(4),
    role                VARCHAR(30)   NOT NULL DEFAULT 'USER',
    ai_requests_count   BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------
-- REFRESH TOKENS (for JWT refresh / logout invalidation)
-- ---------------------------------------------------------
CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- ---------------------------------------------------------
-- NODE DEFINITIONS  (Node Marketplace / palette source of truth)
-- ---------------------------------------------------------
CREATE TABLE node_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type_key        VARCHAR(60)  NOT NULL UNIQUE,     -- e.g. 'openai', 'slack', 'if_condition'
    display_name    VARCHAR(100) NOT NULL,             -- e.g. 'OpenAI'
    category        VARCHAR(30)  NOT NULL,             -- TRIGGER | AI | LOGIC | INTEGRATION | ACTION
    description     TEXT,
    icon            VARCHAR(60),
    color           VARCHAR(20),
    config_schema   JSONB,                              -- JSON schema describing configurable fields
    requires_integration VARCHAR(30),                  -- optional FK-by-name to an integration provider
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_node_definitions_category ON node_definitions(category);

-- ---------------------------------------------------------
-- WORKFLOWS
-- ---------------------------------------------------------
CREATE TABLE workflows (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name              VARCHAR(200) NOT NULL,
    description       TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT',   -- DRAFT | ACTIVE | RUNNING | ARCHIVED
    is_deployed       BOOLEAN NOT NULL DEFAULT FALSE,
    trigger_type      VARCHAR(60),                             -- 'GitHub Event', 'Cron (Daily)', 'Webhook', ...
    trigger_config    JSONB,
    canvas_nodes      JSONB NOT NULL DEFAULT '[]',              -- React Flow nodes
    canvas_edges      JSONB NOT NULL DEFAULT '[]',              -- React Flow edges
    executions_count  BIGINT NOT NULL DEFAULT 0,
    last_run_at       TIMESTAMPTZ,
    webhook_token     VARCHAR(64) UNIQUE,                      -- used for /api/webhooks/{token}
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_workflows_user ON workflows(user_id);
CREATE INDEX idx_workflows_status ON workflows(status);

-- ---------------------------------------------------------
-- EXECUTIONS
-- ---------------------------------------------------------
CREATE TABLE executions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id     UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status          VARCHAR(20) NOT NULL DEFAULT 'RUNNING',  -- RUNNING | SUCCESS | FAILED
    triggered_by    VARCHAR(30) NOT NULL,                     -- webhook | schedule | api | manual
    duration_ms     BIGINT,
    steps_logs      JSONB NOT NULL DEFAULT '[]',
    error_message   TEXT,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ
);
CREATE INDEX idx_executions_workflow ON executions(workflow_id);
CREATE INDEX idx_executions_user ON executions(user_id);
CREATE INDEX idx_executions_status ON executions(status);
CREATE INDEX idx_executions_started_at ON executions(started_at DESC);

-- ---------------------------------------------------------
-- INTEGRATIONS  (per-user connected credentials)
-- ---------------------------------------------------------
CREATE TABLE integrations (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider                  VARCHAR(30) NOT NULL,   -- github | slack | openai | gmail | google_sheets | notion | discord
    account_label             VARCHAR(150),            -- e.g. connected username/workspace
    encrypted_access_token    TEXT,
    encrypted_refresh_token   TEXT,
    scopes                    TEXT,  -- comma-separated permission scopes, e.g. "Read/Write Repos"
    status                    VARCHAR(20) NOT NULL DEFAULT 'DISCONNECTED', -- HEALTHY | DISCONNECTED | ERROR
    last_checked_at           TIMESTAMPTZ,
    token_expires_at          TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, provider)
);
CREATE INDEX idx_integrations_user ON integrations(user_id);

-- ---------------------------------------------------------
-- TEMPLATES
-- ---------------------------------------------------------
CREATE TABLE templates (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(200) NOT NULL,
    description       TEXT,
    trigger_icon_key  VARCHAR(60),     -- e.g. 'github_event' -> maps to node_definitions.type_key
    target_icon_key   VARCHAR(60),
    canvas_nodes      JSONB NOT NULL DEFAULT '[]',
    canvas_edges      JSONB NOT NULL DEFAULT '[]',
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------
-- ASSISTANT (chat history for AI Assistant page)
-- ---------------------------------------------------------
CREATE TABLE assistant_conversations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_assistant_conv_user ON assistant_conversations(user_id);

CREATE TABLE assistant_messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES assistant_conversations(id) ON DELETE CASCADE,
    role             VARCHAR(20) NOT NULL,   -- user | assistant | system
    content          TEXT NOT NULL,
    generated_workflow_json JSONB,           -- if assistant produced a workflow JSON in this turn
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_assistant_messages_conv ON assistant_messages(conversation_id);

-- ---------------------------------------------------------
-- AUDIT LOG (lightweight, used across modules)
-- ---------------------------------------------------------
CREATE TABLE audit_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID REFERENCES users(id) ON DELETE SET NULL,
    action       VARCHAR(100) NOT NULL,
    entity_type  VARCHAR(60),
    entity_id    UUID,
    metadata     JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);
