package com.autoworkflow.common.llm;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the key -> AiProvider map explicitly from the autowired List, rather than
 * declaring `Map<String, AiProvider>` as the injected field itself. The latter looks
 * equivalent but isn't: Spring always resolves a `Map<String, T>` injection point as
 * "every T bean keyed by bean name", not by consulting the values. OpenAiClient/
 * GeminiClient/OpenRouterClient happened to have custom bean names matching their
 * intended keys, so this would accidentally work — but that's fragile. Any future
 * AiProvider added with a plain @Component (no explicit bean name) would silently
 * register under a camelCase bean name instead of its real key().
 *
 * This is the same map AiService used to build internally — extracted here so
 * AiProviderRouter (AUTO mode) can resolve providers by key from the exact same
 * source of truth instead of duplicating the List<AiProvider> -> Map logic.
 */
@Component
public class AiProviderRegistry {

    private final Map<String, AiProvider> providers;

    public AiProviderRegistry(List<AiProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(AiProvider::key, Function.identity()));
    }

    /** Case-insensitive lookup; null if no provider is registered under that key. */
    public AiProvider get(String key) {
        return key == null ? null : providers.get(key.toLowerCase());
    }

    public boolean has(String key) {
        return key != null && providers.containsKey(key.toLowerCase());
    }

    /** For error messages ("Unsupported AI provider: x. Available: [...]") and diagnostics — never exposes credentials. */
    public Set<String> availableKeys() {
        return providers.keySet();
    }
}
