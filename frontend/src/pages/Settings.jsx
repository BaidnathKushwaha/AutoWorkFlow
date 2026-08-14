import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { User, Key, Copy, Check, Eye, RefreshCw, LogOut, Share2, Bell, Globe2 } from 'lucide-react'
import { useAuthStore } from '../store/authStore'
import userService from '../services/user/userService'
import { toast } from 'sonner'

// Settings = "how is my AutoWorkflow account configured?" — profile, the platform API
// key, execution defaults, notifications. It intentionally has NO external-service
// connection cards; that's Integrations' job (see /integrations). See PHASE 19 in the
// architecture brief this file was built against for the exact split.

export default function Settings() {
  const { user, setUser, logout } = useAuthStore()

  return (
    <div style={{ padding: '32px', maxWidth: '760px', margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '28px' }}>
      <div>
        <h1 style={{ fontSize: '22px', fontWeight: 700 }}>Settings</h1>
        <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '4px' }}>
          Your AutoWorkflow account configuration. Looking to connect GitHub, Slack, or an AI provider?{' '}
          <Link to="/integrations" style={{ color: 'var(--accent)' }}>That's in Integrations.</Link>
        </p>
      </div>

      <ProfileSection user={user} onUpdated={setUser} />
      <PlatformApiKeySection />
      <ExecutionPreferencesSection />
      <NotificationsSection />
      <ConnectedServicesShortcut />
      <DangerZoneSection onLogout={logout} />
    </div>
  )
}

function SectionCard({ icon: Icon, title, description, children }) {
  return (
    <section style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: '12px', padding: '20px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '4px' }}>
        {Icon && <Icon size={16} style={{ color: 'var(--accent)' }} />}
        <h2 style={{ fontSize: '15px', fontWeight: 600 }}>{title}</h2>
      </div>
      {description && <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '16px' }}>{description}</p>}
      {children}
    </section>
  )
}

const inputStyle = { background: 'var(--bg-input)', border: '1px solid var(--border)', padding: '9px 12px', borderRadius: '7px', color: 'var(--text-primary)', fontSize: '13px', outline: 'none', width: '100%' }

function ProfileSection({ user, onUpdated }) {
  const [name, setName] = useState(user?.name || '')
  const [email, setEmail] = useState(user?.email || '')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    setName(user?.name || '')
    setEmail(user?.email || '')
  }, [user?.name, user?.email])

  const handleSave = async () => {
    if (!name.trim() || !email.trim()) {
      toast.error('Name and email are required.')
      return
    }
    setSaving(true)
    try {
      const updated = await userService.updateProfile({ name: name.trim(), email: email.trim() })
      onUpdated({ ...user, ...updated })
      toast.success('Profile updated.')
    } catch (err) {
      // Honest failure — no fake "saved" state. See Phase 16: backend failures stay failures.
      const message = err?.response?.data?.message || err?.message || 'Could not update profile.'
      toast.error(message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <SectionCard icon={User} title="Profile Information">
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)' }}>Name</label>
          <input type="text" value={name} onChange={(e) => setName(e.target.value)} style={inputStyle} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)' }}>Email</label>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} style={inputStyle} />
        </div>
        <button className="btn-primary" onClick={handleSave} disabled={saving} style={{ alignSelf: 'flex-start', marginTop: '4px', opacity: saving ? 0.7 : 1 }}>
          {saving ? 'Saving...' : 'Save Changes'}
        </button>
      </div>
    </SectionCard>
  )
}

