import { useState, useCallback } from 'react'

export function useWorkflow() {
  const [isDirty, setIsDirty] = useState(false)
  
  const handleNodesChange = useCallback(() => {
    setIsDirty(true)
  }, [])

  const handleEdgesChange = useCallback(() => {
    setIsDirty(true)
  }, [])

  const saveWorkflow = useCallback(async () => {
    // Mock save operation
    return new Promise((resolve) => {
      setTimeout(() => {
        setIsDirty(false)
        resolve({ success: true })
      }, 500)
    })
  }, [])

  return {
    isDirty,
    setIsDirty,
    handleNodesChange,
    handleEdgesChange,
    saveWorkflow,
  }
}
