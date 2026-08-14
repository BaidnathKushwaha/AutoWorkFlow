import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export const useNotificationStore = create(
  persist(
    (set) => ({
      notifications: [
        {
          id: 'notif-1',
          title: 'GitHub PR Smart Reviewer ran successfully',
          time: '2 min ago',
          unread: true,
          type: 'success',
        },
        {
          id: 'notif-2',
          title: 'AI Email Router processed 12 emails',
          time: '15 min ago',
          unread: true,
          type: 'info',
        },
      ],

      addNotification: (notification) =>
        set((state) => ({
          notifications: [
            {
              id: `notif-${Date.now()}`,
              time: 'Just now',
              unread: true,
              type: 'info',
              ...notification,
            },
            ...state.notifications,
          ],
        })),

      markAllRead: () =>
        set((state) => ({
          notifications: state.notifications.map((n) => ({ ...n, unread: false })),
        })),

      clearNotifications: () => set({ notifications: [] }),
    }),
    { name: 'autoworkflow-notifications' }
  )
)
