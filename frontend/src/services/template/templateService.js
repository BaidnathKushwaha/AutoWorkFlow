import { apiClient } from '../api/axios'

export const templateService = {
  async list() {
    return apiClient.get('/api/templates')
  },
  async getById(id) {
    return apiClient.get(`/api/templates/${id}`)
  },
  async import(id) {
    return apiClient.post(`/api/templates/${id}/import`)
  },
}

export default templateService
