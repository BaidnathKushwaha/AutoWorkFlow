import { memo, useState, Fragment } from 'react'
import { Handle, Position } from '@xyflow/react'
import {
  ChevronDown, ChevronUp, Clock, Zap, GitPullRequest, Mail, Brain,
  Network, FileText, Tag, GitBranch, RefreshCw, Merge, Timer, Code2,
  Globe, MessageSquare, BookOpen, Table, Database, File, Server,
  Send, MessageCircle, Hash, Blocks
} from 'lucide-react'

const NODE_ICON_MAP = {
  cron_trigger: Clock,
  webhook: Zap,
  github_event: GitPullRequest,
  email_received: Mail,
  ai: Brain,
  ai_router: Network,
  summarizer: FileText,
  classifier: Tag,
  if_condition: GitBranch,
  switch: GitBranch,
  loop: RefreshCw,
  merge: Merge,
  delay: Timer,
  transform: Code2,
  http_request: Globe,
  github: GitPullRequest,
  slack: MessageSquare,
  gmail: Mail,
  notion: BookOpen,
  google_sheets: Table,
  database: Database,
  file: File,
  redis: Server,
  send_email: Send,
  sms: MessageCircle,
  discord: Hash,
}

const NODE_COLOR_MAP = {
  cron_trigger: '#f97316',
  webhook: '#f97316',
  github_event: '#f97316',
  email_received: '#f97316',
  ai: '#7c3aed',
  ai_router: '#7c3aed',
  summarizer: '#7c3aed',
  classifier: '#7c3aed',
  if_condition: '#3b82f6',
  switch: '#3b82f6',
  loop: '#3b82f6',
  merge: '#3b82f6',
  delay: '#3b82f6',
  transform: '#3b82f6',
  http_request: '#06b6d4',
  github: '#06b6d4',
  slack: '#06b6d4',
  gmail: '#06b6d4',
  notion: '#06b6d4',
  google_sheets: '#10b981',
  database: '#10b981',
  file: '#10b981',
  redis: '#10b981',
  send_email: '#eab308',
  sms: '#eab308',
  discord: '#eab308',
}

function getNodeSummary(data) {
  // If specific configuration fields are populated, show a dynamic summary preview
  if (data?.url) return `${data.method || 'GET'} ${data.url}`
  if (data?.expression) return `Cron: ${data.expression} (${data.timezone || 'UTC'})`
  if (data?.prompt) return `Prompt: "${data.prompt.length > 35 ? data.prompt.slice(0, 35) + '...' : data.prompt}"`
  if (data?.inputText) return `Input: "${data.inputText.length > 35 ? data.inputText.slice(0, 35) + '...' : data.inputText}"`
  if (data?.message) return `Message: "${data.message.length > 35 ? data.message.slice(0, 35) + '...' : data.message}"`
  if (data?.channel) return `Channel: ${data.channel}`
  if (data?.repo) return `Repo: ${data.repo}`
  if (data?.left && data?.operator) return `If: ${data.left} ${data.operator} ${data.right || ''}`
  if (data?.code) return `JS: ${data.code.length > 35 ? data.code.slice(0, 35) + '...' : data.code}`
  if (data?.query) return `SQL: ${data.query.length > 35 ? data.query.slice(0, 35) + '...' : data.query}`
  if (data?.to) return `To: ${data.to}`
  if (data?.labels && Array.isArray(data.labels) && data.labels.length > 0) return `Labels: ${data.labels.join(', ')}`
  if (data?.branches && Array.isArray(data.branches) && data.branches.length > 0) return `Branches: ${data.branches.join(', ')}`
  if (data?.cases && Array.isArray(data.cases) && data.cases.length > 0) {
    return `Match "${data.field || '?'}" → ${data.cases.join(', ')}${data.defaultCase ? ` (default: ${data.defaultCase})` : ''}`
  }

  if (data?.description && data.description !== 'New node') {
    return data.description
  }

  return 'Configure this node to start.'
}

