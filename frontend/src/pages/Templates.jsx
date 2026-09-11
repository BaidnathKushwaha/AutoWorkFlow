import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, Zap, Search, Filter, Mail, Brain, Table2, Github, FileText, Loader2, CheckCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import templateService from '../services/template/templateService'

const DIFFICULTY_STYLES = {
  Beginner: { color: '#10b981', bg: 'rgba(16,185,129,0.12)' },
  Intermediate: { color: '#f59e0b', bg: 'rgba(245,158,11,0.12)' },
  Advanced: { color: '#ef4444', bg: 'rgba(239,68,68,0.12)' },
}

const ICONS = { email_received: Mail, gmail: Mail, classifier: Brain, ai: Brain, google_sheets: Table2, github: Github, notion: FileText }

function TemplateCard({ template, onImport, importing }) {
  const diff = DIFFICULTY_STYLES[template.difficulty] || DIFFICULTY_STYLES.Beginner
  const TriggerIcon = ICONS[template.triggerIconKey] || Zap
  const TargetIcon = ICONS[template.targetIconKey] || Zap

  return (
    <div style={{ display: 'flex', flexDirection: 'column', background: 'var(--bg-card)', borderRadius: '16px', border: '1px solid var(--border)', overflow: 'hidden', boxShadow: '0 2px 8px rgba(0,0,0,0.15)' }}>
      <div style={{ height: '150px', position: 'relative', background: 'linear-gradient(135deg, var(--bg-surface), var(--bg-card))', borderBottom: '1px solid var(--border)' }}>
        <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '28px' }}>
          <div style={{ width: 58, height: 58, borderRadius: 14, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(249,115,22,0.12)', border: '1px solid rgba(249,115,22,0.3)' }}><TriggerIcon size={25} color="#f97316" /></div>
          <ArrowRight size={18} color="var(--text-muted)" />
          <div style={{ width: 58, height: 58, borderRadius: 14, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(16,185,129,0.12)', border: '1px solid rgba(16,185,129,0.3)' }}><TargetIcon size={25} color="#10b981" /></div>
        </div>
        <div style={{ position: 'absolute', top: 14, left: 16, display: 'flex', gap: 7 }}>
          <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--accent)', background: 'var(--bg-input)', border: '1px solid var(--border)', padding: '4px 8px', borderRadius: 6 }}>{template.category || 'Automation'}</span>
          <span style={{ fontSize: 10, fontWeight: 700, color: diff.color, background: diff.bg, border: `1px solid ${diff.color}55`, padding: '4px 8px', borderRadius: 6, textTransform: 'uppercase' }}>{template.difficulty || 'Beginner'}</span>
        </div>
      </div>

      <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 12, flex: 1 }}>
        <div>
          <h3 style={{ fontSize: 15, fontWeight: 700, marginBottom: 7, color: 'var(--text-primary)' }}>{template.name}</h3>
          <p style={{ color: 'var(--text-secondary)', fontSize: 12.5, lineHeight: 1.6, margin: 0 }}>{template.description}</p>
        </div>
        {!!template.integrationRequirements?.length && (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {template.integrationRequirements.map(req => <span key={req} style={{ padding: '3px 8px', borderRadius: 99, background: 'var(--bg-input)', border: '1px solid var(--border)', color: 'var(--text-secondary)', fontSize: 11 }}>{req}</span>)}
          </div>
        )}
        <button
          type="button"
          disabled={importing}
          onClick={() => onImport(template.id)}
          style={{ marginTop: 'auto', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '10px 16px', borderRadius: 10, background: importing ? 'var(--bg-input)' : 'var(--accent)', border: '1px solid var(--accent)', color: importing ? 'var(--text-muted)' : '#fff', fontSize: 13, fontWeight: 600, cursor: importing ? 'wait' : 'pointer' }}
        >
          {importing ? <Loader2 size={14} className="spin" /> : <CheckCircle2 size={14} />}
          {importing ? 'Importing...' : 'Use Template'}
          {!importing && <ArrowRight size={14} />}
        </button>
      </div>
    </div>
  )
}

