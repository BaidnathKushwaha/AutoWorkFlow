import { apiClient } from '../api/axios'

export const workflowService = {
  // WORKFLOW CRUD
  async list({ search, status, page = 0, size = 20 } = {}) {
    const params = {}
    if (search) params.search = search
    if (status) params.status = status
    params.page = page
    params.size = size

    return apiClient.get('/api/workflows', { params })
  },

  async getById(id) {
    return apiClient.get(`/api/workflows/${id}`)
  },

  async create(data) {
    return apiClient.post('/api/workflows', data)
  },

  async update(id, data) {
    return apiClient.put(`/api/workflows/${id}`, data)
  },

  async delete(id) {
    return apiClient.delete(`/api/workflows/${id}`)
  },

  async deploy(id) {
    return apiClient.post(`/api/workflows/${id}/deploy`)
  },

  async toggleActive(id) {
    return apiClient.patch(`/api/workflows/${id}/toggle`)
  },

  async trigger(id) {
    return apiClient.post(`/api/workflows/${id}/trigger`)
  },

  // NODE DEFINITIONS
  async listNodes() {
    return apiClient.get('/api/nodes')
  },

  async listNodesGrouped() {
    return apiClient.get('/api/nodes/grouped')
  },

  async listNodesByCategory(category) {
    return apiClient.get(`/api/nodes/category/${category}`)
  },

  // TEMPLATES
  async listTemplates() {
    return apiClient.get('/api/templates')
  },

  async getTemplateById(id) {
    return apiClient.get(`/api/templates/${id}`)
  },

  async importTemplate(id) {
    return apiClient.post(`/api/templates/${id}/import`)
  },
}

export default workflowService
