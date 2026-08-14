import { Search } from 'lucide-react'
import {
  Clock, Zap, GitPullRequest, Mail, Brain, Network, FileText, Tag,
  GitBranch, RefreshCw, Merge, Timer, Code2, Globe, MessageSquare,
  BookOpen, Table, Database, File, Server, Send, MessageCircle, Hash, Blocks
} from 'lucide-react'
import { nodeCategories } from '../../data/nodeTypes'
import { useState } from 'react'

const ICON_MAP = {
  Clock, Zap, GitPullRequest, Mail, Brain, Network, FileText, Tag,
  GitBranch, RefreshCw, Merge, Timer, Code2, Globe, MessageSquare,
  BookOpen, Table, Database, File, Server, Send, MessageCircle, Hash
}

export default function NodePalette() {
  const [searchTerm, setSearchTerm] = useState('')

  const onDragStart = (event, nodeType, label, description) => {
    event.dataTransfer.setData('application/reactflow', JSON.stringify({ type: nodeType, label, description }));
    event.dataTransfer.effectAllowed = 'move';
  };

  const filteredCategories = nodeCategories.map(cat => ({
    ...cat,
    nodes: cat.nodes.filter(n => 
      n.label.toLowerCase().includes(searchTerm.toLowerCase()) || 
      n.description?.toLowerCase().includes(searchTerm.toLowerCase())
    )
  })).filter(cat => cat.nodes.length > 0)

  return (
    <div
      style={{
        width: '300px',
        background: 'var(--bg-surface)',
        borderRight: '1px solid var(--border)',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <div style={{ padding: '20px', borderBottom: '1px solid var(--border)' }}>
        <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
          <Search size={16} color="var(--text-muted)" style={{ position: 'absolute', left: '12px' }} />
          <input
            type="text"
            placeholder="Search nodes..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{
              width: '100%',
              background: 'var(--bg-input)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-sm)',
              padding: '9px 12px 9px 38px',
              color: 'var(--text-primary)',
              fontSize: '13px',
              outline: 'none',
            }}
          />
        </div>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '20px' }}>
        {filteredCategories.map((category, idx) => (
          <div key={idx} style={{ marginBottom: '28px' }}>
            <h3 style={{ fontSize: '11px', textTransform: 'uppercase', color: 'var(--text-muted)', fontWeight: 700, letterSpacing: '0.5px', marginBottom: '12px' }}>
              {category.label || category.name}
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {category.nodes.map((node, nIdx) => {
                const IconComponent = typeof node.icon === 'string'
                  ? (ICON_MAP[node.icon] || Blocks)
                  : (node.icon || Blocks)

                return (
                  <div
                    key={nIdx}
                    className="card-hover"
                    draggable
                    onDragStart={(event) => onDragStart(event, node.type, node.label, node.description)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '12px',
                      padding: '11px 14px',
                      background: 'var(--bg-card)',
                      border: '1px solid var(--border)',
                      borderRadius: 'var(--radius-sm)',
                      cursor: 'grab',
                    }}
                  >
                    <div
                      style={{
                        width: '28px',
                        height: '28px',
                        borderRadius: '6px',
                        background: `${node.color || '#6366f1'}20`,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0,
                      }}
                    >
                      <IconComponent size={15} color={node.color || '#6366f1'} />
                    </div>
                    <div>
                      <div style={{ fontSize: '13px', fontWeight: 500 }}>{node.label}</div>
                      {node.description && (
                        <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>{node.description}</div>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}


