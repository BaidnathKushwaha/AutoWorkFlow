import NodeWrapper from './NodeWrapper'
import { Blocks } from 'lucide-react'

export default function IntegrationNode(props) {
  return (
    <NodeWrapper
      {...props}
      type="integration"
      color="var(--node-comms)"
      icon={Blocks}
    />
  )
}
