import { apiClient } from '../api/axios'

export const assistantService = {
  /**
   * Send a chat message to the assistant.
   * @param {object} data { message: string, conversationId?: string }
   */
  async chat(data) {
    return apiClient.post('/api/assistant/chat', data)
  },

  /**
   * Get the list of assistant conversations.
   */
  async listConversations() {
    return apiClient.get('/api/assistant/conversations')
  },

  /**
   * Get chat history messages for a specific conversation.
   */
  async getHistory(conversationId) {
    return apiClient.get(`/api/assistant/conversations/${conversationId}/messages`)
  },

  /**
   * Delete an assistant conversation owned by the authenticated user.
   */
  async deleteConversation(conversationId) {
    return apiClient.delete(`/api/assistant/conversations/${conversationId}`)
  },
}

export default assistantService
