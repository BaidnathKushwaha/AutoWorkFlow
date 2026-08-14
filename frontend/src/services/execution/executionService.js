import { apiClient } from '../api/axios'

export const executionService = {
  // EXECUTION LOGS
  async list({ page = 0, size = 20 } = {}) {
    return apiClient.get('/api/executions', {
      params: { page, size },
    })
  },

  async getById(id) {
    return apiClient.get(`/api/executions/${id}`)
  },

  // DASHBOARD TELEMETRY
  async getDashboardStats() {
    return apiClient.get('/api/dashboard/stats')
  },

  async getExecutionOverview() {
    return apiClient.get('/api/dashboard/execution-overview')
  },
}

export default executionService
