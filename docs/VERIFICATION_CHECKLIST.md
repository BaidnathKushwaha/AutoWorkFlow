# Baseline Verification Checklist

Use this checklist when closing the current milestone and again before resuming feature development. Mark an item complete only after running it and recording the result.

## 1. Repository hygiene

- [ ] Confirm `git status` is clean or document any intentionally uncommitted changes.
- [ ] Confirm the intended commit is on `main`.
- [ ] Review the last several commits and open pull requests.
- [ ] Search tracked files for accidental API keys, client secrets, JWT secrets, populated `.env` files, and personal tokens.
- [ ] Confirm development Compose credentials are not being used in a public environment.

## 2. Frontend

From `frontend/`:

- [ ] `npm install` completes.
- [ ] `npm run lint` passes or all remaining failures are documented.
- [ ] `npm test` passes or all failures are documented.
- [ ] `npm run build` succeeds.
- [ ] Login, workflow list, canvas load/save, and execution views work against the configured backend.
- [ ] New executions do not incorrectly display stale execution output.

## 3. Backend and database

From `autoworkflow-backend/`:

- [ ] `./mvnw test` passes or all failures are documented.
- [ ] `./mvnw clean package` succeeds.
- [ ] Application starts with documented environment variables.
- [ ] A fresh PostgreSQL database migrates successfully.
- [ ] Swagger UI/API docs load as expected under the active security configuration.
- [ ] No secret values are exposed in logs or API responses.

## 4. Authentication and ownership

- [ ] Signup/login and token refresh/logout paths work.
- [ ] Protected routes reject unauthenticated requests.
- [ ] A user cannot read or mutate another user's workflow or integration.
- [ ] OAuth state/callback handling rejects invalid or reused state where applicable.

## 5. Workflow lifecycle

- [ ] Create and save a workflow.
- [ ] Reload it and verify nodes/edges/configuration persist.
- [ ] Edit and save it again.
- [ ] Manually execute a valid workflow without requiring a trigger, if supported by the current implementation.
- [ ] Confirm deployment rejects workflows that do not meet trigger requirements.
- [ ] Deploy a valid trigger-based workflow.
- [ ] Modify a deployed graph and verify deployment invalidation.
- [ ] Verify execution success and failure states are persisted and visible.

## 6. Control flow and transformations

- [ ] IF/condition node takes the expected path for true and false inputs.
- [ ] Switch/AI routing selects only the intended branch.
- [ ] Default branch behavior is correct for unmatched values.
- [ ] Loop processes configured input items and follows body/continuation handles correctly.
- [ ] Merge behavior matches its implemented semantics.
- [ ] Transform mappings work for scalar, nested, and array data supported by the current implementation.
- [ ] Invalid or ambiguous graphs fail safely with a useful error.
- [ ] Execution terminates correctly for loops and malformed/cyclic graphs.

## 7. AI providers

For each provider you intend to claim as working:

- [ ] Valid credentials are configured.
- [ ] A supported model identifier is selected.
- [ ] Account quota is available.
- [ ] A real request succeeds.
- [ ] Provider errors are surfaced as execution failures rather than misleading successful output.
- [ ] Execution logs do not expose secrets.

A provider's presence in configuration does not satisfy this section.

## 8. External integrations

- [ ] Google OAuth uses the exact registered redirect URI.
- [ ] Gmail authorization and a real supported Gmail action succeed.
- [ ] Google Sheets authorization and a real read/write workflow succeed.
- [ ] GitHub webhook reaches the configured endpoint and is authenticated as expected.
- [ ] Slack OAuth/action works if included in the intended feature claims.
- [ ] Expired credentials, missing scopes, and provider API errors produce actionable errors.
- [ ] External webhook endpoints are reachable over HTTPS during remote-provider testing.

## 9. Documentation closeout

- [ ] README describes only capabilities represented in the current repository.
- [ ] Local setup instructions match current paths and scripts.
- [ ] Required environment variables are documented without exposing values.
- [ ] Known limitations and unverified behavior are explicit.
- [ ] Future features are labeled as deferred rather than completed.
- [ ] Record test results and the commit SHA in the project handover when verification is run.

## Result log

Fill this in when checks are actually executed:

| Date | Commit SHA | Area | Result | Notes |
|---|---|---|---|---|
| Not run in this documentation pass | `31248e01d04bc24c0827d1e00b56d4dc04b03d53` | Local test/build verification | Not verified | Run the commands above in a local checkout |
