import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { GitPullRequest, MessageSquare, Mail, Brain, Database, FileText, CheckCircle, XCircle, AlertTriangle, Loader2 } from 'lucide-react'
import integrationService from '../services/integration/integrationService'
import { toast } from 'sonner'

// "What external services does AutoWorkflow have permission to access?" — this page,
// and only this page, connects/disconnects external providers. See Settings.jsx for
// account/profile config, which deliberately has no connection cards of its own.
//
// IMPORTANT: every action here reflects the REAL backend result. There is no
// localStorage fallback that shows "Connected" when the backend call actually
// failed — a failed connect/disconnect stays failed and says so.

const PROVIDER_METADATA = {
  github: { name: 'GitHub', icon: GitPullRequest, color: 'var(--text-primary)', kind: 'oauth' },
  slack: { name: 'Slack', icon: MessageSquare, color: 'var(--accent-amber)', kind: 'oauth' },
  openai: { name: 'OpenAI', icon: Brain, color: 'var(--node-ai)', kind: 'key', placeholder: 'sk-...' },
  gemini: { name: 'Gemini', icon: Brain, color: 'var(--node-ai)', kind: 'key', placeholder: 'AIza...' },
  openrouter: { name: 'OpenRouter', icon: Brain, color: 'var(--node-ai)', kind: 'key', placeholder: 'sk-or-...' },
  gmail: { name: 'Gmail', icon: Mail, color: 'var(--accent-rose)', kind: 'oauth' },
  google_sheets: { name: 'Google Sheets', icon: Database, color: 'var(--node-storage)', kind: 'oauth' },
  notion: { name: 'Notion', icon: FileText, color: 'var(--text-primary)', kind: 'oauth' },
  discord: { name: 'Discord', icon: MessageSquare, color: '#5865F2', kind: 'oauth' },
}

// Backend status enum stays HEALTHY/DISCONNECTED/ERROR internally (not worth a DB/API
// change just for a label) — but for API-key providers (OpenAI, Gemini) "Healthy" is
// misleading: connecting one only stores the key, it's never actually verified against
// the provider (deliberately — we don't want to spend the user's API quota just to test
// a key on connect). Label those "Connected" instead, and reserve "Healthy" for OAuth
// providers where the status genuinely reflects a verified token exchange.
function statusLabel(status, providerKind) {
  if (status === 'HEALTHY') return providerKind === 'key' ? 'Connected' : 'Healthy'
  if (status === 'DISCONNECTED') return 'Disconnected'
  if (status === 'ERROR') return 'Error'
  return status
}