export default function Templates() {
  const navigate = useNavigate()
  const [templates, setTemplates] = useState([])
  const [search, setSearch] = useState('')
  const [activeCategory, setActiveCategory] = useState('All')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [importingId, setImportingId] = useState(null)

  useEffect(() => {
    let active = true
    templateService.list()
      .then(res => {
        const data = Array.isArray(res) ? res : (res?.data || [])
        if (active) setTemplates(data)
      })
      .catch(err => { if (active) setError(err?.response?.data?.message || err?.message || 'Unable to load templates.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  const categories = useMemo(() => ['All', ...Array.from(new Set(templates.map(t => t.category).filter(Boolean)))], [templates])
  const filtered = useMemo(() => templates.filter(t => {
    const q = search.trim().toLowerCase()
    const categoryMatch = activeCategory === 'All' || t.category === activeCategory
    const textMatch = !q || [t.name, t.description, ...(t.integrationRequirements || [])].join(' ').toLowerCase().includes(q)
    return categoryMatch && textMatch
  }), [templates, search, activeCategory])

  const handleImport = async (id) => {
    setImportingId(id)
    try {
      const res = await templateService.import(id)
      const workflow = res?.data || res
      if (!workflow?.id) throw new Error('Backend did not return an imported workflow.')
      toast.success('Template imported as a draft.')
      navigate(`/builder/${workflow.id}`)
    } catch (err) {
      toast.error('Template import failed', { description: err?.response?.data?.message || err?.message || 'Unable to import template.' })
    } finally {
      setImportingId(null)
    }
  }

  return (
    <div>
      <div style={{ marginBottom: 32 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: 'linear-gradient(135deg, var(--accent) 0%, var(--accent-violet) 100%)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Zap size={18} color="#fff" /></div>
          <h1 style={{ fontSize: 24, fontWeight: 800, letterSpacing: '-0.5px' }}>Workflow Templates</h1>
        </div>
        <p style={{ color: 'var(--text-secondary)', fontSize: 14 }}>Executable workflow templates served by the backend catalogue.</p>
      </div>

      <div style={{ display: 'flex', gap: 16, marginBottom: 28, flexWrap: 'wrap', alignItems: 'center' }}>
        <div style={{ position: 'relative', flex: 1, minWidth: 200, maxWidth: 360 }}>
          <Search size={14} style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
          <input type="text" placeholder="Search templates..." value={search} onChange={e => setSearch(e.target.value)} style={{ width: '100%', padding: '9px 12px 9px 34px', background: 'var(--bg-input)', border: '1px solid var(--border)', borderRadius: 10, color: 'var(--text-primary)', fontSize: 13, outline: 'none', boxSizing: 'border-box' }} />
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
          <Filter size={13} color="var(--text-muted)" />
          {categories.map(category => <button key={category} onClick={() => setActiveCategory(category)} style={{ padding: '6px 14px', borderRadius: 99, fontSize: 12, fontWeight: 600, border: '1px solid', borderColor: activeCategory === category ? 'var(--accent)' : 'var(--border)', background: activeCategory === category ? 'var(--accent)' : 'var(--bg-input)', color: activeCategory === category ? '#fff' : 'var(--text-secondary)', cursor: 'pointer' }}>{category}</button>)}
        </div>
      </div>

      {loading ? (
        <div style={{ padding: 60, textAlign: 'center', color: 'var(--text-muted)' }}><Loader2 size={24} className="spin" /> Loading templates...</div>
      ) : error ? (
        <div style={{ padding: 30, textAlign: 'center', color: 'var(--accent-rose)', background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 16 }}>{error}</div>
      ) : (
        <>
          <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 20 }}>Showing {filtered.length} of {templates.length} templates</p>
          {filtered.length === 0 ? <div style={{ padding: 60, textAlign: 'center', color: 'var(--text-muted)', background: 'var(--bg-card)', borderRadius: 16 }}>No templates found.</div> : <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 24 }}>{filtered.map(template => <TemplateCard key={template.id} template={template} onImport={handleImport} importing={importingId === template.id} />)}</div>}
        </>
      )}
    </div>
  )
}
