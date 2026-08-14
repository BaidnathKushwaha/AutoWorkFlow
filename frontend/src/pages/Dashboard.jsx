import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import {
  Activity, Play, CheckCircle, XCircle, Brain,
  Plus, Upload, GitPullRequest, MessageSquare, MoreVertical
} from 'lucide-react'
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts'
import { useWorkflowStore } from '../store/workflowStore'
import { useExecutionStore } from '../store/executionStore'
import executionService from '../services/execution/executionService'

export default function Dashboard() {
  const { workflows } = useWorkflowStore()
  const { executions, fetchExecutions } = useExecutionStore()
  const [telemetry, setTelemetry] = useState(null)

  useEffect(() => {
    fetchExecutions()
    
    // Fetch backend dashboard stats if available
    executionService.getDashboardStats()
      .then(res => {
        if (res?.data?.data) {
          setTelemetry(res.data.data)
        }
      })
      .catch(() => {
        // Fall back gracefully if endpoint is unauthenticated or loading
      })
  }, [fetchExecutions])

  // Compute dynamic metrics combining backend telemetry and store state
  const totalWorkflows = telemetry?.totalWorkflows ?? workflows.length
  const activeWorkflows = telemetry?.activeWorkflows ?? workflows.filter(w => w.status === 'active').length
  
  const realTotalExecutions = telemetry?.totalExecutions ?? (
    executions.length > 0 ? executions.length : workflows.reduce((sum, w) => sum + (w.executions || 0), 0)
  )

  const failedRuns = telemetry?.failedRuns ?? executions.filter(e => e.status === 'failed' || e.status === 'FAILED').length

  const aiRequests = telemetry?.aiRequests ?? (
    executions.reduce((acc, e) => acc + (e.stepsLogs ? e.stepsLogs.filter(s => (s.nodeName || '').toLowerCase().includes('gpt') || (s.nodeName || '').toLowerCase().includes('ai')).length : 1), 0)
  )

  const statsData = [
    { title: 'Total Workflows', value: totalWorkflows.toString(), icon: Activity, color: 'var(--accent)' },
    { title: 'Active Workflows', value: activeWorkflows.toString(), icon: Play, color: 'var(--accent-emerald)' },
    { title: 'Total Executions', value: realTotalExecutions.toLocaleString(), icon: CheckCircle, color: 'var(--accent-cyan)' },
    { title: 'Failed Runs', value: failedRuns.toString(), icon: XCircle, color: 'var(--accent-rose)' },
    { title: 'AI Requests', value: aiRequests > 1000 ? `${(aiRequests / 1000).toFixed(1)}k` : aiRequests.toString(), icon: Brain, color: 'var(--accent-violet)' },
  ]

  // Construct chart data dynamically based on actual execution history by day of week
  const daysOfWeek = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
  const chartData = daysOfWeek.map((dayName) => {
    const dayExecs = executions.filter((e) => {
      if (e.timestamp && typeof e.timestamp === 'string' && e.timestamp.toLowerCase().includes(dayName.toLowerCase())) return true
      if (e.startedAt) {
        const d = new Date(e.startedAt)
        if (!isNaN(d.getTime())) {
          const dayIdx = (d.getDay() + 6) % 7 // Mon = 0
          return daysOfWeek[dayIdx] === dayName
        }
      }
      return false
    })

    return {
      name: dayName,
      executions: dayExecs.length,
      success: dayExecs.length,
    }
  })

  // Get the 4 most recently active workflows
  const recentWorkflows = workflows.slice(0, 4)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '4px' }}>Dashboard</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>Welcome back. Real-time overview of your workflow automation telemetry.</p>
        </div>
      </div>

      {/* STATS SECTION */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px' }}>
        {statsData.map((stat, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.1 }}
            className="card card-hover"
            style={{ padding: '20px' }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
              <div style={{ width: '40px', height: '40px', borderRadius: '10px', background: `${stat.color}15`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <stat.icon size={20} color={stat.color} />
              </div>
            </div>
            <div style={{ fontSize: '28px', fontWeight: 700, fontFamily: 'Syne', marginBottom: '4px' }}>{stat.value}</div>
            <div style={{ fontSize: '13px', color: 'var(--text-secondary)', fontWeight: 500 }}>{stat.title}</div>
          </motion.div>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '24px' }}>
        {/* EXECUTION GRAPH */}
        <div className="card" style={{ padding: '24px', display: 'flex', flexDirection: 'column' }}>
          <h2 style={{ fontSize: '16px', fontWeight: 600, marginBottom: '24px' }}>Execution Overview</h2>
          <div style={{ flex: 1, minHeight: '300px' }}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData} margin={{ top: 0, right: 0, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorSuccess" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="var(--accent)" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="var(--accent)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border-subtle)" vertical={false} />
                <XAxis dataKey="name" stroke="var(--text-muted)" fontSize={12} tickLine={false} axisLine={false} />
                <YAxis stroke="var(--text-muted)" fontSize={12} tickLine={false} axisLine={false} />
                <Tooltip
                  contentStyle={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: '8px' }}
                  itemStyle={{ color: 'var(--text-primary)', fontSize: '13px' }}
                />
                <Area type="monotone" dataKey="executions" name="Executions" stroke="var(--accent)" fillOpacity={1} fill="url(#colorSuccess)" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* QUICK ACTIONS */}
        <div className="card" style={{ padding: '24px' }}>
          <h2 style={{ fontSize: '16px', fontWeight: 600, marginBottom: '20px' }}>Quick Actions</h2>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <Link to="/builder/new" className="btn-secondary" style={{ width: '100%', justifyContent: 'flex-start', padding: '12px 16px', textDecoration: 'none' }}>
              <Plus size={18} color="var(--accent)" /> Create Workflow
            </Link>
            <Link to="/templates" className="btn-secondary" style={{ width: '100%', justifyContent: 'flex-start', padding: '12px 16px', textDecoration: 'none' }}>
              <Upload size={18} color="var(--accent-cyan)" /> Import Template
            </Link>
            <Link to="/integrations" className="btn-secondary" style={{ width: '100%', justifyContent: 'flex-start', padding: '12px 16px', textDecoration: 'none' }}>
              <GitPullRequest size={18} /> Connect GitHub
            </Link>
            <Link to="/integrations" className="btn-secondary" style={{ width: '100%', justifyContent: 'flex-start', padding: '12px 16px', textDecoration: 'none' }}>
              <MessageSquare size={18} color="var(--accent-amber)" /> Connect Slack
            </Link>
          </div>
        </div>
      </div>

      {/* RECENT WORKFLOWS */}
      <div className="card">
        <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 style={{ fontSize: '16px', fontWeight: 600 }}>Recent Workflows</h2>
          <Link to="/workflows" style={{ fontSize: '13px', color: 'var(--accent)', textDecoration: 'none', fontWeight: 500 }}>View All</Link>
        </div>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                <th style={{ padding: '16px 24px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Workflow Name</th>
                <th style={{ padding: '16px 24px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Status</th>
                <th style={{ padding: '16px 24px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Trigger</th>
                <th style={{ padding: '16px 24px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Last Run</th>
                <th style={{ padding: '16px 24px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {recentWorkflows.map((wf) => (
                <tr key={wf.id} style={{ borderBottom: '1px solid var(--border-subtle)' }} className="card-hover">
                  <td style={{ padding: '16px 24px', fontSize: '14px', fontWeight: 500 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: wf.status === 'active' ? 'var(--accent-emerald)' : wf.status === 'failed' ? 'var(--accent-rose)' : 'var(--text-muted)' }} />
                      <Link to={`/builder/${wf.id}`} style={{ textDecoration: 'none', color: 'inherit' }}>{wf.name}</Link>
                    </div>
                  </td>
                  <td style={{ padding: '16px 24px' }}>
                    <span className={`status-${wf.status}`} style={{ padding: '4px 10px', borderRadius: '12px', fontSize: '12px', fontWeight: 600, textTransform: 'capitalize' }}>
                      {wf.status}
                    </span>
                  </td>
                  <td style={{ padding: '16px 24px', fontSize: '14px', color: 'var(--text-secondary)' }}>{wf.trigger}</td>
                  <td style={{ padding: '16px 24px', fontSize: '14px', color: 'var(--text-secondary)' }}>{wf.lastRun}</td>
                  <td style={{ padding: '16px 24px', textAlign: 'right' }}>
                    <button className="btn-ghost" style={{ padding: '4px 8px' }}><MoreVertical size={16} /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