function NodeWrapper({ data, selected, type, nodeType, children, color: propColor, icon: Icon, isRunning, isFailed, isSuccess, executionData: propExecutionData }) {
  // Collapse state: new nodes dropped from palette have data.collapsed = true;
  // nodes loaded from backend have no collapsed flag → default to false (expanded).
  const [isCollapsed, setIsCollapsed] = useState(!!data?.collapsed)

  const summaryText = getNodeSummary(data, type)
  const executionData = propExecutionData || data?.executionData
  const itemCount = Array.isArray(executionData?.output)
      ? executionData.output.length
      : executionData?.output != null
          ? 1
          : 0

  const isSwitch = nodeType === 'switch'
  // Defensive normalization for RENDERING safety specifically (trim + drop empty +
  // dedupe) — independent of ConfigPanel's own validation on the editing side, so a
  // node can never render duplicate/empty React keys or duplicate React Flow handle
  // IDs, even for data saved before that validation existed.
  const switchCases = (() => {
    if (!isSwitch || !Array.isArray(data?.cases)) return []
    const seen = new Set()
    const result = []
    for (const raw of data.cases) {
      const trimmed = typeof raw === 'string' ? raw.trim() : ''
      if (!trimmed || seen.has(trimmed)) continue
      seen.add(trimmed)
      result.push(trimmed)
    }
    return result
  })()

  // Only Switch gets a nodeType-driven icon override — every other type keeps the
  // exact original lookup chain (type -> data.type -> Icon prop -> Blocks) so IF
  // Condition / AI Router / Loop / Merge / Delay / Transform render identically to
  // before this change, unaffected by nodeType now being passed through for Switch.
  const RenderIcon = (isSwitch && NODE_ICON_MAP.switch) || NODE_ICON_MAP[type] || NODE_ICON_MAP[data?.type] || Icon || Blocks
  const iconColor = (isSwitch && NODE_COLOR_MAP.switch) || NODE_COLOR_MAP[type] || NODE_COLOR_MAP[data?.type] || propColor || '#7c3aed'

  return (
    <div
      className={`card ${selected ? 'glow-accent' : ''} ${isRunning ? 'running-glow' : ''}`}
      style={{
        width: '280px',
        background: 'var(--bg-surface)',
        borderColor: selected ? 'var(--accent)' : iconColor,
        borderRadius: '12px',
        padding: 0,
        position: 'relative',
        overflow: 'visible',
        borderWidth: selected ? '2px' : '1px',
        transition: 'all 0.2s',
      }}
    >
      {/* Node Header */}
      <div
        style={{
          padding: '14px 18px',
          background: `rgba(0,0,0,0.2)`,
          borderBottom: isCollapsed ? 'none' : '1px solid var(--border-subtle)',
          borderRadius: isCollapsed ? '11px' : '11px 11px 0 0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div style={{ width: '24px', height: '24px', borderRadius: '6px', background: `${iconColor}20`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <RenderIcon size={14} color={iconColor} />
          </div>
          <span style={{ fontSize: '13px', fontWeight: 600 }}>{data.label}</span>
        </div>

        
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          {/* Status indicator */}
          {isRunning && <div className="pulse-dot" style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent-amber)' }} />}
          {isSuccess && <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent-emerald)' }} />}
          {isFailed && <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent-rose)' }} />}
          {data.isActive !== false && !isRunning && !isSuccess && !isFailed && (
            <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent-emerald)' }} />
          )}

          {/* Expand/Collapse toggle */}
          <button
            title={isCollapsed ? 'Expand node' : 'Collapse node'}
            onClick={(e) => {
              e.stopPropagation()
              setIsCollapsed(prev => !prev)
            }}
            style={{
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              padding: '2px',
              display: 'flex',
              alignItems: 'center',
              color: 'var(--text-muted)',
              borderRadius: '4px',
              transition: 'color 0.15s',
            }}
            onMouseEnter={e => (e.currentTarget.style.color = 'var(--text-primary)')}
            onMouseLeave={e => (e.currentTarget.style.color = 'var(--text-muted)')}
          >
            {isCollapsed ? <ChevronDown size={14} /> : <ChevronUp size={14} />}
          </button>
        </div>
      </div>

      {/* Node Body — hidden when collapsed */}
      {!isCollapsed && (
        <>
          <div style={{ padding: '12px 18px 14px', fontSize: '12px', color: 'var(--text-secondary)', lineHeight: '1.5', wordBreak: 'break-word' }}>
            {summaryText}
          </div>
          {switchCases.length > 0 && (
            <div style={{ padding: '0 18px 12px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
              {switchCases.map((c) => (
                <div key={c} style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '11px', color: 'var(--text-muted)' }}>
                  <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: iconColor, flexShrink: 0 }} />
                  {c}{data?.defaultCase === c ? ' (default)' : ''}
                </div>
              ))}
            </div>
          )}
          {executionData && (
            <div
              style={{
                padding: '0 16px 10px',
                display: 'flex',
                justifyContent: 'flex-end',
              }}
            >
              <div
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '5px',
                  padding: '3px 7px',
                  borderRadius: '5px',
                  fontSize: '10px',
                  fontWeight: 600,
                  background: executionData.status === 'failed'
                      ? 'rgba(244, 63, 94, 0.1)'
                      : 'rgba(16, 185, 129, 0.1)',
                  color: executionData.status === 'failed'
                      ? 'var(--accent-rose)'
                      : 'var(--accent-emerald)',
                  border: `1px solid ${
                      executionData.status === 'failed'
                          ? 'rgba(244, 63, 94, 0.25)'
                          : 'rgba(16, 185, 129, 0.25)'
                  }`,
                }}
              >
                {executionData.status === 'failed' ? '✗ Error' : `✓ ${itemCount} item${itemCount === 1 ? '' : 's'}`}
              </div>
            </div>
          )}
          {children}
        </>
      )}
      
      {/* Target (input) handle */}
      {type !== 'trigger' && (
        <Handle
          type="target"
          position={Position.Top}
          isConnectable={true}
          className="custom-node-handle custom-handle-top"
          style={{
            width: '14px',
            height: '14px',
            borderRadius: '50%',
            background: '#6366f1',
            border: '2px solid #ffffff',
            boxShadow: '0 0 10px rgba(99, 102, 241, 0.9), 0 0 4px rgba(255, 255, 255, 0.9)',
            top: '-7px',
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 100,
          }}
        />
      )}
      
      {/* Source (output) handle(s). Switch renders one named handle per configured
          case instead of the single default handle every other node type uses —
          id = the case's exact string value, so onConnect can read
          params.sourceHandle directly as the branch to write into edge.data.branch.
          Each handle gets a small label directly beneath it (not just a bare dot) so
          the branch it represents is visually obvious without opening ConfigPanel;
          the card itself does not grow — labels sit in the already-visible overflow
          area below the node, same region the handle dot itself occupies. */}
      {type !== 'output' && (
        isSwitch && switchCases.length > 0 ? (
          switchCases.map((caseValue, i) => {
            const leftPct = ((i + 1) / (switchCases.length + 1)) * 100
            return (
              <Fragment key={caseValue}>
                <Handle
                  id={caseValue}
                  type="source"
                  position={Position.Bottom}
                  isConnectable={true}
                  className="custom-node-handle custom-handle-bottom"
                  title={caseValue}
                  style={{
                    width: '14px',
                    height: '14px',
                    borderRadius: '50%',
                    background: iconColor || '#a855f7',
                    border: '2px solid #ffffff',
                    boxShadow: `0 0 10px ${iconColor || '#a855f7'}, 0 0 4px rgba(255, 255, 255, 0.9)`,
                    bottom: '-7px',
                    left: `${leftPct}%`,
                    transform: 'translateX(-50%)',
                    zIndex: 100,
                  }}
                />
                {/* Label directly under its handle — makes the branch visually obvious
                    without opening ConfigPanel. Card height/footprint is unchanged;
                    this sits in the already-visible overflow area below the node,
                    same as the dot itself. */}
                <div
                  title={caseValue}
                  style={{
                    position: 'absolute',
                    left: `${leftPct}%`,
                    bottom: '-24px',
                    transform: 'translateX(-50%)',
                    fontSize: '9.5px',
                    lineHeight: 1.2,
                    color: 'var(--text-muted)',
                    maxWidth: '64px',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    textAlign: 'center',
                    pointerEvents: 'none',
                    zIndex: 99,
                  }}
                >
                  {caseValue}
                </div>
              </Fragment>
            )
          })
        ) : (
          <Handle
            type="source"
            position={Position.Bottom}
            isConnectable={true}
            className="custom-node-handle custom-handle-bottom"
            style={{
              width: '14px',
              height: '14px',
              borderRadius: '50%',
              background: iconColor || '#a855f7',
              border: '2px solid #ffffff',
              boxShadow: `0 0 10px ${iconColor || '#a855f7'}, 0 0 4px rgba(255, 255, 255, 0.9)`,
              bottom: '-7px',
              left: '50%',
              transform: 'translateX(-50%)',
              zIndex: 100,
            }}
          />
        )
      )}
    </div>
  )
}

export default memo(NodeWrapper)
