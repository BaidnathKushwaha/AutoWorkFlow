import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  User as UserIcon,
  KeyRound,
  Globe2,
  Bell,
  Link2,
  Loader2,
  Copy,
  RefreshCw,
  Eye,
  EyeOff,
} from 'lucide-react'
import { toast } from 'sonner'
import userService from '../services/user/userService'
import { useAuthStore } from '../store/authStore'

// Settings covers account/profile config, platform API key, client-side execution
// defaults, and notification preferences. It deliberately has no connection cards
// of its own — see Integrations.jsx for connecting/disconnecting external providers.
//
// AI provider/model selection (the "Default AI Provider" control) is a persistent,
// authenticated, server-side user preference — NOT a localStorage default. See
// AiPreferenceService / UserController#getAiPreferences|updateAiPreferences on the
// backend. localStorage remains authoritative only for things the backend has no
// opinion about (timezone display, notification toggle).

const inputStyle = {
  width: '100%',
  padding: '10px 12px',
  borderRadius: 'var(--radius-sm)',
  border: '1px solid var(--border)',
  background: 'var(--bg-input)',
  color: 'var(--text-primary)',
  fontSize: '13px',
  outline: 'none',
}

const labelStyle = {
  fontSize: '12px',
  fontWeight: 600,
  color: 'var(--text-secondary)',
}

function SectionCard({ icon: Icon, title, description, children }) {
  return (
      <div
          style={{
            background: 'var(--bg-card)',
            border: '1px solid var(--border-subtle)',
            borderRadius: 'var(--radius-lg)',
            boxShadow: 'var(--shadow-card)',
            padding: '24px',
            display: 'flex',
            flexDirection: 'column',
            gap: '16px',
          }}
      >
        <div>
          <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                marginBottom: description ? '4px' : 0,
              }}
          >
            {Icon && <Icon size={18} style={{ color: 'var(--accent)' }} />}
            <h2 style={{ fontSize: '15px', fontWeight: 700, margin: 0 }}>
              {title}
            </h2>
          </div>

          {description && (
              <p
                  style={{
                    fontSize: '13px',
                    color: 'var(--text-secondary)',
                    margin: 0,
                  }}
              >
                {description}
              </p>
          )}
        </div>

        {children}
      </div>
  )
}

function ProfileSection() {
  const { user, setUser } = useAuthStore()

  const [name, setName] = useState(user?.name || '')
  const [email, setEmail] = useState(user?.email || '')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    setName(user?.name || '')
    setEmail(user?.email || '')
  }, [user?.name, user?.email])

  const dirty = name !== (user?.name || '') || email !== (user?.email || '')

  const handleSave = async () => {
    if (!name.trim() || !email.trim()) {
      toast.error('Name and email are required.')
      return
    }

    setSaving(true)

    try {
      const updated = await userService.updateProfile({ name, email })
      setUser(updated)
      toast.success('Profile updated successfully.')
    } catch (err) {
      toast.error(
          err?.response?.data?.message ||
          err?.message ||
          'Could not update profile.'
      )
    } finally {
      setSaving(false)
    }
  }

  return (
      <SectionCard
          icon={UserIcon}
          title="Profile"
          description="Your account name and email address."
      >
        <div
            style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr',
              gap: '12px',
            }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <label style={labelStyle}>Name</label>
            <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                style={inputStyle}
            />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <label style={labelStyle}>Email</label>
            <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                style={inputStyle}
            />
          </div>
        </div>

        <button
            onClick={handleSave}
            disabled={!dirty || saving}
            style={{
              alignSelf: 'flex-start',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '9px 16px',
              borderRadius: 'var(--radius-sm)',
              border: 'none',
              background: !dirty || saving ? 'var(--border)' : 'var(--accent)',
              color: !dirty || saving ? 'var(--text-muted)' : '#fff',
              fontSize: '13px',
              fontWeight: 600,
              cursor: !dirty || saving ? 'not-allowed' : 'pointer',
            }}
        >
          {saving && <Loader2 size={14} className="animate-spin" />}
          Save Changes
        </button>
      </SectionCard>
  )
}

