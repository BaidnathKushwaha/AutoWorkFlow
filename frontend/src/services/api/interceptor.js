import axios from 'axios'
import { apiClient } from './axios'
import { STORAGE_KEYS, API_BASE_URL } from '../../utils/constants'

let isRefreshing = false
let failedQueue = []

const processQueue = (error, token = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token)
    }
  })
  failedQueue = []
}

// Request Interceptor: Attach bearer token if it exists
apiClient.interceptors.request.use(
  (config) => {
    const accessToken = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response Interceptor: Unwrap responses and handle token refresh on 401
apiClient.interceptors.response.use(
  (response) => {
    // Unpack backend ApiResponse wrapper if present
    if (response.data && typeof response.data.success === 'boolean') {
      if (response.data.success) {
        return response.data.data
      } else {
        return Promise.reject(new Error(response.data.message || 'API request failed'))
      }
    }
    return response.data
  },
  async (error) => {
    const originalRequest = error.config

    // If unauthorized (401) and not already retrying
    if (error.response?.status === 401 && !originalRequest._retry) {
      // Avoid infinite loop if auth/refresh itself fails with 401
      if (
        originalRequest.url?.includes('/api/auth/refresh') ||
        originalRequest.url?.includes('/api/auth/login') ||
        originalRequest.url?.includes('/api/auth/google') ||
        originalRequest.url?.includes('/api/auth/signup')
      ) {
        const apiErrorMsg = error.response?.data?.message || error.message || 'Authentication failed'
        return Promise.reject(new Error(apiErrorMsg))
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            return apiClient(originalRequest)
          })
          .catch((err) => {
            return Promise.reject(err)
          })
      }

      originalRequest._retry = true
      isRefreshing = true

      const refreshTokenValue = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN)
      if (!refreshTokenValue) {
        if (!originalRequest._skipAuthRedirect) {
          handleAuthFailure()
        }
        isRefreshing = false
        return Promise.reject(new Error('Session expired. Please log in again.'))
      }

      try {
        // Send a direct post request using standard axios to prevent circular interception
        const refreshResponse = await axios.post(`${API_BASE_URL}/api/auth/refresh`, {
          refreshToken: refreshTokenValue,
        })

        // Backend response is wrapped in ApiResponse<AuthResponse>
        const authData = refreshResponse.data.data
        const newAccessToken = authData.accessToken
        const newRefreshToken = authData.refreshToken

        localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, newAccessToken)
        if (newRefreshToken) {
          localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, newRefreshToken)
        }
        if (authData.user) {
          localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(authData.user))
        }

        apiClient.defaults.headers.common['Authorization'] = `Bearer ${newAccessToken}`
        originalRequest.headers['Authorization'] = `Bearer ${newAccessToken}`

        processQueue(null, newAccessToken)
        isRefreshing = false

        return apiClient(originalRequest)
      } catch (refreshErr) {
        processQueue(refreshErr, null)
        if (!originalRequest._skipAuthRedirect) {
          handleAuthFailure()
        }
        isRefreshing = false
        return Promise.reject(new Error('Session expired. Please log in again.'))
      }
    }

    // Extract cleanest error message
    const apiErrorMsg = error.response?.data?.message || error.message || 'An unexpected error occurred'
    return Promise.reject(new Error(apiErrorMsg))
  }
)

function handleAuthFailure() {
  localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN)
  localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN)
  localStorage.removeItem(STORAGE_KEYS.USER)
  localStorage.removeItem(STORAGE_KEYS.AUTHED)
  window.dispatchEvent(new Event('auth-failure'))
}
