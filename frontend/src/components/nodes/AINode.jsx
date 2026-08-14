import NodeWrapper from './NodeWrapper'
import { Brain } from 'lucide-react'

export default function AINode(props) {
  return (
    <NodeWrapper
      {...props}
      type="ai"
      color="var(--node-ai)"
      icon={Brain}
    />
  )
}
