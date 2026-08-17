import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'

import Assistant from '../Assistant'
import assistantService from '../../services/assistant/assistantService'

vi.mock('../../services/assistant/assistantService', () => ({
    default: {
        chat: vi.fn(),
        listConversations: vi.fn(),
        getHistory: vi.fn(),
        deleteConversation: vi.fn(),
    },
}))

vi.mock('../../store/authStore', () => ({
    useAuthStore: (selector) =>
        selector({
            user: { id: 'user-1' },
        }),
}))

vi.mock('../../components/workflow/WorkflowProposalPreview', () => ({
    default: () => null,
}))

vi.mock('sonner', () => ({
    toast: {
        error: vi.fn(),
        success: vi.fn(),
    },
}))

const conversations = [
    { id: 'conversation-1', title: 'First chat' },
    { id: 'conversation-2', title: 'Second chat' },
    { id: 'conversation-3', title: 'Third chat' },
    { id: 'conversation-4', title: 'Fourth chat' },
    { id: 'conversation-5', title: 'Fifth chat' },
]

const renderAssistant = () =>
    render(
        <MemoryRouter>
            <Assistant />
        </MemoryRouter>
    )

beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()

    assistantService.listConversations.mockResolvedValue({
        data: conversations,
    })

    assistantService.getHistory.mockResolvedValue({
        data: [
            {
                id: 'message-1',
                role: 'user',
                content: 'Build a Gmail workflow',
            },
            {
                id: 'message-2',
                role: 'assistant',
                content: 'I can help with that.',
            },
        ],
    })

    assistantService.chat.mockResolvedValue({
        data: {
            conversationId: 'conversation-6',
            message: {
                content: 'Assistant reply',
            },
        },
    })

    assistantService.deleteConversation.mockResolvedValue({})
})

describe('Assistant conversation persistence and chat management', () => {
    it('restores the user-scoped active conversation and history on mount', async () => {
        localStorage.setItem(
            'assistant.activeConversation.user-1',
            'conversation-2'
        )

        renderAssistant()

        expect(
            await screen.findByText('Build a Gmail workflow')
        ).toBeInTheDocument()

        expect(assistantService.getHistory).toHaveBeenCalledWith(
            'conversation-2'
        )
    })

    it('shows at least the five recent conversations returned by the backend', async () => {
        renderAssistant()

        expect(await screen.findByText('First chat')).toBeInTheDocument()
        expect(screen.getByText('Second chat')).toBeInTheDocument()
        expect(screen.getByText('Third chat')).toBeInTheDocument()
        expect(screen.getByText('Fourth chat')).toBeInTheDocument()
        expect(screen.getByText('Fifth chat')).toBeInTheDocument()
    })

    it('creates a fresh conversation after New Chat', async () => {
        const user = userEvent.setup()
        localStorage.setItem(
            'assistant.activeConversation.user-1',
            'conversation-1'
        )

        renderAssistant()

        await screen.findByText('First chat')
        await user.click(screen.getByRole('button', { name: 'New Chat' }))

        expect(
            screen.getByText(
                'Hello! Describe the workflow you want to build, and I will help you review a structured workflow proposal.'
            )
        ).toBeInTheDocument()

        expect(
            localStorage.getItem('assistant.activeConversation.user-1')
        ).toBeNull()

        const input = screen.getByPlaceholderText(
            'Create a workflow that reads my Gmail and posts summaries to Slack...'
        )

        await user.type(input, 'Start a new workflow')
        await user.click(screen.getByRole('button', { name: 'Send message' }))

        await waitFor(() => {
            expect(assistantService.chat).toHaveBeenCalledWith({
                message: 'Start a new workflow',
            })
        })

        expect(
            localStorage.getItem('assistant.activeConversation.user-1')
        ).toBe('conversation-6')
    })

    it('opens a previous conversation without creating a new one', async () => {
        const user = userEvent.setup()
        renderAssistant()

        await user.click(screen.getByRole('button', { name: 'Second chat' }))

        await waitFor(() => {
            expect(assistantService.getHistory).toHaveBeenCalledWith(
                'conversation-2'
            )
        })

        expect(assistantService.chat).not.toHaveBeenCalled()
        expect(
            await screen.findByText('Build a Gmail workflow')
        ).toBeInTheDocument()
    })

    it('deletes the current conversation and returns to clean New Chat state', async () => {
        const user = userEvent.setup()
        localStorage.setItem(
            'assistant.activeConversation.user-1',
            'conversation-1'
        )
        window.confirm = vi.fn(() => true)

        renderAssistant()
        await screen.findByText('First chat')

        await user.click(
            screen.getByRole('button', {
                name: 'Delete First chat',
            })
        )

        await waitFor(() => {
            expect(assistantService.deleteConversation).toHaveBeenCalledWith(
                'conversation-1'
            )
        })

        expect(
            localStorage.getItem('assistant.activeConversation.user-1')
        ).toBeNull()

        expect(
            screen.getByText(
                'Hello! Describe the workflow you want to build, and I will help you review a structured workflow proposal.'
            )
        ).toBeInTheDocument()
    })
})
