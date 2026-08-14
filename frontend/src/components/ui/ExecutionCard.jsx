import { Play, CheckCircle, XCircle } from 'lucide-react'

export default function ExecutionCard({ execution }) {
  return (
    <div className="card card-hover" style={{ padding: '16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
      <div>
        <div style={{ fontSize: '14px', fontWeight: 600, marginBottom: '4px' }}>{execution.workflow}</div>
        <div style={{ fontSize: '12px', color: 'var(--text-secondary)', fontFamily: 'monospace' }}>{execution.id}</div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{execution.duration}</div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', fontWeight: 500, color: execution.status === 'success' ? 'var(--accent-emerald)' : execution.status === 'failed' ? 'var(--accent-rose)' : 'var(--accent-amber)' }}>
          {execution.status === 'success' && <CheckCircle size={14} />}
          {execution.status === 'failed' && <XCircle size={14} />}
          {execution.status === 'running' && <Play size={14} />}
          <span style={{ textTransform: 'capitalize' }}>{execution.status}</span>
        </div>
      </div>
    </div>
  )
}
