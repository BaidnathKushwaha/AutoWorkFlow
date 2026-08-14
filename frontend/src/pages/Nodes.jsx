import { nodeCategories } from '../data/nodeTypes'

export default function Nodes() {
  return (
    <div>
      <div style={{ marginBottom: '32px' }}>
        <h1 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '8px' }}>Node Marketplace</h1>
        <p style={{ color: 'var(--text-secondary)' }}>Explore all available nodes to use in your workflows.</p>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '48px' }}>
        {nodeCategories.map(section => (
          <div key={section.id}>
            <h2 style={{ fontSize: '18px', fontWeight: 600, marginBottom: '24px', borderBottom: '1px solid var(--border)', paddingBottom: '12px' }}>
              {section.label}
            </h2>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '20px' }}>
              {section.nodes.map(node => (
                <div key={node.type} className="card card-hover" style={{ padding: '20px', display: 'flex', gap: '16px', alignItems: 'flex-start' }}>
                  <div style={{ width: '40px', height: '40px', borderRadius: '10px', background: 'var(--bg-surface)', border: `1px solid ${node.color}`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, backgroundColor: `${node.color}15` }}>
                    <span style={{ fontSize: '18px', color: node.color, fontWeight: 700 }}>{node.label.charAt(0)}</span>
                  </div>
                  <div>
                    <h3 style={{ fontSize: '15px', fontWeight: 600, marginBottom: '4px' }}>{node.label}</h3>
                    <p style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.5 }}>{node.description}</p>
                    <span style={{ display: 'inline-block', marginTop: '8px', fontSize: '11px', fontWeight: 600, padding: '2px 8px', borderRadius: '6px', background: `${node.color}15`, color: node.color, border: `1px solid ${node.color}40` }}>
                      {node.type}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
