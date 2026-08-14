-- ============================================================
-- Seed node_definitions to match the Node Marketplace UI exactly
-- Categories: TRIGGER (orange), AI (purple), LOGIC (blue),
--             INTEGRATION (teal), ACTION (yellow)
-- ============================================================

INSERT INTO node_definitions (type_key, display_name, category, description, icon, color, requires_integration) VALUES
-- TRIGGER
('cron_trigger',   'Cron Trigger',    'TRIGGER', 'Fires the workflow on a schedule.', 'clock',   'orange', NULL),
('webhook',        'Webhook',         'TRIGGER', 'Fires the workflow when an HTTP request hits a unique URL.', 'webhook', 'orange', NULL),
('github_event',   'GitHub Event',    'TRIGGER', 'Fires on GitHub repository events (PR opened, push, etc).', 'github',  'orange', 'github'),
('email_received', 'Email Received',  'TRIGGER', 'Fires when a new email arrives in the connected inbox.', 'mail', 'orange', 'gmail'),

-- AI
('openai',      'OpenAI',      'AI', 'Calls an OpenAI chat completion with a configurable prompt.', 'brain',    'purple', 'openai'),
('ai_router',   'AI Router',   'AI', 'Uses an LLM to route execution down one of several branches.', 'brain',    'purple', 'openai'),
('summarizer',  'Summarizer',  'AI', 'Summarizes input text using an LLM.', 'brain', 'purple', 'openai'),
('classifier',  'Classifier',  'AI', 'Classifies input text into one of several labels using an LLM.', 'brain', 'purple', 'openai'),

-- LOGIC
('if_condition', 'IF Condition', 'LOGIC', 'Branches execution based on a boolean expression.', 'git-branch', 'blue', NULL),
('loop',         'Loop',         'LOGIC', 'Iterates over an array, executing downstream nodes per item.', 'repeat', 'blue', NULL),
('merge',        'Merge',        'LOGIC', 'Merges multiple incoming branches into one payload.', 'merge', 'blue', NULL),
('delay',        'Delay',        'LOGIC', 'Pauses execution for a configured duration.', 'timer', 'blue', NULL),
('transform',    'Transform',    'LOGIC', 'Transforms the payload using a JSON mapping/expression.', 'shuffle', 'blue', NULL),

-- INTEGRATION
('http_request',  'HTTP Request',  'INTEGRATION', 'Makes a generic HTTP request to any URL.', 'globe', 'teal', NULL),
('github',        'GitHub',        'INTEGRATION', 'Reads/writes GitHub repository data.', 'github', 'teal', 'github'),
('slack',         'Slack',         'INTEGRATION', 'Posts messages to a Slack channel.', 'slack', 'teal', 'slack'),
('gmail',         'Gmail',         'INTEGRATION', 'Reads or sends email via Gmail.', 'mail', 'teal', 'gmail'),
('notion',        'Notion',        'INTEGRATION', 'Reads/writes pages in a Notion database.', 'file-text', 'teal', 'notion'),
('google_sheets',  'Google Sheets', 'INTEGRATION', 'Reads/writes rows in a Google Sheet.', 'sheet', 'teal', 'google_sheets'),
('database',       'Database',      'INTEGRATION', 'Runs a parameterized query against a connected database.', 'database', 'teal', NULL),
('file',           'File',          'INTEGRATION', 'Reads or writes a file in workflow storage.', 'file', 'teal', NULL),
('redis',          'Redis',         'INTEGRATION', 'Reads/writes a key in the connected Redis instance.', 'database', 'teal', NULL),

-- ACTION
('send_email', 'Send Email', 'ACTION', 'Sends an email via the connected provider.', 'send', 'yellow', 'gmail'),
('sms',        'SMS',        'ACTION', 'Sends an SMS message.', 'message-square', 'yellow', NULL),
('discord',    'Discord',    'ACTION', 'Posts a message to a Discord channel.', 'message-circle', 'yellow', 'discord');

-- ============================================================
-- Seed templates matching Templates page
-- ============================================================

INSERT INTO templates (name, description, trigger_icon_key, target_icon_key, canvas_nodes, canvas_edges) VALUES
(
  'GitHub PR Smart Reviewer',
  'Automatically analyze pull requests using GPT-4 and comment on code quality.',
  'github_event', 'openai',
  '[{"id":"1","type":"github_event","position":{"x":100,"y":100},"data":{"label":"PR Opened"}},
    {"id":"2","type":"openai","position":{"x":400,"y":100},"data":{"label":"AI Code Review"}},
    {"id":"3","type":"github","position":{"x":700,"y":100},"data":{"label":"Post Comment"}}]',
  '[{"id":"e1-2","source":"1","target":"2"},{"id":"e2-3","source":"2","target":"3"}]'
),
(
  'AI Email Router',
  'Read incoming emails, classify intent, and route to Slack or auto-reply.',
  'email_received', 'classifier',
  '[{"id":"1","type":"email_received","position":{"x":100,"y":100},"data":{"label":"Email Received"}},
    {"id":"2","type":"classifier","position":{"x":400,"y":100},"data":{"label":"Classify Intent"}},
    {"id":"3","type":"slack","position":{"x":700,"y":100},"data":{"label":"Slack Notify"}}]',
  '[{"id":"e1-2","source":"1","target":"2"},{"id":"e2-3","source":"2","target":"3"}]'
),
(
  'Resume Matcher',
  'Parse incoming resumes and score them against specific job descriptions.',
  'file', 'openai',
  '[{"id":"1","type":"file","position":{"x":100,"y":100},"data":{"label":"Resume Upload"}},
    {"id":"2","type":"openai","position":{"x":400,"y":100},"data":{"label":"Score Resume"}},
    {"id":"3","type":"google_sheets","position":{"x":700,"y":100},"data":{"label":"Log Result"}}]',
  '[{"id":"e1-2","source":"1","target":"2"},{"id":"e2-3","source":"2","target":"3"}]'
),
(
  'Trend Generator',
  'Fetch daily news, summarize trends, and publish to a Notion database.',
  'cron_trigger', 'notion',
  '[{"id":"1","type":"cron_trigger","position":{"x":100,"y":100},"data":{"label":"Daily Cron"}},
    {"id":"2","type":"http_request","position":{"x":350,"y":100},"data":{"label":"Fetch News"}},
    {"id":"3","type":"summarizer","position":{"x":600,"y":100},"data":{"label":"Summarize"}},
    {"id":"4","type":"notion","position":{"x":850,"y":100},"data":{"label":"Publish"}}]',
  '[{"id":"e1-2","source":"1","target":"2"},{"id":"e2-3","source":"2","target":"3"},{"id":"e3-4","source":"3","target":"4"}]'
);
