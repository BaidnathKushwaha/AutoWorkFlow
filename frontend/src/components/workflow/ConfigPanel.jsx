import { X, Trash2, Copy, Check, Link2, Plus } from 'lucide-react'
import { useState } from 'react'
import { nodeConfigs, PROVIDER_MODELS } from '../../data/nodeTypes'
import { toast } from 'sonner'
import { buildWebhookUrl } from '../../utils/constants'

// Pure decision logic for handleFieldChange's provider-change special case, pulled out
// so it's independently testable without rendering the component. Given the newly
// selected provider and the model currently stored on the node, returns the patch to
// apply — or null if the provider change needs no special model handling (plain
// `{ [key]: value }` in that case).
//
//   -> auto:            always clears the model (AiProviderRouter strips it anyway;
//                        leaving a stale provider-specific value stored but hidden is
//                        misleading data).
//   auto -> concrete:    currentModel is '' (from the clear above) -> not in the new
//                        provider's list -> picks that provider's first model.
//   concrete -> concrete: unchanged pre-existing behavior — replaces an incompatible
//                        model with the new provider's first one, leaves a compatible
//                        model alone.
export function resolveProviderChangePatch(newProvider, currentModel) {
    if (newProvider === 'auto') {
        return { provider: 'auto', model: '' }
    }
    const validModels = PROVIDER_MODELS[newProvider] || []
    if (validModels.length > 0 && !validModels.includes(currentModel)) {
        return { provider: newProvider, model: validModels[0] }
    }
    return null
}

// Resolves a select field's <option> list. Most fields use a plain, static
// `field.options` array. Fields marked `optionsFrom: 'provider'` (the AI `model`
// field on ai/summarizer/classifier/ai_router) instead look up PROVIDER_MODELS
// keyed by that same node's current `provider` value, so the model dropdown
// updates when the provider does.
//
// If the currently stored value isn't in the resolved list — a legacy saved
// workflow, or a custom OpenRouter model id typed in some other way — it's kept
// as an extra option rather than silently dropped, so the dropdown never lies
// about what's actually stored on the node (the classic "value doesn't match any
// <option>, browser silently shows the first one instead" trap).
function resolveSelectOptions(field, currentValue, selectedNode) {
    let options = field.options || []
    if (field.optionsFrom === 'provider') {
        const provider = selectedNode?.data?.provider || 'gemini'
        options = PROVIDER_MODELS[provider] || []
    }
    if (field.optionsFrom === 'cases') {
        options = Array.isArray(selectedNode?.data?.cases) ? selectedNode.data.cases : []
    }
    if (currentValue && !options.includes(currentValue)) {
        options = [currentValue, ...options]
    }
    return options
}

// Switch node's case editor — a plain ordered list of text values (no code/expression
// editor, per spec). Each case becomes one of the node's dynamic output handles (see
// NodeWrapper.jsx). Always commits a brand-new array (never mutates in place) since a
// freshly-dropped node's default `cases` array is shared by reference until first edit.
// One case row: keeps its own draft text while typing (so the user can freely edit,
// including transiently-empty/duplicate text, without the list re-normalizing mid-
// keystroke) and only commits — trimmed — on blur. An invalid commit (empty, or a
// duplicate of another case) is rejected with a toast and the draft reverts to its
// last valid committed value, leaving every other case untouched.
function CaseRow({ value, onRemove, onValidateAndCommit }) {
    const [draft, setDraft] = useState(value)

    const commit = () => {
        const trimmed = draft.trim()
        if (trimmed === value) return // no real change
        const accepted = onValidateAndCommit(trimmed)
        if (!accepted) setDraft(value) // revert to last valid value
    }

    return (
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <input
                type="text"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onBlur={commit}
                onKeyDown={(e) => { if (e.key === 'Enter') e.currentTarget.blur() }}
                style={{ flex: 1, background: 'var(--bg-input)', border: '1px solid var(--border)', padding: '7px 10px', borderRadius: '6px', color: 'var(--text-primary)', fontSize: '12px', outline: 'none' }}
            />
            <button type="button" onClick={onRemove} title="Remove case"
                style={{ background: 'transparent', border: 'none', color: 'var(--accent-rose)', cursor: 'pointer', padding: '4px' }}>
                <Trash2 size={13} />
            </button>
        </div>
    )
}

