import { useState } from 'react'
import { Terminal, AlertCircle, Database, Brain, X, CheckCircle, XCircle } from 'lucide-react'
import { useExecutionStore } from '../../store/executionStore'

export default function ExecutionConsole({ isOpen, onClose, onSelectExecutionNode }) {
  const [activeTab, setActiveTab] = useState('logs')
  const executions = useExecutionStore((state) => state.executions)
  const selectedExecutionId = useExecutionStore((state) => state.selectedExecutionId)
  const setSelectedExecutionId = useExecutionStore((state) => state.setSelectedExecutionId)

  if (!isOpen) return null;

  // Deliberately does NOT fall back to executions[0] (the most recent execution across
  // ALL workflows) when nothing is selected — that fallback was the actual root cause of
  // a stale/unrelated execution (old SUCCESS status, old Webhook Input) being displayed
  // as if it were the current run's result, including right after a failed Run attempt
  // that never got a new executionId. No selection now genuinely means no data yet.
  const activeExecution =
    (selectedExecutionId && executions.find((e) => e.id === selectedExecutionId)) || null

  const stepLogs = activeExecution?.stepsLogs || []
  const errors = stepLogs.filter(s => s.status === 'failed' || s.error)
  const apiResponses = stepLogs.filter(s => s.outputPayload)
  const aiResponses = stepLogs.filter(s => s.nodeName?.toLowerCase().includes('ai') || s.nodeName?.toLowerCase().includes('openai') || s.nodeName?.toLowerCase().includes('summariz'))

  const tabs = [
    { id: 'logs', label: 'Logs', icon: Terminal, count: stepLogs.length },
    { id: 'errors', label: 'Errors', icon: AlertCircle, count: errors.length },
    { id: 'api', label: 'API Responses', icon: Database, count: apiResponses.length },
    { id: 'ai', label: 'AI Output', icon: Brain, count: aiResponses.length },
  ]

  return (
    <div
      style={{
        height: '260px',
        background: 'var(--bg-card)',
        borderTop: '1px solid var(--border)',
        display: 'flex',
        flexDirection: 'column',
        position: 'absolute',
        bottom: 0,
        left: 0,
        right: 0,
        zIndex: 50,
      }}
    >
      {/* Header Tabs */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--border)', background: 'var(--bg-surface)' }}>
        <div style={{ display: 'flex' }}>
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: '12px 20px',
                background: activeTab === tab.id ? 'var(--bg-card)' : 'transparent',
                border: 'none',
                borderTop: activeTab === tab.id ? '2px solid var(--accent)' : '2px solid transparent',
                borderRight: '1px solid var(--border)',
                color: activeTab === tab.id ? 'var(--accent)' : 'var(--text-secondary)',
                cursor: 'pointer',
                fontSize: '13px',
                fontWeight: 600,
              }}
            >
              <tab.icon size={14} />
              {tab.label}
              {tab.count > 0 && (
                <span style={{
                  fontSize: '11px', padding: '1px 6px', borderRadius: '99px',
                  background: activeTab === tab.id ? 'var(--accent)22' : 'var(--bg-input)',
                  color: activeTab === tab.id ? 'var(--accent)' : 'var(--text-muted)'
                }}>
                  {tab.count}
                </span>
              )}
            </button>
          ))}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginRight: '16px' }}>
          {executions.length > 0 && (
            <select
              value={activeExecution?.id || ''}
              onChange={(e) => setSelectedExecutionId(e.target.value)}
              style={{ background: 'var(--bg-input)', border: '1px solid var(--border)', borderRadius: '4px', color: 'var(--text-primary)', fontSize: '11px', padding: '4px 8px', outline: 'none', cursor: 'pointer' }}
            >
              {executions.map((e) => (
                <option key={e.id} value={e.id}>
                  Exec: {e.id.length > 12 ? `${e.id.substring(0, 12)}...` : e.id} ({e.status})
                </option>
              ))}
            </select>
          )}
          <button className="btn-ghost" onClick={onClose}>
            <X size={16} />
          </button>
        </div>
      </div>

      {/* Console Content Area */}
      <div style={{ flex: 1, padding: '16px', overflowY: 'auto', fontFamily: 'JetBrains Mono, monospace', fontSize: '12.5px', color: 'var(--text-secondary)' }}>
        {!activeExecution ? (
          <div style={{ color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '8px', padding: '12px' }}>
            <Terminal size={14} /> No execution data available yet. Click "Run" to execute the workflow.
          </div>
        ) : (
          <>
            {activeTab === 'logs' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                <div style={{ display: 'flex', gap: '12px', color: 'var(--text-muted)', fontSize: '11.5px', borderBottom: '1px dashed var(--border)', paddingBottom: '6px' }}>
                  <span>EXEC ID: {activeExecution.id}</span>
                  <span>| WORKFLOW: {activeExecution.workflow}</span>
                  <span>| STATUS: <span style={{ color: activeExecution.status === 'success' ? 'var(--accent-emerald)' : 'var(--accent-rose)', fontWeight: 700 }}>{activeExecution.status?.toUpperCase()}</span></span>
                  <span>| DURATION: {activeExecution.duration}</span>
                </div>

                {stepLogs.map((step, idx) => {
                  const time = step.startTime ? new Date(step.startTime).toLocaleTimeString() : `Step ${idx + 1}`
                  const isOk = step.status === 'success' || !step.error
                  const clickable = !!(onSelectExecutionNode && step.nodeId)
                  return (
                    <div
                      key={idx}
                      onClick={clickable ? () => onSelectExecutionNode(step.nodeId) : undefined}
                      title={clickable ? 'Click to open this node' : undefined}
                      style={{
                        display: 'flex', flexDirection: 'column', gap: '4px',
                        background: 'rgba(255,255,255,0.01)', padding: '6px 10px', borderRadius: '6px',
                        border: '1px solid var(--border-subtle)',
                        cursor: clickable ? 'pointer' : 'default',
                      }}
                      onMouseEnter={clickable ? (e) => { e.currentTarget.style.borderColor = 'var(--accent)' } : undefined}
                      onMouseLeave={clickable ? (e) => { e.currentTarget.style.borderColor = 'var(--border-subtle)' } : undefined}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <span style={{ color: 'var(--text-muted)', minWidth: '75px' }}>[{time}]</span>
                        <span style={{ color: isOk ? 'var(--accent-emerald)' : 'var(--accent-rose)', fontWeight: 700, minWidth: '45px' }}>
                          {isOk ? 'OK' : 'FAIL'}
                        </span>
                        <span style={{ color: 'var(--text-primary)', fontWeight: 600 }}>{step.nodeName || `Node ${idx + 1}`}</span>
                        {step.durationMs != null && (
                          <span style={{ color: 'var(--text-muted)', fontSize: '11px', marginLeft: 'auto' }}>{step.durationMs}ms</span>
                        )}
                      </div>
                      {step.outputPayload?.summary ? (
                        <div style={{ paddingLeft: '90px', fontSize: '12px', color: 'var(--node-ai)', fontWeight: 600, marginTop: '2px' }}>
                          ✨ {step.outputPayload.summary}
                        </div>
                      ) : step.outputPayload && (
                        <div style={{ paddingLeft: '90px', fontSize: '11.5px', color: 'var(--text-muted)' }}>
                          Output: <span style={{ color: 'var(--accent-cyan)' }}>{JSON.stringify(step.outputPayload)}</span>
                        </div>
                      )}
                      {step.error && (
                        <div style={{ paddingLeft: '90px', fontSize: '11.5px', color: 'var(--accent-rose)' }}>
                          Error: {step.error}
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            )}

            {activeTab === 'errors' && (
              <div>
                {errors.length === 0 ? (
                  <div style={{ color: 'var(--accent-emerald)', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <CheckCircle size={14} /> Clean execution. No errors detected.
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {errors.map((err, i) => (
                      <div
                        key={i}
                        onClick={onSelectExecutionNode && err.nodeId ? () => onSelectExecutionNode(err.nodeId) : undefined}
                        style={{ padding: '12px', background: 'rgba(244,63,94,0.08)', border: '1px solid var(--accent-rose)', borderRadius: '8px', color: 'var(--accent-rose)', cursor: (onSelectExecutionNode && err.nodeId) ? 'pointer' : 'default' }}
                      >
                        <div style={{ fontWeight: 700, marginBottom: '4px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                          <XCircle size={14} /> Node "{err.nodeName}" Failed
                        </div>
                        <div style={{ fontSize: '12px', fontFamily: 'monospace' }}>{err.error || 'Execution stopped due to missing configuration or disconnected integration.'}</div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {activeTab === 'api' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {apiResponses.map((step, i) => (
                  <div key={i} style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', padding: '10px 14px', borderRadius: '8px' }}>
                    <div style={{ fontWeight: 600, color: 'var(--accent)', marginBottom: '4px' }}>{step.nodeName}</div>
                    <pre style={{ margin: 0, fontSize: '11px', color: 'var(--text-primary)', whiteSpace: 'pre-wrap' }}>
                      {JSON.stringify(step.outputPayload, null, 2)}
                    </pre>
                  </div>
                ))}
              </div>
            )}

            {activeTab === 'ai' && (
              <div>
                {aiResponses.length === 0 ? (
                  <div style={{ color: 'var(--text-muted)' }}>No AI node responses recorded in this execution.</div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {aiResponses.map((step, i) => (
                      <div key={i} style={{ background: 'rgba(124,58,237,0.06)', border: '1px solid var(--node-ai)', padding: '12px', borderRadius: '8px' }}>
                        <div style={{ fontWeight: 600, color: 'var(--node-ai)', marginBottom: '6px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                          <Brain size={14} /> {step.nodeName} Response
                        </div>
                        <div style={{ color: 'var(--text-primary)', fontSize: '12px', lineHeight: 1.5 }}>
                          {step.outputPayload?.summary || step.outputPayload?.result || JSON.stringify(step.outputPayload)}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
