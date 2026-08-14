export default function StatsCard({ title, value, icon: Icon, color }) {
  return (
    <div className="card card-hover" style={{ padding: '20px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
        <div style={{ width: '40px', height: '40px', borderRadius: '10px', background: `${color}15`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Icon size={20} color={color} />
        </div>
      </div>
      <div style={{ fontSize: '28px', fontWeight: 700, fontFamily: 'Syne', marginBottom: '4px' }}>{value}</div>
      <div style={{ fontSize: '13px', color: 'var(--text-secondary)', fontWeight: 500 }}>{title}</div>
    </div>
  )
}
