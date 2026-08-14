import { useState } from 'react'
import { Brain, Send, Bot, User, Code } from 'lucide-react'

export default function Assistant() {
  const [messages, setMessages] = useState([
    { role: 'assistant', text: 'Hello! I am AutoWorkflow Assistant. Describe the workflow you want to build, and I will generate the JSON for you.' }
  ])
  const [input, setInput] = useState('')

  const handleSend = (e) => {
    e.preventDefault()
    if (!input.trim()) return

    setMessages([...messages, { role: 'user', text: input }])
    setInput('')

    // Mock AI response
    setTimeout(() => {
      setMessages(prev => [...prev, { 
        role: 'assistant', 
        text: 'I\'ve generated a workflow based on your request. Here is the configuration:',
        isCode: true,
        code: JSON.stringify({
          nodes: [
            { type: "trigger", label: "GitHub PR" },
            { type: "ai", label: "Review Code" }
          ]
        }, null, 2)
      }])
    }, 1000)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 100px)' }}>
      <div style={{ marginBottom: '24px' }}>
        <h1 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Brain color="var(--accent-violet)" /> AI Assistant
        </h1>
        <p style={{ color: 'var(--text-secondary)' }}>Describe your automation needs in plain English.</p>
      </div>

      <div className="card" style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <div style={{ flex: 1, overflowY: 'auto', padding: '24px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
          {messages.map((msg, i) => (
            <div key={i} style={{ display: 'flex', gap: '16px', flexDirection: msg.role === 'user' ? 'row-reverse' : 'row' }}>
              <div style={{ width: '36px', height: '36px', borderRadius: '50%', background: msg.role === 'user' ? 'linear-gradient(135deg, #6366f1, #7c3aed)' : 'var(--bg-surface)', border: msg.role === 'assistant' ? '1px solid var(--border)' : 'none', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                {msg.role === 'user' ? <User size={18} color="white" /> : <Bot size={18} color="var(--accent-violet)" />}
              </div>
              
              <div style={{ maxWidth: '70%', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <div style={{ background: msg.role === 'user' ? 'var(--accent)' : 'var(--bg-surface)', border: msg.role === 'assistant' ? '1px solid var(--border)' : 'none', padding: '16px', borderRadius: '16px', borderTopLeftRadius: msg.role === 'assistant' ? 0 : '16px', borderTopRightRadius: msg.role === 'user' ? 0 : '16px', color: msg.role === 'user' ? 'white' : 'var(--text-primary)', fontSize: '14px', lineHeight: 1.6 }}>
                  {msg.text}
                </div>
                
                {msg.isCode && (
                  <div style={{ background: 'var(--bg-base)', border: '1px solid var(--border)', borderRadius: '12px', overflow: 'hidden' }}>
                    <div style={{ padding: '8px 12px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', color: 'var(--text-muted)' }}>
                      <Code size={14} /> workflow.json
                    </div>
                    <pre style={{ padding: '16px', margin: 0, fontSize: '13px', overflowX: 'auto', color: 'var(--accent-cyan)' }}>
                      {msg.code}
                    </pre>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>

        <div style={{ padding: '24px', borderTop: '1px solid var(--border)', background: 'var(--bg-surface)' }}>
          <form onSubmit={handleSend} style={{ display: 'flex', gap: '12px' }}>
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Create a workflow that reads my Gmail and posts summaries to Slack..."
              style={{ flex: 1, background: 'var(--bg-input)', border: '1px solid var(--border)', padding: '16px 20px', borderRadius: '12px', color: 'var(--text-primary)', fontSize: '14px', outline: 'none' }}
            />
            <button type="submit" className="btn-primary" style={{ padding: '0 24px', borderRadius: '12px' }}>
              <Send size={18} />
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
