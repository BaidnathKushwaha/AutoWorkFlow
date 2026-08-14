import { Zap, ShieldCheck, Lock } from 'lucide-react'
import MakeWorkflowMotion from './MakeWorkflowMotion'
import { Link } from 'react-router-dom'

export default function AuthGraphics() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between', padding: '48px 56px', position: 'relative', zIndex: 10 }}>
      <div>
        {/* Logo */}
        <Link to="/" style={{ display: 'inline-flex', alignItems: 'center', gap: '10px', textDecoration: 'none', marginBottom: '40px' }}>
          <div style={{ width: '36px', height: '36px', background: 'linear-gradient(135deg, #6366f1, #7c3aed)', borderRadius: '10px', display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 4px 14px rgba(99, 102, 241, 0.35)' }}>
            <Zap size={20} color="white" />
          </div>
          <span style={{ fontFamily: 'Syne, sans-serif', fontWeight: 800, fontSize: '22px', color: '#0f172a', letterSpacing: '-0.02em' }}>AutoWorkflow</span>
        </Link>

        {/* Hero Title */}
        <div style={{ marginBottom: '32px' }}>
          <h1 style={{ fontSize: '48px', fontWeight: 800, lineHeight: 1.15, letterSpacing: '-0.03em', color: '#0f172a', marginBottom: '16px', fontFamily: 'Syne, sans-serif' }}>
            The visual{' '}
            <span style={{ background: 'linear-gradient(135deg, #7c3aed 0%, #6366f1 100%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
              AI
            </span>
            <br />
            automation{' '}
            <span style={{ background: 'linear-gradient(135deg, #6366f1 0%, #3b82f6 100%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
              platform
            </span>
          </h1>
          <p style={{ fontSize: '15px', color: '#64748b', lineHeight: 1.6, maxWidth: '440px' }}>
            Connect any app, trigger, or AI model. Build and manage powerful workflows visually.
          </p>
        </div>

        {/* Live Workflow Pipeline Card */}
        <div 
          style={{ 
            background: 'rgba(255, 255, 255, 0.75)', 
            backdropFilter: 'blur(20px)', 
            WebkitBackdropFilter: 'blur(20px)',
            border: '1px solid rgba(255, 255, 255, 0.9)', 
            borderRadius: '24px', 
            padding: '20px 24px', 
            boxShadow: '0 10px 30px rgba(99, 102, 241, 0.05), 0 2px 8px rgba(0, 0, 0, 0.02)', 
            marginBottom: '32px' 
          }}
        >
          <MakeWorkflowMotion />
        </div>
      </div>

      {/* 3 Bottom Feature Badges */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px' }}>
        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255, 255, 255, 0.75)', border: '1px solid rgba(255, 255, 255, 0.9)', display: 'flex', alignItems: 'center', gap: '10px', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
          <ShieldCheck size={20} color="#10b981" style={{ flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: '12px', color: '#0f172a' }}>SOC 2 Compliant</div>
            <div style={{ fontSize: '11px', color: '#64748b' }}>Enterprise Security</div>
          </div>
        </div>

        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255, 255, 255, 0.75)', border: '1px solid rgba(255, 255, 255, 0.9)', display: 'flex', alignItems: 'center', gap: '10px', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
          <Zap size={20} color="#f59e0b" style={{ flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: '12px', color: '#0f172a' }}>Real-time Execution</div>
            <div style={{ fontSize: '11px', color: '#64748b' }}>Sub-50ms Latency</div>
          </div>
        </div>

        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255, 255, 255, 0.75)', border: '1px solid rgba(255, 255, 255, 0.9)', display: 'flex', alignItems: 'center', gap: '10px', boxShadow: '0 2px 8px rgba(0,0,0,0.02)' }}>
          <Lock size={20} color="#3b82f6" style={{ flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: '12px', color: '#0f172a' }}>Scalable & Reliable</div>
            <div style={{ fontSize: '11px', color: '#64748b' }}>Built for Scale</div>
          </div>
        </div>
      </div>
    </div>
  )
}
