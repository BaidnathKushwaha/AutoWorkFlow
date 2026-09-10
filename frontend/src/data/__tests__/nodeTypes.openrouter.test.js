import { describe, expect, it } from 'vitest'
import { PROVIDER_MODELS } from '../nodeTypes'

describe('OpenRouter curated model catalogue', () => {
  const expected = [
    'nvidia/nemotron-3-super-120b-a12b:free',
    'google/gemma-4-31b-it:free',
    'google/gemma-4-26b-a4b-it:free',
    'nvidia/nemotron-3-ultra-550b-a55b:free',
    'cohere/north-mini-code:free',
  ]

  it('contains exactly the five curated models in the required order', () => {
    expect(PROVIDER_MODELS.openrouter).toEqual(expected)
  })

  it('does not expose removed models or openrouter/free', () => {
    expect(PROVIDER_MODELS.openrouter).not.toEqual(expect.arrayContaining([
      'deepseek/deepseek-v4-flash:free',
      'openai/gpt-oss-120b:free',
      'qwen/qwen3-235b-a22b-2507:free',
      'google/gemini-2.5-flash',
      'openrouter/free',
    ]))
  })
})
