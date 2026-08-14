import { apiClient } from '../api/axios'

/**
 * Platform account/user endpoints. Distinct from integrationService — this is
 * about the user's AutoWorkflow account itself (profile, platform API key),
 * never external provider credentials. See Settings.jsx vs Integrations.jsx.
 */
export const userService = {
  /** GET /api/users/me — includes hasApiKey/apiKeyLastFour for a masked display without revealing the key. */
  async me() {
    return apiClient.get('/api/users/me')
  },

  /** PUT /api/users/me — { name, email } */
  async updateProfile(payload) {
    return apiClient.put('/api/users/me', payload)
  },

  /** POST /api/users/me/api-key — generates (or rotates) the platform API key. Returns the full key ONCE. */
  async generateApiKey() {
    return apiClient.post('/api/users/me/api-key')
  },

  /** GET /api/users/me/api-key/reveal — returns the existing full platform API key. */
  async revealApiKey() {
    return apiClient.get('/api/users/me/api-key/reveal')
  },
}

export default userService
