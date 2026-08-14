import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import executionService from '../services/execution/executionService'

/** Detect whether an execution ID looks like a real UUID vs. a local mock/temp record */
const isRealExecution = (id) => {
  if (!id) return false
  // UUIDs: 8-4-4-4-12 hex pattern
  if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) return true
  // Local executions created by the builder use exe_<base36> pattern
  if (typeof id === 'string' && id.startsWith('exe_')) return true
  return false
}

export const useExecutionStore = create(
  persist(
    (set, get) => ({
      executions: [],          // Start empty — no hardcoded mocks
      selectedExecutionId: null,
      currentExecution: null,
      loading: false,
      loadingDetail: false,
      error: null,

      setSelectedExecutionId: (id) => set({ selectedExecutionId: id }),

      /** Clears the "current" execution being shown (e.g. right before a new Run starts)
       *  without touching the executions history list — history is never affected by this. */
      clearCurrentExecution: () => set({ selectedExecutionId: null, currentExecution: null, error: null }),

      fetchExecutions: async ({ page = 0, size = 50 } = {}) => {
        set({ loading: true, error: null })
        try {
          const res = await executionService.list({ page, size })
          const pageData = res
          const apiItems = pageData?.content || (Array.isArray(pageData) ? pageData : [])

          if (Array.isArray(apiItems) && apiItems.length > 0) {
            const formatted = apiItems.map((exe) => ({
              id: exe.id,
              workflowId: exe.workflowId,
              workflow: exe.workflowName || 'Workflow Execution',
              status: (exe.status || 'SUCCESS').toLowerCase(),
              duration: exe.durationMs != null
                ? (exe.durationMs < 1000 ? `${exe.durationMs}ms` : `${(exe.durationMs / 1000).toFixed(1)}s`)
                : '0.0s',
              durationMs: exe.durationMs || 0,
              trigger: exe.triggeredBy || 'MANUAL',
              timestamp: exe.startedAt ? new Date(exe.startedAt).toLocaleString() : 'Just now',
              startedAt: exe.startedAt,
              errorMessage: exe.errorMessage,
            }))

            // Preserve locally-added executions (from builder/workflows trigger) that aren't in API results
            const apiIds = new Set(formatted.map(item => item.id))
            const localOnly = (get().executions || []).filter(
              item => item.id && item.id.startsWith('exe_') && !apiIds.has(item.id)
            )

            const existingExecutions = get().executions || []

            const merged = formatted.map(item => {
              const existing = existingExecutions.find(e => e.id === item.id)

              return existing?.stepsLogs
                  ? { ...item, stepsLogs: existing.stepsLogs }
                  : item
            })

            set({
              executions: [...merged, ...localOnly],
              loading: false
            })
          } else {
            // If API returns empty, keep only locally-created executions (not mock ones)
            const localOnly = (get().executions || []).filter(
              item => item.id && item.id.startsWith('exe_')
            )
            set({ executions: localOnly, loading: false })
          }
        } catch (err) {
          console.warn('Backend execution list fetch note:', err?.message || err)
          // On error, keep whatever is currently in the store (don't wipe local executions)
          set({ loading: false })
        }
      },

      fetchExecutionById: async (id) => {
        set({ loadingDetail: true, currentExecution: null, error: null })
        try {
          const res = await executionService.getById(id)
          const detail = res

          if (detail && detail.id) {
            let stepsLogs = []
            if (Array.isArray(detail.stepsLogs)) {
              stepsLogs = detail.stepsLogs
            } else if (typeof detail.stepsLogs === 'string') {
              try { stepsLogs = JSON.parse(detail.stepsLogs) } catch (e) { stepsLogs = [] }
            }

            const formattedDetail = {
              id: detail.id,
              workflowId: detail.workflowId,
              workflow: detail.workflowName || 'Workflow Execution',
              status: (detail.status || 'SUCCESS').toLowerCase(),
              duration: detail.durationMs != null
                ? (detail.durationMs < 1000 ? `${detail.durationMs}ms` : `${(detail.durationMs / 1000).toFixed(1)}s`)
                : '0.0s',
              durationMs: detail.durationMs || 0,
              trigger: detail.triggeredBy || 'MANUAL',
              timestamp: detail.startedAt ? new Date(detail.startedAt).toLocaleString() : 'Just now',
              startedAt: detail.startedAt,
              finishedAt: detail.finishedAt,
              errorMessage: detail.errorMessage,
              stepsLogs,
            }
            set((state) => ({
              currentExecution: formattedDetail,
              loadingDetail: false,
              executions: [formattedDetail, ...state.executions.filter(e => e.id !== formattedDetail.id)]
            }))
            return formattedDetail
          }
        } catch (err) {
          console.warn(`Backend execution detail fetch note for ${id}:`, err)
        }

        const local = get().executions.find((exe) => exe.id === id)
        if (local) {
          set({ currentExecution: local, loadingDetail: false })
          return local
        }

        set({ loadingDetail: false, error: 'Execution not found' })
        return null
      },

      addExecution: (execution) => set((state) => ({
        executions: [execution, ...state.executions]
      })),

      clearExecutions: () => set({ executions: [] }),
    }),
    {
      name: 'autoworkflow-executions',
      // Only persist the executions list (not transient loading/error states)
      partialize: (state) => ({ executions: state.executions }),
      // On rehydrate, keep all execution records intact except exact static legacy mock IDs
      onRehydrateStorage: () => (state) => {
        if (state && Array.isArray(state.executions)) {
          const STATIC_MOCK_IDS = new Set(['exe_f92jdk', 'exe_m29dkw', 'exe_x83mdj', 'exe_p92lsd', 'exe_a82jdm', 'exe_mskeefaz'])
          state.executions = state.executions.filter(e => e?.id && !STATIC_MOCK_IDS.has(e.id))
        }
      },
    }
  )
)
