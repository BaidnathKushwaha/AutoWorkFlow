import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Play, CheckCircle, XCircle, Search, Clock, RefreshCw } from 'lucide-react'
import { useExecutionStore } from '../store/executionStore'

export default function Executions() {
  const { executions, loading, fetchExecutions } = useExecutionStore()
  const [searchQuery, setSearchQuery] = useState('')

  useEffect(() => {
    const STATIC_MOCK_IDS = new Set(['exe_f92jdk', 'exe_m29dkw', 'exe_x83mdj', 'exe_p92lsd', 'exe_a82jdm', 'exe_mskeefaz'])
    const currentExecs = useExecutionStore.getState().executions
    const cleaned = currentExecs.filter(e => e?.id && !STATIC_MOCK_IDS.has(e.id))
    if (cleaned.length !== currentExecs.length) useExecutionStore.setState({ executions: cleaned })
    fetchExecutions()
  }, [fetchExecutions])

  const filteredExecutions = executions.filter((exe) => {
    const query = searchQuery.toLowerCase().trim()
    if (!query) return true
    return (exe.id && exe.id.toLowerCase().includes(query)) || (exe.workflow && exe.workflow.toLowerCase().includes(query)) || (exe.trigger && exe.trigger.toLowerCase().includes(query)) || (exe.status && exe.status.toLowerCase().includes(query))
  })

  return (
    <div>
      <div style={{ marginBottom: '32px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', flexWrap: 'wrap', gap: '16px' }}>
        <div><h1 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '8px' }}>Execution History</h1><p style={{ color: 'var(--text-secondary)' }}>Monitor all workflow runs across your platform in real-time.</p></div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}><button className="btn-secondary" onClick={() => fetchExecutions()} disabled={loading} style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 14px' }} title="Refresh Executions"><RefreshCw size={16} className={loading ? 'spin' : ''} /><span>Refresh</span></button><div style={{ position: 'relative' }}><Search size={16} color="var(--text-muted)" style={{ position: 'absolute', left: '12px', top: '10px' }} /><input type="text" placeholder="Search execution or workflow..." value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} style={{ background: 'var(--bg-input)', border: '1px solid var(--border)', borderRadius: '8px', padding: '8px 16px 8px 36px', color: 'var(--text-primary)', outline: 'none', minWidth: '240px' }} /></div></div>
      </div>

      <div className="card" style={{ overflow: 'hidden' }}>
        {loading && filteredExecutions.length === 0 ? <div style={{ padding: '48px', textAlign: 'center', color: 'var(--text-secondary)' }}><RefreshCw size={24} className="spin" style={{ marginBottom: '12px', color: 'var(--accent)' }} /><div>Loading execution history...</div></div> : filteredExecutions.length === 0 ? <div style={{ padding: '48px', textAlign: 'center', color: 'var(--text-secondary)' }}><p style={{ fontSize: '16px', fontWeight: 500, marginBottom: '8px' }}>No executions found</p><p style={{ fontSize: '14px', color: 'var(--text-muted)' }}>{searchQuery ? 'No execution matching your search query.' : 'Run a workflow to populate real-time execution logs.'}</p></div> : <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
          <thead><tr style={{ background: 'var(--bg-surface)', borderBottom: '1px solid var(--border)' }}><th style={{ padding: '16px 24px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Execution ID</th><th style={{ padding: '16px 24px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Workflow</th><th style={{ padding: '16px 24px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Status</th><th style={{ padding: '16px 24px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Duration</th><th style={{ padding: '16px 24px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Trigger</th><th style={{ padding: '16px 24px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Timestamp</th></tr></thead>
          <tbody>{filteredExecutions.map((exe) => <tr key={exe.id} style={{ borderBottom: '1px solid var(--border-subtle)' }} className="card-hover"><td style={{ padding: '16px 24px', fontSize: '14px', fontFamily: 'monospace', color: 'var(--accent)' }}><Link to={`/executions/${exe.id}`} onClick={() => useExecutionStore.getState().setSelectedExecutionId(exe.id)} style={{ color: 'inherit', textDecoration: 'none', fontWeight: 600 }}>{exe.id.length > 18 ? `${exe.id.substring(0, 18)}...` : exe.id}</Link></td><td style={{ padding: '16px 24px', fontSize: '14px', fontWeight: 500 }}>{exe.workflow}</td><td style={{ padding: '16px 24px' }}><div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', fontWeight: 600, color: exe.status === 'success' ? 'var(--accent-emerald)' : exe.status === 'failed' ? 'var(--accent-rose)' : 'var(--accent-amber)' }}>{exe.status === 'success' && <CheckCircle size={14} />}{exe.status === 'failed' && <XCircle size={14} />}{exe.status === 'running' && <Play size={14} />}<span style={{ textTransform: 'capitalize' }}>{exe.status}</span></div></td><td style={{ padding: '16px 24px', fontSize: '14px', color: 'var(--text-secondary)' }}><div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}><Clock size={14} /> {exe.duration}</div></td><td style={{ padding: '16px 24px', fontSize: '14px', color: 'var(--text-secondary)' }}><span style={{ background: 'var(--bg-surface)', padding: '2px 8px', borderRadius: '4px', fontSize: '12px', textTransform: 'uppercase', fontWeight: 600, border: '1px solid var(--border)' }}>{exe.trigger}</span></td><td style={{ padding: '16px 24px', fontSize: '14px', color: 'var(--text-secondary)' }}>{exe.timestamp}</td></tr>)}</tbody>
        </table>}
      </div>
    </div>
  )
}
