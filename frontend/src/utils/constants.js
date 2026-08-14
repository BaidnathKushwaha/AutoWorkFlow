export const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

// The externally-reachable origin GitHub (or any external system) should call for webhooks.
// When testing with `ngrok http 8080`, set VITE_WEBHOOK_BASE_URL in your .env to the printed
// https://xxxx.ngrok-free.app URL. Falls back to API_BASE_URL (fine for same-machine testing,
// but GitHub's servers can't reach a bare localhost URL).
export const WEBHOOK_BASE_URL = import.meta.env.VITE_WEBHOOK_BASE_URL || API_BASE_URL

export function buildWebhookUrl(token) {
  if (!token) return null
  return `${WEBHOOK_BASE_URL.replace(/\/$/, '')}/api/webhooks/${token}`
}

export const STORAGE_KEYS = {
  ACCESS_TOKEN: 'autoworkflow_access_token',
  REFRESH_TOKEN: 'autoworkflow_refresh_token',
  USER: 'autoworkflow_user',
  AUTHED: 'autoworkflow_authed',
}

export const WORKFLOW_STATUS = {
  ACTIVE: 'active',
  DRAFT: 'draft',
  PAUSED: 'paused',
}

export const NODE_CATEGORIES = {
  TRIGGER: 'trigger',
  ACTION: 'action',
  LOGIC: 'logic',
  AI: 'ai',
  INTEGRATION: 'integration',
}

export const EXECUTION_STATUS = {
  RUNNING: 'running',
  COMPLETED: 'completed',
  FAILED: 'failed',
}