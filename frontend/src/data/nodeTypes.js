// All node types available in the palette. Canonical keys must match NodeStrategy.getTypeKey().

export const PROVIDER_MODELS = {
  gemini: ['gemini-3.6-flash'],
  openai: ['gpt-4o-mini', 'gpt-4o', 'gpt-4-turbo', 'gpt-3.5-turbo'],
  openrouter: [
    'nvidia/nemotron-3-super-120b-a12b:free',
    'google/gemma-4-31b-it:free',
    'google/gemma-4-26b-a4b-it:free',
    'nvidia/nemotron-3-ultra-550b-a55b:free',
    'cohere/north-mini-code:free',
  ],
  auto: [],
}

export const AI_PROVIDERS = ['gemini', 'openai', 'openrouter', 'auto']

export const nodeCategories = [
  { id: 'triggers', label: 'Triggers', color: '#f97316', nodes: [
    { type: 'cron_trigger', label: 'Cron Trigger', icon: 'Clock', description: 'Schedule on a timer', color: '#f97316' },
    { type: 'webhook', label: 'Webhook', icon: 'Zap', description: 'HTTP POST trigger', color: '#f97316' },
    { type: 'github_event', label: 'GitHub Event', icon: 'GitPullRequest', description: 'PR, push, issue events', color: '#f97316' },
    { type: 'email_received', label: 'Email Received', icon: 'Mail', description: 'Trigger on new Gmail messages', color: '#f97316' },
  ] },
  { id: 'ai', label: 'AI Nodes', color: '#7c3aed', nodes: [
    { type: 'ai', label: 'AI Completion', icon: 'Brain', description: 'Run a prompt through connected AI providers', color: '#7c3aed' },
    { type: 'ai_router', label: 'AI Router', icon: 'Network', description: 'AI-based conditional routing', color: '#7c3aed' },
    { type: 'summarizer', label: 'Summarizer', icon: 'FileText', description: 'Summarize long text', color: '#7c3aed' },
    { type: 'classifier', label: 'Classifier', icon: 'Tag', description: 'Classify input into categories', color: '#7c3aed' },
  ] },
  { id: 'logic', label: 'Logic', color: '#3b82f6', nodes: [
    { type: 'if_condition', label: 'IF Condition', icon: 'GitBranch', description: 'Typed and compound deterministic condition', color: '#3b82f6' },
    { type: 'switch', label: 'Switch', icon: 'GitBranch', description: 'Route to one of several branches', color: '#3b82f6' },
    { type: 'loop', label: 'Loop', icon: 'RefreshCw', description: 'Iterate over a list with isolated body execution', color: '#3b82f6' },
    { type: 'merge', label: 'Merge', icon: 'Merge', description: 'Merge parallel branches', color: '#3b82f6' },
    { type: 'delay', label: 'Delay', icon: 'Timer', description: 'Wait before continuing', color: '#3b82f6' },
    { type: 'transform', label: 'Transform', icon: 'Code2', description: 'Reshape data with declarative field mapping', color: '#3b82f6' },
  ] },
  { id: 'integrations', label: 'Integrations', color: '#06b6d4', nodes: [
    { type: 'http_request', label: 'HTTP Request', icon: 'Globe', description: 'Call any external API', color: '#06b6d4' },
    { type: 'github', label: 'GitHub', icon: 'GitPullRequest', description: 'Create PRs, issues, comments', color: '#06b6d4' },
    { type: 'slack', label: 'Slack', icon: 'MessageSquare', description: 'Send Slack messages', color: '#06b6d4' },
    { type: 'gmail', label: 'Gmail', icon: 'Mail', description: 'Send, read, and search emails', color: '#06b6d4' },
    { type: 'notion', label: 'Notion', icon: 'BookOpen', description: 'Create/update Notion pages', color: '#06b6d4' },
  ] },
  { id: 'storage', label: 'Storage', color: '#10b981', nodes: [
    { type: 'google_sheets', label: 'Google Sheets', icon: 'Table', description: 'Append, read, and find spreadsheet rows', color: '#10b981' },
    { type: 'database', label: 'Database', icon: 'Database', description: 'Query SQL/NoSQL databases', color: '#10b981' },
    { type: 'file', label: 'File', icon: 'File', description: 'Read/write files', color: '#10b981' },
    { type: 'redis', label: 'Redis', icon: 'Server', description: 'Cache key-value data', color: '#10b981' },
  ] },
  { id: 'communication', label: 'Communication', color: '#eab308', nodes: [
    { type: 'send_email', label: 'Send Email', icon: 'Send', description: 'Send transactional emails', color: '#eab308' },
    { type: 'sms', label: 'SMS', icon: 'MessageCircle', description: 'Send SMS via Twilio', color: '#eab308' },
    { type: 'discord', label: 'Discord', icon: 'Hash', description: 'Post Discord messages', color: '#eab308' },
  ] },
]

