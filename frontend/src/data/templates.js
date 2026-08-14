import { GitPullRequest, Mail, FileText, TrendingUp, Zap } from 'lucide-react'

export const templates = [
  {
    id: '1',
    title: 'GitHub PR Smart Reviewer',
    desc: 'Automatically analyze pull requests using GPT-4 and comment on code quality, reducing review lag.',
    icon: GitPullRequest,
    color: '#6366f1',
    category: 'DevOps',
    difficulty: 'Intermediate',
    tags: ['GitHub', 'OpenAI', 'Slack'],
    nodeChain: ['GitHub PR', 'AI Review', 'Slack Notify'],
    nodeColors: ['#6366f1', '#7c3aed', '#f59e0b'],
    nodes: [
      { id: 'node-1', type: 'github_event', position: { x: 250, y: 100 }, data: { label: 'GitHub PR', description: 'Triggers when a PR is opened' } },
      { id: 'node-2', type: 'ai', position: { x: 250, y: 250 }, data: { label: 'Code Reviewer', description: 'Analyzes PR diff with GPT-4' } },
      { id: 'node-3', type: 'slack', position: { x: 250, y: 400 }, data: { label: 'Slack Notify', description: 'Send review summary to channel' } }
    ],
    edges: [
      { id: 'edge-1-2', source: 'node-1', target: 'node-2', animated: true },
      { id: 'edge-2-3', source: 'node-2', target: 'node-3', animated: true }
    ]
  },
  {
    id: '2',
    title: 'AI Email Router',
    desc: 'Read incoming emails, classify intent using AI, and route to Slack support or trigger auto-reply.',
    icon: Mail,
    color: '#f43f5e',
    category: 'Communication',
    difficulty: 'Beginner',
    tags: ['Webhook', 'OpenAI', 'Slack'],
    nodeChain: ['Email Trigger', 'AI Classify', 'Route'],
    nodeColors: ['#f43f5e', '#7c3aed', '#f59e0b'],
    nodes: [
      { id: 'node-1', type: 'webhook', position: { x: 250, y: 100 }, data: { label: 'Email Webhook', description: 'Receives new emails' } },
      { id: 'node-2', type: 'ai', position: { x: 250, y: 250 }, data: { label: 'Intent Classifier', description: 'GPT-4 classifies email intent' } },
      { id: 'node-3', type: 'slack', position: { x: 100, y: 400 }, data: { label: 'Route to Support', description: 'Send to #support' } },
      { id: 'node-4', type: 'http_request', position: { x: 400, y: 400 }, data: { label: 'Auto-Reply API', description: 'Send automated response' } }
    ],
    edges: [
      { id: 'edge-1-2', source: 'node-1', target: 'node-2', animated: true },
      { id: 'edge-2-3', source: 'node-2', target: 'node-3', animated: true, label: 'If Support' },
      { id: 'edge-2-4', source: 'node-2', target: 'node-4', animated: true, label: 'If General' }
    ]
  },
  {
    id: '3',
    title: 'Resume Matcher',
    desc: 'Parse incoming resumes and score them against specific job descriptions with AI-powered analysis.',
    icon: FileText,
    color: '#10b981',
    category: 'HR & Recruiting',
    difficulty: 'Beginner',
    tags: ['Webhook', 'OpenAI', 'Database'],
    nodeChain: ['Upload', 'AI Score', 'Save to DB'],
    nodeColors: ['#f97316', '#7c3aed', '#10b981'],
    nodes: [
      { id: 'node-1', type: 'webhook', position: { x: 250, y: 100 }, data: { label: 'Upload Resume', description: 'Triggered on file upload' } },
      { id: 'node-2', type: 'ai', position: { x: 250, y: 250 }, data: { label: 'Extract & Score', description: 'Extract skills and match JD' } },
      { id: 'node-3', type: 'database', position: { x: 250, y: 400 }, data: { label: 'Save to DB', description: 'Store candidate profile' } }
    ],
    edges: [
      { id: 'edge-1-2', source: 'node-1', target: 'node-2', animated: true },
      { id: 'edge-2-3', source: 'node-2', target: 'node-3', animated: true }
    ]
  },
  {
    id: '4',
    title: 'Trend Generator',
    desc: 'Fetch daily news, summarize trends using GPT-4, and publish a digest to your Notion workspace.',
    icon: TrendingUp,
    color: '#06b6d4',
    category: 'Content',
    difficulty: 'Intermediate',
    tags: ['Cron', 'HTTP', 'OpenAI', 'Notion'],
    nodeChain: ['Cron', 'Fetch', 'AI Summarize', 'Notion'],
    nodeColors: ['#f97316', '#06b6d4', '#7c3aed', '#6366f1'],
    nodes: [
      { id: 'node-1', type: 'cron_trigger', position: { x: 250, y: 100 }, data: { label: 'Daily Trigger', description: 'Runs every morning' } },
      { id: 'node-2', type: 'http_request', position: { x: 250, y: 250 }, data: { label: 'Fetch News', description: 'Call HackerNews API' } },
      { id: 'node-3', type: 'ai', position: { x: 250, y: 400 }, data: { label: 'Summarize Trends', description: 'GPT-4 identifies top trends' } },
      { id: 'node-4', type: 'database', position: { x: 250, y: 550 }, data: { label: 'Update Notion', description: 'Publish to workspace' } }
    ],
    edges: [
      { id: 'edge-1-2', source: 'node-1', target: 'node-2', animated: true },
      { id: 'edge-2-3', source: 'node-2', target: 'node-3', animated: true },
      { id: 'edge-3-4', source: 'node-3', target: 'node-4', animated: true }
    ]
  },
  {
    id: '5',
    title: 'AI Text Summarizer',
    desc: 'Receive input text via Webhook, reformat with JavaScript, and generate concise AI-summarized text output instantly.',
    icon: Zap,
    color: '#f59e0b',
    category: 'AI & Automation',
    difficulty: 'Beginner',
    tags: ['Webhook', 'Transform', 'AI Summarizer'],
    nodeChain: ['Webhook Trigger', 'Transform Text', 'AI Summarizer'],
    nodeColors: ['#f97316', '#3b82f6', '#7c3aed'],
    nodes: [
      { id: 'node-1', type: 'webhook', position: { x: 250, y: 100 }, data: { label: 'Webhook Input', description: 'Receives raw text payload' } },
      { id: 'node-2', type: 'transform', position: { x: 250, y: 250 }, data: { label: 'Transform Text', description: 'Extracts and formats text field', mappings: [{ output: 'text', source: 'text' }] } },
       { id: 'node-3', type: 'summarizer', position: { x: 250, y: 400 }, data: { label: 'AI Text Summarizer', description: 'Generates concise summarized text output', provider: 'gemini', model: 'gemini-3.6-flash', maxLength: 200, textField: 'text' } }
    ],
    edges: [
      { id: 'edge-1-2', source: 'node-1', target: 'node-2', animated: true },
      { id: 'edge-2-3', source: 'node-2', target: 'node-3', animated: true }
    ]
  },
]