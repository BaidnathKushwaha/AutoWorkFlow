import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Zap, ChevronRight, Search, Filter } from 'lucide-react'
import { templates } from '../data/templates'

const DIFFICULTY_STYLES = {
  Beginner:     { bg: 'rgba(16,185,129,0.12)', color: '#10b981', border: 'rgba(16,185,129,0.3)' },
  Intermediate: { bg: 'rgba(245,158,11,0.12)', color: '#f59e0b', border: 'rgba(245,158,11,0.3)' },
  Advanced:     { bg: 'rgba(239,68,68,0.12)',  color: '#ef4444', border: 'rgba(239,68,68,0.3)' },
}

const ALL_CATEGORIES = ['All', ...Array.from(new Set(templates.map(t => t.category)))]

function NodeChainPreview({ nodes, colors, accentColor }) {
  const display = nodes.slice(0, 4)
  const extra = nodes.length - 4
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '0', flexWrap: 'nowrap', overflow: 'hidden' }}>
      {display.map((label, i) => (
        <div key={i} style={{ display: 'flex', alignItems: 'center', minWidth: 0 }}>
          <div style={{
            padding: '4px 10px', borderRadius: '6px',
            background: `${colors[i] || accentColor}18`,
            border: `1px solid ${colors[i] || accentColor}44`,
            color: colors[i] || accentColor,
            fontSize: '11px', fontWeight: 600, whiteSpace: 'nowrap',
            maxWidth: '90px', overflow: 'hidden', textOverflow: 'ellipsis',
          }}>
            {label}
          </div>
          {i < display.length - 1 && (
            <ChevronRight size={12} color="var(--text-muted)" style={{ margin: '0 2px', flexShrink: 0 }} />
          )}
        </div>
      ))}
      {extra > 0 && (
        <>
          <ChevronRight size={12} color="var(--text-muted)" style={{ margin: '0 2px' }} />
          <span style={{ fontSize: '11px', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>+{extra} more</span>
        </>
      )}
    </div>
  )
}

function TagPill({ tag }) {
  return (
    <span style={{
      padding: '2px 8px', borderRadius: '99px',
      background: 'var(--bg-input)', border: '1px solid var(--border)',
      color: 'var(--text-secondary)', fontSize: '11px', fontWeight: 500,
      whiteSpace: 'nowrap',
    }}>
      {tag}
    </span>
  )
}

function TemplateCard({ template }) {
  const [hovered, setHovered] = useState(false)
  const diff = DIFFICULTY_STYLES[template.difficulty] || DIFFICULTY_STYLES.Beginner
  const Icon = template.icon

  return (
    <div
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        display: 'flex', flexDirection: 'column',
        background: 'var(--bg-card)', borderRadius: '16px',
        border: `1px solid ${hovered ? template.color + '55' : 'var(--border)'}`,
        overflow: 'hidden', transition: 'all 0.25s ease',
        transform: hovered ? 'translateY(-4px)' : 'translateY(0)',
        boxShadow: hovered ? `0 12px 40px ${template.color}22, 0 4px 16px rgba(0,0,0,0.3)` : '0 2px 8px rgba(0,0,0,0.15)',
      }}
    >
      {/* Header gradient preview */}
      <div style={{
        height: '148px', position: 'relative', overflow: 'hidden',
        background: `linear-gradient(135deg, ${template.color}18 0%, var(--bg-surface) 100%)`,
        borderBottom: `1px solid ${hovered ? template.color + '33' : 'var(--border)'}`,
        transition: 'border-color 0.25s',
      }}>
        {/* Animated glowing orb behind icon */}
        <div style={{
          position: 'absolute', top: '50%', left: '50%',
          transform: 'translate(-50%, -50%)',
          width: '80px', height: '80px', borderRadius: '50%',
          background: `radial-gradient(circle, ${template.color}30 0%, transparent 70%)`,
          filter: 'blur(12px)',
          transition: 'all 0.3s',
          opacity: hovered ? 1 : 0.5,
        }} />

        {/* Node chain pills */}
        <div style={{
          position: 'absolute', bottom: '14px', left: '16px', right: '16px',
        }}>
          <NodeChainPreview
            nodes={template.nodeChain || template.nodes.map(n => n.data.label)}
            colors={template.nodeColors || []}
            accentColor={template.color}
          />
        </div>

        {/* Icon + category badge in top-left */}
        <div style={{ position: 'absolute', top: '16px', left: '16px', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div style={{
            width: '38px', height: '38px', borderRadius: '10px',
            background: `${template.color}20`, border: `1px solid ${template.color}55`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            transition: 'all 0.25s',
            boxShadow: hovered ? `0 0 16px ${template.color}55` : 'none',
          }}>
            <Icon size={18} color={template.color} />
          </div>
          <span style={{
            fontSize: '11px', fontWeight: 600, color: template.color,
            background: `${template.color}15`, border: `1px solid ${template.color}33`,
            padding: '3px 8px', borderRadius: '6px',
          }}>
            {template.category}
          </span>
        </div>

        {/* Difficulty badge top-right */}
        <div style={{ position: 'absolute', top: '16px', right: '16px' }}>
          <span style={{
            fontSize: '10px', fontWeight: 700, letterSpacing: '0.5px',
            textTransform: 'uppercase', padding: '3px 8px', borderRadius: '6px',
            background: diff.bg, color: diff.color, border: `1px solid ${diff.border}`,
          }}>
            {template.difficulty}
          </span>
        </div>

        {/* Step count */}
        <div style={{ position: 'absolute', top: '52px', right: '16px' }}>
          <span style={{ fontSize: '11px', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Zap size={11} /> {template.nodes.length} steps
          </span>
        </div>
      </div>

      {/* Body */}
      <div style={{ padding: '20px', flex: 1, display: 'flex', flexDirection: 'column', gap: '12px' }}>
        <div>
          <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '6px', color: 'var(--text-primary)', lineHeight: 1.3 }}>
            {template.title}
          </h3>
          <p style={{ color: 'var(--text-secondary)', fontSize: '12.5px', lineHeight: 1.6 }}>
            {template.desc}
          </p>
        </div>

        {/* Tags row */}
        {template.tags && (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            {template.tags.map(tag => <TagPill key={tag} tag={tag} />)}
          </div>
        )}

        {/* CTA */}
        <Link
          to={`/builder/new?template=${template.id}`}
          style={{
            marginTop: 'auto', display: 'flex', alignItems: 'center', justifyContent: 'center',
            gap: '8px', padding: '10px 16px', borderRadius: '10px', textDecoration: 'none',
            background: hovered ? template.color : 'var(--bg-input)',
            border: `1px solid ${hovered ? template.color : 'var(--border)'}`,
            color: hovered ? '#fff' : 'var(--text-primary)',
            fontSize: '13px', fontWeight: 600, transition: 'all 0.25s',
          }}
        >
          Use Template <ArrowRight size={14} />
        </Link>
      </div>
    </div>
  )
}

