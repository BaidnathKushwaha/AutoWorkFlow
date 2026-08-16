import { apiClient } from '../api/axios'

/**
 * Platform account/user endpoints.
 *
 * This service handles AutoWorkflow account configuration.
 * External provider credentials remain in Integrations.
 */
export const userService = {
  async me() {
    return apiClient.get('/api/users/me')
  },

  async updateProfile(payload) {
    return apiClient.put('/api/users/me', payload)
  },

  async generateApiKey() {
    return apiClient.post('/api/users/me/api-key')
  },

  async revealApiKey() {
    return apiClient.get('/api/users/me/api-key/reveal')
  },

  async getAiPreferences() {
    return apiClient.get('/api/users/me/ai-preferences')
  },

  async updateAiPreferences(payload) {
    return apiClient.put(
        '/api/users/me/ai-preferences',
        payload
    )
  },
}

export default userService