export const TRIGGER_NODE_TYPES = new Set(nodeCategories.find(c => c.id === 'triggers')?.nodes.map(n => n.type) ?? [])

export const nodeConfigs = {
  webhook: { fields: [{ key: 'method', label: 'HTTP Method', type: 'select', options: ['POST', 'GET', 'PUT'] }] },
  cron_trigger: { fields: [
    { key: 'expression', label: 'Cron Expression', type: 'text', placeholder: '0 9 * * 1-5' },
    { key: 'timezone', label: 'Timezone', type: 'select', options: ['UTC', 'Asia/Kolkata', 'America/New_York', 'Europe/London'] },
  ] },
  email_received: { fields: [
    { key: 'fromFilter', label: 'From (filter, optional)', type: 'text', placeholder: 'billing@example.com' },
    { key: 'subjectFilter', label: 'Subject contains (filter, optional)', type: 'text', placeholder: 'Invoice' },
    { key: 'jobDescription', label: 'Job Description (optional)', type: 'textarea', placeholder: 'Paste the job description for downstream resume matching...' },
  ] },
  github_event: { fields: [
    { key: 'repo', label: 'Repository', type: 'text', placeholder: 'owner/repo-name' },
    { key: 'event', label: 'Event Type', type: 'select', options: ['pull_request', 'push', 'issues', 'release'] },
    { key: 'branch', label: 'Branch Filter', type: 'text', placeholder: 'main' },
  ] },
  http_request: { fields: [
    { key: 'url', label: 'URL', type: 'text', placeholder: 'https://api.example.com/endpoint' },
    { key: 'method', label: 'Method', type: 'select', options: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'] },
    { key: 'headers', label: 'Headers (JSON)', type: 'textarea', placeholder: '{"Authorization": "Bearer ..."}' },
    { key: 'body', label: 'Request Body', type: 'textarea', placeholder: '{"key": "value"}' },
  ] },
  ai: { fields: [
    { key: 'provider', label: 'AI Provider', type: 'select', options: AI_PROVIDERS, default: 'gemini' },
    { key: 'model', label: 'Model', type: 'select', optionsFrom: 'provider', default: 'gemini-3.6-flash' },
    { key: 'system', label: 'System Message', type: 'textarea', placeholder: 'You are a helpful assistant...' },
    { key: 'prompt', label: 'Prompt', type: 'textarea', placeholder: 'Analyze the following: {{input}}' },
    { key: 'structuredOutput', label: 'Require JSON output', type: 'checkbox', default: false },
    { key: 'temperature', label: 'Temperature', type: 'range', min: 0, max: 1, step: 0.1, default: 0.7 },
    { key: 'max_tokens', label: 'Max Tokens', type: 'number', placeholder: '1000' },
    { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
  ] },
  if_condition: { fields: [
    { key: 'field', label: 'Field (nested path)', type: 'text', placeholder: 'user.profile.status' },
    { key: 'operator', label: 'Operator', type: 'select', options: [
      'equals', 'not_equals', 'contains', 'not_contains', 'starts_with', 'ends_with',
      'greater_than', 'greater_than_or_equal', 'less_than', 'less_than_or_equal',
      'is_empty', 'is_not_empty', 'exists', 'not_exists', 'is_true', 'is_false'
    ] },
    { key: 'value', label: 'Expected Value', type: 'text', placeholder: 'success / 100 / true' },
    { key: 'condition', label: 'Compound Condition (JSON)', type: 'textarea', rows: 7,
      placeholder: '{"logic":"AND","conditions":[{"field":"status","operator":"equals","value":"paid"},{"field":"amount","operator":"greater_than","value":1000}]}' },
  ] },
  switch: { fields: [
    { key: 'field', label: 'Field to Match', type: 'text', placeholder: 'match', description: 'Nested paths such as user.profile.role are supported.' },
    { key: 'cases', label: 'Cases', type: 'case-list', default: ['Case 1', 'Case 2'] },
    { key: 'defaultCase', label: 'Default Case', type: 'select', optionsFrom: 'cases' },
  ] },
  loop: { fields: [
    { key: 'arrayField', label: 'Array Path', type: 'text', placeholder: 'messages' },
    { key: 'bodyStartNodeId', label: 'Loop Body Start Node ID', type: 'text', placeholder: 'node-id', description: 'First node executed for every item.' },
    { key: 'continuationNodeId', label: 'Continuation Node ID', type: 'text', placeholder: 'node-id', description: 'Node executed once after all iterations finish.' },
  ] },
  slack: { fields: [
    { key: 'channel', label: 'Channel', type: 'text', placeholder: '#general' },
    { key: 'message', label: 'Message', type: 'textarea', placeholder: 'Hello from AutoWorkflow! {{data.result}}' },
    { key: 'username', label: 'Bot Username', type: 'text', placeholder: 'AutoWorkflow Bot' },
    { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
  ] },
  github: { fields: [
    { key: 'repo', label: 'Repository (owner/repo)', type: 'text', placeholder: 'owner/repo-name' },
    { key: 'action', label: 'Action Type', type: 'select', options: ['create_issue', 'create_pr', 'create_comment'] },
    { key: 'title', label: 'Issue/PR Title', type: 'text', placeholder: 'Issue title' },
    { key: 'body', label: 'Issue/PR Body', type: 'textarea', placeholder: 'Issue description...' },
    { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
  ] },
  summarizer: { fields: [
    { key: 'provider', label: 'AI Provider', type: 'select', options: AI_PROVIDERS, default: 'gemini' },
    { key: 'model', label: 'Model Name', type: 'select', optionsFrom: 'provider', default: 'gemini-3.6-flash' },
    { key: 'maxLength', label: 'Max Length (Chars)', type: 'number', placeholder: '200' },
    { key: 'inputText', label: 'Direct Input Text (Optional)', type: 'textarea', placeholder: 'Enter text to summarize directly here...' },
    { key: 'textField', label: 'Payload Field to Summarize', type: 'text', placeholder: 'text' },
    { key: 'allowRawFallback', label: 'Fall back to raw JSON if no text field found', type: 'checkbox', default: false },
    { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
  ] },
  classifier: { fields: [
    { key: 'provider', label: 'AI Provider', type: 'select', options: AI_PROVIDERS, default: 'gemini' },
    { key: 'model', label: 'Model Name', type: 'select', optionsFrom: 'provider', default: 'gemini-3.6-flash' },
    { key: 'labels', label: 'Labels', type: 'tags', placeholder: 'Personal, Official, Spam' },
    { key: 'textField', label: 'Payload Field to Classify', type: 'text', placeholder: 'body' },
    { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
  ] },
  ai_router: { fields: [
    { key: 'provider', label: 'AI Provider', type: 'select', options: AI_PROVIDERS, default: 'gemini' },
    { key: 'model', label: 'Model Name', type: 'select', optionsFrom: 'provider', default: 'gemini-3.6-flash' },
    { key: 'branches', label: 'Branches', type: 'tags', placeholder: 'urgent, normal' },
    { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
  ] },
  gmail: { fields: [
    { key: 'action', label: 'Operation', type: 'select', options: ['send', 'read', 'search'] },
    { key: 'to', label: 'Recipient Email', type: 'text', placeholder: 'user@example.com' },
    { key: 'subject', label: 'Subject', type: 'text', placeholder: 'Notification' },
    { key: 'body', label: 'Body', type: 'textarea', placeholder: 'Hello from AutoWorkflow... {{input}}' },
    { key: 'query', label: 'Gmail Search Query', type: 'text', placeholder: 'from:billing@example.com newer_than:7d' },
    { key: 'maxResults', label: 'Max Results', type: 'number', placeholder: '10' },
    { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
  ] },
  notion: { fields: [
    { key: 'action', label: 'Action', type: 'select', options: ['create_page', 'update_page'] },
    { key: 'databaseId', label: 'Database ID', type: 'text', placeholder: 'Enter Database ID' },
    { key: 'content', label: 'Page Content', type: 'textarea', placeholder: 'Content...' },
    { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
  ] },
  google_sheets: { fields: [
    { key: 'operation', label: 'Operation', type: 'select', options: ['append', 'read', 'find'] },
    { key: 'spreadsheetId', label: 'Spreadsheet ID', type: 'text', placeholder: 'Spreadsheet ID' },
    { key: 'range', label: 'Sheet / A1 Range', type: 'text', placeholder: 'Sheet1!A1:D100' },
    { key: 'values', label: 'Append Values (JSON)', type: 'textarea', placeholder: '["{{sender}}", "{{subject}}", "{{label}}"]' },
    { key: 'findColumn', label: 'Find Column', type: 'text', placeholder: 'A or 0' },
    { key: 'findValue', label: 'Find Value', type: 'text', placeholder: 'candidate@example.com' },
    { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
  ] },
  discord: { fields: [
    { key: 'message', label: 'Message', type: 'textarea', placeholder: 'Discord message content...' },
    { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
  ] },
  send_email: { fields: [
    { key: 'to', label: 'Recipient Email', type: 'text', placeholder: 'user@example.com' },
    { key: 'subject', label: 'Subject', type: 'text', placeholder: 'Alert' },
    { key: 'body', label: 'Body', type: 'textarea', placeholder: 'Email content...' },
    { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
  ] },
  sms: { fields: [
    { key: 'to', label: 'Phone Number', type: 'text', placeholder: '+1234567890' },
    { key: 'message', label: 'SMS Body', type: 'textarea', placeholder: 'SMS text alert' },
    { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
  ] },
  database: { fields: [
    { key: 'query', label: 'SQL Query', type: 'textarea', placeholder: 'SELECT * FROM table...' },
    { key: 'continueOnFail', label: 'Continue workflow if this node fails', type: 'checkbox', default: false },
  ] },
  transform: { fields: [
    { key: 'mappings', label: 'Field Mappings', type: 'mapping-editor', description: 'Map fields from the input payload to a new output shape.' },
    { key: 'conversions', label: 'Type Conversions (JSON)', type: 'textarea', rows: 4, placeholder: '{"amount":"number","active":"boolean"}' },
    { key: 'filter', label: 'Array Filter (JSON)', type: 'textarea', rows: 5, placeholder: '{"arrayPath":"items","condition":{"field":"status","operator":"equals","value":"active"}}' },
    { key: 'map', label: 'Array Map (JSON)', type: 'textarea', rows: 5, placeholder: '{"arrayPath":"items","fields":{"name":"user.name","price":"price"}}' },
  ] },
}
