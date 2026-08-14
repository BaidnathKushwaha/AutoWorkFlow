import { NavLink, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard, GitBranch, Play, Blocks, Puzzle,
  Bot, Settings, ChevronLeft, ChevronRight, Zap,
  LogOut, User, LayoutTemplate,
} from 'lucide-react'
import { useAuthStore } from '../../store/authStore'

const navItems = [
  { to: '/dashboard', icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/workflows', icon: GitBranch, label: 'Workflows' },
  { to: '/templates', icon: LayoutTemplate, label: 'Templates' },
  { to: '/executions', icon: Play, label: 'Executions' },
  { to: '/nodes', icon: Blocks, label: 'Nodes' },
  { to: '/integrations', icon: Puzzle, label: 'Integrations' },
  { to: '/assistant', icon: Bot, label: 'AI Assistant' },
  { to: '/settings', icon: Settings, label: 'Settings' },
]

export default function Sidebar({ collapsed, onToggle }) {
  const navigate = useNavigate()
  const logout = useAuthStore((state) => state.logout)
  const user = useAuthStore((state) => state.user)

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }


  return (
    <aside
      style={{
        width: collapsed ? '64px' : '220px',
        minWidth: collapsed ? '64px' : '220px',
        background: 'var(--bg-surface)',
        backdropFilter: 'blur(16px)',
        WebkitBackdropFilter: 'blur(16px)',
        borderRight: '1px solid var(--border)',
        display: 'flex',
        flexDirection: 'column',
        transition: 'width 0.25s ease, min-width 0.25s ease',
        position: 'relative',
        zIndex: 50,
      }}
    >
      {/* Logo */}
      <div
        style={{
          padding: collapsed ? '20px 0' : '20px 16px',
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          borderBottom: '1px solid var(--border-subtle)',
          justifyContent: collapsed ? 'center' : 'flex-start',
        }}
      >
        <div
          style={{
            width: '32px',
            height: '32px',
            background: 'linear-gradient(135deg, var(--accent), var(--accent-violet))',
            borderRadius: '8px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <Zap size={16} color="white" />
        </div>
        {!collapsed && (
          <span
            style={{
              fontFamily: 'Syne, sans-serif',
              fontWeight: 700,
              fontSize: '16px',
              color: 'var(--text-primary)',
              whiteSpace: 'nowrap',
            }}
          >
            AutoWorkflow
          </span>
        )}
      </div>

      {/* Nav items */}
      <nav style={{ flex: 1, padding: '12px 8px', overflowY: 'auto' }}>
        {navItems.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
            style={{
              justifyContent: collapsed ? 'center' : 'flex-start',
              marginBottom: '2px',
            }}
            title={collapsed ? label : ''}
          >
            <Icon size={18} style={{ flexShrink: 0 }} />
            {!collapsed && <span>{label}</span>}
          </NavLink>
        ))}
      </nav>

      {/* Bottom: user + logout */}
      <div
        style={{
          padding: '12px 8px',
          borderTop: '1px solid var(--border-subtle)',
        }}
      >
        <div
          className="nav-item"
          style={{ justifyContent: collapsed ? 'center' : 'flex-start', marginBottom: '4px' }}
          title={collapsed ? 'Profile' : ''}
        >
          <div
            style={{
              width: '28px',
              height: '28px',
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #6366f1, #7c3aed)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <User size={14} color="white" />
          </div>
          {!collapsed && (
            <div>
              <div style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-primary)', lineHeight: 1.2 }}>
                {user?.name || user?.username || 'User'}
              </div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{user?.email || ''}</div>
            </div>
          )}
        </div>

        <button
          className="nav-item btn-ghost"
          onClick={handleLogout}
          style={{
            width: '100%',
            justifyContent: collapsed ? 'center' : 'flex-start',
            color: 'var(--accent-rose)',
          }}
          title={collapsed ? 'Logout' : ''}
        >
          <LogOut size={16} />
          {!collapsed && <span>Logout</span>}
        </button>
      </div>

      {/* Collapse toggle */}
      <button
        onClick={onToggle}
        style={{
          position: 'absolute',
          top: '22px',
          right: '-12px',
          width: '24px',
          height: '24px',
          borderRadius: '50%',
          background: 'var(--bg-card)',
          border: '1px solid var(--border)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: 'pointer',
          zIndex: 20,
          color: 'var(--text-muted)',
          transition: 'all 0.15s',
        }}
      >
        {collapsed ? <ChevronRight size={12} /> : <ChevronLeft size={12} />}
      </button>
    </aside>
  )
}