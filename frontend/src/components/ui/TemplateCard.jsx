import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'

export default function TemplateCard({ template }) {
  return (
    <div className="card card-hover" style={{ display: 'flex', flexDirection: 'column' }}>
      <div style={{ height: '120px', background: 'var(--bg-surface)', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'center' }} className="grid-bg">
        <template.icon size={32} color={template.color} />
      </div>
      <div style={{ padding: '20px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <h3 style={{ fontSize: '16px', fontWeight: 600, marginBottom: '8px' }}>{template.title}</h3>
        <p style={{ color: 'var(--text-secondary)', fontSize: '13px', lineHeight: 1.5, marginBottom: '20px', flex: 1 }}>{template.desc}</p>
        <Link to={`/builder/new?template=${template.id}`} className="btn-secondary" style={{ width: '100%', justifyContent: 'center', textDecoration: 'none' }}>
          Use Template <ArrowRight size={16} />
        </Link>
      </div>
    </div>
  )
}
