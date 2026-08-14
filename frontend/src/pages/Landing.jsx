import { useState } from 'react'
import { Link } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Zap, ArrowRight, Brain, Webhook, MousePointerSquareDashed,
  Blocks, PenTool, GitPullRequest, Mail, FileText, CheckCircle2, Sparkles,
  ShieldCheck, Cpu, Database, Play, Layers, MessageSquare, ArrowUpRight
} from 'lucide-react'
import BackgroundWorkflowStream from '../components/layout/BackgroundWorkflowStream'

export default function Landing() {
  const [hoveredCardIndex, setHoveredCardIndex] = useState(2) // Default center card highlighted
  const [selectedCategory, setSelectedCategory] = useState('all')

  const features = [
    { icon: Brain, title: 'AI Decision Engine', category: 'ai', desc: 'Integrate LLMs directly into your flows to analyze text, make decisions, and route data.', color: '#a855f7' },
    { icon: Webhook, title: 'Event-Driven Triggers', category: 'triggers', desc: 'Trigger executions instantly via webhooks, cron schedules, or platform events.', color: '#06b6d4' },
    { icon: MousePointerSquareDashed, title: 'Drag & Drop Builder', category: 'core', desc: 'Visually construct pipelines with an intuitive canvas and rich node ecosystem.', color: '#6366f1' },
    { icon: Blocks, title: 'Multi-Service Integrations', category: 'integrations', desc: 'Connect easily to external APIs, Slack, GitHub, Gmail, Notion, and databases.', color: '#10b981' },
    { icon: PenTool, title: 'AI Summarizer Strategy', category: 'ai', desc: 'Summarize long text payloads and route branches dynamically using LLM evaluations.', color: '#f59e0b' },
    { icon: GitPullRequest, title: 'GitHub Automation', category: 'integrations', desc: 'Manage PRs, issues, and code reviews automatically with AI triggers.', color: '#ec4899' },
  ]

  const arcCards = [
    {
      id: '1',
      title: 'GitHub PR Reviewer',
      desc: 'Auto-analyze code diffs with GPT-4 & post review comments.',
      color: '#6366f1',
      icon: GitPullRequest,
      baseRotation: -18,
      translateX: -40,
      badge: 'DevOps'
    },
    {
      id: '2',
      title: 'AI Email Router',
      desc: 'Classify incoming customer support intent & dispatch to Slack.',
      color: '#f43f5e',
      icon: Mail,
      baseRotation: -9,
      translateX: -20,
      badge: 'Support'
    },
    {
      id: '5',
      title: 'AI Text Summarizer',
      desc: 'Extract nested payload fields & generate concise Gemini summaries.',
      color: '#f59e0b',
      icon: Zap,
      baseRotation: 0,
      translateX: 0,
      badge: 'Featured'
    },
    {
      id: '3',
      title: 'Resume Matcher',
      desc: 'Parse candidate profiles and score against job descriptions.',
      color: '#10b981',
      icon: FileText,
      baseRotation: 9,
      translateX: 20,
      badge: 'HR'
    },
    {
      id: '4',
      title: 'Trend Generator',
      desc: 'Fetch HackerNews topics, summarize trends, & log to Notion.',
      color: '#06b6d4',
      icon: Brain,
      baseRotation: 18,
      translateX: 40,
      badge: 'Content'
    },
  ]

  const filteredFeatures = selectedCategory === 'all' 
    ? features 
    : features.filter(f => f.category === selectedCategory)

  return (
    <div style={{ minHeight: '100vh', color: 'var(--text-primary)', background: 'transparent', position: 'relative', overflowX: 'hidden', fontFamily: 'DM Sans, sans-serif' }}>
      
      {/* Smooth Dark Mobile-UI Gradient Background */}
      <BackgroundWorkflowStream />

      {/* HEADER */}
      <header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 50,
          background: 'rgba(244, 247, 252, 0.85)',
          backdropFilter: 'blur(16px)',
          WebkitBackdropFilter: 'blur(16px)',
          borderBottom: '1px solid rgba(59, 130, 246, 0.15)',
        }}
      >
        <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '16px 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Link to="/" style={{ display: 'flex', alignItems: 'center', gap: '10px', textDecoration: 'none', color: 'var(--text-primary)' }}>
            <div style={{ width: '32px', height: '32px', background: 'linear-gradient(135deg, #2563eb, #7c3aed)', borderRadius: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Zap size={18} color="white" />
            </div>
            <span style={{ fontFamily: 'Syne, sans-serif', fontWeight: 800, fontSize: '20px' }}>AutoWorkflow</span>
          </Link>
          
          <nav style={{ display: 'flex', alignItems: 'center', gap: '32px' }}>
            {['Templates', 'Capabilities', 'Architecture'].map(item => (
              <a 
                key={item} 
                href={`#${item.toLowerCase()}`} 
                style={{ color: 'var(--text-secondary)', textDecoration: 'none', fontSize: '14px', fontWeight: 500, transition: 'color 0.2s' }} 
                onMouseOver={e => e.target.style.color = 'var(--text-primary)'} 
                onMouseOut={e => e.target.style.color = 'var(--text-secondary)'}
              >
                {item}
              </a>
            ))}
          </nav>

          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <Link to="/login" style={{ color: 'var(--text-primary)', textDecoration: 'none', fontSize: '14px', fontWeight: 600 }}>Login</Link>
            <Link to="/signup" className="btn-pill-black" style={{ textDecoration: 'none', padding: '9px 22px', fontSize: '14px' }}>
              Get Started
            </Link>
          </div>
        </div>
      </header>

      {/* OPAL-STYLE CENTERED HERO SECTION */}
      <section style={{ padding: '56px 24px 36px', maxWidth: '900px', margin: '0 auto', textAlign: 'center', position: 'relative' }}>
        
        {/* Floating Mini Prompt Pills */}
        <div style={{ display: 'flex', justifyContent: 'center', gap: '12px', marginBottom: '24px', flexWrap: 'wrap' }}>
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }} className="opal-pill-badge">
            <Sparkles size={14} color="#7c3aed" />
            <span>"Summarize PRs & alert Slack"</span>
          </motion.div>
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5, delay: 0.15 }} className="opal-pill-badge">
            <Brain size={14} color="#2563eb" />
            <span>"Classify Support Tickets"</span>
          </motion.div>
        </div>

        {/* Main Headline */}
        <motion.h1 
          initial={{ opacity: 0, y: 20 }} 
          animate={{ opacity: 1, y: 0 }} 
          transition={{ duration: 0.6 }}
          style={{ fontSize: '56px', fontWeight: 800, lineHeight: 1.12, marginBottom: '20px', letterSpacing: '-0.03em' }}
        >
          The visual AI <br />
          automation platform
        </motion.h1>

        {/* Subtitle */}
        <p style={{ fontSize: '18px', color: 'var(--text-secondary)', marginBottom: '32px', lineHeight: 1.6, maxWidth: '580px', margin: '0 auto 32px' }}>
          Connect any app, trigger, or AI model. Construct and manage automated LLM workflows visually, in code, or with a prompt.
        </p>

        {/* CTA Buttons */}
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
          <Link to="/signup" className="btn-pill-black" style={{ textDecoration: 'none', padding: '14px 36px', fontSize: '15px' }}>
            Get Started Free <ArrowRight size={16} />
          </Link>
          <Link to="/templates" style={{ color: 'var(--text-secondary)', fontSize: '14px', fontWeight: 600, textDecoration: 'none', padding: '12px 20px', borderRadius: '9999px', transition: 'color 0.2s' }}>
            Explore Templates →
          </Link>
        </div>
      </section>

      {/* HERO VIDEO SHOWCASE SECTION */}
      <section style={{ maxWidth: '1000px', margin: '0 auto 48px', padding: '0 24px', position: 'relative' }}>
        <motion.div
          initial={{ opacity: 0, y: 30, scale: 0.98 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.8, delay: 0.2 }}
          style={{
            position: 'relative',
            borderRadius: '28px',
            overflow: 'hidden',
            background: 'rgba(255, 255, 255, 0.9)',
            border: '1px solid rgba(59, 130, 246, 0.25)',
            boxShadow: '0 25px 60px rgba(37, 99, 235, 0.12), 0 0 35px rgba(59, 130, 246, 0.1)',
            padding: '12px',
            backdropFilter: 'blur(16px)',
          }}
        >
          <div style={{ position: 'relative', borderRadius: '20px', overflow: 'hidden', background: '#0f172a' }}>
            <video
              src="/videos/robot.mp4"
              autoPlay
              loop
              muted
              playsInline
              style={{
                width: '100%',
                maxHeight: '520px',
                objectFit: 'cover',
                borderRadius: '20px',
                display: 'block',
              }}
            />
          </div>
        </motion.div>
      </section>

      {/* OPAL RADIAL ARC GALLERY CONTAINER SECTION */}
      <section id="templates" style={{ maxWidth: '1140px', margin: '0 auto 24px', padding: '0 24px' }}>
        <div className="opal-card-container" style={{ padding: '48px 32px', textAlign: 'center', position: 'relative', overflow: 'hidden' }}>
          
          <h2 style={{ fontSize: '32px', fontWeight: 800, marginBottom: '12px', letterSpacing: '-0.02em' }}>
            Start from scratch or explore our gallery <br /> for instant inspiration
          </h2>
          <p style={{ fontSize: '15px', color: 'var(--text-secondary)', marginBottom: '40px' }}>
            Hover over any template card below to inspect its workflow structure.
          </p>

          {/* Radial Arc Fan of Cards */}
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'flex-end', minHeight: '340px', paddingBottom: '24px', position: 'relative' }}>
            {arcCards.map((card, index) => {
              const isHovered = hoveredCardIndex === index
              const Icon = card.icon

              return (
                <motion.div
                  key={card.id}
                  onMouseEnter={() => setHoveredCardIndex(index)}
                  animate={{
                    rotate: isHovered ? 0 : card.baseRotation,
                    y: isHovered ? -24 : 0,
                    scale: isHovered ? 1.08 : 1,
                    zIndex: isHovered ? 20 : 10 - Math.abs(index - 2),
                  }}
                  transition={{ type: 'spring', stiffness: 300, damping: 25 }}
                  style={{
                    width: '210px',
                    height: '270px',
                    background: 'rgba(255, 255, 255, 0.95)',
                    border: `1px solid ${isHovered ? card.color : 'rgba(59, 130, 246, 0.18)'}`,
                    borderRadius: '24px',
                    padding: '20px',
                    margin: '0 -18px',
                    boxShadow: isHovered 
                      ? `0 20px 40px rgba(37, 99, 235, 0.15), 0 0 30px ${card.color}33` 
                      : '0 10px 30px rgba(37, 99, 235, 0.06)',
                    cursor: 'pointer',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                    textAlign: 'left',
                    transformOrigin: 'bottom center',
                    backdropFilter: 'blur(16px)',
                  }}
                >
                  <div>
                    {/* Top Icon + Badge */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                      <div style={{ width: '38px', height: '38px', borderRadius: '12px', background: `${card.color}22`, border: `1px solid ${card.color}44`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <Icon size={20} color={card.color} />
                      </div>
                      <span style={{ fontSize: '10px', fontWeight: 700, padding: '2px 8px', borderRadius: '99px', background: `${card.color}22`, color: card.color }}>
                        {card.badge}
                      </span>
                    </div>

                    <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '8px', color: 'var(--text-primary)' }}>
                      {card.title}
                    </h3>
                    <p style={{ fontSize: '12px', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                      {card.desc}
                    </p>
                  </div>

                  {/* Launch button on card bottom */}
                  <Link
                    to={`/builder/new?template=${card.id}`}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '6px',
                      fontSize: '13px',
                      fontWeight: 600,
                      color: card.color,
                      textDecoration: 'none',
                    }}
                  >
                    Launch <ArrowUpRight size={14} />
                  </Link>
                </motion.div>
              )
            })}
          </div>

          {/* Bottom Container Pill Button */}
          <div style={{ marginTop: '16px' }}>
            <Link to="/templates" className="btn-pill-black" style={{ textDecoration: 'none', padding: '12px 32px', fontSize: '15px' }}>
              Explore All Templates <ArrowRight size={16} />
            </Link>
          </div>

        </div>
      </section>

      {/* CAPABILITIES CONTAINER SECTION */}
      <section id="capabilities" style={{ maxWidth: '1140px', margin: '0 auto 24px', padding: '0 24px' }}>
        <div className="opal-card-container" style={{ padding: '40px 32px' }}>
          <div style={{ textAlign: 'center', marginBottom: '32px' }}>
            <h2 style={{ fontSize: '32px', fontWeight: 800, marginBottom: '12px', letterSpacing: '-0.02em' }}>
              Capabilities & Integrations
            </h2>
            <p style={{ fontSize: '15px', color: 'var(--text-secondary)' }}>
              Everything required to build, execute, and monitor event-driven AI flows.
            </p>

            {/* Category Filter Pills */}
            <div style={{ display: 'inline-flex', gap: '8px', padding: '6px', background: 'rgba(255,255,255,0.03)', borderRadius: '9999px', border: '1px solid var(--border)', marginTop: '20px' }}>
              {[
                { id: 'all', label: 'All Capabilities' },
                { id: 'ai', label: 'AI Layer' },
                { id: 'triggers', label: 'Triggers' },
                { id: 'integrations', label: 'Integrations' },
              ].map(tab => (
                <button
                  key={tab.id}
                  onClick={() => setSelectedCategory(tab.id)}
                  style={{
                    padding: '6px 18px',
                    borderRadius: '9999px',
                    border: 'none',
                    background: selectedCategory === tab.id ? '#0f172a' : 'transparent',
                    color: selectedCategory === tab.id ? '#f8fafc' : 'var(--text-secondary)',
                    fontWeight: 600,
                    fontSize: '13px',
                    cursor: 'pointer',
                    transition: 'all 0.2s ease',
                    border: selectedCategory === tab.id ? '1px solid rgba(255,255,255,0.2)' : 'none',
                  }}
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </div>

          {/* Feature Cards Grid */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
            <AnimatePresence mode="popLayout">
              {filteredFeatures.map((f) => (
                <motion.div 
                  key={f.title}
                  layout
                  initial={{ opacity: 0, scale: 0.96 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.96 }}
                  transition={{ duration: 0.25 }}
                  style={{
                    background: 'rgba(255, 255, 255, 0.8)',
                    border: '1px solid rgba(59, 130, 246, 0.15)',
                    borderRadius: '20px',
                    padding: '24px',
                    backdropFilter: 'blur(12px)',
                    boxShadow: '0 4px 20px rgba(37, 99, 235, 0.05)',
                  }}
                >
                  <div style={{ width: '40px', height: '40px', borderRadius: '12px', background: `${f.color}15`, border: `1px solid ${f.color}30`, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '16px' }}>
                    <f.icon size={20} color={f.color} />
                  </div>
                  <h3 style={{ fontSize: '17px', fontWeight: 700, marginBottom: '6px' }}>{f.title}</h3>
                  <p style={{ color: 'var(--text-secondary)', fontSize: '13px', lineHeight: 1.5 }}>{f.desc}</p>
                </motion.div>
              ))}
            </AnimatePresence>
          </div>
        </div>
      </section>

      {/* DECOUPLED ARCHITECTURE CONTAINER SECTION */}
      <section id="architecture" style={{ maxWidth: '1140px', margin: '0 auto 24px', padding: '0 24px' }}>
        <div className="opal-card-container" style={{ padding: '40px 32px' }}>
          <div style={{ textAlign: 'center', marginBottom: '28px' }}>
            <h2 style={{ fontSize: '32px', fontWeight: 800, marginBottom: '8px', letterSpacing: '-0.02em' }}>
              Decoupled Architecture
            </h2>
            <p style={{ fontSize: '15px', color: 'var(--text-secondary)', maxWidth: '580px', margin: '0 auto' }}>
              Spring Boot 3 + PostgreSQL engine with an isolated LLM execution layer and visual React Flow canvas.
            </p>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px' }}>
            <div style={{ background: 'rgba(255, 255, 255, 0.8)', border: '1px solid rgba(59, 130, 246, 0.15)', padding: '20px', borderRadius: '16px', boxShadow: '0 4px 16px rgba(37, 99, 235, 0.04)' }}>
              <div style={{ width: '34px', height: '34px', borderRadius: '10px', background: '#2563eb22', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '10px' }}>
                <Layers size={18} color="#2563eb" />
              </div>
              <h4 style={{ fontWeight: 700, fontSize: '15px', marginBottom: '4px' }}>Visual React Canvas</h4>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>Drag-and-drop node placement & step logs.</p>
            </div>

            <div style={{ background: 'rgba(255, 255, 255, 0.8)', border: '1px solid rgba(59, 130, 246, 0.15)', padding: '20px', borderRadius: '16px', boxShadow: '0 4px 16px rgba(37, 99, 235, 0.04)' }}>
              <div style={{ width: '34px', height: '34px', borderRadius: '10px', background: '#7c3aed22', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '10px' }}>
                <Brain size={18} color="#7c3aed" />
              </div>
              <h4 style={{ fontWeight: 700, fontSize: '15px', marginBottom: '4px' }}>Unified AI Engine</h4>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>Standardized PayloadTextResolver with Gemini & OpenAI.</p>
            </div>

            <div style={{ background: 'rgba(255, 255, 255, 0.8)', border: '1px solid rgba(59, 130, 246, 0.15)', padding: '20px', borderRadius: '16px', boxShadow: '0 4px 16px rgba(37, 99, 235, 0.04)' }}>
              <div style={{ width: '34px', height: '34px', borderRadius: '10px', background: '#05966922', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '10px' }}>
                <Cpu size={18} color="#059669" />
              </div>
              <h4 style={{ fontWeight: 700, fontSize: '15px', marginBottom: '4px' }}>DAG Engine</h4>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>Liveness propagation & DAG cycle validation.</p>
            </div>

            <div style={{ background: 'rgba(255, 255, 255, 0.8)', border: '1px solid rgba(59, 130, 246, 0.15)', padding: '20px', borderRadius: '16px', boxShadow: '0 4px 16px rgba(37, 99, 235, 0.04)' }}>
              <div style={{ width: '34px', height: '34px', borderRadius: '10px', background: '#0284c722', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '10px' }}>
                <Database size={18} color="#0284c7" />
              </div>
              <h4 style={{ fontWeight: 700, fontSize: '15px', marginBottom: '4px' }}>PostgreSQL Store</h4>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>Authoritative persistence & run history.</p>
            </div>
          </div>
        </div>
      </section>

      {/* HOW IT WORKS CONTAINER SECTION */}
      <section style={{ maxWidth: '1140px', margin: '0 auto 24px', padding: '0 24px' }}>
        <div className="opal-card-container" style={{ padding: '40px 32px' }}>
          <div style={{ textAlign: 'center', marginBottom: '28px' }}>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '4px 12px', borderRadius: '99px', background: 'rgba(37, 99, 235, 0.1)', border: '1px solid rgba(37, 99, 235, 0.25)', color: '#2563eb', fontSize: '12px', fontWeight: 600, marginBottom: '10px' }}>
              <Sparkles size={13} />
              <span>3-Step Pipeline Execution</span>
            </div>
            <h2 style={{ fontSize: '32px', fontWeight: 800, marginBottom: '8px', letterSpacing: '-0.02em' }}>
              How AutoWorkflow Automates Your Tasks
            </h2>
            <p style={{ fontSize: '15px', color: 'var(--text-secondary)', maxWidth: '560px', margin: '0 auto' }}>
              From incoming webhooks to LLM intelligence and database audit trails in seconds.
            </p>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '16px' }}>
            {/* Step 1 */}
            <div style={{ background: 'rgba(255, 255, 255, 0.8)', border: '1px solid rgba(59, 130, 246, 0.15)', padding: '24px 20px', borderRadius: '20px', boxShadow: '0 4px 16px rgba(37, 99, 235, 0.04)' }}>
              <div style={{ fontSize: '11px', fontWeight: 800, letterSpacing: '0.1em', color: '#2563eb', textTransform: 'uppercase', marginBottom: '10px' }}>
                Step 01
              </div>
              <div style={{ width: '38px', height: '38px', borderRadius: '10px', background: 'rgba(37, 99, 235, 0.12)', border: '1px solid rgba(37, 99, 235, 0.25)', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '12px' }}>
                <Webhook size={18} color="#2563eb" />
              </div>
              <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '6px' }}>Connect & Trigger</h3>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                Drop Webhook or Cron nodes onto the canvas to instantly capture events from GitHub, Slack, or custom apps.
              </p>
            </div>

            {/* Step 2 */}
            <div style={{ background: 'rgba(255, 255, 255, 0.8)', border: '1px solid rgba(59, 130, 246, 0.15)', padding: '24px 20px', borderRadius: '20px', boxShadow: '0 4px 16px rgba(37, 99, 235, 0.04)' }}>
              <div style={{ fontSize: '11px', fontWeight: 800, letterSpacing: '0.1em', color: '#7c3aed', textTransform: 'uppercase', marginBottom: '10px' }}>
                Step 02
              </div>
              <div style={{ width: '38px', height: '38px', borderRadius: '10px', background: 'rgba(124, 58, 237, 0.12)', border: '1px solid rgba(124, 58, 237, 0.25)', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '12px' }}>
                <Brain size={18} color="#7c3aed" />
              </div>
              <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '6px' }}>Resolve & Analyze</h3>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                PayloadTextResolver parses incoming text streams and feeds them into Gemini LLMs to extract insights automatically.
              </p>
            </div>

            {/* Step 3 */}
            <div style={{ background: 'rgba(255, 255, 255, 0.8)', border: '1px solid rgba(59, 130, 246, 0.15)', padding: '24px 20px', borderRadius: '20px', boxShadow: '0 4px 16px rgba(37, 99, 235, 0.04)' }}>
              <div style={{ fontSize: '11px', fontWeight: 800, letterSpacing: '0.1em', color: '#059669', textTransform: 'uppercase', marginBottom: '10px' }}>
                Step 03
              </div>
              <div style={{ width: '38px', height: '38px', borderRadius: '10px', background: 'rgba(5, 150, 105, 0.12)', border: '1px solid rgba(5, 150, 105, 0.25)', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '12px' }}>
                <CheckCircle2 size={18} color="#059669" />
              </div>
              <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '6px' }}>Dispatch & Audit</h3>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                Route responses to external APIs, trigger downstream tasks, and log complete execution traces to PostgreSQL.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* OPAL CTA CARD SECTION (Matches Opal "Join our Discord for support" card) */}
      <section style={{ maxWidth: '1140px', margin: '0 auto 32px', padding: '0 24px' }}>
        <div 
          className="opal-card-container" 
          style={{ 
            padding: '48px 32px', 
            borderRadius: '40px', 
            textAlign: 'center', 
            position: 'relative', 
            overflow: 'hidden',
          }}
        >
          <div style={{ position: 'relative', zIndex: 10, maxWidth: '600px', margin: '0 auto' }}>
            <h2 style={{ fontSize: '36px', fontWeight: 800, lineHeight: 1.2, marginBottom: '16px', letterSpacing: '-0.02em' }}>
              Build your first AI workflow <br /> in minutes
            </h2>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '14px', flexWrap: 'wrap', marginTop: '24px' }}>
              <Link to="/signup" className="btn-pill-black" style={{ fontSize: '14px', padding: '12px 32px' }}>
                Get Started Free <ArrowRight size={15} />
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* GOOGLE OPAL ROUNDED PILL FOOTER CONTAINER CARD */}
      <footer style={{ maxWidth: '1140px', margin: '0 auto 40px', padding: '0 24px' }}>
        <div className="opal-card-container" style={{ padding: '24px 36px', borderRadius: '40px' }}>
          
          {/* Top Row: Links */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '24px', fontSize: '13px', color: 'var(--text-secondary)', flexWrap: 'wrap', marginBottom: '16px' }}>
            <Link to="/workflows" style={{ color: 'var(--text-secondary)', textDecoration: 'none', fontWeight: 500 }}>Visual Builder</Link>
            <Link to="/templates" style={{ color: 'var(--text-secondary)', textDecoration: 'none', fontWeight: 500 }}>Templates</Link>
            <a href="#architecture" style={{ color: 'var(--text-secondary)', textDecoration: 'none', fontWeight: 500 }}>Architecture</a>
            <Link to="/executions" style={{ color: 'var(--text-secondary)', textDecoration: 'none', fontWeight: 500 }}>Execution Logs</Link>
          </div>

          {/* Horizontal Line (Matching Google Opal Footer Line) */}
          <div style={{ height: '1px', background: 'rgba(59, 130, 246, 0.15)', width: '100%', marginBottom: '16px' }} />

          {/* Bottom Row: Logo + Links + Help */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '20px', flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <div style={{ width: '24px', height: '24px', background: '#6366f1', borderRadius: '6px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Zap size={14} color="white" />
                </div>
                <span style={{ fontFamily: 'Syne', fontWeight: 800, fontSize: '16px', color: 'var(--text-primary)' }}>AutoWorkflow</span>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '16px', fontSize: '12px', color: 'var(--text-muted)' }}>
                <span>© 2026 AutoWorkflow</span>
                <span style={{ cursor: 'pointer' }}>Privacy</span>
                <span style={{ cursor: 'pointer' }}>Terms</span>
                <span style={{ cursor: 'pointer' }}>Security</span>
              </div>
            </div>

            <div style={{ fontSize: '12px', color: 'var(--text-secondary)', fontWeight: 500, cursor: 'pointer' }}>
              Help & Support
            </div>
          </div>

        </div>
      </footer>

    </div>
  )
}
