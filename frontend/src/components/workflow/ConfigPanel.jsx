import { X, Trash2, Copy, Check, Link2, Plus } from 'lucide-react'
import { useState } from 'react'
import { nodeConfigs, PROVIDER_MODELS } from '../../data/nodeTypes'
import { toast } from 'sonner'
import { buildWebhookUrl } from '../../utils/constants'

export function resolveProviderChangePatch(newProvider, currentModel) {
  if (newProvider === 'auto') return { provider: 'auto', model: '' }
  const validModels = PROVIDER_MODELS[newProvider] || []
  if (validModels.length > 0 && !validModels.includes(currentModel)) return { provider: newProvider, model: validModels[0] }
  return null
}

function resolveSelectOptions(field, currentValue, selectedNode) {
  let options = field.options || []
  if (field.optionsFrom === 'provider') options = PROVIDER_MODELS[selectedNode?.data?.provider || 'gemini'] || []
  if (field.optionsFrom === 'cases') options = Array.isArray(selectedNode?.data?.cases) ? selectedNode.data.cases : []
  if (currentValue && !options.includes(currentValue)) options = [currentValue, ...options]
  return options
}

function CaseListEditor({ value, onCommit }) {
  const cases = Array.isArray(value) ? value : []
  const commit = (index, raw) => {
    const next = raw.trim()
    if (!next) { toast.error('Case value cannot be empty.'); return }
    if (cases.some((c, i) => i !== index && c === next)) { toast.error(`"${next}" is already used by another case.`); return }
    onCommit(cases.map((c, i) => i === index ? next : c))
  }
  return <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
    {cases.map((item, i) => <div key={`${i}-${item}`} style={{ display: 'flex', gap: 6 }}>
      <input defaultValue={item} onBlur={e => commit(i, e.target.value)} onKeyDown={e => e.key === 'Enter' && e.currentTarget.blur()} style={inputStyle} />
      <button type="button" onClick={() => onCommit(cases.filter((_, j) => j !== i))} style={iconButtonStyle}><Trash2 size={13} /></button>
    </div>)}
    <button type="button" onClick={() => { let n = cases.length + 1; let label = `Case ${n}`; while (cases.includes(label)) label = `Case ${++n}`; onCommit([...cases, label]) }} style={addButtonStyle}><Plus size={12} /> Add case</button>
  </div>
}

function TagsInput({ value, placeholder, onCommit }) {
  const [text, setText] = useState(Array.isArray(value) ? value.join(', ') : (value || ''))
  const commit = raw => onCommit(raw.split(',').map(s => s.trim()).filter(Boolean))
  return <input value={text} placeholder={placeholder} onChange={e => setText(e.target.value)} onBlur={e => commit(e.target.value)} style={inputStyle} />
}

function MappingEditor({ value, onCommit }) {
  const rows = Array.isArray(value) ? value : []
  const update = (i, patch) => onCommit(rows.map((r, j) => j === i ? { ...r, ...patch } : r))
  const cell = { ...inputStyle, padding: '6px 8px' }
  return <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 24px', gap: 6, fontSize: 10, fontWeight: 600, color: 'var(--text-muted)' }}><span>Output field</span><span>Source path</span><span>Strip prefix</span><span /></div>
    {rows.map((r, i) => <div key={i} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 24px', gap: 6 }}>
      <input style={cell} placeholder="repo" value={r.output || ''} onChange={e => update(i, { output: e.target.value })} />
      <input style={cell} placeholder="repository.full_name" value={r.source || ''} onChange={e => update(i, { source: e.target.value })} />
      <input style={cell} placeholder="refs/heads/" value={r.strip || ''} onChange={e => update(i, { strip: e.target.value })} />
      <button type="button" onClick={() => onCommit(rows.filter((_, j) => j !== i))} style={iconButtonStyle}><Trash2 size={13} /></button>
    </div>)}
    <button type="button" onClick={() => onCommit([...rows, { output: '', source: '', strip: '' }])} style={addButtonStyle}><Plus size={12} /> Add mapping row</button>
    <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>Source path supports nested fields and array indices.</div>
  </div>
}

const inputStyle = { background: 'var(--bg-input)', border: '1px solid var(--border)', padding: '8px 12px', borderRadius: 6, color: 'var(--text-primary)', fontSize: 13, outline: 'none', width: '100%', boxSizing: 'border-box' }
const iconButtonStyle = { background: 'transparent', border: 'none', color: 'var(--accent-rose)', cursor: 'pointer', padding: 4 }
const addButtonStyle = { display: 'flex', alignItems: 'center', gap: 5, alignSelf: 'flex-start', background: 'transparent', border: '1px dashed var(--border)', borderRadius: 6, padding: '6px 10px', color: 'var(--text-secondary)', fontSize: 11, cursor: 'pointer' }

function isFakeSamplePayload(payload) {
  return !!payload && typeof payload === 'object' && (payload.action === 'test_run' || payload.text === 'Sample text from trigger node for testing workflow execution.' || payload.title === 'Sample Test Input Title')
}

