package com.autoworkflow.common.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds `app.ai.auto-provider-order` (a YAML list) — the deterministic order
 * AiProviderRouter tries providers in when a node's config has provider="auto".
 * Deliberately just a plain ordered list of provider keys rather than anything
 * fancier (weights, priorities, health scores) — see Phase 8 scope: no cost
 * optimization, no health monitoring, no load balancing. Order = preference, full stop.
 */
@Component
@ConfigurationProperties(prefix = "app.ai")
@Getter
@Setter
public class AiAutoModeProperties {

    private List<String> autoProviderOrder = new ArrayList<>(List.of("openrouter", "gemini", "openai"));
}
