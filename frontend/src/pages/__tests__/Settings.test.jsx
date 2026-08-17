import {
    describe,
    it,
    expect,
    vi,
    beforeEach,
} from 'vitest'

import {
    render,
    screen,
    waitFor,
} from '@testing-library/react'

import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { toast } from 'sonner'

import Settings from '../Settings'
import userService from '../../services/user/userService'

vi.mock(
    '../../services/user/userService',
    () => ({
        default: {
            getAiPreferences: vi.fn(),
            updateAiPreferences: vi.fn(),
            me: vi.fn(),
            updateProfile: vi.fn(),
            generateApiKey: vi.fn(),
            revealApiKey: vi.fn(),
        },
    })
)

vi.mock(
    '../../store/authStore',
    () => ({
        useAuthStore: () => ({
            user: {
                name: 'Test User',
                email: 'test@example.com',
            },
            setUser: vi.fn(),
        }),
    })
)

vi.mock(
    'sonner',
    () => ({
        toast: {
            error: vi.fn(),
            success: vi.fn(),
        },
    })
)

const providers = [
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
]

const renderSettings = () =>
    render(
        <MemoryRouter>
            <Settings />
        </MemoryRouter>
    )

describe('Settings AI preferences', () => {
    beforeEach(() => {
        vi.clearAllMocks()

        userService.me.mockResolvedValue({
            hasApiKey: false,
        })

        userService.getAiPreferences.mockResolvedValue({
            mode: 'AUTO',
            provider: null,
            model: null,
            providers,
        })

        userService.updateAiPreferences.mockResolvedValue({
            mode: 'AUTO',
            provider: null,
            model: null,
            providers,
        })
    })

    it('renders AUTO state from backend', async () => {
        renderSettings()

        await waitFor(() => {
            expect(
                screen.getByLabelText('AI Mode')
            ).toHaveValue('AUTO')
        })
    })

    it('restores SPECIFIC state exactly', async () => {
        userService.getAiPreferences.mockResolvedValue({
            mode: 'SPECIFIC',
            provider: 'openrouter',
            model: 'google/gemini-2.5-flash',
            providers,
        })

        renderSettings()

        await waitFor(() => {
            expect(
                screen.getByLabelText('AI Mode')
            ).toHaveValue('SPECIFIC')

            expect(
                screen.getByLabelText('AI Provider')
            ).toHaveValue('openrouter')

            expect(
                screen.getByLabelText('AI Model')
            ).toHaveValue(
                'google/gemini-2.5-flash'
            )
        })
    })

    it('saving AUTO sends null provider and model', async () => {
        const user = userEvent.setup()

        userService.updateAiPreferences
            .mockResolvedValueOnce({
                mode: 'SPECIFIC',
                provider: 'openrouter',
                model: 'google/gemini-2.5-flash',
                providers,
            })
            .mockResolvedValueOnce({
                mode: 'AUTO',
                provider: null,
                model: null,
                providers,
            })

        renderSettings()

        const mode =
            await screen.findByLabelText('AI Mode')

        await waitFor(() => {
            expect(mode).toBeEnabled()
        })

        await user.selectOptions(
            mode,
            'SPECIFIC'
        )

        await waitFor(() => {
            expect(
                screen.getByLabelText(
                    'AI Provider'
                )
            ).toBeInTheDocument()
        })

        await user.selectOptions(
            mode,
            'AUTO'
        )

        await waitFor(() => {
            expect(
                userService.updateAiPreferences
            ).toHaveBeenCalledWith({
                mode: 'AUTO',
                provider: null,
                model: null,
            })
        })

        expect(
            userService.updateAiPreferences
        ).not.toHaveBeenCalledWith(
            expect.objectContaining({
                provider: 'auto',
            })
        )
    })

    it('selecting SPECIFIC exposes provider and model', async () => {
        const user = userEvent.setup()

        userService.updateAiPreferences.mockResolvedValue({
            mode: 'SPECIFIC',
            provider: 'openrouter',
            model: 'google/gemini-2.5-flash',
            providers,
        })

        renderSettings()

        const mode =
            await screen.findByLabelText('AI Mode')

        await waitFor(() => {
            expect(mode).toBeEnabled()
        })

        await user.selectOptions(
            mode,
            'SPECIFIC'
        )

        expect(
            await screen.findByLabelText(
                'AI Provider'
            )
        ).toBeInTheDocument()

        expect(
            screen.getByLabelText('AI Model')
        ).toBeInTheDocument()
    })

    it('provider selection remains provider-aware', async () => {
        const user = userEvent.setup()

        userService.updateAiPreferences.mockResolvedValue({
            mode: 'SPECIFIC',
            provider: 'openrouter',
            model: 'google/gemini-2.5-flash',
            providers,
        })

        renderSettings()

        const mode =
            await screen.findByLabelText('AI Mode')

        await waitFor(() => {
            expect(mode).toBeEnabled()
        })

        await user.selectOptions(
            mode,
            'SPECIFIC'
        )

        const provider =
            await screen.findByLabelText(
                'AI Provider'
            )

        await user.selectOptions(
            provider,
            'openrouter'
        )

        const model =
            await screen.findByLabelText(
                'AI Model'
            )

        expect(model).toHaveValue(
            'google/gemini-2.5-flash'
        )
    })

    it('saves SPECIFIC provider and model', async () => {
        const user = userEvent.setup()

        userService.updateAiPreferences.mockResolvedValue({
            mode: 'SPECIFIC',
            provider: 'openrouter',
            model: 'google/gemini-2.5-flash',
            providers,
        })

        renderSettings()

        const mode =
            await screen.findByLabelText('AI Mode')

        await waitFor(() => {
            expect(mode).toBeEnabled()
        })

        await user.selectOptions(
            mode,
            'SPECIFIC'
        )

        const provider =
            await screen.findByLabelText(
                'AI Provider'
            )

        await user.selectOptions(
            provider,
            'openrouter'
        )

        await waitFor(() => {
            expect(
                userService.updateAiPreferences
            ).toHaveBeenCalledWith({
                mode: 'SPECIFIC',
                provider: 'openrouter',
                model: 'google/gemini-2.5-flash',
            })
        })
    })

    it('handles preference loading failure', async () => {
        userService.getAiPreferences.mockRejectedValue(
            new Error('Failed to load preferences')
        )

        renderSettings()

        await waitFor(() => {
            expect(toast.error).toHaveBeenCalledWith(
                'Failed to load preferences'
            )
        })
    })
})
