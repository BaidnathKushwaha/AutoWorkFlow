import { format, formatDistanceToNow } from 'date-fns'

/**
 * Formats a date string or timestamp into a readable date format.
 * Example: Aug 4, 2026, 1:45 PM
 */
export const formatDate = (date, formatStr = 'MMM d, yyyy, h:mm a') => {
  if (!date) return '-'
  try {
    const d = new Date(date)
    return format(d, formatStr)
  } catch (error) {
    return String(date)
  }
}

/**
 * Formats a date to relative time from now.
 * Example: "3 minutes ago"
 */
export const formatRelativeTime = (date) => {
  if (!date) return '-'
  try {
    const d = new Date(date)
    return formatDistanceToNow(d, { addSuffix: true })
  } catch (error) {
    return String(date)
  }
}

/**
 * Formats numbers nicely (e.g. 1000 -> 1,000).
 */
export const formatNumber = (num) => {
  if (num === null || num === undefined) return '0'
  return Number(num).toLocaleString()
}

/**
 * Formats duration in milliseconds to a human-readable string.
 * Example: 1250ms -> "1.3s" or 120000ms -> "2m 0s"
 */
export const formatDuration = (ms) => {
  if (ms === null || ms === undefined) return '-'
  if (ms < 1000) return `${ms}ms`
  const seconds = (ms / 1000).toFixed(1)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remainingSecs = Math.round(seconds % 60)
  return `${minutes}m ${remainingSecs}s`
}