function getActualInputPayload(selectedNode, executionData) {
  if (!executionData) return null
  const input = executionData.input
  if (input != null && !isFakeSamplePayload(input) && (typeof input !== 'object' || Object.keys(input).length > 0)) return input
  if (executionData.output?.inputText) return executionData.output.inputText
  if (selectedNode?.data?.inputText) return selectedNode.data.inputText
  if (selectedNode?.data?.prompt) return selectedNode.data.prompt
  return null
}

export default function ConfigPanel({ selectedNode, executionData, onClose, onDeleteNode, onUpdateNode, webhookToken, webhookUrl: webhookUrlProp, deployed, initialTab }) {
  const [copied, setCopied] = useState(false)
  const [activeTab, setActiveTab] = useState(initialTab || 'parameters')
  const [copiedJson, setCopiedJson] = useState(false)
  if (!selectedNode) return <div style={{ width: 380, background: 'var(--bg-surface)', borderLeft: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)' }}>Select a node to configure</div>

  const nodeType = selectedNode.type
  const configSchema = nodeConfigs[nodeType]
  const webhookNode = nodeType === 'webhook' || nodeType === 'github_event' || nodeType === 'trigger' || selectedNode.id?.includes('trigger')
  const webhookUrl = webhookUrlProp || buildWebhookUrl(webhookToken)
  const handleFieldChange = (key, value) => {
    if (!onUpdateNode) return
    if (key === 'provider') {
      const patch = resolveProviderChangePatch(value, selectedNode.data?.model)
      if (patch) return onUpdateNode(selectedNode.id, patch)
    }
    onUpdateNode(selectedNode.id, { [key]: value })
  }
  const formatJson = value => value == null ? 'No execution data yet' : typeof value === 'string' ? (() => { try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value } })() : JSON.stringify(value, null, 2)
  const copyJson = async value => { try { await navigator.clipboard.writeText(formatJson(value)); setCopiedJson(true); toast.success('JSON copied'); setTimeout(() => setCopiedJson(false), 1500) } catch { toast.error('Could not copy JSON') } }
  const isVisible = field => !field.visibleWhen || Object.entries(field.visibleWhen).every(([key, expected]) => selectedNode.data?.[key] === expected)

  return <div style={{ width: 380, background: 'var(--bg-surface)', borderLeft: '1px solid var(--border)', display: 'flex', flexDirection: 'column' }}>
    <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}><h3 style={{ fontSize: 16, fontWeight: 600 }}>{selectedNode.data?.label || 'Configuration'}</h3><button className="btn-ghost" onClick={onClose}><X size={16} /></button></div>
    <div style={{ flex: 1, overflowY: 'auto', padding: '20px 24px' }}>
      <div style={{ display: 'flex', borderBottom: '1px solid var(--border)', marginBottom: 24 }}>{[['parameters', 'Parameters'], ['input', 'Input'], ['output', 'Output']].map(([tab, label]) => <button key={tab} type="button" onClick={() => setActiveTab(tab)} style={{ flex: 1, padding: '10px 8px', border: 'none', borderBottom: activeTab === tab ? '2px solid var(--accent)' : '2px solid transparent', background: 'transparent', color: activeTab === tab ? 'var(--text-primary)' : 'var(--text-muted)', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>{label}</button>)}</div>
      {activeTab === 'parameters' && <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
        {webhookNode && <div style={{ background: 'rgba(249,115,22,.08)', border: '1px solid rgba(249,115,22,.3)', borderRadius: 8, padding: 12 }}><div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#f97316', fontWeight: 600, fontSize: 13 }}><Link2 size={15} /> Webhook Payload URL</div><p style={{ fontSize: 11, color: 'var(--text-secondary)' }}>Point your GitHub repository webhooks or HTTP POST triggers to this URL:</p>{webhookUrl ? <div style={{ display: 'flex', gap: 6 }}><input readOnly value={webhookUrl} style={inputStyle} /><button type="button" onClick={() => { navigator.clipboard.writeText(webhookUrl); setCopied(true); toast.success('Webhook URL copied to clipboard!'); setTimeout(() => setCopied(false), 2000) }} className="btn-primary">{copied ? <Check size={13} /> : <Copy size={13} />}</button></div> : <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Save workflow to generate unique Webhook URL token.</span>}{!deployed && webhookUrl && <span style={{ fontSize: 11, color: '#f97316' }}>Not deployed yet — click Deploy so GitHub can reach this URL.</span>}</div>}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}><label style={labelStyle}>Node Name</label><input value={selectedNode.data?.label || ''} onChange={e => onUpdateNode?.(selectedNode.id, { label: e.target.value })} style={inputStyle} /></div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}><label style={labelStyle}>Description</label><input value={selectedNode.data?.description || ''} placeholder="Brief summary of node function..." onChange={e => handleFieldChange('description', e.target.value)} style={inputStyle} /></div>
        {configSchema?.fields.filter(isVisible).map(field => {
          const currentValue = selectedNode.data?.[field.key] !== undefined ? selectedNode.data[field.key] : (field.default !== undefined ? field.default : '')
          return <div key={`${field.key}-${JSON.stringify(field.visibleWhen || {})}`} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}><label style={labelStyle}>{field.label}</label>{field.description && <span title={field.description} style={{ fontSize: 11, color: 'var(--text-muted)' }}>ⓘ</span>}</div>
            {field.type === 'text' && <input value={currentValue} placeholder={field.placeholder} onChange={e => handleFieldChange(field.key, e.target.value)} style={inputStyle} />}
            {field.type === 'number' && <input type="number" value={currentValue} placeholder={field.placeholder} onChange={e => handleFieldChange(field.key, e.target.value)} style={inputStyle} />}
            {field.type === 'textarea' && <textarea rows={field.rows || 4} value={currentValue} placeholder={field.placeholder} onChange={e => handleFieldChange(field.key, e.target.value)} style={{ ...inputStyle, resize: 'vertical', fontFamily: 'monospace' }} />}
            {field.type === 'select' && field.optionsFrom === 'provider' && selectedNode.data?.provider === 'auto' && <div style={{ padding: 8, borderRadius: 6, background: 'var(--bg-input)', border: '1px dashed var(--border)', color: 'var(--text-muted)', fontSize: 12 }}>Auto — each provider tried uses its own default model</div>}
            {field.type === 'select' && !(field.optionsFrom === 'provider' && selectedNode.data?.provider === 'auto') && <select value={currentValue} onChange={e => handleFieldChange(field.key, e.target.value)} style={inputStyle}>{resolveSelectOptions(field, currentValue, selectedNode).map(opt => <option key={opt} value={opt}>{opt}</option>)}</select>}
            {field.type === 'tags' && <TagsInput value={currentValue} placeholder={field.placeholder} onCommit={v => handleFieldChange(field.key, v)} />}
            {field.type === 'mapping-editor' && <MappingEditor value={currentValue} onCommit={v => handleFieldChange(field.key, v)} />}
            {field.type === 'case-list' && <CaseListEditor value={currentValue} onCommit={v => handleFieldChange(field.key, v)} />}
            {field.type === 'checkbox' && <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--text-secondary)' }}><input type="checkbox" checked={!!currentValue} onChange={e => handleFieldChange(field.key, e.target.checked)} />{currentValue ? 'Enabled' : 'Disabled'}</label>}
            {field.type === 'range' && <div style={{ display: 'flex', gap: 12 }}><input type="range" min={field.min} max={field.max} step={field.step} value={currentValue} onChange={e => handleFieldChange(field.key, parseFloat(e.target.value))} style={{ flex: 1 }} /><span>{currentValue}</span></div>}
          </div>
        })}
        {!configSchema && <div style={{ padding: 12, border: '1px dashed var(--border)', color: 'var(--text-muted)', fontSize: 12, textAlign: 'center' }}>No custom settings required for this node.</div>}
      </div>}
      {(activeTab === 'input' || activeTab === 'output') && (() => {
        const displayed = activeTab === 'input' ? getActualInputPayload(selectedNode, executionData) : (executionData?.output ?? null)
        const hasValue = displayed !== null && displayed !== undefined && displayed !== ''
        return <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}><div style={{ display: 'flex', justifyContent: 'space-between' }}><span style={labelStyle}>{activeTab === 'input' ? 'Input Payload' : 'Output Payload'}</span><button type="button" onClick={() => copyJson(displayed)} disabled={!hasValue} className="btn-ghost">{copiedJson ? <Check size={12} /> : <Copy size={12} />}</button></div>{hasValue ? <pre style={{ margin: 0, padding: 12, background: 'var(--bg-input)', border: '1px solid var(--border)', borderRadius: 7, color: 'var(--text-primary)', fontSize: 11, lineHeight: 1.5, fontFamily: 'monospace', whiteSpace: 'pre-wrap', wordBreak: 'break-word', maxHeight: 450, overflow: 'auto' }}>{formatJson(displayed)}</pre> : <div style={{ padding: 30, textAlign: 'center', border: '1px dashed var(--border)', borderRadius: 7, color: 'var(--text-muted)', fontSize: 12 }}>{!executionData ? 'No execution data yet' : activeTab === 'input' ? 'No input payload for this step execution' : 'No output payload for this step execution'}</div>}</div>
      })()}
      <div style={{ borderTop: '1px solid var(--border)', marginTop: 24, paddingTop: 20 }}><button onClick={() => onDeleteNode(selectedNode.id)} style={{ width: '100%', padding: 10, borderRadius: 8, background: 'rgba(244,63,94,.1)', border: '1px solid var(--accent-rose)', color: 'var(--accent-rose)', fontSize: 13, fontWeight: 600, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, cursor: 'pointer' }}><Trash2 size={15} /> Delete Node</button></div>
    </div>
  </div>
}

const labelStyle = { fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', letterSpacing: '.02em' }