function PlatformApiKeySection() {
  const [hasKey, setHasKey] = useState(false)
  const [lastFour, setLastFour] = useState('')
  const [revealedKey, setRevealedKey] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)

  const loadStatus = () => {
    setLoading(true)

    userService
        .me()
        .then((me) => {
          setHasKey(Boolean(me?.hasApiKey))
          setLastFour(me?.apiKeyLastFour || '')
        })
        .catch(() => {
          // Non-fatal — the rest of Settings still works.
        })
        .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadStatus()
  }, [])

  const handleGenerate = async () => {
    setBusy(true)

    try {
      const result = await userService.generateApiKey()
      setRevealedKey(result?.apiKey || null)
      setHasKey(true)
      setLastFour(result?.lastFour || '')
      toast.success(
          hasKey
              ? 'API key regenerated. Copy it now — you can reveal it again later.'
              : 'API key generated. Copy it now.'
      )
    } catch (err) {
      toast.error(
          err?.response?.data?.message ||
          err?.message ||
          'Could not generate API key.'
      )
    } finally {
      setBusy(false)
    }
  }

  const handleReveal = async () => {
    setBusy(true)

    try {
      const result = await userService.revealApiKey()
      setRevealedKey(result?.apiKey || null)
    } catch (err) {
      toast.error(
          err?.response?.data?.message ||
          err?.message ||
          'Could not reveal API key.'
      )
    } finally {
      setBusy(false)
    }
  }

  const handleCopy = () => {
    if (!revealedKey) return
    navigator.clipboard?.writeText(revealedKey)
    toast.success('Copied to clipboard.')
  }

  const displayValue = loading
      ? 'Loading…'
      : revealedKey
          ? revealedKey
          : hasKey
              ? `•••••••••••••••••••• ${lastFour}`
              : 'No key generated yet'

  return (
      <SectionCard
          icon={KeyRound}
          title="Platform API Key"
          description="Use this key to authenticate external requests to AutoWorkflow. This is not an OpenAI, Gemini, or GitHub key — connect those in Integrations."
      >
        <div style={{ display: 'flex', gap: '8px' }}>
          <input
              type="text"
              value={displayValue}
              readOnly
              style={{ ...inputStyle, flex: 1, fontFamily: 'monospace' }}
          />

          {revealedKey && (
              <button
                  onClick={handleCopy}
                  title="Copy"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: '0 12px',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid var(--border)',
                    background: 'var(--bg-input)',
                    color: 'var(--text-secondary)',
                    cursor: 'pointer',
                  }}
              >
                <Copy size={15} />
              </button>
          )}

          {hasKey && !revealedKey && (
              <button
                  onClick={handleReveal}
                  disabled={busy || loading}
                  title="Reveal"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: '0 12px',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid var(--border)',
                    background: 'var(--bg-input)',
                    color: 'var(--text-secondary)',
                    cursor: busy || loading ? 'not-allowed' : 'pointer',
                  }}
              >
                <Eye size={15} />
              </button>
          )}

          {revealedKey && (
              <button
                  onClick={() => setRevealedKey(null)}
                  title="Hide"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: '0 12px',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid var(--border)',
                    background: 'var(--bg-input)',
                    color: 'var(--text-secondary)',
                    cursor: 'pointer',
                  }}
              >
                <EyeOff size={15} />
              </button>
          )}
        </div>

        <button
            onClick={handleGenerate}
            disabled={busy || loading}
            style={{
              alignSelf: 'flex-start',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '9px 16px',
              borderRadius: 'var(--radius-sm)',
              border: 'none',
              background: 'var(--accent)',
              color: '#fff',
              fontSize: '13px',
              fontWeight: 600,
              cursor: busy || loading ? 'not-allowed' : 'pointer',
              opacity: busy || loading ? 0.7 : 1,
            }}
        >
          {busy ? (
              <Loader2 size={14} className="animate-spin" />
          ) : (
              <RefreshCw size={14} />
          )}
          {hasKey ? 'Regenerate Key' : 'Generate Key'}
        </button>
      </SectionCard>
  )
}

