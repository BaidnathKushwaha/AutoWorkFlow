import { useCallback, useEffect, useRef, useState } from 'react'
import { Brain, Bot, MessageSquarePlus, Send, Trash2, User } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import assistantService from '../services/assistant/assistantService'
import WorkflowProposalPreview from '../components/workflow/WorkflowProposalPreview'
import { useAuthStore } from '../store/authStore'
import { assistantActiveConversationKey } from '../utils/constants'

const WELCOME_MESSAGE = {
    role: 'assistant',
    text:
        'Hello! Describe the workflow you want to build, and I will help you review a structured workflow proposal.',
}

function getAssistantMessage(response) {
    return (
        response?.data?.message ||
        response?.message ||
        response?.data ||
        response
    )
}

function normalizeHistoryMessage(message) {
    return {
        role: message?.role === 'user' ? 'user' : 'assistant',
        text: message?.content || '',
        workflowProposal: message?.workflowProposal || null,
        workflowProposalValidation: message?.workflowProposalValidation || null,
    }
}

export default function Assistant() {
    const navigate = useNavigate()
    const user = useAuthStore((state) => state.user)

    const [messages, setMessages] = useState([WELCOME_MESSAGE])
    const [conversationId, setConversationId] = useState(null)
    const [conversations, setConversations] = useState([])
    const [input, setInput] = useState('')
    const [isLoading, setIsLoading] = useState(false)
    const [isLoadingChats, setIsLoadingChats] = useState(true)
    const [loadingConversationId, setLoadingConversationId] = useState(null)
    const [deletingConversationId, setDeletingConversationId] = useState(null)

    const messagesEndRef = useRef(null)
    const activeStorageKey = assistantActiveConversationKey(user?.id)

    const persistActiveConversation = useCallback(
        (id) => {
            if (!activeStorageKey) return

            if (id) {
                localStorage.setItem(activeStorageKey, id)
            } else {
                localStorage.removeItem(activeStorageKey)
            }
        },
        [activeStorageKey]
    )

    const refreshConversations = useCallback(async () => {
        const response = await assistantService.listConversations()
        const data = response?.data || response || []
        setConversations(Array.isArray(data) ? data : [])
    }, [])

    const loadConversation = useCallback(
        async (id) => {
            if (!id) {
                setConversationId(null)
                persistActiveConversation(null)
                setMessages([WELCOME_MESSAGE])
                return
            }

            setLoadingConversationId(id)

            try {
                const response = await assistantService.getHistory(id)
                const data = response?.data || response || []
                const history = Array.isArray(data)
                    ? data.map(normalizeHistoryMessage)
                    : []

                setConversationId(id)
                persistActiveConversation(id)
                setMessages(history.length > 0 ? history : [WELCOME_MESSAGE])
            } catch (error) {
                const status = error?.response?.status

                if (status === 404 || status === 403) {
                    persistActiveConversation(null)
                    setConversationId(null)
                    setMessages([WELCOME_MESSAGE])
                }

                const errorMessage =
                    error?.response?.data?.message ||
                    'Unable to load this conversation.'

                toast.error('Conversation could not be loaded', {
                    description: errorMessage,
                })
            } finally {
                setLoadingConversationId(null)
            }
        },
        [persistActiveConversation]
    )

    useEffect(() => {
        let cancelled = false

        const restore = async () => {
            if (!user?.id) {
                setIsLoadingChats(false)
                setConversations([])
                setConversationId(null)
                setMessages([WELCOME_MESSAGE])
                return
            }

            setIsLoadingChats(true)

            try {
                const storedConversationId = activeStorageKey
                    ? localStorage.getItem(activeStorageKey)
                    : null

                const response = await assistantService.listConversations()
                const data = response?.data || response || []
                const recent = Array.isArray(data) ? data : []

                if (cancelled) return

                setConversations(recent)

                if (storedConversationId) {
                    await loadConversation(storedConversationId)
                } else {
                    setConversationId(null)
                    setMessages([WELCOME_MESSAGE])
                }
            } catch (error) {
                if (cancelled) return

                setConversations([])
                setConversationId(null)
                setMessages([WELCOME_MESSAGE])

                toast.error('Unable to load recent chats', {
                    description:
                        error?.response?.data?.message ||
                        'Please try again.',
                })
            } finally {
                if (!cancelled) {
                    setIsLoadingChats(false)
                }
            }
        }

        restore()

        return () => {
            cancelled = true
        }
    }, [activeStorageKey, loadConversation, persistActiveConversation, user?.id])

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({
            behavior: 'smooth',
        })
    }, [messages, isLoading])

    const handleNewChat = () => {
        if (isLoading || loadingConversationId !== null) return

        persistActiveConversation(null)
        setConversationId(null)
        setMessages([WELCOME_MESSAGE])
        setInput('')
    }

    const handleSelectConversation = async (id) => {
        if (isLoading || loadingConversationId === id || id === conversationId) {
            return
        }

        await loadConversation(id)
    }

    const handleDeleteConversation = async (id) => {
        const conversation = conversations.find((item) => item.id === id)
        const title = conversation?.title || 'this conversation'

        if (!window.confirm(`Delete "${title}"? This cannot be undone.`)) {
            return
        }

        setDeletingConversationId(id)

        try {
            await assistantService.deleteConversation(id)

            setConversations((current) =>
                current.filter((item) => item.id !== id)
            )

            if (id === conversationId) {
                persistActiveConversation(null)
                setConversationId(null)
                setMessages([WELCOME_MESSAGE])
            }

            toast.success('Conversation deleted')
        } catch (error) {
            toast.error('Conversation could not be deleted', {
                description:
                    error?.response?.data?.message ||
                    'Please try again.',
            })
        } finally {
            setDeletingConversationId(null)
        }
    }

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
                ...(conversationId ? { conversationId } : {}),
            })

            const data = response?.data || response

            if (data?.conversationId) {
                setConversationId(data.conversationId)
                persistActiveConversation(data.conversationId)
            }

            const assistant = getAssistantMessage(response)

            setMessages((current) => [
                ...current,
                {
                    role: 'assistant',
                    text:
                        assistant?.content ||
                        'The assistant returned an empty response.',
                    workflowProposal: assistant?.workflowProposal || null,
                    workflowProposalValidation:
                        assistant?.workflowProposalValidation || null,
                },
            ])

            await refreshConversations()
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

                <p style={{ color: 'var(--text-secondary)' }}>
                    Describe your automation needs in plain English.
                </p>
            </div>

            <div
                style={{
                    flex: 1,
                    minHeight: 0,
                    display: 'grid',
                    gridTemplateColumns: '260px minmax(0, 1fr)',
                    gap: '16px',
                }}
            >
                <aside
                    className="card"
                    style={{
                        minHeight: 0,
                        display: 'flex',
                        flexDirection: 'column',
                        overflow: 'hidden',
                    }}
                >
                    <div
                        style={{
                            padding: '16px',
                            borderBottom: '1px solid var(--border)',
                        }}
                    >
                        <button
                            type="button"
                            className="btn-primary"
                            onClick={handleNewChat}
                            disabled={
                                isLoading || loadingConversationId !== null
                            }
                            style={{
                                width: '100%',
                                justifyContent: 'center',
                                gap: '8px',
                            }}
                        >
                            <MessageSquarePlus size={17} />
                            New Chat
                        </button>
                    </div>

                    <div
                        style={{
                            padding: '14px 16px 8px',
                            fontSize: '12px',
                            fontWeight: 700,
                            color: 'var(--text-muted)',
                            textTransform: 'uppercase',
                            letterSpacing: '0.06em',
                        }}
                    >
                        Recent Chats
                    </div>

                    <div
                        style={{
                            flex: 1,
                            overflowY: 'auto',
                            padding: '0 8px 12px',
                        }}
                    >
                        {isLoadingChats ? (
                            <div
                                style={{
                                    padding: '20px 10px',
                                    color: 'var(--text-muted)',
                                    fontSize: '13px',
                                }}
                            >
                                Loading chats...
                            </div>
                        ) : conversations.length === 0 ? (
                            <div
                                style={{
                                    padding: '20px 10px',
                                    color: 'var(--text-muted)',
                                    fontSize: '13px',
                                    lineHeight: 1.5,
                                }}
                            >
                                No previous chats yet.
                            </div>
                        ) : (
                            conversations.map((conversation) => {
                                const active = conversation.id === conversationId
                                const deleting = deletingConversationId === conversation.id

                                return (
                                    <div
                                        key={conversation.id}
                                        style={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: '6px',
                                            marginBottom: '4px',
                                            borderRadius: '10px',
                                            background: active
                                                ? 'var(--bg-surface)'
                                                : 'transparent',
                                            border: active
                                                ? '1px solid var(--border)'
                                                : '1px solid transparent',
                                        }}
                                    >
                                        <button
                                            type="button"
                                            onClick={() =>
                                                handleSelectConversation(conversation.id)
                                            }
                                            disabled={
                                                isLoading ||
                                                loadingConversationId !== null ||
                                                deleting
                                            }
                                            style={{
                                                flex: 1,
                                                minWidth: 0,
                                                border: 'none',
                                                background: 'transparent',
                                                color: 'var(--text-primary)',
                                                textAlign: 'left',
                                                padding: '10px 8px',
                                                cursor: 'pointer',
                                            }}
                                            title={conversation.title || 'Untitled chat'}
                                        >
                                            <div
                                                style={{
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis',
                                                    whiteSpace: 'nowrap',
                                                    fontSize: '13px',
                                                    fontWeight: active ? 600 : 500,
                                                }}
                                            >
                                                {conversation.title || 'Untitled chat'}
                                            </div>
                                            {loadingConversationId === conversation.id && (
                                                <div
                                                    style={{
                                                        fontSize: '11px',
                                                        color: 'var(--text-muted)',
                                                        marginTop: '3px',
                                                    }}
                                                >
                                                    Loading...
                                                </div>
                                            )}
                                        </button>

                                        <button
                                            type="button"
                                            aria-label={`Delete ${conversation.title || 'conversation'}`}
                                            onClick={() =>
                                                handleDeleteConversation(conversation.id)
                                            }
                                            disabled={deleting}
                                            className="btn-ghost"
                                            style={{
                                                padding: '7px',
                                                color: 'var(--text-muted)',
                                                flexShrink: 0,
                                            }}
                                        >
                                            <Trash2 size={15} />
                                        </button>
                                    </div>
                                )
                            })
                        )}
                    </div>
                </aside>

                <section
                    className="card"
                    style={{
                        minWidth: 0,
                        minHeight: 0,
                        display: 'flex',
                        flexDirection: 'column',
                        overflow: 'hidden',
                    }}
                >
                    <div
                        style={{
                            flex: 1,
                            minHeight: 0,
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
                                        msg.role === 'user' ? 'row-reverse' : 'row',
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
                                        <User size={18} color="white" />
                                    ) : (
                                        <Bot size={18} color="var(--accent-violet)" />
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
                                                msg.role === 'assistant' ? 0 : '16px',
                                            borderTopRightRadius:
                                                msg.role === 'user' ? 0 : '16px',
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
                                            proposal={msg.workflowProposal}
                                            validation={msg.workflowProposalValidation}
                                            onApply={() => handleApplyProposal(msg)}
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
                                    alignItems: 'flex-start',
                                }}
                                aria-live="polite"
                            >
                                <div
                                    style={{
                                        width: '36px',
                                        height: '36px',
                                        borderRadius: '50%',
                                        background: 'var(--bg-surface)',
                                        border: '1px solid var(--border)',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        flexShrink: 0,
                                    }}
                                >
                                    <Bot size={18} color="var(--accent-violet)" />
                                </div>

                                <div
                                    style={{
                                        background: 'var(--bg-surface)',
                                        border: '1px solid var(--border)',
                                        padding: '16px',
                                        borderRadius: '16px',
                                        borderTopLeftRadius: 0,
                                        color: 'var(--text-secondary)',
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
                            borderTop: '1px solid var(--border)',
                            background: 'var(--bg-surface)',
                        }}
                    >
                        <form
                            onSubmit={handleSend}
                            style={{ display: 'flex', gap: '12px' }}
                        >
                            <input
                                type="text"
                                value={input}
                                disabled={isLoading || loadingConversationId !== null}
                                onChange={(event) => setInput(event.target.value)}
                                placeholder="Create a workflow that reads my Gmail and posts summaries to Slack..."
                                style={{
                                    flex: 1,
                                    background: 'var(--bg-input)',
                                    border: '1px solid var(--border)',
                                    padding: '16px 20px',
                                    borderRadius: '12px',
                                    color: 'var(--text-primary)',
                                    fontSize: '14px',
                                    outline: 'none',
                                }}
                            />

                            <button
                                type="submit"
                                aria-label="Send message"
                                className="btn-primary"
                                disabled={
                                    isLoading ||
                                    loadingConversationId !== null ||
                                    !input.trim()
                                }
                                style={{
                                    padding: '0 24px',
                                    borderRadius: '12px',
                                    opacity:
                                        isLoading ||
                                        loadingConversationId !== null ||
                                        !input.trim()
                                            ? 0.6
                                            : 1,
                                }}
                            >
                                <Send size={18} />
                            </button>
                        </form>
                    </div>
                </section>
            </div>
        </div>
    )
}