function CaseListEditor({ value, onCommit }) {
    const cases = Array.isArray(value) ? value : []

    const validateAndCommit = (index, trimmed) => {
        if (trimmed === '') {
            toast.error('Case value cannot be empty.')
            return false
        }
        if (cases.some((c, i) => i !== index && c === trimmed)) {
            toast.error(`"${trimmed}" is already used by another case.`)
            return false
        }
        onCommit(cases.map((c, i) => (i === index ? trimmed : c)))
        return true
    }

    const addCase = () => {
        let n = cases.length + 1
        let label = `Case ${n}`
        while (cases.includes(label)) { n += 1; label = `Case ${n}` }
        onCommit([...cases, label])
    }
    const removeCase = (index) => {
        onCommit(cases.filter((_, i) => i !== index))
    }

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {cases.map((caseValue, i) => (
                <CaseRow
                    key={`${i}-${caseValue}`}
                    value={caseValue}
                    onRemove={() => removeCase(i)}
                    onValidateAndCommit={(trimmed) => validateAndCommit(i, trimmed)}
                />
            ))}
            {cases.length === 0 && (
                <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>No cases yet — add one below.</div>
            )}
            <button type="button" onClick={addCase}
                style={{ display: 'flex', alignItems: 'center', gap: '5px', alignSelf: 'flex-start', background: 'transparent', border: '1px dashed var(--border)', borderRadius: '6px', padding: '6px 10px', color: 'var(--text-secondary)', fontSize: '11px', cursor: 'pointer' }}>
                <Plus size={12} /> Add case
            </button>
        </div>
    )
}

// Renders a comma-separated list (e.g. classifier labels, router branches) as a single text
// input, but always saves it to node data as a real JSON array — the backend strategies
// (ClassifierStrategy, AiRouterStrategy) iterate the config value as an array, so storing a
// plain string here would silently be treated as empty and fall back to Java-side defaults.
function TagsInput({ value, placeholder, onCommit }) {
    const [prevValue, setPrevValue] = useState(value)
    const [text, setText] = useState(() => (Array.isArray(value) ? value.join(', ') : (value || '')))

    const currentKey = Array.isArray(value) ? value.join('|') : (value || '')
    const prevKey = Array.isArray(prevValue) ? prevValue.join('|') : (prevValue || '')

    if (currentKey !== prevKey) {
        setPrevValue(value)
        setText(Array.isArray(value) ? value.join(', ') : (value || ''))
    }

    const commit = (raw) => {
        const tags = raw.split(',').map(s => s.trim()).filter(Boolean)
        onCommit(tags)
    }

    return (
        <input
            type="text"
            value={text}
            placeholder={placeholder}
            onChange={(e) => setText(e.target.value)}
            onBlur={(e) => commit(e.target.value)}
            style={{ background: 'var(--bg-input)', border: '1px solid var(--border)', padding: '8px 12px', borderRadius: '6px', color: 'var(--text-primary)', fontSize: '13px', outline: 'none' }}
        />
    )
}

