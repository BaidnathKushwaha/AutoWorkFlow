# Testing and Troubleshooting

This guide is a practical checklist for validating the current baseline. The documentation changes have not themselves run these checks.

## Automated checks

From frontend/:

    npm install
    npm test
    npm run lint
    npm run build

From autoworkflow-backend/:

    ./mvnw test
    ./mvnw clean package

Use the exact scripts and wrapper checked into the repository. Record failing commands and their first meaningful error, rather than reporting only the final summary line.

## Baseline smoke test

1. Start PostgreSQL using the root development Compose file.
2. Start the backend with required local environment variables.
3. Start the frontend and confirm the API URL points to the backend.
4. Sign up or log in, then verify a protected /api/users/me request.
5. Create a simple workflow and save it.
6. Run manually with a known payload and inspect execution detail.
7. Edit and save the workflow, then test deploy validation separately.
8. Deploy a webhook-triggered workflow and call its URL with a safe test payload.
9. Test a minimal AI node with one currently available provider/model.
10. Test one real provider operation after OAuth/API-key connection.
11. Verify failures produce an understandable execution status and do not leak secrets.

## Common failure categories

### Database connection or migration error
- Confirm PostgreSQL is running and database URL/user/password match active configuration.
- Check the Flyway error and whether a migration was partially applied.
- Do not delete a database or rewrite an applied migration before backing up data and understanding the failure.

### Authentication/401/403
- Confirm the frontend is sending the expected access token.
- Check token expiry and refresh behavior.
- Verify that the resource belongs to the authenticated user.
- For OAuth callbacks, check state creation/consumption and exact redirect URI configuration.

### Workflow does not execute
- Confirm the workflow is saved and the node type is registered.
- Inspect node IDs, edge source/target IDs, and branch handle labels.
- Distinguish manual run from deployment and webhook-triggered execution.
- Read the execution record and per-step logs; find the first failed node.

### Branch or loop behaves unexpectedly
- For IF/Switch, compare strategy output routing metadata with edge branch labels/handles.
- For Loop, verify the output contains an items array and body/continuation connections resolve to valid node IDs.
- Check executor tests for edge cases before changing traversal behavior.

### AI provider errors
- Confirm provider selection and model identifier.
- Check API key, quota, rate limits, and current model availability.
- Test a small prompt directly through the app before debugging the entire workflow.
- Fallback depends on error classification; not every failure is retryable.

### OAuth connection succeeds but action fails
- Check scopes, account identity, token expiry, provider-specific API permissions, and request payload.
- Run one provider action and inspect its sanitized error.
- A successful connection is not equivalent to a successful workflow node.

### External webhook never arrives
- Confirm the URL is public HTTPS and not localhost.
- Check provider delivery logs and backend request logs.
- Verify the workflow is deployed and token matches the current saved deployment.
- Keep the webhook token private.

## Reporting a bug

Include branch and commit SHA; command or UI steps to reproduce; expected versus actual result; relevant sanitized logs and execution ID; and whether the issue reproduces without external credentials.

Do not attach .env files, API keys, OAuth tokens, private email content, or production webhook URLs.
