// All node types available in the palette.
//
// IMPORTANT: `type` values here are the CANONICAL node type keys and must match
// the backend's NodeStrategy.getTypeKey() exactly (see NodeStrategyRegistry.java).
// They previously did not for several nodes (cron, email_trigger, http,
// github_action, sheets, email_send, if) — those old names still work for
// already-saved workflows via NodeStrategyRegistry's LEGACY_TYPE_ALIASES, but
// every *new* node created from this palette now uses the real backend key
// directly so no translation layer is needed at runtime.

// Model choices per AI provider. Deliberately NOT a huge hardcoded catalogue —
// OpenRouter alone proxies hundreds of models under a `vendor/model` naming
// convention, so this only lists a small, sane starting set per provider.
// `model` is stored as a plain string on the node either way (see nodeConfigs
// below), so a user can still type/paste any other OpenRouter model id the
// backend will forward as-is; these are just the dropdown's suggested values.
//
// `auto` deliberately has NO model list: when provider="auto", the backend's
// AiProviderRouter strips any configured model before trying each provider (a
// model string tied to one provider is meaningless — or wrong — for another),
// and each provider falls back to its own default model. Showing a model
// dropdown here would be misleading, so ConfigPanel renders a plain explanatory
// note for the model field instead of a select when provider="auto" (see the
// `optionsFrom === 'provider' && provider === 'auto'` branch there).
export const PROVIDER_MODELS = {
  gemini: ['gemini-3.6-flash'],
  openai: ['gpt-4o-mini', 'gpt-4o', 'gpt-4-turbo', 'gpt-3.5-turbo'],
  openrouter: ['openrouter/free'],
  auto: [],
}

// "auto" lets AiProviderRouter pick from app.ai.auto-provider-order with safe
// fallback (see backend AiProviderRouter.java). Purely additive: existing saved
// workflows with provider: openai / gemini / openrouter are completely unaffected.
export const AI_PROVIDERS = ['gemini', 'openai', 'openrouter', 'auto']

export const nodeCategories = [
  {
    id: 'triggers',
    label: 'Triggers',
    color: '#f97316',
    nodes: [
      { type: 'cron_trigger', label: 'Cron Trigger', icon: 'Clock', description: 'Schedule on a timer', color: '#f97316' },
      { type: 'webhook', label: 'Webhook', icon: 'Zap', description: 'HTTP POST trigger', color: '#f97316' },
      { type: 'github_event', label: 'GitHub Event', icon: 'GitPullRequest', description: 'PR, push, issue events', color: '#f97316' },
      { type: 'email_received', label: 'Email Received', icon: 'Mail', description: 'Trigger on new email', color: '#f97316' },
    ],
  },
  {
    id: 'ai',
    label: 'AI Nodes',
    color: '#7c3aed',
    nodes: [
      { type: 'ai', label: 'AI Completion', icon: 'Brain', description: 'Run a prompt through OpenAI, Gemini, or any connected provider', color: '#7c3aed' },
      { type: 'ai_router', label: 'AI Router', icon: 'Network', description: 'AI-based conditional routing', color: '#7c3aed' },
      { type: 'summarizer', label: 'Summarizer', icon: 'FileText', description: 'Summarize long text', color: '#7c3aed' },
      { type: 'classifier', label: 'Classifier', icon: 'Tag', description: 'Classify input into categories', color: '#7c3aed' },
    ],
  },
  {
    id: 'logic',
    label: 'Logic',
    color: '#3b82f6',
    nodes: [
      { type: 'if_condition', label: 'IF Condition', icon: 'GitBranch', description: 'Branch on condition', color: '#3b82f6' },
      { type: 'switch', label: 'Switch', icon: 'GitBranch', description: 'Route to one of several branches by matching a field value', color: '#3b82f6' },
      { type: 'loop', label: 'Loop', icon: 'RefreshCw', description: 'Iterate over a list', color: '#3b82f6' },
      { type: 'merge', label: 'Merge', icon: 'Merge', description: 'Merge parallel branches', color: '#3b82f6' },
      { type: 'delay', label: 'Delay', icon: 'Timer', description: 'Wait before continuing', color: '#3b82f6' },
      { type: 'transform', label: 'Transform', icon: 'Code2', description: 'Reshape data with a field mapping (no code)', color: '#3b82f6' },
    ],
  },
  {
    id: 'integrations',
    label: 'Integrations',
    color: '#06b6d4',
    nodes: [
      { type: 'http_request', label: 'HTTP Request', icon: 'Globe', description: 'Call any external API', color: '#06b6d4' },
      { type: 'github', label: 'GitHub', icon: 'GitPullRequest', description: 'Create PRs, issues, comments', color: '#06b6d4' },
      { type: 'slack', label: 'Slack', icon: 'MessageSquare', description: 'Send Slack messages', color: '#06b6d4' },
      { type: 'gmail', label: 'Gmail', icon: 'Mail', description: 'Send and read emails', color: '#06b6d4' },
      { type: 'notion', label: 'Notion', icon: 'BookOpen', description: 'Create/update Notion pages', color: '#06b6d4' },
    ],
  },
  {
    id: 'storage',
    label: 'Storage',
    color: '#10b981',
    nodes: [
      { type: 'google_sheets', label: 'Google Sheets', icon: 'Table', description: 'Read/write spreadsheet data', color: '#10b981' },
      { type: 'database', label: 'Database', icon: 'Database', description: 'Query SQL/NoSQL databases', color: '#10b981' },
      { type: 'file', label: 'File', icon: 'File', description: 'Read/write files', color: '#10b981' },
      { type: 'redis', label: 'Redis', icon: 'Server', description: 'Cache key-value data', color: '#10b981' },
    ],
  },
  {
    id: 'communication',
    label: 'Communication',
    color: '#eab308',
    nodes: [
      { type: 'send_email', label: 'Send Email', icon: 'Send', description: 'Send transactional emails', color: '#eab308' },
      { type: 'sms', label: 'SMS', icon: 'MessageCircle', description: 'Send SMS via Twilio', color: '#eab308' },
      { type: 'discord', label: 'Discord', icon: 'Hash', description: 'Post Discord messages', color: '#eab308' },
    ],
  },
]

