import { apiClient } from '../api/axios'

export const authService = {
  /**
   * Logs in a user with credentials.
   * @param {string} email
   * @param {string} password
   * @returns {Promise<object>} AuthResponse
   */
  async login(email, password) {
    return apiClient.post('/api/auth/login', { email, password })
  },

  /**
   * Registers a new user.
   * @param {string} name
   * @param {string} email
   * @param {string} password
   * @returns {Promise<object>} AuthResponse
   */
  async signup(name, email, password) {
    return apiClient.post('/api/auth/signup', { name, email, password })
  },

  /**
   * Authenticates via Google ID token (from Google Identity Services popup).
   * Backend verifies the token, finds or creates the user, and issues JWT tokens.
   * @param {string} idToken - The credential from Google's callback
   * @returns {Promise<object>} AuthResponse
   */
  async loginWithGoogle(idToken) {
    return apiClient.post('/api/auth/google', { idToken })
  },

  /**
   * Logs out user.
   * @param {string} refreshToken
   */
  async logout(refreshToken) {
    return apiClient.post('/api/auth/logout', { refreshToken })
  },

  /**
   * Manually triggers a token refresh.
   * @param {string} refreshToken
   * @returns {Promise<object>} AuthResponse
   */
  async refresh(refreshToken) {
    return apiClient.post('/api/auth/refresh', { refreshToken })
  },
}

export default authService
