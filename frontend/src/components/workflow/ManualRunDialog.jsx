import { useState } from 'react'
import { X, Play } from 'lucide-react'

const DEFAULT_INPUT = JSON.stringify({
    body: 'John Doe\nJava developer with 2 years of experience in Spring Boot, Java, REST APIs and PostgreSQL.',
    jobDescription: 'We are looking for a Java backend developer with Spring Boot, REST API and PostgreSQL experience.',
}, null, 2)

export default function ManualRunDialog({ open, onClose, onRun }) {
    const [value, setValue] = useState(DEFAULT_INPUT)
    const [error, setError] = useState('')

    if (!open) return null

    const handleRun = () => {
        try {
            const parsed = JSON.parse(value)
            if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
                setError('Manual input must be a JSON object.')
                return
            }
            setError('')
            onRun(parsed)
        } catch (err) {
            setError(`Invalid JSON: ${err.message}`)
        }
    }

    const handleClose = () => {
        setValue(DEFAULT_INPUT)
        setError('')
        onClose()
    }

    return (
        <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="manual-run-title"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) handleClose()
            }}
            style={{
                position: 'fixed', inset: 0, zIndex: 100,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                background: 'rgba(15, 23, 42, 0.45)', padding: '24px',
            }}
        >
            <div style={{
                width: 'min(640px, 100%)', background: 'var(--bg-surface)',
                border: '1px solid var(--border)', borderRadius: '12px',
                boxShadow: '0 20px 60px rgba(15, 23, 42, 0.25)', overflow: 'hidden',
            }}>
                <div style={{
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                    padding: '18px 20px', borderBottom: '1px solid var(--border)',
                }}>
                    <div>
                        <h2 id="manual-run-title" style={{ margin: 0, fontSize: '16px' }}>Run workflow</h2>
                        <p style={{ margin: '5px 0 0', fontSize: '12px', color: 'var(--text-muted)' }}>
                            Provide the JSON payload that should enter the workflow.
                        </p>
                    </div>
                    <button type="button" onClick={handleClose} aria-label="Close" className="btn-ghost" style={{ padding: '5px' }}>
                        <X size={16} />
                    </button>
                </div>

                <div style={{ padding: '20px' }}>
                    <label htmlFor="manual-run-input" style={{ display: 'block', marginBottom: '8px', fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)' }}>
                        Input JSON
                    </label>
                    <textarea
                        id="manual-run-input"
                        value={value}
                        onChange={(event) => { setValue(event.target.value); setError('') }}
                        spellCheck="false"
                        rows={13}
                        style={{
                            width: '100%', boxSizing: 'border-box', resize: 'vertical',
                            background: 'var(--bg-input)', border: `1px solid ${error ? 'var(--accent-rose)' : 'var(--border)'}`,
                            padding: '12px', borderRadius: '8px', color: 'var(--text-primary)',
                            fontSize: '12px', lineHeight: 1.5, fontFamily: 'monospace', outline: 'none',
                        }}
                    />
                    {error && <div style={{ marginTop: '7px', color: 'var(--accent-rose)', fontSize: '11px' }}>{error}</div>}
                    <div style={{ marginTop: '8px', color: 'var(--text-muted)', fontSize: '11px' }}>
                        Resume Matcher example payload is pre-filled. Replace it with any JSON object needed by your workflow.
                    </div>
                </div>

                <div style={{
                    display: 'flex', justifyContent: 'flex-end', gap: '8px',
                    padding: '14px 20px', borderTop: '1px solid var(--border)',
                }}>
                    <button type="button" onClick={handleClose} className="btn-ghost">Cancel</button>
                    <button type="button" onClick={handleRun} className="btn-primary" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <Play size={14} /> Run workflow
                    </button>
                </div>
            </div>
        </div>
    )
}
