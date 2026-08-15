import { useEffect, useRef, useState } from 'react'
import { Brain, Bot, Send, User } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import assistantService from '../services/assistant/assistantService'
import WorkflowProposalPreview from '../components/workflow/WorkflowProposalPreview'

function getAssistantMessage(response) {
    return (
        response?.data?.message ||
        response?.message ||
        response?.data ||
        response
    )
}

export default function Assistant() {
    const navigate = useNavigate()

    const [messages, setMessages] = useState([
        {
            role: 'assistant',
            text:
                'Hello! Describe the workflow you want to build, and I will help you review a structured workflow proposal.',
        },
    ])

    const [conversationId, setConversationId] = useState(null)
    const [input, setInput] = useState('')
    const [isLoading, setIsLoading] = useState(false)

    const messagesEndRef = useRef(null)

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({
            behavior: 'smooth',
        })
    }, [messages, isLoading])

    const handleApplyProposal = (message) => {
        const proposal = message?.workflowProposal
        const validation = message?.workflowProposalValidation

        if (!proposal || validation?.valid !== true) {
            return
        }

        navigate('/builder/new', {
            state: {
                aiWorkflowProposal: proposal,
            },
        })
    }

    const handleSend = async (event) => {
        event.preventDefault()

        const message = input.trim()

        if (!message || isLoading) {
            return
        }

        setMessages((current) => [
            ...current,
            {
                role: 'user',
                text: message,
            },
        ])

        setInput('')
        setIsLoading(true)

        try {
            const response = await assistantService.chat({
                message,
                ...(conversationId
                    ? { conversationId }
                    : {}),
            })

            const data = response?.data || response

            if (data?.conversationId) {
                setConversationId(data.conversationId)
            }

            const assistant =
                getAssistantMessage(response)

            setMessages((current) => [
                ...current,
                {
                    role: 'assistant',
                    text:
                        assistant?.content ||
                        'The assistant returned an empty response.',
                    workflowProposal:
                        assistant?.workflowProposal ||
                        null,
                    workflowProposalValidation:
                        assistant?.workflowProposalValidation ||
                        null,
                },
            ])
        } catch (error) {
            const errorMessage =
                error?.response?.data?.message ||
                error?.response?.data?.error ||
                'The assistant request failed. Please try again.'

            setMessages((current) => [
                ...current,
                {
                    role: 'assistant',
                    text: errorMessage,
                    isError: true,
                },
            ])

            toast.error('Assistant request failed', {
                description: errorMessage,
            })
        } finally {
            setIsLoading(false)
        }
    }

    return (
        <div
            style={{
                display: 'flex',
                flexDirection: 'column',
                height: 'calc(100vh - 100px)',
            }}
        >
            <div style={{ marginBottom: '24px' }}>
                <h1
                    style={{
                        fontSize: '24px',
                        fontWeight: 700,
                        marginBottom: '8px',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                    }}
                >
                    <Brain color="var(--accent-violet)" />
                    AI Assistant
                </h1>

                <p
                    style={{
                        color: 'var(--text-secondary)',
                    }}
                >
                    Describe your automation needs in plain English.
                </p>
            </div>

            <div
                className="card"
                style={{
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    overflow: 'hidden',
                }}
            >
                <div
                    style={{
                        flex: 1,
                        overflowY: 'auto',
                        padding: '24px',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '24px',
                    }}
                >
                    {messages.map((msg, index) => (
                        <div
                            key={`${msg.role}-${index}`}
                            style={{
                                display: 'flex',
                                gap: '16px',
                                flexDirection:
                                    msg.role === 'user'
                                        ? 'row-reverse'
                                        : 'row',
                            }}
                        >
                            <div
                                style={{
                                    width: '36px',
                                    height: '36px',
                                    borderRadius: '50%',
                                    background:
                                        msg.role === 'user'
                                            ? 'linear-gradient(135deg, #6366f1, #7c3aed)'
                                            : 'var(--bg-surface)',
                                    border:
                                        msg.role === 'assistant'
                                            ? '1px solid var(--border)'
                                            : 'none',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    flexShrink: 0,
                                }}
                            >
                                {msg.role === 'user' ? (
                                    <User
                                        size={18}
                                        color="white"
                                    />
                                ) : (
                                    <Bot
                                        size={18}
                                        color="var(--accent-violet)"
                                    />
                                )}
                            </div>

                            <div
                                style={{
                                    maxWidth: '78%',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: '12px',
                                }}
                            >
                                <div
                                    style={{
                                        background:
                                            msg.role === 'user'
                                                ? 'var(--accent)'
                                                : 'var(--bg-surface)',
                                        border:
                                            msg.role === 'assistant'
                                                ? '1px solid var(--border)'
                                                : 'none',
                                        padding: '16px',
                                        borderRadius: '16px',
                                        borderTopLeftRadius:
                                            msg.role ===
                                            'assistant'
                                                ? 0
                                                : '16px',
                                        borderTopRightRadius:
                                            msg.role === 'user'
                                                ? 0
                                                : '16px',
                                        color:
                                            msg.role === 'user'
                                                ? 'white'
                                                : 'var(--text-primary)',
                                        fontSize: '14px',
                                        lineHeight: 1.6,
                                    }}
                                >
                                    {msg.text}
                                </div>

                                {msg.workflowProposal && (
                                    <WorkflowProposalPreview
                                        proposal={
                                            msg.workflowProposal
                                        }
                                        validation={
                                            msg.workflowProposalValidation
                                        }
                                        onApply={() =>
                                            handleApplyProposal(
                                                msg
                                            )
                                        }
                                    />
                                )}
                            </div>
                        </div>
                    ))}

                    {isLoading && (
                        <div
                            style={{
                                display: 'flex',
                                gap: '16px',
                                alignItems:
                                    'flex-start',
                            }}
                            aria-live="polite"
                        >
                            <div
                                style={{
                                    width: '36px',
                                    height: '36px',
                                    borderRadius: '50%',
                                    background:
                                        'var(--bg-surface)',
                                    border:
                                        '1px solid var(--border)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent:
                                        'center',
                                    flexShrink: 0,
                                }}
                            >
                                <Bot
                                    size={18}
                                    color="var(--accent-violet)"
                                />
                            </div>

                            <div
                                style={{
                                    background:
                                        'var(--bg-surface)',
                                    border:
                                        '1px solid var(--border)',
                                    padding: '16px',
                                    borderRadius: '16px',
                                    borderTopLeftRadius: 0,
                                    color:
                                        'var(--text-secondary)',
                                    fontSize: '14px',
                                }}
                            >
                                Thinking...
                            </div>
                        </div>
                    )}

                    <div ref={messagesEndRef} />
                </div>

                <div
                    style={{
                        padding: '24px',
                        borderTop:
                            '1px solid var(--border)',
                        background:
                            'var(--bg-surface)',
                    }}
                >
                    <form
                        onSubmit={handleSend}
                        style={{
                            display: 'flex',
                            gap: '12px',
                        }}
                    >
                        <input
                            type="text"
                            value={input}
                            disabled={isLoading}
                            onChange={(event) =>
                                setInput(event.target.value)
                            }
                            placeholder="Create a workflow that reads my Gmail and posts summaries to Slack..."
                            style={{
                                flex: 1,
                                background:
                                    'var(--bg-input)',
                                border:
                                    '1px solid var(--border)',
                                padding:
                                    '16px 20px',
                                borderRadius: '12px',
                                color:
                                    'var(--text-primary)',
                                fontSize: '14px',
                                outline: 'none',
                            }}
                        />

                        <button
                            type="submit"
                            className="btn-primary"
                            disabled={
                                isLoading ||
                                !input.trim()
                            }
                            style={{
                                padding: '0 24px',
                                borderRadius: '12px',
                                opacity:
                                    isLoading ||
                                    !input.trim()
                                        ? 0.6
                                        : 1,
                            }}
                        >
                            <Send size={18} />
                        </button>
                    </form>
                </div>
            </div>
        </div>
    )
}