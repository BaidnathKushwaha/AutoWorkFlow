import NodeWrapper from './NodeWrapper'
import { GitBranch } from 'lucide-react'

export default function LogicNode(props) {
  return (
    <NodeWrapper
      {...props}
      type="logic"
      nodeType={props.type}
      color="var(--node-logic)"
      icon={GitBranch}
    />
  )
}
