/**
 * Validates whether the given string is a valid email address.
 */
export const validateEmail = (email) => {
  if (!email) return false
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

/**
 * Validates whether the password meets minimum criteria (e.g. min 6 characters).
 */
export const validatePassword = (password) => {
  if (!password) return false
  return password.length >= 6
}

/**
 * Checks if a value is non-empty.
 */
export const validateRequired = (value) => {
  if (value === null || value === undefined) return false
  if (typeof value === 'string') return value.trim().length > 0
  return true
}
