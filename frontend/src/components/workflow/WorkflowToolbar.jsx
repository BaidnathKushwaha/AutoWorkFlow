import { Play, Save, UploadCloud, Square, Download, Upload, Undo, Redo, Loader2 } from 'lucide-react'

export default function WorkflowToolbar({ workflowName, status, onSave, onRun, onDeploy, onStop, onNameChange, hasTriggerNode }) {
  const deployDisabled = !hasTriggerNode
  const deployTitle = deployDisabled
    ? 'Add a trigger node to deploy this workflow. Manual Run does not require a trigger.'
    : 'Deploy workflow'

  return (
    <div
      style={{
        height: '64px',
        background: 'var(--bg-card)',
        borderBottom: '1px solid var(--border)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 16px',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        <input
          type="text"
          value={workflowName || ''}
          onChange={e => onNameChange && onNameChange(e.target.value)}
          placeholder="Workflow Title..."
          style={{
            background: 'transparent',
            border: '1px solid transparent',
            color: 'var(--text-primary)',
            fontSize: '16px',
            fontWeight: 600,
            padding: '4px 8px',
            borderRadius: '4px',
            outline: 'none',
            width: '240px',
            transition: 'border-color 0.2s',
          }}
          onFocus={e => (e.target.style.borderColor = 'var(--border)')}
          onBlur={e => (e.target.style.borderColor = 'transparent')}
        />

        <div
          className={`status-${(status || 'draft').toLowerCase()}`}
          style={{
            padding: '4px 10px',
            borderRadius: '12px',
            fontSize: '12px',
            fontWeight: 600,
            textTransform: 'capitalize',
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
          }}
        >
          {status === 'running' && <div className="pulse-dot" style={{ width: '6px', height: '6px', borderRadius: '50%', background: 'currentColor' }} />}
          {status}
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <div style={{ display: 'flex', gap: '4px', borderRight: '1px solid var(--border)', paddingRight: '12px' }}>
          <button className="btn-ghost" title="Undo"><Undo size={16} /></button>
          <button className="btn-ghost" title="Redo"><Redo size={16} /></button>
          <button className="btn-ghost" title="Import"><Upload size={16} /></button>
          <button className="btn-ghost" title="Export JSON"><Download size={16} /></button>
        </div>

        <button className="btn-secondary" onClick={onSave}>
          <Save size={16} /> Save
        </button>
        
        {status === 'running' ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <button
              className="btn-primary"
              disabled
              style={{
                background: 'var(--accent-amber)',
                color: '#ffffff',
                opacity: 0.9,
                cursor: 'not-allowed',
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                boxShadow: '0 0 12px rgba(245, 158, 11, 0.3)',
              }}
            >
              <Loader2 size={16} style={{ animation: 'spin 1s linear infinite' }} /> Running...
            </button>
            <button
              className="btn-secondary"
              style={{ color: 'var(--accent-rose)', borderColor: 'var(--accent-rose)' }}
              onClick={onStop}
              title="Stop Execution"
            >
              <Square size={16} fill="currentColor" /> Stop
            </button>
          </div>
        ) : (
          <button className="btn-primary" style={{ background: 'var(--accent-emerald)', boxShadow: '0 0 15px rgba(16, 185, 129, 0.3)' }} onClick={onRun}>
            <Play size={16} fill="currentColor" /> Run
          </button>
        )}
        
        <button
          className="btn-primary"
          onClick={deployDisabled ? undefined : onDeploy}
          disabled={deployDisabled}
          title={deployTitle}
          style={deployDisabled ? {
            opacity: 0.45,
            cursor: 'not-allowed',
            pointerEvents: 'none',
          } : undefined}
        >
          <UploadCloud size={16} /> Deploy
        </button>
      </div>
    </div>
  )
}
