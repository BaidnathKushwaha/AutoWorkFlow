import { apiClient } from '../api/axios'

export const integrationService = {
  /**
   * List connected integrations for the current user.
   */
  async list(options = {}) {
    return apiClient.get('/api/integrations', options)
  },

  /**
   * Initiates the OAuth flow for a specific provider by obtaining the authorization URL.
   * @param {string} provider
   */
  async initiateOAuth(provider) {
    return apiClient.get(`/api/integrations/oauth/${provider}`)
  },

  /**
   * Completes OAuth handshake by exchanging the code for user tokens.
   * @param {string} provider
   * @param {string} code
   */
  async oauthCallback(provider, code) {
    return apiClient.post(`/api/integrations/oauth/${provider}/callback`, { code })
  },

  /**
   * Connects an API-key-based provider (OpenAI, Gemini) by sending the raw key once.
   * The backend encrypts it at rest and never returns it again — the frontend must
   * not hold onto the raw key beyond this call, and must not fabricate a "connected"
   * state if this call fails (see Settings.jsx / Integrations.jsx handleConnect).
   * @param {string} provider
   * @param {string} apiKey
   */
  async connectWithKey(provider, apiKey) {
    return apiClient.post(`/api/integrations/key/${provider}`, { apiKey })
  },

  /**
   * Disconnects/deletes a connection provider.
   * @param {string} provider
   */
  async disconnect(provider) {
    return apiClient.delete(`/api/integrations/${provider}`)
  },
}

export default integrationService