// The canonical set of node types that count as "trigger nodes" — derived directly
// from the 'triggers' category above so there is exactly one source of truth.
// WorkflowValidator.java on the backend uses NodeStrategyRegistry.isTriggerType()
// (which checks the same type keys) as its authority; this mirrors that on the frontend.
export const TRIGGER_NODE_TYPES = new Set(
  nodeCategories
    .find(c => c.id === 'triggers')
    ?.nodes.map(n => n.type) ?? []
)

// Node config schemas (what fields appear in the ConfigPanel "Parameters" tab).
// Keyed by the SAME canonical `type` values used above — this must stay in sync
// with nodeCategories or a node will silently show "No custom settings required".
export const nodeConfigs = {
  webhook: {
    fields: [
      { key: 'method', label: 'HTTP Method', type: 'select', options: ['POST', 'GET', 'PUT'] },
    ],
  },
  cron_trigger: {
    fields: [
      { key: 'expression', label: 'Cron Expression', type: 'text', placeholder: '0 9 * * 1-5' },
      { key: 'timezone', label: 'Timezone', type: 'select', options: ['UTC', 'Asia/Kolkata', 'America/New_York', 'Europe/London'] },
    ],
  },
  email_received: {
    fields: [
      { key: 'fromFilter', label: 'From (filter, optional)', type: 'text', placeholder: 'billing@example.com' },
      { key: 'subjectFilter', label: 'Subject contains (filter, optional)', type: 'text', placeholder: 'Invoice' },
    ],
  },
  // Config for the GitHub Event TRIGGER (fires the workflow on push/PR/etc).
  // Distinct from `github` below, which is the GitHub INTEGRATION action node
  // (create issue/PR/comment) — these two node types were previously conflated
  // under one "github" config key even though only the trigger used these fields.
  github_event: {
    fields: [
      { key: 'repo', label: 'Repository', type: 'text', placeholder: 'owner/repo-name' },
      { key: 'event', label: 'Event Type', type: 'select', options: ['pull_request', 'push', 'issues', 'release'] },
      { key: 'branch', label: 'Branch Filter', type: 'text', placeholder: 'main' },
    ],
  },
  http_request: {
    fields: [
      { key: 'url', label: 'URL', type: 'text', placeholder: 'https://api.example.com/endpoint' },
      { key: 'method', label: 'Method', type: 'select', options: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'] },
      { key: 'headers', label: 'Headers (JSON)', type: 'textarea', placeholder: '{"Authorization": "Bearer ..."}' },
      { key: 'body', label: 'Request Body', type: 'textarea', placeholder: '{"key": "value"}' },
    ],
  },
  ai: {
    fields: [
      { key: 'provider', label: 'AI Provider', type: 'select', options: AI_PROVIDERS, default: 'gemini', description: 'Which connected AI provider runs this node' },
      { key: 'model', label: 'Model', type: 'select', optionsFrom: 'provider', default: 'gemini-3.6-flash', description: 'Options depend on the AI Provider selected above' },
      { key: 'system', label: 'System Message', type: 'textarea', placeholder: 'You are a helpful assistant...' },
      { key: 'prompt', label: 'Prompt', type: 'textarea', placeholder: 'Analyze the following: {{input}}' },
      { key: 'temperature', label: 'Temperature', type: 'range', min: 0, max: 1, step: 0.1, default: 0.7 },
      { key: 'max_tokens', label: 'Max Tokens', type: 'number', placeholder: '1000' },
      { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
    ],
  },
  if_condition: {
    fields: [
      { key: 'field', label: 'Field (path in payload)', type: 'text', placeholder: 'status' },
      { key: 'operator', label: 'Operator', type: 'select', options: ['equals', 'not_equals', 'contains', 'greater_than', 'less_than'] },
      { key: 'value', label: 'Expected Value', type: 'text', placeholder: 'success' },
    ],
  },
  // Matches config.field's value in the input payload against config.cases (compared
  // as strings) and follows only the outgoing edge whose sourceHandle/edge.data.branch
  // equals the matched case — see SwitchStrategy.java / WorkflowExecutor's branchKey
  // handling. `cases` also drives the node's dynamic per-case output handles (see
  // NodeWrapper.jsx) — each case becomes one labeled output.
  switch: {
    fields: [
      { key: 'field', label: 'Field to Match', type: 'text', placeholder: 'match', description: 'Field in the input payload to compare against each case below' },
      { key: 'cases', label: 'Cases', type: 'case-list', default: ['Case 1', 'Case 2'], description: 'Each case becomes an output on this node — connect it to whatever should run for that value' },
      { key: 'defaultCase', label: 'Default Case', type: 'select', optionsFrom: 'cases', description: 'Used when the field value does not match any case above' },
    ],
  },
  slack: {
    fields: [
      { key: 'channel', label: 'Channel', type: 'text', placeholder: '#general' },
      { key: 'message', label: 'Message', type: 'textarea', placeholder: 'Hello from AutoWorkflow! {{data.result}}' },
      { key: 'username', label: 'Bot Username', type: 'text', placeholder: 'AutoWorkflow Bot' },
      { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
    ],
  },
  // GitHub INTEGRATION action node (create issue / PR / comment) — see github_event above for the trigger.
  github: {
    fields: [
      { key: 'repo', label: 'Repository (owner/repo)', type: 'text', placeholder: 'owner/repo-name' },
      { key: 'action', label: 'Action Type', type: 'select', options: ['create_issue', 'create_pr', 'create_comment'] },
      { key: 'title', label: 'Issue/PR Title', type: 'text', placeholder: 'Issue title' },
      { key: 'body', label: 'Issue/PR Body', type: 'textarea', placeholder: 'Issue description...' },
      { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
    ],
  },
  summarizer: {
    fields: [
      { key: 'provider', label: 'AI Provider', type: 'select', options: AI_PROVIDERS, default: 'gemini', description: 'Which connected AI provider runs this node' },
      { key: 'model', label: 'Model Name', type: 'select', optionsFrom: 'provider', default: 'gemini-3.6-flash', description: 'Options depend on the AI Provider selected above' },
      { key: 'maxLength', label: 'Max Length (Chars)', type: 'number', placeholder: '200', description: 'Character budget for the summary — used to shape the prompt and hard-truncate the result. Not the same as the token limit sent to the provider.' },
      { key: 'inputText', label: 'Direct Input Text (Optional)', type: 'textarea', placeholder: 'Enter text to summarize directly here...', description: 'Provide static or template text to summarize directly' },
      { key: 'textField', label: 'Payload Field to Summarize', type: 'text', placeholder: 'text', description: 'Dot-path into the input payload, e.g. "text", "data.text", or "commits.0.message"' },
      { key: 'allowRawFallback', label: 'Fall back to raw JSON if no text field found', type: 'checkbox', default: false, description: 'Off by default: no configured text means the node fails clearly instead of silently summarizing raw JSON.' },
      { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
    ],
  },
  classifier: {
    fields: [
      { key: 'provider', label: 'AI Provider', type: 'select', options: AI_PROVIDERS, default: 'gemini', description: 'Which connected AI provider runs this node' },
      { key: 'model', label: 'Model Name', type: 'select', optionsFrom: 'provider', default: 'gemini-3.6-flash', description: 'Options depend on the AI Provider selected above' },
      { key: 'labels', label: 'Labels', type: 'tags', placeholder: 'support, sales, spam, other', description: 'Comma-separated labels the classifier can choose from' },
      { key: 'textField', label: 'Payload Field to Classify', type: 'text', placeholder: 'text', description: 'Field in input payload to classify' },
      { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
    ],
  },
  ai_router: {
    fields: [
      { key: 'provider', label: 'AI Provider', type: 'select', options: AI_PROVIDERS, default: 'gemini', description: 'Which connected AI provider runs this node' },
      { key: 'model', label: 'Model Name', type: 'select', optionsFrom: 'provider', default: 'gemini-3.6-flash', description: 'Options depend on the AI Provider selected above' },
      { key: 'branches', label: 'Branches', type: 'tags', placeholder: 'urgent, normal', description: 'Comma-separated options the router can choose between (first = "true" branch)' },
      { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
    ],
  },
  gmail: {
    fields: [
      { key: 'action', label: 'Action', type: 'select', options: ['send', 'read'] },
      { key: 'to', label: 'Recipient Email', type: 'text', placeholder: 'user@example.com' },
      { key: 'subject', label: 'Subject', type: 'text', placeholder: 'Notification' },
      { key: 'body', label: 'Body', type: 'textarea', placeholder: 'Hello from AutoWorkflow...' },
      { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
    ],
  },
  notion: {
    fields: [
      { key: 'action', label: 'Action', type: 'select', options: ['create_page', 'update_page'] },
      { key: 'databaseId', label: 'Database ID', type: 'text', placeholder: 'Enter Database ID' },
      { key: 'content', label: 'Page Content', type: 'textarea', placeholder: 'Content...' },
      { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
    ],
  },
  google_sheets: {
    fields: [
      { key: 'spreadsheetId', label: 'Spreadsheet ID', type: 'text', placeholder: 'Spreadsheet ID' },
      { key: 'range', label: 'Range', type: 'text', placeholder: 'Sheet1!A1:D10' },
      { key: 'values', label: 'Values (JSON)', type: 'textarea', placeholder: '[["Col1", "Col2"]]' },
      { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
    ],
  },
  discord: {
    fields: [
      { key: 'message', label: 'Message', type: 'textarea', placeholder: 'Discord message content...' },
      { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
    ],
  },
  send_email: {
    fields: [
      { key: 'to', label: 'Recipient Email', type: 'text', placeholder: 'user@example.com' },
      { key: 'subject', label: 'Subject', type: 'text', placeholder: 'Alert' },
      { key: 'body', label: 'Body', type: 'textarea', placeholder: 'Email content...' },
      { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
    ],
  },
  sms: {
    fields: [
      { key: 'to', label: 'Phone Number', type: 'text', placeholder: '+1234567890' },
      { key: 'message', label: 'SMS Body', type: 'textarea', placeholder: 'SMS text alert' },
      { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
    ],
  },
  database: {
    fields: [
      { key: 'query', label: 'SQL Query', type: 'textarea', placeholder: 'SELECT * FROM table...' },
      { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
    ],
  },
  // Safe, no-code field mapping — see TransformStrategy.java for the exact contract.
  // Each row: { output: "repo", source: "repository.full_name", strip: "" }.
  // `source` supports dot-paths with numeric array indices (e.g. "commits.0.message").
  transform: {
    fields: [
      { key: 'mappings', label: 'Field Mappings', type: 'mapping-editor', description: 'Map fields from the input payload to a new output shape. Leave empty to pass the input through unchanged.' },
    ],
  },
}
