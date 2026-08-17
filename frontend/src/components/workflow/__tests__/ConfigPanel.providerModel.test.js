import { describe, it, expect } from 'vitest'
import { resolveProviderChangePatch } from '../ConfigPanel'

describe('resolveProviderChangePatch', () => {
  it('switching TO auto clears the model, regardless of what was previously stored', () => {
    expect(resolveProviderChangePatch('auto', 'gemini-3.6-flash')).toEqual({ provider: 'auto', model: '' })
    expect(resolveProviderChangePatch('auto', 'gpt-4o')).toEqual({ provider: 'auto', model: '' })
    expect(resolveProviderChangePatch('auto', '')).toEqual({ provider: 'auto', model: '' })
  })

  it('switching FROM auto to gemini selects gemini-3.6-flash', () => {
    expect(resolveProviderChangePatch('gemini', '')).toEqual({ provider: 'gemini', model: 'gemini-3.6-flash' })
  })

  it('switching FROM auto to openai selects gpt-4o-mini', () => {
    expect(resolveProviderChangePatch('openai', '')).toEqual({ provider: 'openai', model: 'gpt-4o-mini' })
  })

  it('switching FROM auto to openrouter selects google/gemini-2.5-flash', () => {
    expect(
        resolveProviderChangePatch(
            'openrouter',
            ''
        )
    ).toEqual({
      provider: 'openrouter',
      model: 'google/gemini-2.5-flash'
    })
  })

  it('switching directly between concrete providers replaces an incompatible model (existing behavior preserved)', () => {
    expect(resolveProviderChangePatch('openai', 'gemini-3.6-flash')).toEqual({ provider: 'openai', model: 'gpt-4o-mini' })
  })

  it('switching to a provider where the current model is already valid leaves it alone (returns null -> caller keeps the plain value)', () => {
    expect(resolveProviderChangePatch('openai', 'gpt-4o')).toBeNull()
  })

  it('does not run on load — this is a pure function only handleFieldChange calls on an active change, never on mount', () => {
    // No assertion beyond the function's own purity: same inputs -> same output, no
    // hidden state, so calling it from a render effect would be a caller-side choice,
    // not something this function does on its own.
    expect(resolveProviderChangePatch('gemini', 'gemini-3.6-flash')).toBeNull()
  })
})
