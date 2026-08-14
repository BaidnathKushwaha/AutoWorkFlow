package com.autoworkflow.integration.oauth;

import com.autoworkflow.common.exception.IntegrationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Replaces the old `Map<String, OAuthTokenExchangeClient>` @Bean.
 *
 * That bean was never actually being used: Spring gives special, always-on
 * treatment to any injection point declared as `Map<String, T>` — it
 * unconditionally builds its own map of "every T bean, keyed by *bean
 * name*" and hands that out instead, before it even considers an explicit
 * @Bean of the matching Map type. So every `.get("github")` call was
 * silently missing — the real keys were "githubTokenExchange",
 * "slackTokenExchange", etc.
 *
 * Wrapping the map inside a plain component sidesteps this: the injection
 * point Spring sees at every call site is `OAuthTokenExchangeRegistry`, a
 * normal class, not `Map<...>` — so Spring's Map-specific special-casing
 * never triggers. The List<OAuthTokenExchangeClient> constructor parameter
 * below is unaffected by any of this; List/Collection autowiring just
 * collects instances and was never the problem.
 */
@Component
public class OAuthTokenExchangeRegistry {

    private final Map<String, OAuthTokenExchangeClient> clientsByProvider;

    public OAuthTokenExchangeRegistry(List<OAuthTokenExchangeClient> clients) {
        this.clientsByProvider = clients.stream()
                .collect(Collectors.toMap(OAuthTokenExchangeClient::provider, Function.identity()));
    }

    /** Throws if the provider isn't registered — use this at the "primary" call site. */
    public OAuthTokenExchangeClient resolve(String provider) {
        OAuthTokenExchangeClient client = clientsByProvider.get(provider);
        if (client == null) {
            throw new IntegrationException(
                    "OAuth for '" + provider + "' isn't implemented yet. Available: " + clientsByProvider.keySet());
        }
        return client;
    }

    /** Returns null instead of throwing — for call sites that want to try a fallback/alias before giving up. */
    public OAuthTokenExchangeClient resolveOrNull(String provider) {
        return clientsByProvider.get(provider);
    }

    public boolean supports(String provider) {
        return clientsByProvider.containsKey(provider);
    }

    public Set<String> availableProviders() {
        return clientsByProvider.keySet();
    }
}
