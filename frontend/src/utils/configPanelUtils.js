import { PROVIDER_MODELS } from '../data/nodeTypes'

export function resolveProviderChangePatch(newProvider, currentModel) {
  if (newProvider === 'auto') return { provider: 'auto', model: '' }
  const validModels = PROVIDER_MODELS[newProvider] || []
  if (validModels.length > 0 && !validModels.includes(currentModel)) {
    return { provider: newProvider, model: validModels[0] }
  }
  return null
}
