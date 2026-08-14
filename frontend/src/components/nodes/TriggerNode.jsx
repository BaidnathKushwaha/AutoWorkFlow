import NodeWrapper from './NodeWrapper'
import { Zap } from 'lucide-react'

export default function TriggerNode(props) {
  return (
    <NodeWrapper
      {...props}
      type="trigger"
      color="var(--node-trigger)"
      icon={Zap}
    />
  )
}
