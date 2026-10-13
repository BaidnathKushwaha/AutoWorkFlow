# AI Provider System

This guide follows the backend abstractions in common/llm/ and the user preference model. It describes code structure; live provider availability depends on credentials, quota, model IDs, and provider APIs.

## Main components

| Component | Responsibility |
|---|---|
| AiProvider | Provider contract, including a stable provider key |
| Provider implementations | Translate common requests into provider-specific API calls |
| AiProviderRegistry | Indexes provider implementations by key |
| AiProviderRouter | Resolves AUTO mode and attempts configured providers in order |
| AiAutoModeProperties | Binds the configured ordered provider fallback list |
| AiFailureClassifier | Classifies failures to decide whether fallback should continue |
| AiService | Shared service called by AI node strategies |
| User.aiMode / aiProvider / aiModel | Persists user-level AI preferences |

## Provider selection

The code supports an explicit provider key and an automatic mode. The user model uses AiMode.AUTO or AiMode.SPECIFIC. In AUTO mode, provider/model preference fields are expected to be null; migration V7 enforces this state. In SPECIFIC mode, both provider and model are expected to be present.

The router's AUTO fallback order is configured through app.ai.auto-provider-order. Confirm the active YAML/profile values and provider registry before assuming a particular order.

## Request flow

Node strategy -> AiService -> AiProviderRegistry/provider implementation, or AiProviderRouter in AUTO mode -> failure classification -> fallback or surfaced error.

The workflow executor decides when the AI node runs. The AI service/provider layer handles the request to the selected model.

## Configuration

Environment variables referenced by the checked-in example configuration include OPENAI_API_KEY, GEMINI_API_KEY, and OPENROUTER_API_KEY. Use application.example.yml and the provider implementation as the source of truth for exact model settings and request requirements. Never commit real keys.

## Failure behavior

A provider may fail because of missing configuration, invalid credentials, quota exhaustion, rate limiting, network failures, model retirement, or an incompatible request. The failure classifier determines which failures are eligible for fallback. Do not assume all errors are retried or that quota exhaustion can always be bypassed by another provider.

When debugging:
1. Check the provider selected for the specific node/user preference.
2. Confirm the model identifier is currently accepted by that provider.
3. Check credentials and account quota independently.
4. Inspect the sanitized backend error and provider attempt metadata.
5. Re-test one minimal prompt before testing a full workflow.
6. Confirm a provider failure does not leak credentials or sensitive prompt data into logs.

## Tests and guarantees

The repository contains unit tests for provider routing and summarizer behavior. This documentation pass did not execute them. Use the current tests and provider code as the source of truth for fallback conditions.