// Transform node's field-mapping editor — rows of { output, source, strip } that are saved
// verbatim as the array TransformStrategy.java reads. No JS is ever generated or executed;
// see TransformStrategy's javadoc for why (arbitrary backend script execution == RCE risk).
function MappingEditor({ value, onCommit }) {
    const rows = Array.isArray(value) && value.length > 0 ? value : []

    const updateRow = (index, patch) => {
        const next = rows.map((r, i) => (i === index ? { ...r, ...patch } : r))
        onCommit(next)
    }
    const addRow = () => onCommit([...rows, { output: '', source: '', strip: '' }])
    const removeRow = (index) => onCommit(rows.filter((_, i) => i !== index))

    const cellStyle = { background: 'var(--bg-input)', border: '1px solid var(--border)', padding: '6px 8px', borderRadius: '5px', color: 'var(--text-primary)', fontSize: '12px', outline: 'none', width: '100%' }

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 24px', gap: '6px', fontSize: '10px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                <span>Output field</span>
                <span>Source path</span>
                <span>Strip prefix (opt.)</span>
                <span />
            </div>

            {rows.length === 0 && (
                <div style={{ fontSize: '11px', color: 'var(--text-muted)', padding: '8px 0' }}>
                    No mapping rows — input will pass through to the next node unchanged.
                </div>
            )}

            {rows.map((row, i) => (
                <div key={i} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 24px', gap: '6px', alignItems: 'center' }}>
                    <input type="text" style={cellStyle} placeholder="repo" value={row.output || ''}
                        onChange={(e) => updateRow(i, { output: e.target.value })} />
                    <input type="text" style={cellStyle} placeholder="repository.full_name" value={row.source || ''}
                        onChange={(e) => updateRow(i, { source: e.target.value })} />
                    <input type="text" style={cellStyle} placeholder="refs/heads/" value={row.strip || ''}
                        onChange={(e) => updateRow(i, { strip: e.target.value })} />
                    <button type="button" onClick={() => removeRow(i)} title="Remove row"
                        style={{ background: 'transparent', border: 'none', color: 'var(--accent-rose)', cursor: 'pointer', padding: '4px' }}>
                        <Trash2 size={13} />
                    </button>
                </div>
            ))}

            <button type="button" onClick={addRow}
                style={{ display: 'flex', alignItems: 'center', gap: '5px', alignSelf: 'flex-start', background: 'transparent', border: '1px dashed var(--border)', borderRadius: '6px', padding: '6px 10px', color: 'var(--text-secondary)', fontSize: '11px', cursor: 'pointer' }}>
                <Plus size={12} /> Add mapping row
            </button>

            <div style={{ fontSize: '10px', color: 'var(--text-muted)', lineHeight: 1.5 }}>
                Source path supports nested fields and array indices, e.g. <code>commits.0.message</code>.
            </div>
        </div>
    )
}