function PlatformApiKeySection() {
  const [revealedKey, setRevealedKey] = useState(null) // full key, only ever held after an explicit Reveal/Generate
  const [lastFour, setLastFour] = useState(null)
  const [hasKey, setHasKey] = useState(false)
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    userService.me()
      .then((me) => {
        setHasKey(!!me.hasApiKey)
        setLastFour(me.apiKeyLastFour || null)
      })
      .catch(() => { /* profile fetch failure is surfaced by ProfileSection already */ })
  }, [])

  const handleReveal = async () => {
    setBusy(true)
    try {
      const res = await userService.revealApiKey()
      setRevealedKey(res.apiKey)
      setLastFour(res.lastFour)
      setHasKey(true)
    } catch (err) {
      toast.error(err?.response?.data?.message || err?.message || 'Could not reveal API key.')
    } finally {
      setBusy(false)
    }
  }

  const handleGenerate = async () => {
    if (hasKey && !window.confirm('This replaces your current platform API key. Anything using the old key will stop working. Continue?')) {
      return
    }
    setBusy(true)
    try {
      const res = await userService.generateApiKey()
      setRevealedKey(res.apiKey)
      setLastFour(res.lastFour)
      setHasKey(true)
      toast.success('New platform API key generated.')
    } catch (err) {
      toast.error(err?.response?.data?.message || err?.message || 'Could not generate API key.')
    } finally {
      setBusy(false)
    }
  }

  const handleCopy = () => {
    if (!revealedKey) return
    navigator.clipboard.writeText(revealedKey)
    setCopied(true)
    toast.success('API key copied to clipboard.')
    setTimeout(() => setCopied(false), 2000)
  }

  const displayValue = revealedKey || (lastFour ? `••••••••••••${lastFour}` : (hasKey ? '••••••••••••' : 'No key generated yet'))

  return (
    <SectionCard icon={Key} title="Platform API Key" description="Use this key to authenticate external requests to AutoWorkflow. This is not an OpenAI, Gemini, or GitHub key — connect those in Integrations.">
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <input type="text" readOnly value={displayValue} style={{ ...inputStyle, fontFamily: 'monospace', flex: 1 }} />
          {revealedKey ? (
            <button className="btn-ghost" onClick={handleCopy} disabled={busy} style={{ padding: '9px 12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
              {copied ? <Check size={14} /> : <Copy size={14} />} {copied ? 'Copied' : 'Copy'}
            </button>
          ) : hasKey ? (
            <button className="btn-ghost" onClick={handleReveal} disabled={busy} style={{ padding: '9px 12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Eye size={14} /> {busy ? 'Loading...' : 'Reveal'}
            </button>
          ) : null}
        </div>
        <button className="btn-primary" onClick={handleGenerate} disabled={busy} style={{ alignSelf: 'flex-start', display: 'flex', alignItems: 'center', gap: '6px', opacity: busy ? 0.7 : 1 }}>
          <RefreshCw size={13} /> {hasKey ? 'Generate New Key' : 'Generate Key'}
        </button>
        {revealedKey && (
          <p style={{ fontSize: '11px', color: 'var(--accent-amber)' }}>
            This is shown in full only once per Reveal/Generate — store it somewhere safe now.
          </p>
        )}
      </div>
    </SectionCard>
  )
}

function ExecutionPreferencesSection() {
  // These are UI-only defaults applied when creating new nodes/workflows in this
  // browser session — there is currently no backend "user execution preferences"
  // endpoint, so nothing here is persisted server-side yet. Not claiming otherwise.
  const [timezone, setTimezone] = useState(() => localStorage.getItem('autoworkflow_pref_timezone') || 'UTC')
  const [defaultProvider, setDefaultProvider] = useState(() => localStorage.getItem('autoworkflow_pref_provider') || 'gemini')

  const update = (key, value, setter) => {
    setter(value)
    localStorage.setItem(key, value)
  }

  return (
    <SectionCard icon={Globe2} title="Execution Preferences" description="Local defaults for new nodes in this browser. Not yet synced to your account server-side.">
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)' }}>Default Timezone</label>
          <select value={timezone} onChange={(e) => update('autoworkflow_pref_timezone', e.target.value, setTimezone)} style={inputStyle}>
            {['UTC', 'Asia/Kolkata', 'America/New_York', 'Europe/London'].map(tz => <option key={tz} value={tz}>{tz}</option>)}
          </select>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)' }}>Default AI Provider</label>
          <select value={defaultProvider} onChange={(e) => update('autoworkflow_pref_provider', e.target.value, setDefaultProvider)} style={inputStyle}>
            <option value="gemini">Gemini</option>
            <option value="openai">OpenAI</option>
          </select>
        </div>
      </div>
    </SectionCard>
  )
}

function NotificationsSection() {
  const [emailOnFailure, setEmailOnFailure] = useState(() => localStorage.getItem('autoworkflow_notify_failure') !== 'false')

  const toggle = () => {
    const next = !emailOnFailure
    setEmailOnFailure(next)
    localStorage.setItem('autoworkflow_notify_failure', String(next))
  }

  return (
    <SectionCard icon={Bell} title="Notifications" description="Platform notification preferences (execution alerts, not integration status).">
      <label style={{ display: 'flex', alignItems: 'center', gap: '10px', fontSize: '13px', cursor: 'pointer' }}>
        <input type="checkbox" checked={emailOnFailure} onChange={toggle} style={{ accentColor: 'var(--accent)' }} />
        Notify me when a workflow execution fails
      </label>
    </SectionCard>
  )
}

function ConnectedServicesShortcut() {
  return (
    <SectionCard icon={Share2} title="Connected Services">
      <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '12px' }}>
        Manage GitHub, Slack, Gemini, OpenAI, and other external connections.
      </p>
      <Link to="/integrations" className="btn-primary" style={{ display: 'inline-block', textDecoration: 'none' }}>
        Manage Integrations
      </Link>
    </SectionCard>
  )
}

function DangerZoneSection({ onLogout }) {
  return (
    <SectionCard title="Danger Zone">
      <button
        onClick={() => onLogout(false)}
        style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '9px 16px', borderRadius: '8px', background: 'rgba(244, 63, 94, 0.1)', border: '1px solid var(--accent-rose)', color: 'var(--accent-rose)', fontSize: '13px', fontWeight: 600, cursor: 'pointer' }}
      >
        <LogOut size={14} /> Log Out
      </button>
    </SectionCard>
  )
}