function ExecutionPreferencesSection() {
  const [timezone, setTimezone] = useState(
      () =>
          localStorage.getItem(
              'autoworkflow_pref_timezone'
          ) || 'UTC'
  )

  const [providers, setProviders] = useState([])
  const [defaultProvider, setDefaultProvider] = useState('auto')
  const [defaultModel, setDefaultModel] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let mounted = true

    userService
        .getAiPreferences()
        .then((preferences) => {
          if (!mounted) return

          setProviders(
              Array.isArray(preferences?.providers)
                  ? preferences.providers
                  : []
          )

          setDefaultProvider(
              preferences?.provider || 'auto'
          )

          setDefaultModel(
              preferences?.model || ''
          )
        })
        .catch((err) => {
          if (!mounted) return

          toast.error(
              err?.response?.data?.message ||
              err?.message ||
              'Could not load AI preferences.'
          )
        })
        .finally(() => {
          if (mounted) {
            setLoading(false)
          }
        })

    return () => {
      mounted = false
    }
  }, [])

  const selectedProvider =
      providers.find(
          (provider) =>
              provider.key === defaultProvider
      )

  const availableModels =
      selectedProvider?.models || []

  const handleTimezoneChange = (value) => {
    setTimezone(value)

    localStorage.setItem(
        'autoworkflow_pref_timezone',
        value
    )
  }

  const saveAiPreference = async (
      provider,
      model
  ) => {
    setSaving(true)

    try {
      const updated =
          await userService.updateAiPreferences({
            provider,
            model:
                provider === 'auto'
                    ? null
                    : model,
          })

      setDefaultProvider(
          updated?.provider || provider
      )

      setDefaultModel(
          updated?.model || ''
      )

      if (
          Array.isArray(updated?.providers)
      ) {
        setProviders(updated.providers)
      }

      toast.success(
          'AI preferences updated.'
      )
    } catch (err) {
      toast.error(
          err?.response?.data?.message ||
          err?.message ||
          'Could not update AI preferences.'
      )
    } finally {
      setSaving(false)
    }
  }

  const handleProviderChange = (provider) => {
    if (provider === 'auto') {
      setDefaultProvider('auto')
      setDefaultModel('')

      saveAiPreference(
          'auto',
          null
      )

      return
    }

    const providerDefinition =
        providers.find(
            (item) =>
                item.key === provider
        )

    const firstModel =
        providerDefinition?.models?.[0] || ''

    setDefaultProvider(provider)
    setDefaultModel(firstModel)

    saveAiPreference(
        provider,
        firstModel
    )
  }

  const handleModelChange = (model) => {
    setDefaultModel(model)

    saveAiPreference(
        defaultProvider,
        model
    )
  }

  return (
      <SectionCard
          icon={Globe2}
          title="Execution Preferences"
          description="Defaults used by AutoWorkflow when an AI operation uses your account-level AI preference."
      >
        <div
            style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr',
              gap: '12px',
            }}
        >
          <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '6px',
              }}
          >
            <label
                style={{
                  fontSize: '12px',
                  fontWeight: 600,
                  color: 'var(--text-secondary)',
                }}
            >
              Default Timezone
            </label>

            <select
                value={timezone}
                onChange={(e) =>
                    handleTimezoneChange(
                        e.target.value
                    )
                }
                style={inputStyle}
            >
              {[
                'UTC',
                'Asia/Kolkata',
                'America/New_York',
                'Europe/London',
              ].map((tz) => (
                  <option
                      key={tz}
                      value={tz}
                  >
                    {tz}
                  </option>
              ))}
            </select>
          </div>

          <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '6px',
              }}
          >
            <label
                style={{
                  fontSize: '12px',
                  fontWeight: 600,
                  color: 'var(--text-secondary)',
                }}
            >
              Default AI Provider
            </label>

            <select
                value={defaultProvider}
                onChange={(e) =>
                    handleProviderChange(
                        e.target.value
                    )
                }
                disabled={
                    loading || saving
                }
                style={inputStyle}
            >
              {providers.map(
                  (provider) => (
                      <option
                          key={provider.key}
                          value={provider.key}
                      >
                        {provider.label}
                      </option>
                  )
              )}
            </select>
          </div>

          <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '6px',
                gridColumn: '2',
              }}
          >
            <label
                style={{
                  fontSize: '12px',
                  fontWeight: 600,
                  color: 'var(--text-secondary)',
                }}
            >
              Default AI Model
            </label>

            {defaultProvider === 'auto' ? (
                <input
                    type="text"
                    value="Automatic"
                    readOnly
                    disabled
                    style={{
                      ...inputStyle,
                      opacity: 0.7,
                    }}
                />
            ) : (
                <select
                    value={defaultModel}
                    onChange={(e) =>
                        handleModelChange(
                            e.target.value
                        )
                    }
                    disabled={
                        loading ||
                        saving ||
                        availableModels.length === 0
                    }
                    style={inputStyle}
                >
                  {availableModels.map(
                      (model) => (
                          <option
                              key={model}
                              value={model}
                          >
                            {model}
                          </option>
                      )
                  )}
                </select>
            )}

            <span
                style={{
                  fontSize: '11px',
                  color: 'var(--text-muted)',
                }}
            >
            {defaultProvider === 'auto'
                ? 'Uses the configured provider fallback chain.'
                : 'This provider and model are used for account-level AI operations.'}
          </span>
          </div>
        </div>
      </SectionCard>
  )
}

