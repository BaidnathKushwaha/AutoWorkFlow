import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Zap, Database, Brain, Send, CheckCircle2 } from 'lucide-react'

export default function MakeWorkflowMotion() {
  const [activeNode, setActiveNode] = useState(3)

  const nodes = [
    { id: 1, name: 'Trigger', icon: Zap, x: 45, y: 110, isSquare: true, color: '#6366f1', bg: 'linear-gradient(135deg, #e0e7ff 0%, #c7d2fe 100%)', glow: 'rgba(99, 102, 241, 0.3)', iconColor: '#4f46e5' },
    { id: 2, name: 'Data', icon: Database, x: 125, y: 75, isSquare: false, color: '#a855f7', bg: 'linear-gradient(135deg, #f3e8ff 0%, #e9d5ff 100%)', glow: 'rgba(168, 85, 247, 0.3)', iconColor: '#9333ea' },
    { id: 3, name: 'AI Process', icon: Brain, x: 205, y: 50, isSquare: false, color: '#3b82f6', bg: 'linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%)', glow: 'rgba(59, 130, 246, 0.35)', iconColor: '#2563eb' },
    { id: 4, name: 'Action', icon: Send, x: 285, y: 75, isSquare: false, color: '#06b6d4', bg: 'linear-gradient(135deg, #cffafe 0%, #a5f3fc 100%)', glow: 'rgba(6, 182, 212, 0.3)', iconColor: '#0891b2' },
    { id: 5, name: 'Complete', icon: CheckCircle2, x: 365, y: 110, isSquare: false, color: '#10b981', bg: 'linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%)', glow: 'rgba(16, 185, 129, 0.35)', iconColor: '#059669' },
  ]

  useEffect(() => {
    const interval = setInterval(() => {
      setActiveNode((prev) => (prev % 5) + 1)
    }, 2200)
    return () => clearInterval(interval)
  }, [])

  return (
    <div style={{ width: '100%', height: '180px', position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'transparent' }}>
      <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none' }} viewBox="0 0 410 180">
        <defs>
          <linearGradient id="arcWireGradient" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#818cf8" stopOpacity="0.4" />
            <stop offset="35%" stopColor="#c084fc" stopOpacity="0.5" />
            <stop offset="65%" stopColor="#60a5fa" stopOpacity="0.5" />
            <stop offset="100%" stopColor="#34d399" stopOpacity="0.6" />
          </linearGradient>
          <linearGradient id="activeWireGradient" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#6366f1" />
            <stop offset="50%" stopColor="#a855f7" />
            <stop offset="100%" stopColor="#10b981" />
          </linearGradient>
        </defs>
        <path d="M 45 110 C 100 65, 140 50, 205 50 C 270 50, 310 65, 365 110" fill="none" stroke="url(#arcWireGradient)" strokeWidth="3.5" strokeLinecap="round" />
        <path d="M 45 110 C 100 65, 140 50, 205 50 C 270 50, 310 65, 365 110" fill="none" stroke="url(#activeWireGradient)" strokeWidth="3.5" strokeDasharray="20 180" className="traveling-edge-pulse-1" strokeLinecap="round" />
      </svg>

      {nodes.map((node) => {
        const isActive = activeNode === node.id
        const Icon = node.icon
        return (
          <div key={node.id} style={{ position: 'absolute', left: `calc(50% + ${node.x - 205}px)`, top: `calc(50% + ${node.y - 90}px)`, transform: 'translate(-50%, -50%)', display: 'flex', flexDirection: 'column', alignItems: 'center', zIndex: isActive ? 30 : 20 }}>
            <motion.div
              animate={{ scale: isActive ? 1.15 : 1, y: isActive ? -3 : 0 }}
              transition={{ type: 'spring', stiffness: 300, damping: 20 }}
              style={{ width: node.isSquare ? '48px' : '44px', height: node.isSquare ? '48px' : '44px', borderRadius: node.isSquare ? '14px' : '50%', background: node.bg, border: `2px solid ${isActive ? node.color : 'rgba(255, 255, 255, 0.9)'}`, boxShadow: isActive ? `0 0 24px ${node.glow}, 0 6px 16px rgba(0,0,0,0.08)` : `0 4px 12px ${node.glow}`, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', transition: 'border 0.2s, box-shadow 0.2s' }}
            >
              <Icon size={node.isSquare ? 22 : 20} color={node.iconColor} />
            </motion.div>
            <span style={{ fontSize: '11px', fontWeight: isActive ? 700 : 500, color: isActive ? node.iconColor : '#64748b', marginTop: '6px', whiteSpace: 'nowrap', transition: 'color 0.2s' }}>
              {node.name}
            </span>
          </div>
        )
      })}
    </div>
  )
}
