import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import Settings from '../Settings'
import userService from '../../services/user/userService'

vi.mock('../../services/user/userService', () => ({
    default: {
        getAiPreferences: vi.fn(),
        updateAiPreferences: vi.fn(),
        me: vi.fn(),
        updateProfile: vi.fn(),
        generateApiKey: vi.fn(),
        revealApiKey: vi.fn(),
    },
}))

vi.mock('../../store/authStore', () => ({
    useAuthStore: () => ({
        user: {
            name: 'Test User',
            email: 'test@example.com',
        },
        setUser: vi.fn(),
    }),
}))

describe('Settings AI preferences', () => {
    beforeEach(() => {
        vi.clearAllMocks()

        userService.getAiPreferences.mockResolvedValue({
            mode: 'AUTO',
            provider: null,
            model: null,
            providers: [
                {
                    key: 'openrouter',
                    label: 'OpenRouter',
                    models: [
                        'google/gemini-2.5-flash',
                    ],
                },
                {
                    key: 'gemini',
                    label: 'Gemini',
                    models: [
                        'gemini-3.6-flash',
                    ],
                },
                {
                    key: 'openai',
                    label: 'OpenAI',
                    models: [
                        'gpt-4o-mini',
                    ],
                },
            ],
        })

        userService.updateAiPreferences.mockResolvedValue({
            mode: 'SPECIFIC',
            provider: 'openrouter',
            model: 'google/gemini-2.5-flash',
            providers: [
                {
                    key: 'openrouter',
                    label: 'OpenRouter',
                    models: [
                        'google/gemini-2.5-flash',
                    ],
                },
            ],
        })
    })

    it('loads AUTO preference from backend', async () => {
        render(<Settings />)

        expect(
            await screen.findByDisplayValue('AUTO')
        ).toBeInTheDocument()

        expect(
            userService.getAiPreferences
        ).toHaveBeenCalledTimes(1)
    })

    it('does not hardcode provider models', async () => {
        render(<Settings />)

        await waitFor(() => {
            expect(
                userService.getAiPreferences
            ).toHaveBeenCalled()
        })

        expect(
            screen.queryByText('openrouter/free')
        ).not.toBeInTheDocument()
    })

    it('switches to SPECIFIC using backend catalog', async () => {
        const user = userEvent.setup()

        render(<Settings />)

        const modeSelect =
            await screen.findByDisplayValue('AUTO')

        await user.selectOptions(
            modeSelect,
            'SPECIFIC'
        )

        await waitFor(() => {
            expect(
                screen.getByText('OpenRouter')
            ).toBeInTheDocument()
        })
    })
})