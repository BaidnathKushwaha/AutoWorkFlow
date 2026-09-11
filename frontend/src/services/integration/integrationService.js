import { apiClient } from '../api/axios'

export const integrationService = {
  async list(options = {}) {
    return apiClient.get('/api/integrations', options)
  },

  async initiateOAuth(provider) {
    return apiClient.get(`/api/integrations/oauth/${provider}`)
  },

  async oauthCallback(provider, code, state) {
    return apiClient.post(`/api/integrations/oauth/${provider}/callback`, { code, state })
  },

  async connectWithKey(provider, apiKey) {
    return apiClient.post(`/api/integrations/key/${provider}`, { apiKey })
  },

  async disconnect(provider) {
    return apiClient.delete(`/api/integrations/${provider}`)
  },
}

export default integrationService
