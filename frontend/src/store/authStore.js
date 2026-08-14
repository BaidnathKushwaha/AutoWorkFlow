import { create } from 'zustand'
import { authService } from '../services/auth/authService'
import { STORAGE_KEYS } from '../utils/constants'

// Initial state helpers
const getStoredUser = () => {
  try {
    const userStr = localStorage.getItem(STORAGE_KEYS.USER)
    return userStr ? JSON.parse(userStr) : null
  } catch {
    return null
  }
}

export const useAuthStore = create((set) => ({
  isAuthenticated: localStorage.getItem(STORAGE_KEYS.AUTHED) === 'true',
  user: getStoredUser(),
  loading: false,
  error: null,

  login: async (email, password) => {
    set({ loading: true, error: null })
    try {
      const response = await authService.login(email, password)
      const { accessToken, refreshToken, user } = response

      localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken)
      localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken)
      localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user))
      localStorage.setItem(STORAGE_KEYS.AUTHED, 'true')

      set({ isAuthenticated: true, user, loading: false })
      return response
    } catch (err) {
      const errMsg = err.message || 'Login failed'
      set({ error: errMsg, loading: false })
      throw err
    }
  },

  /**
   * Google OAuth login — accepts the credential (ID token) from Google Identity Services popup.
   * The backend verifies the token, finds or creates the user by email, and issues JWT tokens.
   * If the email already exists from a manual signup, this logs into that same account.
   */
  loginWithGoogle: async (idToken) => {
    set({ loading: true, error: null })
    try {
      const response = await authService.loginWithGoogle(idToken)
      const { accessToken, refreshToken, user } = response

      localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken)
      localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken)
      localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user))
      localStorage.setItem(STORAGE_KEYS.AUTHED, 'true')

      set({ isAuthenticated: true, user, loading: false })
      return response
    } catch (err) {
      const errMsg = err.message || 'Google login failed'
      set({ error: errMsg, loading: false })
      throw err
    }
  },

  signup: async (name, email, password) => {
    set({ loading: true, error: null })
    try {
      const response = await authService.signup(name, email, password)
      const { accessToken, refreshToken, user } = response

      localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken)
      localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken)
      localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user))
      localStorage.setItem(STORAGE_KEYS.AUTHED, 'true')

      set({ isAuthenticated: true, user, loading: false })
      return response
    } catch (err) {
      const errMsg = err.message || 'Signup failed'
      set({ error: errMsg, loading: false })
      throw err
    }
  },

  /** Updates the cached profile (e.g. after PUT /api/users/me succeeds) without a full re-login. */
  setUser: (user) => {
    localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user))
    set({ user })
  },

  logout: async (localOnly = false) => {
    const refreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN)

    // Clear localStorage and state first to immediately update UI
    localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN)
    localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN)
    localStorage.removeItem(STORAGE_KEYS.USER)
    localStorage.removeItem(STORAGE_KEYS.AUTHED)

    set({ isAuthenticated: false, user: null, error: null })

    if (!localOnly && refreshToken) {
      try {
        await authService.logout(refreshToken)
      } catch (err) {
        console.error('Logout request failed:', err)
      }
    }
  },
}))

if (typeof window !== 'undefined') {
  window.addEventListener('auth-failure', () => {
    useAuthStore.getState().logout(true)
  })
}
