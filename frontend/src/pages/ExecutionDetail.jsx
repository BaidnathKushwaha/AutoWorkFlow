import { useEffect } from 'react'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, CheckCircle, XCircle, Clock, Database, AlertCircle, Play, RefreshCw, Cpu, Terminal } from 'lucide-react'
import { useExecutionStore } from '../store/executionStore'

export default function ExecutionDetail() {
  const { id } = useParams()
  const { currentExecution, loadingDetail, fetchExecutionById, setSelectedExecutionId } = useExecutionStore()

  useEffect(() => {
    if (id) {
      setSelectedExecutionId(id)
      fetchExecutionById(id)
    }
  }, [id, fetchExecutionById, setSelectedExecutionId])

  if (loadingDetail) {
    return (
      <div style={{ padding: '64px', textAlign: 'center', color: 'var(--text-secondary)' }}>
        <RefreshCw size={28} className="spin" style={{ marginBottom: '16px', color: 'var(--accent)' }} />
        <div>Loading execution details...</div>
      </div>
    )
  }

  const exe = currentExecution || { id, workflow: 'Workflow Execution', status: 'success', duration: '0s', trigger: 'MANUAL', timestamp: 'Recently', stepsLogs: [] }
  const isSuccess = exe.status === 'success' || exe.status === 'SUCCESS'
  const isFailed = exe.status === 'failed' || exe.status === 'FAILED'
  const isRunning = exe.status === 'running' || exe.status === 'RUNNING'
  const steps = exe.stepsLogs && exe.stepsLogs.length > 0 ? exe.stepsLogs : []

  return (
    <div>
      <div style={{ marginBottom: '24px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <Link to="/executions" style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', color: 'var(--text-secondary)', textDecoration: 'none', fontSize: '14px' }}><ArrowLeft size={16} /> Back to Executions</Link>
          {exe.workflowId && <Link to={`/builder/${exe.workflowId}?executionId=${exe.id}`} onClick={() => setSelectedExecutionId(exe.id)} style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', padding: '6px 14px', background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: '6px', color: 'var(--accent)', textDecoration: 'none', fontSize: '13px', fontWeight: 600 }}><Terminal size={14} /> Inspect in Builder</Link>}
        </div>
        <h1 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '8px', fontFamily: 'monospace' }}>Execution: {exe.id}</h1>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', color: 'var(--text-secondary)', fontSize: '14px', flexWrap: 'wrap' }}>
          <span>Workflow: <strong>{exe.workflow}</strong></span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px', color: isSuccess ? 'var(--accent-emerald)' : isFailed ? 'var(--accent-rose)' : 'var(--accent-amber)', fontWeight: 600 }}>{isSuccess && <CheckCircle size={14} />}{isFailed && <XCircle size={14} />}{isRunning && <Play size={14} />}<span style={{ textTransform: 'capitalize' }}>{exe.status}</span></span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><Clock size={14} /> {exe.duration}</span>
          <span style={{ background: 'var(--bg-surface)', padding: '2px 8px', borderRadius: '4px', fontSize: '12px', textTransform: 'uppercase', fontWeight: 600, border: '1px solid var(--border)' }}>Trigger: {exe.trigger}</span>
          <span>{exe.timestamp}</span>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: '24px' }}>
        <div className="card" style={{ padding: '24px', minHeight: '400px' }}>
          <h2 style={{ fontSize: '16px', fontWeight: 600, marginBottom: '24px', display: 'flex', alignItems: 'center', gap: '8px' }}><Cpu size={18} color="var(--accent)" /> Execution Trace ({steps.length} steps)</h2>
          {steps.length === 0 ? <div style={{ padding: '32px', textAlign: 'center', color: 'var(--text-muted)', background: 'var(--bg-surface)', borderRadius: '12px', border: '1px dashed var(--border)' }}>No detailed step logs recorded for this execution run.</div> : <div style={{ display: 'flex', flexDirection: 'column', gap: '24px', position: 'relative' }}>
            <div style={{ position: 'absolute', left: '20px', top: '20px', bottom: '20px', width: '2px', background: 'var(--border)' }} />
            {steps.map((step, idx) => {
              const stepSuccess = !step.error && (step.status === 'success' || step.status === 'SUCCESS' || !step.status)
              const stepDuration = step.startTime && step.endTime ? (() => { const ms = new Date(step.endTime) - new Date(step.startTime); return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(2)}s` })() : ''
              return <div key={idx} style={{ display: 'flex', gap: '24px', position: 'relative', zIndex: 10 }}>
                <div style={{ width: '40px', height: '40px', borderRadius: '50%', background: 'var(--bg-surface)', border: `2px solid ${stepSuccess ? 'var(--accent-emerald)' : 'var(--accent-rose)'}`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>{stepSuccess ? <CheckCircle size={18} color="var(--accent-emerald)" /> : <XCircle size={18} color="var(--accent-rose)" />}</div>
                <div className="card" style={{ flex: 1, padding: '16px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}><h3 style={{ fontSize: '14px', fontWeight: 600 }}>{step.nodeName || step.nodeId || `Step ${idx + 1}`}</h3>{stepDuration && <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontFamily: 'monospace' }}>{stepDuration}</span>}</div>
                  <p style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '12px' }}>Node ID: <code style={{ fontFamily: 'monospace' }}>{step.nodeId}</code></p>
                  {step.inputPayload && <div style={{ marginBottom: '8px' }}><div style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>Input Payload</div><pre style={{ background: 'var(--bg-input)', padding: '10px 12px', borderRadius: '8px', fontSize: '12px', fontFamily: 'monospace', color: 'var(--text-secondary)', overflowX: 'auto', margin: 0 }}>{typeof step.inputPayload === 'string' ? step.inputPayload : JSON.stringify(step.inputPayload, null, 2)}</pre></div>}
                  {step.outputPayload && <div><div style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>Output Payload</div><pre style={{ background: 'var(--bg-input)', padding: '10px 12px', borderRadius: '8px', fontSize: '12px', fontFamily: 'monospace', color: 'var(--accent-emerald)', overflowX: 'auto', margin: 0 }}>{typeof step.outputPayload === 'string' ? step.outputPayload : JSON.stringify(step.outputPayload, null, 2)}</pre></div>}
                  {step.error && <div style={{ marginTop: '8px', background: 'rgba(239, 68, 68, 0.1)', border: '1px solid var(--accent-rose)', padding: '10px 12px', borderRadius: '8px', fontSize: '12px', color: 'var(--accent-rose)' }}><strong>Step Error:</strong> {step.error}</div>}
                </div>
              </div>
            })}
          </div>}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          <div className="card" style={{ padding: '24px' }}><h2 style={{ fontSize: '16px', fontWeight: 600, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}><Database size={16} /> Steps Executed</h2><div style={{ fontSize: '24px', fontWeight: 700, fontFamily: 'Syne', marginBottom: '4px' }}>{steps.length}</div><div style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>Completed in total duration of <strong>{exe.duration}</strong></div></div>
          <div className="card" style={{ padding: '24px' }}><h2 style={{ fontSize: '16px', fontWeight: 600, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}><AlertCircle size={16} color={exe.errorMessage ? 'var(--accent-rose)' : 'var(--text-muted)'} /> Diagnostics</h2>{exe.errorMessage ? <div style={{ fontSize: '13px', color: 'var(--accent-rose)', background: 'rgba(239, 68, 68, 0.1)', padding: '12px', borderRadius: '8px', border: '1px solid var(--accent-rose)' }}>{exe.errorMessage}</div> : <div style={{ fontSize: '13px', color: 'var(--text-muted)' }}>No runtime errors recorded for this execution.</div>}</div>
        </div>
      </div>
    </div>
  )
}