export default function Integrations() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [integrations, setIntegrations] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [keyDraft, setKeyDraft] = useState('')
  const [connectingProvider, setConnectingProvider] = useState(null)
  const [busyProvider, setBusyProvider] = useState(null)

  const fetchIntegrations = async () => {
    setLoading(true)
    setLoadError(null)
    try {
      const data = await integrationService.list()
      const list = Array.isArray(data) ? data : (data?.data || [])
      // Backend already returns a disconnected stub for every catalog provider
      // (see IntegrationService.listForUser), so we render exactly what it says —
      // no client-side "maybe it's actually connected" guessing.
      setIntegrations(list)
    } catch (err) {
      setLoadError(err?.response?.data?.message || err?.message || 'Unable to connect to backend.')
      setIntegrations([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let cancelled = false

    const loadIntegrations = async () => {
      if (cancelled) return
      await fetchIntegrations()
    }

    loadIntegrations()

    return () => {
      cancelled = true
    }
  }, [])

  // Backend's OAuth callback (IntegrationController#oauthCallbackGet) redirects the
  // browser back here with ?status=success&provider=... or ?status=error&message=...
  // after the provider round-trip completes. Surface exactly what the backend reported
  // — no assuming success just because we landed back on this page.
  useEffect(() => {
    const status = searchParams.get('status')
    if (!status) return

    const refreshIntegrations = async () => {
      if (status === 'success') {
        const provider = searchParams.get('provider')
        const name =
            PROVIDER_METADATA[provider]?.name ||
            provider ||
            'Integration'

        toast.success(`${name} connected.`)

        await fetchIntegrations()
      } else if (status === 'error') {
        const message =
            searchParams.get('message') ||
            'Connection failed.'

        toast.error(message)
      }

      // Clear the query params so a page refresh doesn't re-show the toast.
      setSearchParams({}, { replace: true })
    }

    refreshIntegrations()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams])

  const handleConnect = async (providerKey) => {
    const meta = PROVIDER_METADATA[providerKey]
    if (meta.kind === 'key') {
      setConnectingProvider(providerKey)
      setKeyDraft('')
      return
    }

    setBusyProvider(providerKey)
    try {
      const res = await integrationService.initiateOAuth(providerKey)
      const authUrl = res?.authorizationUrl || res?.data?.authorizationUrl
      if (!authUrl) throw new Error('Backend did not return an authorization URL.')
      window.location.assign(authUrl)
    } catch (err) {
      toast.error(`${meta.name} connection failed: ${err?.response?.data?.message || err?.message || 'unable to start OAuth flow.'}`)
      setBusyProvider(null)
    }
  }

  const handleSaveKey = async (providerKey) => {
    const meta = PROVIDER_METADATA[providerKey]
    if (!keyDraft.trim()) {
      toast.error('Enter an API key first.')
      return
    }
    setBusyProvider(providerKey)
    try {
      await integrationService.connectWithKey(providerKey, keyDraft.trim())
      toast.success(`${meta.name} connected.`)
      setConnectingProvider(null)
      setKeyDraft('')
      await fetchIntegrations()
    } catch (err) {
      // Honest failure: an invalid/rejected key stays reported as a failure, never a fake success.
      toast.error(err?.response?.data?.message || err?.message || `${meta.name} rejected the API key.`)
    } finally {
      setBusyProvider(null)
    }
  }

  const handleDisconnect = async (providerKey) => {
    const meta = PROVIDER_METADATA[providerKey]
    setBusyProvider(providerKey)
    try {
      await integrationService.disconnect(providerKey)
      toast.success(`${meta.name} disconnected.`)
      await fetchIntegrations()
    } catch (err) {
      toast.error(err?.response?.data?.message || err?.message || `Could not disconnect ${meta.name}.`)
    } finally {
      setBusyProvider(null)
    }
  }

  return (
    <div>
      <div style={{ marginBottom: '32px' }}>
        <h1 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '8px' }}>Integrations</h1>
        <p style={{ color: 'var(--text-secondary)' }}>Manage connections to external services and APIs.</p>
      </div>

      {loading && (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '60px', color: 'var(--text-muted)', gap: '12px' }}>
          <Loader2 size={24} className="animate-spin" /> Loading integrations...
        </div>
      )}

      {!loading && loadError && (
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '16px', borderRadius: '10px', background: 'rgba(244, 63, 94, 0.08)', border: '1px solid var(--accent-rose)', color: 'var(--accent-rose)', marginBottom: '20px' }}>
          <AlertTriangle size={16} />
          <div>
            <div style={{ fontWeight: 600, fontSize: '13px' }}>Unable to connect to backend.</div>
            <div style={{ fontSize: '12px', opacity: 0.85 }}>{loadError}</div>
          </div>
          <button className="btn-ghost" onClick={fetchIntegrations} style={{ marginLeft: 'auto' }}>Retry</button>
        </div>
      )}

      {!loading && !loadError && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '24px' }}>
          {integrations.map((integration) => {
            const providerKey = integration.provider
            const meta = PROVIDER_METADATA[providerKey] || { name: providerKey, icon: GitPullRequest, color: 'var(--text-primary)', kind: 'oauth' }
            const Icon = meta.icon
            const isConnected = integration.status === 'HEALTHY'
            const isError = integration.status === 'ERROR'
            const busy = busyProvider === providerKey

            return (
              <div key={providerKey} className="card" style={{ padding: '24px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                      <div style={{ width: '48px', height: '48px', background: 'var(--bg-surface)', border: `1px solid ${isConnected ? meta.color : 'var(--border)'}`, borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <Icon size={24} color={isConnected ? meta.color : 'var(--text-muted)'} />
                      </div>
                      <div>
                        <h3 style={{ fontSize: '16px', fontWeight: 600 }}>{meta.name}</h3>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: isError ? 'var(--accent-rose)' : 'var(--text-secondary)', marginTop: '4px' }}>
                          {isConnected ? <CheckCircle size={12} color="var(--accent-emerald)" /> : <XCircle size={12} color={isError ? 'var(--accent-rose)' : 'var(--text-muted)'} />}
                          {statusLabel(integration.status, meta.kind)}
                        </div>
                        {integration.accountLabel && (
                          <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>{integration.accountLabel}</div>
                        )}
                        {isConnected && meta.kind === 'key' && (
                          <div style={{ fontSize: '10px', color: 'var(--text-muted)', marginTop: '2px' }}>
                            Key stored — not verified against {meta.name} until first use
                          </div>
                        )}
                        {integration.lastCheckedAt && (
                          <div style={{ fontSize: '10px', color: 'var(--text-muted)', marginTop: '2px' }}>
                            Checked {new Date(integration.lastCheckedAt).toLocaleString()}
                          </div>
                        )}
                      </div>
                    </div>

                    <button
                      onClick={() => isConnected ? handleDisconnect(providerKey) : handleConnect(providerKey)}
                      disabled={busy}
                      className={isConnected ? 'btn-secondary' : 'btn-primary'}
                      style={{ ...(isConnected ? { borderColor: 'var(--accent-rose)', color: 'var(--accent-rose)' } : {}), opacity: busy ? 0.6 : 1 }}
                    >
                      {busy ? '...' : (isConnected ? 'Disconnect' : 'Connect')}
                    </button>
                  </div>

                  {connectingProvider === providerKey && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '16px', background: 'var(--bg-surface)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border)' }}>
                      <input
                        type="password"
                        placeholder={`Enter API Key (${meta.placeholder})`}
                        value={keyDraft}
                        onChange={(e) => setKeyDraft(e.target.value)}
                        style={{ background: 'var(--bg-input)', border: '1px solid var(--border)', padding: '8px 12px', borderRadius: '6px', color: 'var(--text-primary)', fontSize: '13px', outline: 'none' }}
                      />
                      <div style={{ display: 'flex', gap: '8px' }}>
                        <button className="btn-primary" onClick={() => handleSaveKey(providerKey)} disabled={busy} style={{ padding: '6px 12px', fontSize: '12px' }}>
                          {busy ? 'Saving...' : 'Save Key'}
                        </button>
                        <button className="btn-ghost" onClick={() => setConnectingProvider(null)} style={{ padding: '6px 12px', fontSize: '12px' }}>
                          Cancel
                        </button>
                      </div>
                    </div>
                  )}

                  <div style={{ background: 'var(--bg-surface)', padding: '12px 16px', borderRadius: '8px', border: '1px solid var(--border-subtle)' }}>
                    <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '4px' }}>Permissions</div>
                    <div style={{ fontSize: '13px', fontWeight: 500 }}>{(integration.scopes || []).join(', ') || '—'}</div>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
