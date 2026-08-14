import { MoreVertical } from 'lucide-react'
import { Link } from 'react-router-dom'

export default function WorkflowCard({ id, name, status, lastRun, trigger }) {
  return (
    <div className="card card-hover" style={{ padding: '20px', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: status === 'active' ? 'var(--accent-emerald)' : status === 'failed' ? 'var(--accent-rose)' : 'var(--text-muted)' }} />
          <h3 style={{ fontSize: '16px', fontWeight: 600 }}>{name}</h3>
        </div>
        <button className="btn-ghost" style={{ padding: '4px' }}>
          <MoreVertical size={16} />
        </button>
      </div>
      
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '20px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
          <span style={{ color: 'var(--text-muted)' }}>Status</span>
          <span className={`status-${status}`} style={{ padding: '2px 8px', borderRadius: '10px', fontSize: '11px', fontWeight: 600, textTransform: 'capitalize' }}>{status}</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
          <span style={{ color: 'var(--text-muted)' }}>Trigger</span>
          <span style={{ color: 'var(--text-secondary)' }}>{trigger}</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
          <span style={{ color: 'var(--text-muted)' }}>Last Run</span>
          <span style={{ color: 'var(--text-secondary)' }}>{lastRun}</span>
        </div>
      </div>
      
      <Link to={`/builder/${id}`} className="btn-secondary" style={{ width: '100%', justifyContent: 'center', textDecoration: 'none' }}>
        Edit Workflow
      </Link>
    </div>
  )
}
