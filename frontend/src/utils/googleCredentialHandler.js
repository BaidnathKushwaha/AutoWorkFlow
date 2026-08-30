export function createGoogleCredentialHandler(handleGoogleCredential) {
    return async (response) => {
        if (!response?.credential) {
            return handleGoogleCredential(null)
        }
        return handleGoogleCredential(response)
    }
}
