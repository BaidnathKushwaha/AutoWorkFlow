import { AlertCircle, CheckCircle2, GitBranch, Workflow } from 'lucide-react'

function getValidationErrors(validation) {
    if (!validation || validation.valid) {
        return []
    }

    if (Array.isArray(validation.errors)) {
        return validation.errors
    }

    if (validation.error) {
        return [validation.error]
    }

    return ['The backend rejected this workflow proposal.']
}

export default function WorkflowProposalPreview({
    proposal,
    validation,
    onApply,
}) {
    if (!proposal) {
        return null
    }

    const valid = validation?.valid === true
    const errors = getValidationErrors(validation)
    const nodes = Array.isArray(proposal.nodes) ? proposal.nodes : []
    const edges = Array.isArray(proposal.edges) ? proposal.edges : []

    return (
        <section
            aria-label="Workflow proposal"
            style={{
                background: 'var(--bg-base)',
                border: `1px solid ${
                    valid ? 'var(--accent-violet)' : 'var(--border)'
                }`,
                borderRadius: '14px',
                overflow: 'hidden',
            }}
        >
            <div
                style={{
                    padding: '14px 16px',
                    borderBottom: '1px solid var(--border)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: '12px',
                }}
            >
                <div
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '10px',
                    }}
                >
                    <Workflow
                        size={18}
                        color="var(--accent-violet)"
                    />

                    <div>
                        <strong style={{ fontSize: '14px' }}>
                            Workflow proposal
                        </strong>

                        <div
                            style={{
                                fontSize: '12px',
                                color: 'var(--text-muted)',
                                marginTop: '2px',
                            }}
                        >
                            {proposal.intent || 'AI generated workflow'}
                        </div>
                    </div>
                </div>

                <span
                    style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '6px',
                        fontSize: '12px',
                        fontWeight: 600,
                        color: valid
                            ? 'var(--accent-green)'
                            : 'var(--accent-red)',
                    }}
                >
                    {valid ? (
                        <CheckCircle2 size={15} />
                    ) : (
                        <AlertCircle size={15} />
                    )}

                    {valid ? 'Validated' : 'Validation failed'}
                </span>
            </div>

            <div style={{ padding: '16px' }}>
                <div
                    style={{
                        display: 'grid',
                        gridTemplateColumns:
                            'repeat(auto-fit, minmax(220px, 1fr))',
                        gap: '10px',
                    }}
                >
                    {nodes.map((node, index) => (
                        <div
                            key={
                                node.id ||
                                `proposal-node-${index}`
                            }
                            style={{
                                border: '1px solid var(--border)',
                                borderRadius: '10px',
                                padding: '12px',
                                background: 'var(--bg-surface)',
                            }}
                        >
                            <div
                                style={{
                                    fontSize: '13px',
                                    fontWeight: 600,
                                }}
                            >
                                {node.configuration?.label ||
                                    node.id ||
                                    `Node ${index + 1}`}
                            </div>

                            <div
                                style={{
                                    fontSize: '12px',
                                    color: 'var(--text-muted)',
                                    marginTop: '4px',
                                }}
                            >
                                {node.type || 'Unknown type'}
                            </div>
                        </div>
                    ))}
                </div>

                <div
                    style={{
                        marginTop: '14px',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        fontSize: '12px',
                        color: 'var(--text-muted)',
                    }}
                >
                    <GitBranch size={14} />

                    {edges.length} connection
                    {edges.length === 1 ? '' : 's'}
                </div>

                {errors.length > 0 && (
                    <div
                        role="alert"
                        style={{
                            marginTop: '14px',
                            padding: '12px',
                            borderRadius: '10px',
                            border: '1px solid var(--border)',
                            background: 'var(--bg-surface)',
                            color: 'var(--text-secondary)',
                            fontSize: '12px',
                        }}
                    >
                        <strong
                            style={{
                                display: 'block',
                                marginBottom: '6px',
                                color: 'var(--text-primary)',
                            }}
                        >
                            Backend validation errors
                        </strong>

                        <ul
                            style={{
                                margin: 0,
                                paddingLeft: '18px',
                            }}
                        >
                            {errors.map((error, index) => (
                                <li key={`${error}-${index}`}>
                                    {String(error)}
                                </li>
                            ))}
                        </ul>
                    </div>
                )}

                {valid && (
                    <div
                        style={{
                            marginTop: '14px',
                            display: 'flex',
                            justifyContent: 'flex-end',
                        }}
                    >
                        <button
                            type="button"
                            className="btn-primary"
                            onClick={onApply}
                            disabled={!valid}
                            style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '8px',
                                borderRadius: '10px',
                                padding: '10px 14px',
                            }}
                        >
                            Review in Workflow Editor
                        </button>
                    </div>
                )}
            </div>
        </section>
    )
}