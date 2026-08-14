package com.autoworkflow.integration.oauth;

/**
 * One implementation per OAuth provider (GithubTokenExchange now,
 * SlackTokenExchange in Milestone 4, ...). Keeping this as an interface from
 * the start means Slack reuses the exact same shape — controller code
 * doesn't change, only a new implementation gets added.
 */
public interface OAuthTokenExchangeClient {

    /** Must match the `provider` path/query values used across the integration module, e.g. "github". */
    String provider();

    /** Exchanges a one-time authorization code (from the OAuth redirect) for a usable access token. */
    OAuthToken exchange(String code);
}
