package com.autoworkflow.execution.engine;

import com.autoworkflow.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Covers Phase 27's required NodeStrategyRegistry scenarios: canonical keys + legacy aliases. */
class NodeStrategyRegistryTest {

    /** Minimal fake strategy so the registry can be exercised without wiring the whole app context. */
    private static NodeStrategy fake(String typeKey) {
        return new NodeStrategy() {
            @Override public String getTypeKey() { return typeKey; }
            @Override public NodeExecutionResult execute(NodeExecutionContext ctx) {
                return NodeExecutionResult.ok(JsonUtils.mapper().createObjectNode());
            }
        };
    }

    private NodeStrategyRegistry registryWithCanonicalStrategies() {
        return new NodeStrategyRegistry(List.of(
                fake("ai"), fake("cron_trigger"), fake("email_received"), fake("http_request"),
                fake("github"), fake("google_sheets"), fake("send_email"), fake("if_condition"),
                fake("transform"), fake("summarizer"), fake("webhook")
        ));
    }

    @Test
    void resolvesCanonicalKeysDirectly() {
        NodeStrategyRegistry registry = registryWithCanonicalStrategies();
        assertThat(registry.resolve("summarizer").getTypeKey()).isEqualTo("summarizer");
        assertThat(registry.resolve("transform").getTypeKey()).isEqualTo("transform");
        assertThat(registry.resolve("webhook").getTypeKey()).isEqualTo("webhook");
    }

    @Test
    void resolvesAllDocumentedLegacyAliasesToTheirCanonicalStrategy() {
        NodeStrategyRegistry registry = registryWithCanonicalStrategies();

        assertThat(registry.resolve("openai").getTypeKey()).isEqualTo("ai");
        assertThat(registry.resolve("cron").getTypeKey()).isEqualTo("cron_trigger");
        assertThat(registry.resolve("email_trigger").getTypeKey()).isEqualTo("email_received");
        assertThat(registry.resolve("http").getTypeKey()).isEqualTo("http_request");
        assertThat(registry.resolve("github_action").getTypeKey()).isEqualTo("github");
        assertThat(registry.resolve("sheets").getTypeKey()).isEqualTo("google_sheets");
        assertThat(registry.resolve("email_send").getTypeKey()).isEqualTo("send_email");
        assertThat(registry.resolve("if").getTypeKey()).isEqualTo("if_condition");
    }

    @Test
    void unknownTypeThrowsClearException() {
        NodeStrategyRegistry registry = registryWithCanonicalStrategies();
        assertThatThrownBy(() -> registry.resolve("totally_made_up_node"))
                .hasMessageContaining("totally_made_up_node");
    }

    @Test
    void isTriggerType_trueOnlyForRealTriggerStrategies() {
        NodeStrategy trigger = new NodeStrategy() {
            @Override public String getTypeKey() { return "webhook"; }
            @Override public boolean isTrigger() { return true; }
            @Override public NodeExecutionResult execute(NodeExecutionContext ctx) { return NodeExecutionResult.ok(null); }
        };
        NodeStrategyRegistry registry = new NodeStrategyRegistry(List.of(trigger, fake("slack"), fake("transform")));

        assertThat(registry.isTriggerType("webhook")).isTrue();
        assertThat(registry.isTriggerType("slack")).isFalse();
        assertThat(registry.isTriggerType("transform")).isFalse();
        assertThat(registry.isTriggerType("does_not_exist")).isFalse();
    }
}