export default function ConfigPanel({ selectedNode, executionData, onClose, onDeleteNode, onUpdateNode, webhookToken, webhookUrl: webhookUrlProp, deployed, initialTab }) {
    const [copied, setCopied] = useState(false)
    const [activeTab, setActiveTab] = useState('parameters')
    const [copiedJson, setCopiedJson] = useState(false)
    const [prevTabKey, setPrevTabKey] = useState(null)

    const currentTabKey = `${selectedNode?.id || ''}_${initialTab || 'parameters'}`
    if (currentTabKey !== prevTabKey) {
        setPrevTabKey(currentTabKey)
        setActiveTab(initialTab || 'parameters')
    }

    if (!selectedNode) {
        return (
            <div style={{ width: '380px', background: 'var(--bg-surface)', borderLeft: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                Select a node to configure
            </div>
        )
    }

    const nodeType = selectedNode.type
    const configSchema = nodeConfigs[nodeType]
    const isWebhookOrGithub = nodeType === 'webhook' || nodeType === 'github_event' || nodeType === 'trigger' || (selectedNode.id && selectedNode.id.includes('trigger'))
    // Prefer the URL the backend computed (respects PUBLIC_BASE_URL / ngrok); fall back to building it client-side.
    const webhookUrl = webhookUrlProp || buildWebhookUrl(webhookToken)

    const handleCopyWebhookUrl = () => {
        if (!webhookUrl) return
        navigator.clipboard.writeText(webhookUrl)
        setCopied(true)
        toast.success('Webhook URL copied to clipboard!')
        setTimeout(() => setCopied(false), 2000)
    }

    const handleFieldChange = (key, value) => {
        if (!onUpdateNode) return

        // When the provider changes on an AI-capable node, keep `model` valid for the
        // newly selected provider instead of silently leaving a mismatched value — see
        // resolveProviderChangePatch above for the exact rules. This only runs on an
        // ACTIVE user change to `provider` — it never touches nodes on load, so a saved
        // workflow with provider: openai / gemini / openrouter / auto loads unchanged.
        if (key === 'provider') {
            const patch = resolveProviderChangePatch(value, selectedNode.data?.model)
            if (patch) {
                onUpdateNode(selectedNode.id, patch)
                return
            }
        }

        onUpdateNode(selectedNode.id, {
            [key]: value
        })
    }

    const handleLabelChange = (e) => {
        if (onUpdateNode) {
            onUpdateNode(selectedNode.id, {
                label: e.target.value
            })
        }
    }

function isFakeSamplePayload(payload) {
    if (!payload || typeof payload !== 'object') return false
    return (
        payload.action === 'test_run' ||
        payload.text === 'Sample text from trigger node for testing workflow execution.' ||
        payload.title === 'Sample Test Input Title'
    )
}

function getActualInputPayload(selectedNode, executionData) {
    if (!executionData) return null

    const rawInput = executionData.input

    if (rawInput != null && !isFakeSamplePayload(rawInput)) {
        if (typeof rawInput === 'object' && Object.keys(rawInput).length === 0) {
            // empty object
        } else {
            return rawInput
        }
    }

    if (executionData.output && typeof executionData.output === 'object' && executionData.output.inputText) {
        return executionData.output.inputText
    }

    if (selectedNode?.data?.inputText) {
        return selectedNode.data.inputText
    }

    if (selectedNode?.data?.prompt) {
        return selectedNode.data.prompt
    }

    return null
}

    const formatJson = (value) => {
        if (value === null || value === undefined) {
            return 'No execution data yet'
        }

        if (typeof value === 'string') {
            try {
                return JSON.stringify(JSON.parse(value), null, 2)
            } catch {
                return value
            }
        }

        try {
            return JSON.stringify(value, null, 2)
        } catch {
            return String(value)
        }
    }

    const handleCopyJson = async (value) => {
        try {
            await navigator.clipboard.writeText(formatJson(value))
            setCopiedJson(true)
            toast.success('JSON copied')
            setTimeout(() => setCopiedJson(false), 1500)
        } catch {
            toast.error('Could not copy JSON')
        }
    }

    return (
        <div style={{ width: '380px', background: 'var(--bg-surface)', borderLeft: '1px solid var(--border)', display: 'flex', flexDirection: 'column' }}>
            <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h3 style={{ fontSize: '16px', fontWeight: 600 }}>{selectedNode.data?.label || 'Configuration'}</h3>
                <button className="btn-ghost" onClick={onClose} style={{ padding: '4px' }}>
                    <X size={16} />
                </button>
            </div>

            <div style={{ flex: 1, overflowY: 'auto', padding: '20px 24px' }}>
                <div style={{
                    display: 'flex',
                    borderBottom: '1px solid var(--border)',
                    marginBottom: '24px'
                }}>
                    {[
                        ['parameters', 'Parameters'],
                        ['input', 'Input'],
                        ['output', 'Output']
                    ].map(([tab, label]) => (
                        <button
                            key={tab}
                            type="button"
                            onClick={() => setActiveTab(tab)}
                            style={{
                                flex: 1,
                                padding: '10px 8px',
                                border: 'none',
                                borderBottom: activeTab === tab
                                    ? '2px solid var(--accent)'
                                    : '2px solid transparent',
                                background: 'transparent',
                                color: activeTab === tab
                                    ? 'var(--text-primary)'
                                    : 'var(--text-muted)',
                                fontSize: '12px',
                                fontWeight: 600,
                                cursor: 'pointer',
                                letterSpacing: '0.02em',
                            }}
                        >
                            {label}
                        </button>
                    ))}
                </div>
                {activeTab === 'parameters' && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>

                    {/* Webhook Token Banner for Webhook / GitHub Triggers */}
                    {isWebhookOrGithub && (
                        <div style={{
                            background: 'rgba(249, 115, 22, 0.08)',
                            border: '1px solid rgba(249, 115, 22, 0.3)',
                            borderRadius: '8px',
                            padding: '12px',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '8px'
                        }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#f97316', fontWeight: 600, fontSize: '13px' }}>
                                <Link2 size={15} />
                                Webhook Payload URL
                            </div>
                            <p style={{ fontSize: '11px', color: 'var(--text-secondary)', margin: 0, lineHeight: 1.4 }}>
                                Point your GitHub repository webhooks or HTTP POST triggers to this URL:
                            </p>
                            {webhookUrl ? (
                                <>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '4px' }}>
                                        <input
                                            type="text"
                                            readOnly
                                            value={webhookUrl}
                                            style={{
                                                flex: 1,
                                                background: 'var(--bg-input)',
                                                border: '1px solid var(--border)',
                                                padding: '6px 8px',
                                                borderRadius: '4px',
                                                fontSize: '11px',
                                                fontFamily: 'monospace',
                                                color: 'var(--text-primary)',
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                            }}
                                        />
                                        <button
                                            type="button"
                                            onClick={handleCopyWebhookUrl}
                                            style={{
                                                background: 'var(--accent)',
                                                border: 'none',
                                                borderRadius: '4px',
                                                padding: '6px 10px',
                                                color: '#fff',
                                                cursor: 'pointer',
                                                display: 'flex',
                                                alignItems: 'center',
                                                gap: '4px',
                                                fontSize: '11px',
                                                fontWeight: 600
                                            }}
                                        >
                                            {copied ? <Check size={13} /> : <Copy size={13} />}
                                            {copied ? 'Copied' : 'Copy'}
                                        </button>
                                    </div>
                                    {!deployed && (
                                        <span style={{ fontSize: '11px', color: '#f97316' }}>
                    Not deployed yet — click Deploy so GitHub can actually reach this URL.
                  </span>
                                    )}
                                </>
                            ) : (
                                <span style={{ fontSize: '11px', color: 'var(--text-muted)', italic: 'true' }}>
                  Save workflow to generate unique Webhook URL token.
                </span>
                            )}
                        </div>
                    )}

                    {/* Node Name */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                        <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', letterSpacing: '0.02em' }}>Node Name</label>
                        <input
                            type="text"
                            value={selectedNode.data?.label || ''}
                            onChange={handleLabelChange}
                            style={{ background: 'var(--bg-input)', border: '1px solid var(--border)', padding: '10px 14px', borderRadius: '8px', color: 'var(--text-primary)', fontSize: '13px', outline: 'none', width: '100%' }}
                        />
                    </div>

                    {/* Node Description */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                        <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', letterSpacing: '0.02em' }}>Description</label>
                        <input
                            type="text"
                            value={selectedNode.data?.description || ''}
                            placeholder="Brief summary of node function..."
                            onChange={(e) => handleFieldChange('description', e.target.value)}
                            style={{ background: 'var(--bg-input)', border: '1px solid var(--border)', padding: '10px 14px', borderRadius: '8px', color: 'var(--text-primary)', fontSize: '13px', outline: 'none', width: '100%' }}
                        />
                    </div>

                    {/* Dynamic Fields from Schema */}
                    {configSchema && configSchema.fields.map((field) => {
                        const currentValue = selectedNode.data?.[field.key] !== undefined ? selectedNode.data[field.key] : (field.default !== undefined ? field.default : '');
                        return (
                            <div key={field.key} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', letterSpacing: '0.02em' }}>{field.label}</label>
                                    {field.description && (
                                        <span title={field.description} style={{ fontSize: '11px', color: 'var(--text-muted)', cursor: 'help' }}>ⓘ</span>
                                    )}
                                </div>

                                {field.type === 'text' && (
                                    <input
                                        type="text"
                                        value={currentValue}
                                        placeholder={field.placeholder}
                                        onChange={(e) => handleFieldChange(field.key, e.target.value)}
                                        style={{ background: 'var(--bg-input)', border: '1px solid var(--border)', padding: '10px 14px', borderRadius: '8px', color: 'var(--text-primary)', fontSize: '13px', outline: 'none', width: '100%' }}
                                    />
                                )}

                                {field.type === 'number' && (
                                    <input
                                        type="number"
                                        value={currentValue}
                                        placeholder={field.placeholder}
                                        onChange={(e) => handleFieldChange(field.key, e.target.value)}
                                        style={{ background: 'var(--bg-input)', border: '1px solid var(--border)', padding: '8px 12px', borderRadius: '6px', color: 'var(--text-primary)', fontSize: '13px', outline: 'none' }}
                                    />
                                )}

                                {field.type === 'textarea' && (
                                    <textarea
                                        rows={field.rows || 4}
                                        value={currentValue}
                                        placeholder={field.placeholder}
                                        onChange={(e) => handleFieldChange(field.key, e.target.value)}
                                        style={{ background: 'var(--bg-input)', border: '1px solid var(--border)', padding: '8px 12px', borderRadius: '6px', color: 'var(--text-primary)', fontSize: '13px', outline: 'none', resize: 'vertical', fontFamily: 'monospace' }}
                                    />
                                )}

                                {field.type === 'select' && field.optionsFrom === 'provider' && selectedNode.data?.provider === 'auto' && (
                                    <div style={{ padding: '8px 12px', borderRadius: '6px', background: 'var(--bg-input)', border: '1px dashed var(--border)', color: 'var(--text-muted)', fontSize: '12px' }}>
                                        Auto — each provider tried uses its own default model
                                    </div>
                                )}

                                {field.type === 'select' && !(field.optionsFrom === 'provider' && selectedNode.data?.provider === 'auto') && (
                                    <select
                                        value={currentValue}
                                        onChange={(e) => handleFieldChange(field.key, e.target.value)}
                                        style={{ background: 'var(--bg-input)', border: '1px solid var(--border)', padding: '8px 12px', borderRadius: '6px', color: 'var(--text-primary)', fontSize: '13px', outline: 'none' }}
                                    >
                                        {resolveSelectOptions(field, currentValue, selectedNode).map(opt => (
                                            <option key={opt} value={opt}>{opt}</option>
                                        ))}
                                    </select>
                                )}

                                {field.type === 'tags' && (
                                    <TagsInput
                                        value={currentValue}
                                        placeholder={field.placeholder}
                                        onCommit={(tags) => handleFieldChange(field.key, tags)}
                                    />
                                )}

                                {field.type === 'mapping-editor' && (
                                    <MappingEditor
                                        value={currentValue}
                                        onCommit={(rows) => handleFieldChange(field.key, rows)}
                                    />
                                )}

                                {field.type === 'case-list' && (
                                    <CaseListEditor
                                        value={currentValue}
                                        onCommit={(cases) => handleFieldChange(field.key, cases)}
                                    />
                                )}

                                {field.type === 'checkbox' && (
                                    <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', color: 'var(--text-secondary)', cursor: 'pointer' }}>
                                        <input
                                            type="checkbox"
                                            checked={!!currentValue}
                                            onChange={(e) => handleFieldChange(field.key, e.target.checked)}
                                            style={{ accentColor: 'var(--accent)' }}
                                        />
                                        {currentValue ? 'Enabled' : 'Disabled'}
                                    </label>
                                )}

                                {field.type === 'range' && (
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                        <input
                                            type="range"
                                            min={field.min}
                                            max={field.max}
                                            step={field.step}
                                            value={currentValue}
                                            onChange={(e) => handleFieldChange(field.key, parseFloat(e.target.value))}
                                            style={{ flex: 1, accentColor: 'var(--accent)' }}
                                        />
                                        <span style={{ fontSize: '13px', color: 'var(--text-primary)', minWidth: '24px', textAlign: 'right' }}>{currentValue}</span>
                                    </div>
                                )}
                            </div>
                        )
                    })}

                    {/* Fallback configuration for other node types */}
                    {!configSchema && (
                        <div style={{ padding: '12px', background: 'rgba(255,255,255,0.02)', borderRadius: '8px', border: '1px dashed var(--border)', color: 'var(--text-muted)', fontSize: '12px', textAlign: 'center' }}>
                            No custom settings required for this node.
                        </div>
                    )}
                </div>
                )}

                {(activeTab === 'input' || activeTab === 'output') && (
                    <div style={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '12px'
                    }}>
                        {(() => {
                            const displayedValue = activeTab === 'input'
                                ? getActualInputPayload(selectedNode, executionData)
                                : (executionData?.output ?? null)

                            const hasValue = displayedValue !== null && displayedValue !== undefined && displayedValue !== ''

                            return (
                                <>
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'center'
                                    }}>
                                        <span style={{
                                            fontSize: '12px',
                                            fontWeight: 600,
                                            color: 'var(--text-secondary)'
                                        }}>
                                            {activeTab === 'input' ? 'Input Payload' : 'Output Payload'}
                                        </span>

                                        <button
                                            type="button"
                                            onClick={() => handleCopyJson(displayedValue)}
                                            disabled={!hasValue}
                                            style={{
                                                display: 'flex',
                                                alignItems: 'center',
                                                gap: '5px',
                                                padding: '5px 8px',
                                                borderRadius: '5px',
                                                border: '1px solid var(--border)',
                                                background: 'var(--bg-input)',
                                                color: 'var(--text-secondary)',
                                                cursor: hasValue ? 'pointer' : 'not-allowed',
                                                fontSize: '11px',
                                                opacity: hasValue ? 1 : 0.5
                                            }}
                                        >
                                            {copiedJson ? <Check size={12} /> : <Copy size={12} />}
                                            {copiedJson ? 'Copied' : 'Copy'}
                                        </button>
                                    </div>

                                    {hasValue ? (
                                        <pre style={{
                                            margin: 0,
                                            padding: '12px',
                                            background: 'var(--bg-input)',
                                            border: '1px solid var(--border)',
                                            borderRadius: '7px',
                                            color: 'var(--text-primary)',
                                            fontSize: '11px',
                                            lineHeight: 1.5,
                                            fontFamily: 'monospace',
                                            whiteSpace: 'pre-wrap',
                                            wordBreak: 'break-word',
                                            overflowX: 'auto',
                                            maxHeight: '450px',
                                            overflowY: 'auto'
                                        }}>
                                            {formatJson(displayedValue)}
                                        </pre>
                                    ) : (
                                        <div style={{
                                            padding: '30px 12px',
                                            textAlign: 'center',
                                            border: '1px dashed var(--border)',
                                            borderRadius: '7px',
                                            color: 'var(--text-muted)',
                                            fontSize: '12px'
                                        }}>
                                            {!executionData
                                                ? 'No execution data yet'
                                                : activeTab === 'input'
                                                    ? 'No input payload for this step execution'
                                                    : 'No output payload for this step execution'}
                                        </div>
                                    )}
                                </>
                            )
                        })()}
                    </div>
                )}

                {/* Delete Node Section */}
                <div style={{ borderTop: '1px solid var(--border)', marginTop: '24px', paddingTop: '20px' }}>
                    <button
                        onClick={() => onDeleteNode(selectedNode.id)}
                        style={{
                            width: '100%',
                            padding: '10px',
                            borderRadius: '8px',
                            background: 'rgba(244, 63, 94, 0.1)',
                            border: '1px solid var(--accent-rose)',
                            color: 'var(--accent-rose)',
                            fontSize: '13px',
                            fontWeight: 600,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '8px',
                            cursor: 'pointer',
                            transition: 'all 0.2s',
                        }}
                        className="card-hover"
                    >
                        <Trash2 size={15} />
                        Delete Node
                    </button>
                </div>
            </div>
        </div>
    )
}