export default function Templates() {
  const [search, setSearch] = useState('')
  const [activeCategory, setActiveCategory] = useState('All')

  const filtered = templates.filter(t => {
    const matchesCategory = activeCategory === 'All' || t.category === activeCategory
    const q = search.toLowerCase()
    const matchesSearch = !q || t.title.toLowerCase().includes(q) || t.desc.toLowerCase().includes(q) || (t.tags || []).some(tag => tag.toLowerCase().includes(q))
    return matchesCategory && matchesSearch
  })

  return (
    <div>
      {/* Page header */}
      <div style={{ marginBottom: '32px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
          <div style={{
            width: '36px', height: '36px', borderRadius: '10px',
            background: 'linear-gradient(135deg, var(--accent) 0%, var(--accent-violet) 100%)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Zap size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: '24px', fontWeight: 800, letterSpacing: '-0.5px' }}>Workflow Templates</h1>
        </div>
        <p style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>
          Production-ready automation workflows. Pick a template and customise it — no setup required.
        </p>
      </div>

      {/* Search & Filters */}
      <div style={{ display: 'flex', gap: '16px', marginBottom: '28px', flexWrap: 'wrap', alignItems: 'center' }}>
        {/* Search bar */}
        <div style={{ position: 'relative', flex: '1', minWidth: '200px', maxWidth: '360px' }}>
          <Search size={14} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
          <input
            type="text"
            placeholder="Search templates..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            style={{
              width: '100%', padding: '9px 12px 9px 34px',
              background: 'var(--bg-input)', border: '1px solid var(--border)',
              borderRadius: '10px', color: 'var(--text-primary)', fontSize: '13px',
              outline: 'none', boxSizing: 'border-box',
            }}
          />
        </div>

        {/* Category chips */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
          <Filter size={13} color="var(--text-muted)" />
          {ALL_CATEGORIES.map(cat => (
            <button
              key={cat}
              onClick={() => setActiveCategory(cat)}
              style={{
                padding: '6px 14px', borderRadius: '99px', fontSize: '12px', fontWeight: 600,
                border: '1px solid',
                borderColor: activeCategory === cat ? 'var(--accent)' : 'var(--border)',
                background: activeCategory === cat ? 'var(--accent)' : 'var(--bg-input)',
                color: activeCategory === cat ? '#fff' : 'var(--text-secondary)',
                cursor: 'pointer', transition: 'all 0.18s',
              }}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>

      {/* Count */}
      <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '20px' }}>
        Showing {filtered.length} of {templates.length} templates
      </p>

      {/* Grid */}
      {filtered.length === 0 ? (
        <div style={{
          textAlign: 'center', padding: '60px 20px',
          color: 'var(--text-muted)', background: 'var(--bg-card)',
          borderRadius: '16px', border: '1px dashed var(--border)',
        }}>
          <Search size={32} style={{ marginBottom: '12px', opacity: 0.4 }} />
          <p style={{ fontSize: '15px', fontWeight: 600, marginBottom: '4px' }}>No templates found</p>
          <p style={{ fontSize: '13px' }}>Try a different search term or category.</p>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '24px' }}>
          {filtered.map(template => (
            <TemplateCard key={template.id} template={template} />
          ))}
        </div>
      )}
    </div>
  )
}
