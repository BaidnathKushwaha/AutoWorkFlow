import { Search, Bell, Plus, User, LogOut, Settings, X, GitBranch, ArrowRight } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useState, useRef, useEffect, useMemo } from 'react'
import { useAuthStore } from '../../store/authStore'
import { useNotificationStore } from '../../store/notificationStore'
import { useWorkflowStore } from '../../store/workflowStore'

export default function Topbar() {
  const [showNotifications, setShowNotifications] = useState(false)
  const [showProfile, setShowProfile] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [isSearchFocused, setIsSearchFocused] = useState(false)
  const navigate = useNavigate()

  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const workflows = useWorkflowStore((state) => state.workflows)

  const { notifications, markAllRead, clearNotifications } = useNotificationStore()
  const hasUnread = notifications.some((n) => n.unread)

  // Filter workflows matching current search query
  const searchResults = useMemo(() => {
    if (!searchQuery.trim()) return []
    const q = searchQuery.toLowerCase().trim()
    return workflows.filter(
      (wf) =>
        wf.name?.toLowerCase().includes(q) ||
        wf.description?.toLowerCase().includes(q) ||
        wf.trigger?.toLowerCase().includes(q) ||
        wf.status?.toLowerCase().includes(q)
    )
  }, [workflows, searchQuery])

  // Mark all as read when the panel is opened
  const handleToggleNotifications = () => {
    const next = !showNotifications
    setShowNotifications(next)
    setShowProfile(false)
    if (next && hasUnread) {
      markAllRead()
    }
  }

  // Close dropdowns when clicking outside
  const notifRef = useRef(null)
  const profileRef = useRef(null)
  const searchRef = useRef(null)

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (notifRef.current && !notifRef.current.contains(event.target)) {
        setShowNotifications(false)
      }
      if (profileRef.current && !profileRef.current.contains(event.target)) {
        setShowProfile(false)
      }
      if (searchRef.current && !searchRef.current.contains(event.target)) {
        setIsSearchFocused(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  const handleSearchKeyDown = (e) => {
    if (e.key === 'Enter') {
      if (searchQuery.trim()) {
        navigate(`/workflows?search=${encodeURIComponent(searchQuery.trim())}`)
        setIsSearchFocused(false)
      }
    } else if (e.key === 'Escape') {
      setIsSearchFocused(false)
      e.target.blur()
    }
  }

  return (
    <header
      style={{
        height: '64px',
        background: 'var(--bg-surface)',
        backdropFilter: 'blur(16px)',
        WebkitBackdropFilter: 'blur(16px)',
        borderBottom: '1px solid var(--border)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 24px',
        position: 'relative',
        zIndex: 40,
      }}
    >
      {/* Left side */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        <div
          style={{
            position: 'relative',
            display: 'flex',
            alignItems: 'center',
          }}
          ref={searchRef}
        >
          <Search
            size={16}
            color="var(--text-muted)"
            style={{ position: 'absolute', left: '12px', pointerEvents: 'none', zIndex: 2 }}
          />
          <input
            type="text"
            placeholder="Search workflows..."
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value)
              setIsSearchFocused(true)
            }}
            onFocus={() => setIsSearchFocused(true)}
            onKeyDown={handleSearchKeyDown}
            style={{
              background: 'var(--bg-input)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-lg)',
              padding: searchQuery ? '8px 32px 8px 36px' : '8px 16px 8px 36px',
              color: 'var(--text-primary)',
              fontSize: '14px',
              width: isSearchFocused || searchQuery ? '320px' : '240px',
              outline: 'none',
              transition: 'all 0.2s ease',
              borderColor: isSearchFocused ? 'var(--accent)' : 'var(--border)',
              boxShadow: isSearchFocused ? '0 0 0 2px rgba(99,102,241,0.15)' : 'none',
            }}
          />
          {searchQuery && (
            <button
              onClick={() => {
                setSearchQuery('')
                setIsSearchFocused(false)
              }}
              style={{
                position: 'absolute',
                right: '10px',
                background: 'none',
                border: 'none',
                color: 'var(--text-muted)',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '2px',
                borderRadius: '50%',
              }}
              title="Clear search"
            >
              <X size={14} />
            </button>
          )}

          {/* Search Dropdown Popup */}
          {isSearchFocused && searchQuery.trim() && (
            <div
              style={{
                position: 'absolute',
                top: 'calc(100% + 8px)',
                left: 0,
                width: '360px',
                background: 'var(--bg-surface)',
                border: '1px solid var(--border)',
                borderRadius: '10px',
                boxShadow: 'var(--shadow-glow), 0 10px 25px -5px rgba(0, 0, 0, 0.4)',
                overflow: 'hidden',
                zIndex: 100,
              }}
            >
              <div
                style={{
                  padding: '8px 12px',
                  background: 'var(--bg-card)',
                  borderBottom: '1px solid var(--border-subtle)',
                  fontSize: '11px',
                  fontWeight: 600,
                  color: 'var(--text-muted)',
                  textTransform: 'uppercase',
                  letterSpacing: '0.05em',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <span>Workflows ({searchResults.length})</span>
                <span style={{ fontSize: '10px', textTransform: 'none', opacity: 0.8 }}>Press ↵ to view all</span>
              </div>

              {searchResults.length === 0 ? (
                <div style={{ padding: '20px 16px', textAlign: 'center' }}>
                  <p style={{ fontSize: '13px', color: 'var(--text-secondary)', margin: '0 0 8px 0' }}>
                    No workflows match "{searchQuery}"
                  </p>
                  <button
                    onClick={() => {
                      navigate(`/workflows?search=${encodeURIComponent(searchQuery)}`)
                      setIsSearchFocused(false)
                    }}
                    style={{
                      fontSize: '12px',
                      color: 'var(--accent)',
                      background: 'transparent',
                      border: 'none',
                      cursor: 'pointer',
                      fontWeight: 500,
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '4px',
                    }}
                  >
                    Search all workflows <ArrowRight size={12} />
                  </button>
                </div>
              ) : (
                <div>
                  {searchResults.slice(0, 5).map((wf) => (
                    <div
                      key={wf.id}
                      onClick={() => {
                        navigate(`/builder/${wf.id}`)
                        setIsSearchFocused(false)
                      }}
                      style={{
                        padding: '10px 14px',
                        borderBottom: '1px solid var(--border-subtle)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        cursor: 'pointer',
                        transition: 'background 0.15s ease',
                      }}
                      onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(99,102,241,0.1)')}
                      onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', overflow: 'hidden' }}>
                        <div
                          style={{
                            width: '28px',
                            height: '28px',
                            borderRadius: '6px',
                            background: 'rgba(99,102,241,0.12)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            flexShrink: 0,
                            color: 'var(--accent)',
                          }}
                        >
                          <GitBranch size={15} />
                        </div>
                        <div style={{ overflow: 'hidden' }}>
                          <div
                            style={{
                              fontSize: '13px',
                              fontWeight: 500,
                              color: 'var(--text-primary)',
                              whiteSpace: 'nowrap',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                            }}
                          >
                            {wf.name}
                          </div>
                          <div
                            style={{
                              fontSize: '11px',
                              color: 'var(--text-muted)',
                              whiteSpace: 'nowrap',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                            }}
                          >
                            {wf.trigger || wf.description || 'Workflow'}
                          </div>
                        </div>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexShrink: 0 }}>
                        <span
                          style={{
                            fontSize: '10px',
                            padding: '2px 6px',
                            borderRadius: '10px',
                            fontWeight: 500,
                            background: wf.status === 'active' ? 'rgba(34,197,94,0.15)' : 'rgba(148,163,184,0.15)',
                            color: wf.status === 'active' ? '#22c55e' : 'var(--text-muted)',
                          }}
                        >
                          {wf.status || 'draft'}
                        </span>
                      </div>
                    </div>
                  ))}

                  <div
                    onClick={() => {
                      navigate(`/workflows?search=${encodeURIComponent(searchQuery)}`)
                      setIsSearchFocused(false)
                    }}
                    style={{
                      padding: '10px 14px',
                      background: 'var(--bg-card)',
                      fontSize: '12px',
                      color: 'var(--accent)',
                      fontWeight: 500,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '6px',
                      transition: 'background 0.15s ease',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(99,102,241,0.12)')}
                    onMouseLeave={(e) => (e.currentTarget.style.background = 'var(--bg-card)')}
                  >
                    See all results for "{searchQuery}" <ArrowRight size={13} />
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Right side */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        <Link to="/builder/new" className="btn-primary" style={{ textDecoration: 'none' }}>
          <Plus size={16} />
          Create Workflow
        </Link>

        {/* Notifications */}
        <div style={{ position: 'relative' }} ref={notifRef}>
          <button
            className="btn-ghost"
            onClick={handleToggleNotifications}
            style={{
              width: '36px',
              height: '36px',
              padding: 0,
              justifyContent: 'center',
              position: 'relative',
            }}
            title="Notifications"
          >
            <Bell size={18} />
            {/* Red dot — only rendered when there are unread notifications */}
            {hasUnread && (
              <span
                style={{
                  position: 'absolute',
                  top: '8px',
                  right: '8px',
                  width: '8px',
                  height: '8px',
                  background: 'var(--accent-rose)',
                  borderRadius: '50%',
                  border: '2px solid var(--bg-surface)',
                  animation: 'pulse 1.5s ease-in-out infinite',
                }}
              />
            )}
          </button>
          
          {showNotifications && (
            <div style={{
              position: 'absolute',
              top: '100%',
              right: 0,
              marginTop: '8px',
              width: '320px',
              background: 'var(--bg-surface)',
              border: '1px solid var(--border)',
              borderRadius: '8px',
              boxShadow: 'var(--shadow-glow)',
              overflow: 'hidden',
              zIndex: 50,
            }}>
              <div style={{
                padding: '12px 16px',
                borderBottom: '1px solid var(--border)',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}>
                <span style={{ fontWeight: 600, fontSize: '14px' }}>Notifications</span>
                {notifications.length > 0 && (
                  <button
                    onClick={() => clearNotifications()}
                    style={{
                      background: 'none',
                      border: 'none',
                      fontSize: '12px',
                      color: 'var(--text-muted)',
                      cursor: 'pointer',
                    }}
                  >
                    Clear all
                  </button>
                )}
              </div>

              {notifications.length === 0 ? (
                <div style={{ padding: '24px 16px', fontSize: '13px', color: 'var(--text-secondary)', textAlign: 'center' }}>
                  No new notifications
                </div>
              ) : (
                <div>
                  {notifications.map((n) => (
                    <div
                      key={n.id}
                      style={{
                        padding: '12px 16px',
                        borderBottom: '1px solid var(--border-subtle)',
                        display: 'flex',
                        gap: '10px',
                        alignItems: 'flex-start',
                        background: n.unread ? 'rgba(99,102,241,0.06)' : 'transparent',
                        transition: 'background 0.2s',
                      }}
                    >
                      <div
                        style={{
                          width: '8px',
                          height: '8px',
                          borderRadius: '50%',
                          background: n.unread ? 'var(--accent)' : 'var(--border)',
                          flexShrink: 0,
                          marginTop: '5px',
                        }}
                      />
                      <div>
                        <div style={{ fontSize: '13px', color: 'var(--text-primary)', lineHeight: 1.4 }}>{n.title}</div>
                        <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>{n.time}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Profile */}
        <div style={{ position: 'relative' }} ref={profileRef}>
          <div
            onClick={() => {
              setShowProfile(!showProfile)
              setShowNotifications(false)
            }}
            style={{
              width: '32px',
              height: '32px',
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #6366f1, #7c3aed)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
            }}
          >
            <User size={16} color="white" />
          </div>

          {showProfile && (
            <div style={{
              position: 'absolute',
              top: '100%',
              right: 0,
              marginTop: '8px',
              width: '200px',
              background: 'var(--bg-surface)',
              border: '1px solid var(--border)',
              borderRadius: '8px',
              boxShadow: 'var(--shadow-glow)',
              overflow: 'hidden',
              zIndex: 50
            }}>
              <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)' }}>
                <div style={{ fontSize: '14px', fontWeight: 600 }}>{user?.name || 'User'}</div>
                <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{user?.email || ''}</div>
              </div>
              <div style={{ padding: '8px' }}>
                <Link to="/settings" style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '8px', textDecoration: 'none', color: 'var(--text-primary)', borderRadius: '4px', fontSize: '13px' }} className="card-hover">
                  <Settings size={14} /> Settings
                </Link>
                <button onClick={handleLogout} style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '8px', width: '100%', background: 'transparent', border: 'none', cursor: 'pointer', color: 'var(--accent-rose)', borderRadius: '4px', fontSize: '13px' }} className="card-hover">
                  <LogOut size={14} /> Logout
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
