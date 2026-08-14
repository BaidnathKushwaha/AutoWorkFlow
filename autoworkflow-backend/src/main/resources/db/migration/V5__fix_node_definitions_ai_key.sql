-- The generic AI completion node's strategy key was renamed from "openai" to "ai" when
-- Gemini support was added (see AiNodeStrategy.getTypeKey() / NodeStrategyRegistry's
-- LEGACY_TYPE_ALIASES). The node_definitions catalog table is descriptive metadata only
-- (not currently consumed by the frontend builder, which uses its own static
-- nodeTypes.js palette) but was left stale with the old key. Fixing it for consistency.
UPDATE node_definitions
SET type_key = 'ai',
    display_name = 'AI Completion',
    description = 'Runs a prompt through OpenAI, Gemini, or any connected AI provider.'
WHERE type_key = 'openai';