function NotificationsSection() {
  const [notifyOnFailure, setNotifyOnFailure] = useState(
      () =>
          localStorage.getItem(
              'autoworkflow_pref_notify_on_failure'
          ) !== 'false'
  )

  const toggle = () => {
    const next = !notifyOnFailure
    setNotifyOnFailure(next)
    localStorage.setItem(
        'autoworkflow_pref_notify_on_failure',
        String(next)
    )
  }

  return (
      <SectionCard
          icon={Bell}
          title="Notifications"
          description="Platform notification preferences (execution alerts, not integration status)."
      >
        <label
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              fontSize: '13px',
              color: 'var(--text-primary)',
              cursor: 'pointer',
            }}
        >
          <input
              type="checkbox"
              checked={notifyOnFailure}
              onChange={toggle}
          />
          Notify me when a workflow execution fails
        </label>
      </SectionCard>
  )
}

function ConnectedServicesSection() {
  return (
      <SectionCard
          icon={Link2}
          title="Connected Services"
          description="Manage GitHub, Slack, Gemini, OpenAI, and other external connections."
      >
        <Link
            to="/integrations"
            style={{
              alignSelf: 'flex-start',
              display: 'inline-flex',
              alignItems: 'center',
              padding: '9px 16px',
              borderRadius: 'var(--radius-sm)',
              border: 'none',
              background: 'var(--accent)',
              color: '#fff',
              fontSize: '13px',
              fontWeight: 600,
              textDecoration: 'none',
            }}
        >
          Manage Integrations
        </Link>
      </SectionCard>
  )
}

export default function Settings() {
  return (
      <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '24px',
          }}
      >
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '4px' }}>
            Settings
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>
            Manage your account, platform API key, and execution defaults.
          </p>
        </div>

        <ProfileSection />
        <PlatformApiKeySection />
        <ExecutionPreferencesSection />
        <NotificationsSection />
        <ConnectedServicesSection />
      </div>
  )
}