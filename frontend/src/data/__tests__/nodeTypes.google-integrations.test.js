import { describe, expect, it } from 'vitest'
import { nodeConfigs } from '../nodeTypes'

describe('Google integration node configuration contracts', () => {
  it('exposes operation-aware Gmail fields', () => {
    const fields = Object.fromEntries(nodeConfigs.gmail.fields.map(field => [field.key, field]))
    expect(fields.action.options).toEqual(['send', 'read', 'search'])
    expect(fields.to).toBeDefined()
    expect(fields.subject).toBeDefined()
    expect(fields.body).toBeDefined()
    expect(fields.query).toBeDefined()
    expect(fields.maxResults).toBeDefined()
  })

  it('exposes operation-aware Sheets fields', () => {
    const fields = Object.fromEntries(nodeConfigs.google_sheets.fields.map(field => [field.key, field]))
    expect(fields.operation.options).toEqual(['append', 'read', 'find'])
    expect(fields.spreadsheetId).toBeDefined()
    expect(fields.range).toBeDefined()
    expect(fields.values).toBeDefined()
    expect(fields.findColumn).toBeDefined()
    expect(fields.findValue).toBeDefined()
  })
})
