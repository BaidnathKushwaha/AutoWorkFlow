package com.autoworkflow.execution.condition;

import com.autoworkflow.common.exception.IntegrationException;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ConditionEvaluatorTest {
    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    private JsonNode json(String value) throws Exception { return JsonUtils.mapper().readTree(value); }

    @Test void resolvesNestedObjectAndArrayPaths() throws Exception {
        JsonNode input = json("{\"user\":{\"profile\":{\"status\":\"active\"}},\"items\":[{\"price\":12.5},{\"price\":99.5}]} ");
        assertThat(evaluator.evaluate(input, json("{\"field\":\"user.profile.status\",\"operator\":\"equals\",\"value\":\"active\"}"))).isTrue();
        assertThat(evaluator.evaluate(input, json("{\"field\":\"items.1.price\",\"operator\":\"greater_than\",\"value\":50}"))).isTrue();
    }

    @Test void supportsCompoundAndOr() throws Exception {
        JsonNode input = json("{\"status\":\"paid\",\"amount\":1200,\"country\":\"IN\"}");
        JsonNode condition = json("{\"logic\":\"AND\",\"conditions\":[{\"field\":\"status\",\"operator\":\"equals\",\"value\":\"paid\"},{\"logic\":\"OR\",\"conditions\":[{\"field\":\"amount\",\"operator\":\"greater_than\",\"value\":1000},{\"field\":\"country\",\"operator\":\"equals\",\"value\":\"US\"}]}]}");
        assertThat(evaluator.evaluate(input, condition)).isTrue();
    }

    @Test void supportsAllPrimitiveOperators() throws Exception {
        JsonNode input = json("{\"text\":\"invoice-paid\",\"n\":10,\"flag\":true,\"empty\":\"\"}");
        assertThat(evaluator.evaluate(input, json("{\"field\":\"text\",\"operator\":\"contains\",\"value\":\"paid\"}"))).isTrue();
        assertThat(evaluator.evaluate(input, json("{\"field\":\"text\",\"operator\":\"not_contains\",\"value\":\"failed\"}"))).isTrue();
        assertThat(evaluator.evaluate(input, json("{\"field\":\"text\",\"operator\":\"starts_with\",\"value\":\"invoice\"}"))).isTrue();
        assertThat(evaluator.evaluate(input, json("{\"field\":\"text\",\"operator\":\"ends_with\",\"value\":\"paid\"}"))).isTrue();
        assertThat(evaluator.evaluate(input, json("{\"field\":\"n\",\"operator\":\"greater_than_or_equal\",\"value\":10}"))).isTrue();
        assertThat(evaluator.evaluate(input, json("{\"field\":\"n\",\"operator\":\"less_than_or_equal\",\"value\":10}"))).isTrue();
        assertThat(evaluator.evaluate(input, json("{\"field\":\"flag\",\"operator\":\"is_true\"}"))).isTrue();
        assertThat(evaluator.evaluate(input, json("{\"field\":\"flag\",\"operator\":\"is_false\"}"))).isFalse();
        assertThat(evaluator.evaluate(input, json("{\"field\":\"empty\",\"operator\":\"is_empty\"}"))).isTrue();
        assertThat(evaluator.evaluate(input, json("{\"field\":\"missing\",\"operator\":\"not_exists\"}"))).isTrue();
    }

    @Test void rejectsUnsupportedAndIncompatibleComparisons() throws Exception {
        JsonNode input = json("{\"n\":10}");
        assertThatThrownBy(() -> evaluator.evaluate(input, json("{\"field\":\"n\",\"operator\":\"wat\",\"value\":1}")))
                .isInstanceOf(IntegrationException.class).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> evaluator.evaluate(input, json("{\"field\":\"n\",\"operator\":\"contains\",\"value\":true}")))
                .isInstanceOf(IntegrationException.class).hasMessageContaining("incompatible");
    }
}
