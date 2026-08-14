import { motion, useScroll, useTransform } from 'framer-motion'

export default function BackgroundWorkflowStream() {
  const { scrollYProgress } = useScroll()
  
  // Smooth subtle shift of ambient gradient depth as the user scrolls
  const yShift = useTransform(scrollYProgress, [0, 1], [0, 80])

  return (
    <div 
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        width: '100vw',
        height: '100vh',
        pointerEvents: 'none',
        zIndex: 0,
        overflow: 'hidden',
        background: 'linear-gradient(180deg, #f4f7fc 0%, #edf3fc 35%, #e8f0fe 70%, #f0f4f9 100%)',
      }}
    >
      {/* Soft Diffused Ambient Glow 1 — Top Left Ice Blue */}
      <motion.div 
        style={{ 
          position: 'absolute', 
          top: '-10%', 
          left: '-5%', 
          width: '65vw', 
          height: '650px', 
          borderRadius: '50%', 
          background: 'radial-gradient(circle, rgba(191, 219, 254, 0.6) 0%, rgba(219, 234, 254, 0.35) 50%, transparent 80%)', 
          filter: 'blur(120px)',
          y: yShift,
        }} 
      />

      {/* Soft Diffused Ambient Glow 2 — Mid Right Soft Violet Blue */}
      <motion.div 
        style={{ 
          position: 'absolute', 
          top: '25%', 
          right: '-10%', 
          width: '60vw', 
          height: '600px', 
          borderRadius: '50%', 
          background: 'radial-gradient(circle, rgba(199, 210, 254, 0.5) 0%, rgba(224, 231, 255, 0.25) 60%, transparent 80%)', 
          filter: 'blur(130px)',
        }} 
      />

      {/* Soft Diffused Ambient Glow 3 — Bottom Center Crisp Sky Blue */}
      <motion.div 
        style={{ 
          position: 'absolute', 
          bottom: '-15%', 
          left: '20%', 
          width: '60vw', 
          height: '550px', 
          borderRadius: '50%', 
          background: 'radial-gradient(circle, rgba(224, 242, 254, 0.7) 0%, rgba(240, 249, 255, 0.4) 70%, transparent 90%)', 
          filter: 'blur(100px)',
        }} 
      />

      {/* Ultra-subtle grid overlay */}
      <div 
        style={{
          position: 'absolute',
          inset: 0,
          opacity: 0.03,
          backgroundImage: 'radial-gradient(#3b82f6 1px, transparent 0)',
          backgroundSize: '24px 24px',
        }}
      />
    </div>
  )
}
