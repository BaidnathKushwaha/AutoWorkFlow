import { useState, useCallback, useRef, useEffect } from 'react'
import { useParams, useSearchParams, useNavigate } from 'react-router-dom'
import { useWorkflowStore } from '../store/workflowStore'
import { useExecutionStore } from '../store/executionStore'
import { useNotificationStore } from '../store/notificationStore'
import workflowService from '../services/workflow/workflowService'
import integrationService from '../services/integration/integrationService'
import { toast } from 'sonner'
import {
    ReactFlow,
    MiniMap,
    Controls,
    Background,
    useNodesState,
    useEdgesState,
    addEdge,
    ReactFlowProvider,
    MarkerType,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'

import WorkflowToolbar from '../components/workflow/WorkflowToolbar'
import NodePalette from '../components/workflow/NodePalette'
import ConfigPanel from '../components/workflow/ConfigPanel'
import ExecutionConsole from '../components/workflow/ExecutionConsole'

import TriggerNode from '../components/nodes/TriggerNode'
import AINode from '../components/nodes/AINode'
import LogicNode from '../components/nodes/LogicNode'
import IntegrationNode from '../components/nodes/IntegrationNode'

import { nodeCategories, nodeConfigs, TRIGGER_NODE_TYPES } from '../data/nodeTypes'
import { templates } from '../data/templates'

const baseNodeTypes = {
    trigger: TriggerNode,
    ai: AINode,
    logic: LogicNode,
    integration: IntegrationNode,
}

const nodeTypes = { ...baseNodeTypes }

nodeCategories.forEach(category => {
    let NodeComponent
    if (category.id === 'triggers') NodeComponent = TriggerNode
    else if (category.id === 'ai') NodeComponent = AINode
    else if (category.id === 'logic') NodeComponent = LogicNode
    else NodeComponent = IntegrationNode

    category.nodes.forEach(node => {
        nodeTypes[node.type] = NodeComponent
    })
})

const NODE_PROVIDERS = {
    slack: 'slack',
    github_action: 'github', // legacy type key, kept for already-saved workflows
    github: 'github', // GitHub API action node (create issue/PR/comment) — needs the GitHub integration
    // NOTE: 'github_event' (the GitHub webhook TRIGGER) is intentionally NOT mapped here.
    // It receives whatever GitHub posts to the workflow's webhook URL — it never makes an
    // outbound GitHub API call itself, so it doesn't require a connected GitHub integration
    // the way the `github` action node does. Conflating the two here previously caused this
    // "connected providers" check to demand a GitHub connection just to receive a webhook.
    gmail: 'gmail',
    email_trigger: 'gmail', // legacy type key
    email_received: 'gmail',
    notion: 'notion',
    sheets: 'google_sheets', // legacy type key
    google_sheets: 'google_sheets',
    discord: 'discord',
    ai: 'openai',
    summarizer: 'openai',
    ai_router: 'openai',
    classifier: 'openai',
}

function WorkflowBuilderInner() {
    const { id } = useParams()
    const [searchParams] = useSearchParams()
    const navigate = useNavigate()
    const reactFlowWrapper = useRef(null)
    const { workflows, addWorkflow, updateWorkflow, saveWorkflowCanvas } = useWorkflowStore()

    const templateId = searchParams.get('template')
    const template = templateId ? templates.find(t => t.id === templateId) : null
    const existingWorkflow = (!template && id !== 'new') ? workflows.find(w => w.id === id) : null

    const startingNodes = template
        ? template.nodes
        : (existingWorkflow?.canvasNodes?.length ? existingWorkflow.canvasNodes : [])
    const startingEdges = (template
        ? template.edges
        : (existingWorkflow?.canvasEdges?.length ? existingWorkflow.canvasEdges : [])
    ).map(e => ({
        ...e,
        animated: true,
        type: 'smoothstep',
        style: { stroke: '#818cf8', strokeWidth: 3 },
        markerEnd: {
            type: MarkerType.ArrowClosed,
            color: '#818cf8',
            width: 14,
            height: 14,
        },
    }))

    const [nodes, setNodes, onNodesChange] = useNodesState(startingNodes)
    const [edges, setEdges, onEdgesState] = useEdgesState(startingEdges)
    const [reactFlowInstance, setReactFlowInstance] = useState(null)

    const [workflowName, setWorkflowName] = useState(
        template ? template.title : existingWorkflow?.name || 'Untitled Workflow'
    )
    const [workflowStatus, setWorkflowStatus] = useState(existingWorkflow?.status || 'draft')
    const [selectedNodeId, setSelectedNodeId] = useState(null)
    const [isConsoleOpen, setIsConsoleOpen] = useState(false)
    // null = normal canvas selection (ConfigPanel defaults to Parameters). Set to 'input'
    // when a node is selected via the Execution Console, so ConfigPanel opens straight to
    // the data the person actually clicked to see, instead of Parameters. Cleared on the
    // next ordinary canvas click so normal behavior isn't permanently changed.
    const [configPanelInitialTab, setConfigPanelInitialTab] = useState(null)
    const [nodeExecutionData, setNodeExecutionData] = useState({})
    const [connectedProviders, setConnectedProviders] = useState(new Set())
    // Local state for webhookToken — populated immediately from DB load
    const [webhookToken, setWebhookToken] = useState(existingWorkflow?.webhookToken || null)
    const [webhookUrl, setWebhookUrl] = useState(existingWorkflow?.webhookUrl || null)
    const [deployed, setDeployed] = useState(existingWorkflow?.deployed || false)

    const isUuid = (str) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(str)

    // True when the workflow has a Webhook or GitHub trigger node
    const isWebhookOrGithub = nodes.some(n =>
        ['webhook', 'github_event', 'email_trigger', 'email_received'].includes(n.type) ||
        (n.data?.label || '').toLowerCase().includes('webhook') ||
        (n.data?.label || '').toLowerCase().includes('github')
    )

    // Build a callable webhook URL from a token
    const buildWebhookUrl = (token) =>
        token ? `${window.location.origin.replace('5173', '8080')}/api/webhooks/${token}` : null

    useEffect(() => {
        if (isUuid(id)) {
            workflowService.getById(id)
                .then(res => {
                    const wf = res?.data || res
                    if (wf) {
                        if (wf.name) setWorkflowName(wf.name)
                        if (wf.webhookToken) setWebhookToken(wf.webhookToken)
                        if (wf.webhookUrl) setWebhookUrl(wf.webhookUrl)
                        setDeployed(!!wf.deployed)
                        if (wf.status) setWorkflowStatus(wf.status.toLowerCase())
                        if (wf.canvasNodes && Array.isArray(wf.canvasNodes) && wf.canvasNodes.length > 0) {
                            setNodes(wf.canvasNodes)
                        }
                        if (wf.canvasEdges && Array.isArray(wf.canvasEdges) && wf.canvasEdges.length > 0) {
                            setEdges(wf.canvasEdges)
                        }
                        // Update or add to local store so existingWorkflow resolves correctly
                        if (existingWorkflow) {
                            updateWorkflow(id, {
                                webhookToken: wf.webhookToken,
                                webhookUrl: wf.webhookUrl,
                                deployed: wf.deployed,
                                canvasNodes: wf.canvasNodes,
                                canvasEdges: wf.canvasEdges,
                            })
                        } else {
                            addWorkflow({
                                id: wf.id,
                                name: wf.name,
                                description: wf.description,
                                status: (wf.status || 'DRAFT').toLowerCase(),
                                trigger: wf.triggerType || 'Manual Trigger',
                                lastRun: wf.lastRunAt ? new Date(wf.lastRunAt).toLocaleTimeString() : 'Never',
                                executions: wf.executionsCount || 0,
                                nodeCount: wf.canvasNodes?.length || 0,
                                canvasNodes: wf.canvasNodes,
                                canvasEdges: wf.canvasEdges,
                                webhookToken: wf.webhookToken,
                                webhookUrl: wf.webhookUrl,
                                deployed: wf.deployed,
                            })
                        }
                    }
                })
                .catch(err => {
                    console.warn('Backend get workflow notice:', err)
                })
        }
    }, [id])

    useEffect(() => {
        integrationService.list({ _skipAuthRedirect: true })
            .then(res => {
                const list = Array.isArray(res) ? res : (res?.data || [])
                const healthy = new Set(
                    list
                        .filter(i =>
                            i.status === 'HEALTHY' ||
                            i.status === 'CONNECTED' ||
                            i.connected
                        )
                        .map(i => i.provider)
                )
                setConnectedProviders(healthy)
            })
            .catch(err => {
                console.warn('Backend list integrations notice:', err)
                setConnectedProviders(new Set())
            })
    }, [])

    const execIdParam = searchParams.get('executionId') || searchParams.get('execution')

    useEffect(() => {
        // Only auto-load an execution here when the URL explicitly asks for one (e.g.
        // navigating in from Execution History with ?executionId=...) — this is the one
        // "intentional" case. Do NOT fall back to the execution store's global
        // selectedExecutionId: that's whatever was last viewed anywhere in the app
        // (possibly for a completely different workflow), and using it here was exactly
        // the bug where opening/switching workflows could show a stale, unrelated
        // execution as if it were current.
        if (!execIdParam) return

        useExecutionStore.getState().setSelectedExecutionId(execIdParam)
        useExecutionStore.getState().fetchExecutionById(execIdParam).then(detail => {
            // Defensive guard: never apply execution data that turns out to belong to a
            // different workflow than the one currently open.
            if (detail && detail.workflowId && id && detail.workflowId !== id) return

            if (detail && detail.stepsLogs) {
                const executionMap = {}
                detail.stepsLogs.forEach(step => {
                    if (step.nodeId) {
                        executionMap[step.nodeId] = {
                            input: step.inputPayload ?? null,
                            output: step.outputPayload ?? null,
                            status: step.status === 'failed' || !!step.error ? 'failed' : 'success',
                            error: step.error || null,
                            duration: step.durationMs ?? null,
                        }
                    }
                })
                setNodeExecutionData(executionMap)
            }
        })
    }, [execIdParam, id])

    const selectedExecutionId = useExecutionStore((state) => state.selectedExecutionId)

    useEffect(() => {
        if (!selectedExecutionId) return

        const applyExecutionDetail = (detail) => {
            if (detail && detail.workflowId && id && detail.workflowId !== id) return
            if (detail && detail.stepsLogs) {
                const executionMap = {}
                detail.stepsLogs.forEach(step => {
                    if (step.nodeId) {
                        executionMap[step.nodeId] = {
                            input: step.inputPayload ?? null,
                            output: step.outputPayload ?? null,
                            status: step.status === 'failed' || !!step.error ? 'failed' : 'success',
                            error: step.error || null,
                            duration: step.durationMs ?? null,
                        }
                    }
                })
                setNodeExecutionData(executionMap)
            }
        }

        const currentInStore = useExecutionStore.getState().executions.find(e => e.id === selectedExecutionId)
        if (currentInStore && currentInStore.stepsLogs) {
            applyExecutionDetail(currentInStore)
        } else {
            useExecutionStore.getState().fetchExecutionById(selectedExecutionId).then(applyExecutionDetail)
        }
    }, [selectedExecutionId, id])


    const handleUpdateNodeData = useCallback((nodeId, newData) => {
        // Switch-specific: if the case list changed, prune any edges whose branch
        // (sourceHandle / edge.data.branch) no longer corresponds to a real case —
        // otherwise a renamed/removed case leaves a dangling edge the executor's
        // dead-edge propagation would just permanently starve, invisibly.
        const existing = nodes.find(n => n.id === nodeId)
        let patch = newData
        if (existing?.type === 'switch' && Array.isArray(newData?.cases)) {
            const oldCases = Array.isArray(existing.data?.cases) ? existing.data.cases : []
            const newCases = newData.cases
            const removed = oldCases.filter(c => !newCases.includes(c))
            if (removed.length > 0) {
                setEdges((eds) => eds.filter(e => {
                    if (e.source !== nodeId) return true
                    const branch = e.sourceHandle || e.data?.branch
                    return !removed.includes(branch)
                }))
            }
            // Don't leave a defaultCase pointing at a case that no longer exists.
            if (patch.defaultCase === undefined && existing.data?.defaultCase && !newCases.includes(existing.data.defaultCase)) {
                patch = { ...patch, defaultCase: '' }
            }
        }

        setNodes((nds) => nds.map((n) => {
            if (n.id === nodeId) {
                return {
                    ...n,
                    data: {
                        ...n.data,
                        ...patch
                    }
                }
            }
            return n
        }))
    }, [nodes, setNodes, setEdges])

    const onConnect = useCallback(
        (params) => setEdges((eds) => addEdge({
            ...params,
            // Only meaningfully populated when the connection comes from a node with
            // named output handles (currently just Switch — see NodeWrapper.jsx, where
            // each case's Handle id is the case value itself). IF Condition / AI Router
            // don't set sourceHandle, so this is a no-op for them — their existing
            // branch-setting behavior (however it currently works) is untouched.
            ...(params.sourceHandle ? { data: { ...params.data, branch: params.sourceHandle } } : {}),
            animated: true,
            type: 'smoothstep',
            style: { stroke: '#818cf8', strokeWidth: 3 },
            markerEnd: {
                type: MarkerType.ArrowClosed,
                color: '#818cf8',
                width: 14,
                height: 14,
            },
        }, eds)),
        [setEdges]
    )

    const handleDeleteNode = useCallback((nodeId) => {
        setNodes((nds) => nds.filter((node) => node.id !== nodeId))
        setEdges((eds) => eds.filter((edge) => edge.source !== nodeId && edge.target !== nodeId))
        setSelectedNodeId(null)
    }, [setNodes, setEdges])

    const onDragOver = useCallback((event) => {
        event.preventDefault()
        event.dataTransfer.dropEffect = 'move'
    }, [])

    const onDrop = useCallback((event) => {
        event.preventDefault()

        const nodeDataString = event.dataTransfer.getData('application/reactflow')

        if (!nodeDataString) return

        const { type, label, description } = JSON.parse(nodeDataString)
        if (typeof type === 'undefined' || !type) return

        // In React Flow v11+ / @xyflow/react, screenToFlowPosition converts screen client coordinates directly
        const position = reactFlowInstance.screenToFlowPosition({
            x: event.clientX,
            y: event.clientY,
        })

        // Bake each field's schema default straight into the node's data at creation time.
        const schemaDefaults = {}
        const configSchema = nodeConfigs[type]
        if (configSchema) {
            configSchema.fields.forEach(field => {
                if (field.default !== undefined) {
                    schemaDefaults[field.key] = field.default
                }
            })
        }

        const newNode = {
            id: `node-${Date.now()}`,
            type,
            position,
            // collapsed: true → NodeWrapper initialises to compact state for new nodes.
            // Nodes loaded from the backend have no `collapsed` field (or false) → expanded.
            data: { label, description: description || 'Configure node properties in panel', ...schemaDefaults, collapsed: true },
        }

        setNodes((nds) => nds.concat(newNode))
    }, [reactFlowInstance, setNodes])

    const onNodeClick = useCallback((_, node) => {
        setConfigPanelInitialTab(null) // ordinary canvas click -> ConfigPanel defaults to Parameters
        setSelectedNodeId(node.id)
    }, [])

    const onPaneClick = useCallback(() => {
        setSelectedNodeId(null)
    }, [])

    // Passed to ExecutionConsole: clicking a step selects that node on the canvas, focuses
    // it if possible, and opens ConfigPanel straight to the Input tab for that execution.
    const handleSelectExecutionNode = useCallback((nodeId) => {
        if (!nodeId) return
        setConfigPanelInitialTab('input')
        setSelectedNodeId(nodeId)
        if (reactFlowInstance?.fitView) {
            const target = nodes.find(n => n.id === nodeId)
            if (target) {
                reactFlowInstance.fitView({ nodes: [{ id: nodeId }], padding: 0.6, duration: 300 })
            }
        }
    }, [nodes, reactFlowInstance])

    const selectedNode = nodes.find(n => n.id === selectedNodeId)

    // Save workflow canvas snapshot to store and backend API.
    // Persists nodes/edges/trigger only — does NOT deploy or activate the workflow.
    // Returns the workflow's real (UUID) id so callers like handleDeploy can chain off it
    // even right after creating a brand-new workflow (before the route param updates).
    const handleSave = async ({ silent, preserveStatus } = {}) => {
        try {
            const triggerNode = nodes.find(n => n.type === 'trigger' || n.type === 'webhook' || n.type === 'github_event' || n.type === 'cron' || n.type === 'cron_trigger' || (n.id && n.id.includes('trigger')))
            const triggerType = triggerNode?.type || triggerNode?.data?.label || 'Manual'
            const triggerLabel = triggerNode?.data?.label || 'Manual Trigger'

            const isExistingUuid = isUuid(id)
            let backendWf = null

            try {
                if (isExistingUuid) {
                    const res = await workflowService.update(id, {
                        name: workflowName,
                        triggerType,
                        canvasNodes: nodes,
                        canvasEdges: edges,
                    })
                    backendWf = res?.data || res
                } else {
                    const res = await workflowService.create({
                        name: workflowName || 'New Workflow',
                        description: 'Custom automation workflow',
                        triggerType,
                        canvasNodes: nodes,
                        canvasEdges: edges,
                    })
                    backendWf = res?.data || res
                }
            } catch (err) {
                const errorMsg = err?.response?.data?.message || err?.message || 'Failed to save workflow.'
                console.error('Backend workflow save error:', err)
                toast.error('Save failed', { description: errorMsg })
                return null
            }

            if (!backendWf?.id) {
                toast.error('Save failed', { description: 'Backend did not return a valid workflow ID.' })
                return null
            }

            const finalId = backendWf.id
            const finalToken = backendWf.webhookToken || null
            const finalUrl = backendWf.webhookUrl || (finalToken ? buildWebhookUrl(finalToken) : null)
            const finalStatus = (backendWf.status || 'draft').toLowerCase()
            const isDep = !!backendWf.deployed

            if (finalToken) setWebhookToken(finalToken)
            if (finalUrl) setWebhookUrl(finalUrl)
            if (!preserveStatus) setWorkflowStatus(finalStatus)
            setDeployed(isDep)

            const wfData = {
                id: finalId,
                name: backendWf.name || workflowName || 'Untitled Workflow',
                status: finalStatus,
                trigger: triggerLabel,
                lastRun: existingWorkflow?.lastRun || 'Never',
                executions: (existingWorkflow?.executions || 0),
                nodeCount: nodes.length,
                description: backendWf.description || 'Custom automation workflow',
                canvasNodes: nodes,
                canvasEdges: edges,
                webhookToken: finalToken,
                webhookUrl: finalUrl,
                deployed: isDep,
            }

            if (existingWorkflow || isExistingUuid) {
                updateWorkflow(finalId, wfData)
                saveWorkflowCanvas(finalId, nodes, edges)
            } else {
                addWorkflow(wfData)
            }

            if (!silent) {
                toast.success(`Workflow "${workflowName}" saved!`, {
                    description: finalUrl ? `Webhook URL: ${finalUrl}` : undefined
                })
                useNotificationStore.getState().addNotification({
                    title: `Workflow "${workflowName}" saved`,
                    type: 'info',
                })
            }

            if (!isExistingUuid && backendWf.id) {
                navigate(`/builder/${backendWf.id}`, { replace: true })
            }

            return finalId
        } catch (err) {
            console.error('handleSave unexpected error:', err)
            toast.error('Save failed', { description: err?.message || 'An unexpected error occurred.' })
            return null
        }
    }

    // Deploy: persists the latest canvas first, then requires backend confirmation to activate.
    const handleDeploy = async () => {
        const wfId = await handleSave({ silent: true })
        if (!wfId) return
        const targetId = wfId

        if (!targetId || !isUuid(targetId)) {
            toast.error('Deployment failed', {
                description: 'Workflow must be saved to the backend before deployment.'
            })
            return
        }

        let backendWf = null
        try {
            const res = await workflowService.deploy(targetId)
            backendWf = res?.data || res
        } catch (err) {
            const errorMsg = err?.response?.data?.message || err?.message || 'Workflow deployment failed.'
            console.error('Backend deploy error:', err)
            toast.error('Deployment failed', {
                description: errorMsg
            })
            return
        }

        if (!backendWf) {
            toast.error('Deployment failed', {
                description: 'Backend did not confirm deployment.'
            })
            return
        }

        const token = backendWf?.webhookToken

        if (!token) {
            toast.error('Deployment failed', {
                description: 'Backend deployment succeeded but returned no webhook token.'
            })
            return
        }

        const url = backendWf?.webhookUrl || buildWebhookUrl(token)

        setWebhookToken(token)
        setWebhookUrl(url)
        setWorkflowStatus('active')
        setDeployed(true)

        updateWorkflow(targetId, {
            status: 'active',
            deployed: true,
            webhookToken: token,
            webhookUrl: url,
        })

        toast.success('Workflow deployed & active!', {
            description: url || 'Webhook listener enabled'
        })
        useNotificationStore.getState().addNotification({
            title: `Workflow "${workflowName}" deployed`,
            type: 'success',
        })
    }

    const handleRun = async () => {
        setWorkflowStatus('running')
        setIsConsoleOpen(true)

        // Clear any stale execution from a previous run (or a previous workflow) before
        // this attempt does anything else. If workflowService.trigger() below throws —
        // e.g. backend validation rejects the workflow — nothing after this point ever
        // runs, so this is also what guarantees a failed-before-executionId attempt shows
        // a clean empty state instead of the last successful run's data.
        useExecutionStore.getState().setSelectedExecutionId(null)
        useExecutionStore.getState().clearCurrentExecution()
        setNodeExecutionData({})

        setNodes(curr =>
            curr.map(n => ({
                ...n,
                data: {
                    ...n.data,
                    isRunning: false,
                    isSuccess: false,
                    isFailed: false,
                    executionData: null,
                }
            }))
        )

        try {
            let wfId = await handleSave({ silent: true, preserveStatus: true })
            const runId = wfId || id

            if (!runId || !isUuid(runId)) {
                throw new Error('Workflow must be saved before execution.')
            }

            const runRes = await workflowService.trigger(runId)
            const executionId = runRes?.executionId || runRes?.id || runRes?.data?.executionId

            let executionDetail = null
            if (executionId) {
                useExecutionStore.getState().setSelectedExecutionId(executionId)
                executionDetail = await useExecutionStore.getState().fetchExecutionById(executionId)
                await useExecutionStore.getState().fetchExecutions()
            }

            if (executionDetail) {
                const stepsLogs = executionDetail?.stepsLogs || []
                const executionMap = {}

                stepsLogs.forEach(step => {
                    if (step.nodeId) {
                        executionMap[step.nodeId] = {
                            input: step.inputPayload ?? null,
                            output: step.outputPayload ?? null,
                            status: step.status === 'failed' || !!step.error
                                ? 'failed'
                                : 'success',
                            error: step.error || null,
                            duration: step.durationMs ?? null,
                        }
                    }
                })

                setNodeExecutionData(executionMap)
                const execFailed = executionDetail?.status === 'failed' || !!executionDetail?.errorMessage

                const animateExecution = async () => {
                    for (const step of stepsLogs) {
                        const nodeId = step.nodeId

                        // Start this node
                        setNodes(curr =>
                            curr.map(n =>
                                n.id === nodeId
                                    ? {
                                        ...n,
                                        data: {
                                            ...n.data,
                                            isRunning: true,
                                            isSuccess: false,
                                            isFailed: false,
                                            executionData: executionMap[nodeId] || null,
                                        }
                                    }
                                    : n
                            )
                        )

                        // Keep the running state visible
                        await new Promise(resolve => setTimeout(resolve, 300))

                        const isFail = step.status === 'failed' || !!step.error

                        // Finish this node
                        setNodes(curr =>
                            curr.map(n =>
                                n.id === nodeId
                                    ? {
                                        ...n,
                                        data: {
                                            ...n.data,
                                            isRunning: false,
                                            isSuccess: !isFail,
                                            isFailed: isFail,
                                            executionData: executionMap[nodeId] || null,
                                        }
                                    }
                                    : n
                            )
                        )

                        await new Promise(resolve => setTimeout(resolve, 300))
                    }
                }

                await animateExecution()

                const durationDisplay = executionDetail?.duration || `${((executionDetail?.durationMs || 0) / 1000).toFixed(1)}s`
                if (execFailed) {
                    toast.error(`Execution Failed: ${executionDetail?.errorMessage || 'Step failure'}`)
                } else {
                    toast.success(`Workflow "${workflowName}" executed successfully (${durationDisplay})`)
                }
            } else {
                throw new Error('Execution was triggered, but execution details could not be retrieved.')
            }
        } catch (err) {
            console.error('Workflow execution failed:', err)
            // We don't know per-node status here (the trigger call itself failed, or
            // execution detail couldn't be fetched) — clear to neutral rather than
            // marking every node "failed", which would be misleading (Phase 10: never
            // mark all nodes failed/successful when we haven't actually observed that).
            setNodes(curr => curr.map(n => ({
                ...n,
                data: { ...n.data, isRunning: false, isFailed: false, isSuccess: false }
            })))
            const errMsg = err?.response?.data?.message || err?.message || 'Workflow execution failed'
            toast.error(errMsg)
        } finally {
            setWorkflowStatus(deployed ? 'active' : 'draft')
        }
    }

    return (
        <div style={{ display: 'flex', flexDirection: 'column', height: '100%', width: '100%', overflow: 'hidden' }}>
            <WorkflowToolbar
                workflowName={workflowName}
                onNameChange={setWorkflowName}
                status={workflowStatus}
                onSave={() => handleSave()}
                onRun={handleRun}
                onDeploy={handleDeploy}
                onStop={() => setWorkflowStatus(deployed ? 'active' : 'draft')}
                hasTriggerNode={nodes.some(n => TRIGGER_NODE_TYPES.has(n.type))}
            />

            <div style={{ display: 'flex', flex: 1, overflow: 'hidden', position: 'relative' }}>
                <NodePalette />

                <div style={{ flex: 1, position: 'relative', background: '#e9eff8', borderLeft: '1px solid var(--border)', borderRight: '1px solid var(--border)', boxShadow: 'inset 0 0 16px rgba(15, 23, 42, 0.05)' }} ref={reactFlowWrapper}>
                    <ReactFlow
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
                        onEdgesChange={onEdgesState}
                        onConnect={onConnect}
                        onInit={setReactFlowInstance}
                        onDrop={onDrop}
                        onDragOver={onDragOver}
                        onNodeClick={onNodeClick}
                        onPaneClick={onPaneClick}
                        nodeTypes={nodeTypes}
                        defaultEdgeOptions={{
                            animated: true,
                            type: 'smoothstep',
                            style: { stroke: '#6366f1', strokeWidth: 3 },
                            markerEnd: {
                                type: MarkerType.ArrowClosed,
                                color: '#6366f1',
                                width: 14,
                                height: 14,
                            },
                        }}
                        fitView
                        proOptions={{ hideAttribution: true }}
                    >
                        <Background variant="dots" gap={24} size={1.5} color="#94a3b8" />
                        <Controls />
                        <MiniMap />
                    </ReactFlow>

                    <ExecutionConsole
                        isOpen={isConsoleOpen}
                        onClose={() => setIsConsoleOpen(false)}
                        onSelectExecutionNode={handleSelectExecutionNode}
                    />
                </div>

                <ConfigPanel
                    selectedNode={selectedNode}
                    initialTab={configPanelInitialTab}
                    executionData={
                        selectedNode
                            ? nodeExecutionData[selectedNode.id]
                            : null
                    }
                    onClose={() => setSelectedNodeId(null)}
                    onDeleteNode={handleDeleteNode}
                    onUpdateNode={handleUpdateNodeData}
                    webhookToken={webhookToken}
                    webhookUrl={webhookUrl}
                    deployed={deployed}
                />
            </div>
        </div>

    )
}

export default function WorkflowBuilder() {
    return (
        <ReactFlowProvider>
            <WorkflowBuilderInner />
        </ReactFlowProvider>
    )
}