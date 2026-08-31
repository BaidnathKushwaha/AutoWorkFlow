import { useState, useMemo, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Link, useSearchParams } from 'react-router-dom'
import {
    Play, Search, LayoutGrid, List, Trash2, Edit2,
    GitBranch, Activity, Database, Clock, AlertCircle, Link2, Copy, Check
} from 'lucide-react'
import { useWorkflowStore } from '../store/workflowStore'
import { useExecutionStore } from '../store/executionStore'
import { useNotificationStore } from '../store/notificationStore'
import workflowService from '../services/workflow/workflowService'
import { buildWebhookUrl } from '../utils/constants'
import { toast } from 'sonner'

export default function Workflows() {
    const { workflows, setWorkflows, deleteWorkflow, updateWorkflow } = useWorkflowStore()
    const [searchParams] = useSearchParams()
    const urlSearch = searchParams.get('search') || ''

    const [searchQuery, setSearchQuery] = useState(urlSearch)
    const [statusFilter, setStatusFilter] = useState('all') // 'all', 'ran', 'active', 'draft'
    const [viewMode, setViewMode] = useState('grid') // 'grid', 'list'
    const [isDeleting, setIsDeleting] = useState(null)
    const [copiedTokenId, setCopiedTokenId] = useState(null)

    // Sync workflows from backend database on mount
    useEffect(() => {
        workflowService.list({ size: 100 })
            .then(res => {
                const pageData = res?.data || res
                const dbList = pageData?.content || (Array.isArray(pageData) ? pageData : [])
                if (dbList && dbList.length > 0) {
                    setWorkflows(current => {
                        const map = new Map(current.map(w => [w.id, w]))
                        dbList.forEach(dbItem => {
                            const existing = map.get(dbItem.id) || {}
                            map.set(dbItem.id, {
                                ...existing,
                                id: dbItem.id,
                                name: dbItem.name || existing.name,
                                description: dbItem.description || existing.description,
                                status: (dbItem.status || existing.status || 'active').toLowerCase(),
                                trigger: dbItem.triggerType || existing.trigger || 'Manual Trigger',
                                executions: dbItem.executionsCount ?? existing.executions ?? 0,
                                nodeCount: dbItem.nodesCount || existing.nodeCount || 0,
                                webhookToken: dbItem.webhookToken || existing.webhookToken,
                                webhookUrl: dbItem.webhookUrl || existing.webhookUrl,
                                deployed: dbItem.deployed ?? existing.deployed ?? false,
                                lastRun: dbItem.lastRunAt ? new Date(dbItem.lastRunAt).toLocaleTimeString() : (existing.lastRun || 'Never'),
                            })
                        })
                        return Array.from(map.values())
                    })
                }
            })
            .catch(err => {
                console.warn('Backend workflow list notice:', err)
            })
    }, [setWorkflows])

    // Filter workflows
    const filteredWorkflows = useMemo(() => {
        return workflows.filter(wf => {
            const matchesSearch =
                (wf.name || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
                (wf.description || '').toLowerCase().includes(searchQuery.toLowerCase())

            const matchesStatus =
                statusFilter === 'all' ||
                (statusFilter === 'ran' ? (wf.executions || 0) > 0 : wf.status === statusFilter)

            return matchesSearch && matchesStatus
        })
    }, [workflows, searchQuery, statusFilter])

    const handleToggleStatus = async (id, currentStatus) => {
        const newStatus = currentStatus === 'active' ? 'draft' : 'active'
        updateWorkflow(id, { status: newStatus })
        toast.success(`Workflow status updated to ${newStatus}`)
        if (isUuid(id)) {
            try {
                await workflowService.toggleActive(id)
            } catch (err) {
                console.warn('Backend toggle status notice:', err?.message || err)
            }
        }
    }

    const handleTriggerWorkflow = async (id, name) => {
        try {
            const runRes = await workflowService.trigger(id)
            const executionId = runRes?.executionId || runRes?.id || runRes?.data?.executionId

            if (executionId) {
                useExecutionStore.getState().setSelectedExecutionId(executionId)
                await useExecutionStore.getState().fetchExecutionById(executionId)
            }
            await useExecutionStore.getState().fetchExecutions()

            const wf = workflows.find(w => w.id === id)
            updateWorkflow(id, {
                executions: (wf?.executions || 0) + 1,
                lastRun: 'Just now'
            })

            toast.success(`Triggered workflow: ${name}`, {
                description: 'Backend execution completed.'
            })
            useNotificationStore.getState().addNotification({
                title: `Workflow "${name}" triggered`,
                type: 'success',
            })
        } catch (err) {
            console.error('Trigger workflow API error:', err)
            const msg = err?.response?.data?.message || err?.message || 'Failed to trigger workflow'
            toast.error(`Execution Error: ${msg}`)
        }
    }

    const isUuid = (str) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(str)

    const handleDeleteWorkflow = async (id, name) => {
        // Optimistically remove from local store first
        deleteWorkflow(id)
        setIsDeleting(null)
        // Persist deletion to backend for real DB-persisted workflows
        if (isUuid(id)) {
            try {
                await workflowService.delete(id)
            } catch (err) {
                console.warn('Backend delete workflow notice:', err?.message || err)
            }
        }
        toast.error(`Deleted workflow: ${name}`)
    }


    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>

            {/* HEADER SECTION */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                    <h1 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '4px' }}>Workflows</h1>
                    <p style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>
                        Manage, trigger, and edit your automation pipelines. ({filteredWorkflows.length} found)
                    </p>
                </div>
            </div>

            {/* FILTER & CONTROLS BAR */}
            <div
                className="card"
                style={{
                    padding: '16px 20px',
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: '16px',
                    alignItems: 'center',
                    justifyContent: 'space-between'
                }}
            >
                {/* Search */}
                <div style={{ position: 'relative', display: 'flex', alignItems: 'center', flex: '1', minWidth: '240px', maxWidth: '400px' }}>
                    <Search size={16} color="var(--text-muted)" style={{ position: 'absolute', left: '12px' }} />
                    <input
                        type="text"
                        placeholder="Search workflows by name or description..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        style={{
                            background: 'var(--bg-input)',
                            border: '1px solid var(--border)',
                            borderRadius: 'var(--radius-sm)',
                            padding: '8px 16px 8px 36px',
                            color: 'var(--text-primary)',
                            fontSize: '14px',
                            width: '100%',
                            outline: 'none',
                            transition: 'border-color 0.2s',
                        }}
                        onFocus={(e) => (e.target.style.borderColor = 'var(--accent)')}
                        onBlur={(e) => (e.target.style.borderColor = 'var(--border)')}
                    />
                </div>

                {/* Filters and View Toggle */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>

                    {/* Status Filters */}
                    <div style={{ display: 'flex', background: 'var(--bg-input)', padding: '4px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)' }}>
                        {[
                            { key: 'ran',    label: 'Previously Run' },
                            { key: 'all',    label: 'All' },
                            { key: 'active', label: 'Active' },
                            { key: 'draft',  label: 'Draft' },
                        ].map(({ key, label }) => (
                            <button
                                key={key}
                                onClick={() => setStatusFilter(key)}
                                style={{
                                    padding: '6px 12px',
                                    borderRadius: '4px',
                                    border: 'none',
                                    fontSize: '13px',
                                    fontWeight: 600,
                                    background: statusFilter === key ? 'var(--bg-card)' : 'transparent',
                                    color: statusFilter === key ? 'var(--text-primary)' : 'var(--text-secondary)',
                                    cursor: 'pointer',
                                    transition: 'all 0.15s ease',
                                    whiteSpace: 'nowrap',
                                }}
                            >
                                {label}
                            </button>
                        ))}

                    </div>

                    {/* Grid/List Toggle */}
                    <div style={{ display: 'flex', background: 'var(--bg-input)', padding: '4px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)' }}>
                        <button
                            onClick={() => setViewMode('grid')}
                            style={{
                                padding: '6px',
                                borderRadius: '4px',
                                border: 'none',
                                background: viewMode === 'grid' ? 'var(--bg-card)' : 'transparent',
                                color: viewMode === 'grid' ? 'var(--accent)' : 'var(--text-secondary)',
                                cursor: 'pointer',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                            }}
                            title="Grid View"
                        >
                            <LayoutGrid size={16} />
                        </button>
                        <button
                            onClick={() => setViewMode('list')}
                            style={{
                                padding: '6px',
                                borderRadius: '4px',
                                border: 'none',
                                background: viewMode === 'list' ? 'var(--bg-card)' : 'transparent',
                                color: viewMode === 'list' ? 'var(--accent)' : 'var(--text-secondary)',
                                cursor: 'pointer',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                            }}
                            title="List View"
                        >
                            <List size={16} />
                        </button>
                    </div>

                </div>
            </div>

            {/* WORKFLOWS PRESENTATION */}
            {filteredWorkflows.length === 0 ? (
                <div
                    className="card"
                    style={{
                        padding: '48px 24px',
                        textAlign: 'center',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        gap: '16px'
                    }}
                >
                    <div style={{ width: '64px', height: '64px', borderRadius: '50%', background: 'var(--bg-input)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-secondary)' }}>
                        <AlertCircle size={28} />
                    </div>
                    <div>
                        <h3 style={{ fontSize: '18px', fontWeight: 600, marginBottom: '6px' }}>No Workflows Found</h3>
                        <p style={{ color: 'var(--text-secondary)', fontSize: '14px', maxWidth: '400px', margin: '0 auto' }}>
                            We couldn't find any workflows matching your search query or filters. Create a new one or modify filters.
                        </p>
                    </div>
                    <button className="btn-secondary" onClick={() => { setSearchQuery(''); setStatusFilter('all'); }} style={{ marginTop: '8px' }}>
                        Clear Filters
                    </button>
                </div>
            ) : viewMode === 'grid' ? (

                /* GRID VIEW */
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '20px' }}>
                    <AnimatePresence mode="popLayout">
                        {filteredWorkflows.map((wf) => (
                            <motion.div
                                key={wf.id}
                                layout
                                initial={{ opacity: 0, scale: 0.95 }}
                                animate={{ opacity: 1, scale: 1 }}
                                exit={{ opacity: 0, scale: 0.9 }}
                                transition={{ duration: 0.2 }}
                                className="card card-hover"
                                style={{ padding: '24px', display: 'flex', flexDirection: 'column', height: '100%', justifyContent: 'space-between', position: 'relative', overflow: 'hidden' }}
                            >
                                <div>
                                    {/* Top: title and status toggle */}
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '12px', marginBottom: '12px' }}>
                                        <h3 style={{ fontSize: '16px', fontWeight: 600, lineHeight: 1.3 }}>
                                            <Link to={`/builder/${wf.id}`} style={{ textDecoration: 'none', color: 'inherit' }}>
                                                {wf.name}
                                            </Link>
                                        </h3>
                                        <button
                                            onClick={() => handleToggleStatus(wf.id, wf.status)}
                                            className={`status-${wf.status}`}
                                            style={{
                                                padding: '4px 10px',
                                                borderRadius: '12px',
                                                fontSize: '11px',
                                                fontWeight: 700,
                                                textTransform: 'capitalize',
                                                border: 'none',
                                                cursor: 'pointer',
                                                flexShrink: 0
                                            }}
                                            title="Click to toggle status"
                                        >
                                            {wf.status}
                                        </button>
                                    </div>

                                    {/* Description */}
                                    <p style={{ color: 'var(--text-secondary)', fontSize: '13px', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', height: '40px', marginBottom: '20px' }}>
                                        {wf.description || 'No description provided for this workflow.'}
                                    </p>

                                    {/* Indicators / Stats Grid */}
                                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '20px', padding: '12px', background: 'var(--bg-input)', borderRadius: 'var(--radius-sm)' }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                            <Database size={14} color="var(--accent)" />
                                            <div>
                                                <div style={{ fontSize: '10px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>Nodes</div>
                                                <div style={{ fontSize: '12px', fontWeight: 600 }}>{wf.nodeCount || 0} Steps</div>
                                            </div>
                                        </div>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                            <Activity size={14} color="var(--accent-cyan)" />
                                            <div>
                                                <div style={{ fontSize: '10px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase' }}>Executions</div>
                                                <div style={{ fontSize: '12px', fontWeight: 600 }}>{wf.executions || 0} Runs</div>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                {/* Footer details & action buttons */}
                                <div>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid var(--border-subtle)', paddingTop: '16px', marginTop: '4px' }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--text-muted)' }}>
                                            <Clock size={12} />
                                            <span>{wf.lastRun || 'Never'}</span>
                                        </div>

                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                            {wf.webhookToken && (
                                                <button
                                                    onClick={() => {
                                                        const url = wf.webhookUrl || buildWebhookUrl(wf.webhookToken)
                                                        navigator.clipboard.writeText(url)
                                                        setCopiedTokenId(wf.id)
                                                        toast.success(wf.deployed ? 'Webhook URL copied to clipboard!' : 'Webhook URL copied — deploy this workflow before pointing GitHub at it.')
                                                        setTimeout(() => setCopiedTokenId(null), 2000)
                                                    }}
                                                    className="btn-ghost"
                                                    style={{ padding: '6px', borderRadius: '6px', color: '#f97316' }}
                                                    title={wf.deployed ? 'Copy Webhook URL for GitHub / HTTP triggers' : 'Not deployed yet — copy anyway'}
                                                >
                                                    {copiedTokenId === wf.id ? <Check size={16} /> : <Link2 size={16} />}
                                                </button>
                                            )}

                                            {/* Play Button */}
                                            <button
                                                onClick={() => handleTriggerWorkflow(wf.id, wf.name)}
                                                className="btn-ghost"
                                                style={{ padding: '6px', borderRadius: '6px', color: 'var(--accent-emerald)' }}
                                                title="Trigger Workflow Now"
                                            >
                                                <Play size={16} fill="currentColor" />
                                            </button>

                                            {/* Edit Button */}
                                            <Link
                                                to={`/builder/${wf.id}`}
                                                className="btn-ghost"
                                                style={{ padding: '6px', borderRadius: '6px', color: 'var(--text-primary)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                                                title="Open in Builder"
                                            >
                                                <Edit2 size={16} />
                                            </Link>

                                            {/* Delete Button */}
                                            <button
                                                onClick={() => setIsDeleting(wf.id)}
                                                className="btn-ghost"
                                                style={{ padding: '6px', borderRadius: '6px', color: 'var(--accent-rose)' }}
                                                title="Delete Workflow"
                                            >
                                                <Trash2 size={16} />
                                            </button>
                                        </div>
                                    </div>
                                </div>

                                {/* Confirm Delete Overlay */}
                                {isDeleting === wf.id && (
                                    <div
                                        style={{
                                            position: 'absolute',
                                            inset: 0,
                                            background: 'rgba(10, 15, 30, 0.95)',
                                            display: 'flex',
                                            flexDirection: 'column',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            padding: '20px',
                                            textAlign: 'center',
                                            zIndex: 10
                                        }}
                                    >
                                        <p style={{ fontWeight: 600, fontSize: '14px', marginBottom: '16px' }}>Are you sure you want to delete this workflow?</p>
                                        <div style={{ display: 'flex', gap: '12px' }}>
                                            <button
                                                className="btn-secondary"
                                                onClick={() => setIsDeleting(null)}
                                                style={{ padding: '6px 12px', fontSize: '12px' }}
                                            >
                                                Cancel
                                            </button>
                                            <button
                                                className="btn-primary"
                                                onClick={() => handleDeleteWorkflow(wf.id, wf.name)}
                                                style={{ padding: '6px 12px', fontSize: '12px', background: 'var(--accent-rose)' }}
                                            >
                                                Delete
                                            </button>
                                        </div>
                                    </div>
                                )}
                            </motion.div>
                        ))}
                    </AnimatePresence>
                </div>
            ) : (

                /* LIST / TABLE VIEW */
                <div className="card" style={{ overflowX: 'auto' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                        <thead>
                        <tr style={{ borderBottom: '1px solid var(--border)' }}>
                            <th style={{ padding: '16px 20px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', width: '100px' }}>Status</th>
                            <th style={{ padding: '16px 20px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', minWidth: '200px' }}>Workflow</th>
                            <th style={{ padding: '16px 20px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', minWidth: '240px' }}>Description</th>
                            <th style={{ padding: '16px 20px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Trigger</th>
                            <th style={{ padding: '16px 20px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', textAlign: 'center' }}>Steps</th>
                            <th style={{ padding: '16px 20px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', textAlign: 'center' }}>Executions</th>
                            <th style={{ padding: '16px 20px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)' }}>Last Run</th>
                            <th style={{ padding: '16px 20px', fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', textAlign: 'right' }}>Actions</th>
                        </tr>
                        </thead>
                        <tbody>
                        <AnimatePresence mode="popLayout">
                            {filteredWorkflows.map((wf) => (
                                <tr
                                    key={wf.id}
                                    style={{ borderBottom: '1px solid var(--border-subtle)', position: 'relative' }}
                                    className="card-hover"
                                >
                                    {/* Status badge cell */}
                                    <td style={{ padding: '16px 20px' }}>
                                        <button
                                            onClick={() => handleToggleStatus(wf.id, wf.status)}
                                            className={`status-${wf.status}`}
                                            style={{
                                                padding: '4px 8px',
                                                borderRadius: '12px',
                                                fontSize: '11px',
                                                fontWeight: 700,
                                                textTransform: 'capitalize',
                                                border: 'none',
                                                cursor: 'pointer'
                                            }}
                                        >
                                            {wf.status}
                                        </button>
                                    </td>

                                    {/* Name cell */}
                                    <td style={{ padding: '16px 20px', fontWeight: 600, fontSize: '14px' }}>
                                        <Link to={`/builder/${wf.id}`} style={{ textDecoration: 'none', color: 'inherit' }}>
                                            {wf.name}
                                        </Link>
                                    </td>

                                    {/* Description cell */}
                                    <td style={{ padding: '16px 20px', fontSize: '13px', color: 'var(--text-secondary)' }}>
                                        <div style={{ display: '-webkit-box', WebkitLineClamp: 1, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                                            {wf.description || '-'}
                                        </div>
                                    </td>

                                    {/* Trigger cell */}
                                    <td style={{ padding: '16px 20px', fontSize: '13px', color: 'var(--text-secondary)' }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                            <GitBranch size={12} color="var(--accent)" />
                                            <span>{wf.trigger || 'Manual'}</span>
                                        </div>
                                    </td>

                                    {/* Nodes count cell */}
                                    <td style={{ padding: '16px 20px', textAlign: 'center', fontSize: '13px', fontWeight: 500 }}>
                                        {wf.nodeCount || 0}
                                    </td>

                                    {/* Executions count cell */}
                                    <td style={{ padding: '16px 20px', textAlign: 'center', fontSize: '13px', fontWeight: 500 }}>
                                        {wf.executions || 0}
                                    </td>

                                    {/* Last run cell */}
                                    <td style={{ padding: '16px 20px', fontSize: '13px', color: 'var(--text-muted)' }}>
                                        {wf.lastRun || 'Never'}
                                    </td>

                                    {/* Actions cell */}
                                    <td style={{ padding: '16px 20px', textAlign: 'right' }}>
                                        {isDeleting === wf.id ? (
                                            <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                                                <button
                                                    className="btn-primary"
                                                    onClick={() => handleDeleteWorkflow(wf.id, wf.name)}
                                                    style={{ padding: '4px 8px', fontSize: '11px', background: 'var(--accent-rose)' }}
                                                >
                                                    Yes
                                                </button>
                                                <button
                                                    className="btn-secondary"
                                                    onClick={() => setIsDeleting(null)}
                                                    style={{ padding: '4px 8px', fontSize: '11px' }}
                                                >
                                                    No
                                                </button>
                                            </div>
                                        ) : (
                                            <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                                                <button
                                                    onClick={() => handleTriggerWorkflow(wf.id, wf.name)}
                                                    className="btn-ghost"
                                                    style={{ padding: '4px', borderRadius: '4px', color: 'var(--accent-emerald)' }}
                                                    title="Trigger Workflow Now"
                                                >
                                                    <Play size={15} fill="currentColor" />
                                                </button>
                                                <Link
                                                    to={`/builder/${wf.id}`}
                                                    className="btn-ghost"
                                                    style={{ padding: '4px', borderRadius: '4px', color: 'var(--text-primary)', display: 'flex', alignItems: 'center' }}
                                                    title="Edit"
                                                >
                                                    <Edit2 size={15} />
                                                </Link>
                                                <button
                                                    onClick={() => setIsDeleting(wf.id)}
                                                    className="btn-ghost"
                                                    style={{ padding: '4px', borderRadius: '4px', color: 'var(--accent-rose)' }}
                                                    title="Delete"
                                                >
                                                    <Trash2 size={15} />
                                                </button>
                                            </div>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </AnimatePresence>
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    )
}