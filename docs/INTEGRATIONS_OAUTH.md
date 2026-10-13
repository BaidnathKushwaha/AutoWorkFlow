# Integrations and OAuth Lifecycle

This document summarizes the generic integration API and the token/state persistence model found in the backend. A configured connection is not proof that every action or trigger is currently working end to end.

## Integration record

The integrations table stores a per-user provider connection. The entity includes provider key and account label; encrypted access token and optional encrypted refresh token; scopes and integration status; token expiry and last-check timestamps.

The database enforces one row per (user_id, provider). IntegrationService is responsible for retrieving/decrypting credentials for supported backend operations. Never log raw token values.

## OAuth lifecycle

1. An authenticated user requests OAuth start.
2. The backend creates random state and stores its hash.
3. The browser visits the provider authorization URL.
4. The provider redirects to the configured callback.
5. The backend validates and consumes state.
6. The authorization code is exchanged for provider tokens.
7. Tokens are saved to the user's Integration record.
8. The frontend receives a success or error redirect.

The oauth_states table stores a state hash, user ID, provider, expiry, and optional used timestamp. The service uses a short time-to-live and consumes state to mitigate replay. State is not a substitute for the access token and should not be exposed in logs.

The generic controller supports a browser GET callback and a POST callback. Google can use a shared browser callback route; the actual integration provider is recovered from trusted server-side OAuth state. Check provider configuration and redirect URI values together.

## Provider and operation layers

The codebase contains generic integration persistence/OAuth infrastructure plus provider-specific clients, controllers, and strategies. These are separate layers:

- Connection management obtains, persists, refreshes where supported, and disconnects credentials.
- Provider actions call a real provider API such as sending a Slack message or interacting with GitHub.
- Workflow node strategies adapt workflow input/configuration to that action and return a node result.
- Trigger/polling services receive external events or poll a provider and start a workflow.

A successful OAuth callback proves only that the authorization exchange and persistence path completed. Verify at least one actual API operation for the integration.

## Webhooks

The public route POST /api/webhooks/{token} looks up a workflow by its unique webhook token and refuses to run it unless it is deployed. The token in the path is a secret. Anyone who obtains it may be able to trigger the workflow, so do not paste production webhook URLs into public issues, screenshots, or docs; rotate/redeploy if exposed; use HTTPS and a publicly reachable endpoint; and validate payload assumptions of downstream nodes.

The frontend uses VITE_WEBHOOK_BASE_URL to construct an externally reachable webhook URL. A localhost URL is not reachable by GitHub or other external servers.

## Gmail trigger state

The gmail_trigger_states table stores a unique workflow ID, Gmail history cursor, and update timestamp. The polling scheduler uses this cursor to track changes. Test cursor advancement, duplicate delivery handling, expired/invalid cursors, permissions, and actual workflow execution rather than only checking that the account is connected.

## Security checklist

- Keep OAuth client secrets and provider keys in environment configuration or a secret manager.
- Verify redirect URIs exactly, including scheme, host, path, and trailing slash.
- Validate OAuth state and provider association before token exchange.
- Encrypt tokens at rest and avoid raw token logging.
- Check ownership before connecting or disconnecting a user's integration.
- Do not assume every provider implements refresh-token behavior identically.
- Test provider API permissions/scopes and error paths.
