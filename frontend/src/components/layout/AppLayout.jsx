import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'
import BackgroundWorkflowStream from './BackgroundWorkflowStream'

export default function AppLayout() {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden', background: 'var(--bg-base)', position: 'relative' }}>
      {/* Universal Smooth Dark Mobile-UI Atmosphere Background */}
      <BackgroundWorkflowStream />

      <Sidebar collapsed={sidebarCollapsed} onToggle={() => setSidebarCollapsed(!sidebarCollapsed)} />

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', position: 'relative', zIndex: 10 }}>
        <Topbar />

        <main
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '24px',
            position: 'relative',
            background: 'transparent',
          }}
        >
          <Outlet />
        </main>
      </div>
    </div>
  )
}