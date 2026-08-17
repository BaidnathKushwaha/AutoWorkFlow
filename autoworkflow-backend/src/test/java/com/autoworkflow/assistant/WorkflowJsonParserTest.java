package com.autoworkflow.assistant;

import com.autoworkflow.assistant.dto.WorkflowProposal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowJsonParserTest {

    @Test
    void normalConversation_isParsedAsStructuredResponseWithoutProposal() {
        String response = """
                {
                  "answer": "I can help you build workflows.",
                  "workflowProposal": null
                }
                """;

        WorkflowJsonParser.ParsedAssistantResponse parsed =
                WorkflowJsonParser.parse(response);

        assertThat(parsed.answer())
                .isEqualTo("I can help you build workflows.");

        assertThat(parsed.workflowProposal())
                .isNull();
    }

    @Test
    void workflowResponse_isParsedIntoWorkflowProposal() {
        String response = """
                {
                  "answer": "I propose a simple summarizer workflow.",
                  "workflowProposal": {
                    "intent": "Summarize text",
                    "nodes": [
                      {
                        "id": "node-1",
                        "type": "text_input",
                        "configuration": {
                          "label": "Input"
                        }
                      },
                      {
                        "id": "node-2",
                        "type": "llm",
                        "configuration": {
                          "label": "Summarizer"
                        }
                      }
                    ],
                    "edges": [
                      {
                        "id": "edge-1",
                        "source": "node-1",
                        "target": "node-2",
                        "configuration": {}
                      }
                    ]
                  }
                }
                """;

        WorkflowJsonParser.ParsedAssistantResponse parsed =
                WorkflowJsonParser.parse(response);

        WorkflowProposal proposal = parsed.workflowProposal();

        assertThat(parsed.answer())
                .isEqualTo("I propose a simple summarizer workflow.");

        assertThat(proposal)
                .isNotNull();

        assertThat(proposal.intent())
                .isEqualTo("Summarize text");

        assertThat(proposal.nodes())
                .hasSize(2);

        assertThat(proposal.edges())
                .hasSize(1);

        assertThat(proposal.nodes().get(0).id())
                .isEqualTo("node-1");

        assertThat(proposal.edges().get(0).source())
                .isEqualTo("node-1");

        assertThat(proposal.edges().get(0).target())
                .isEqualTo("node-2");
    }

    @Test
    void malformedJson_isRejected() {
        String response = """
            {
              "answer": "I can help you build a workflow.",
              "workflowProposal": {
                "intent": "Summarize",
              }
            }
            """;

        assertThatThrownBy(
                () -> WorkflowJsonParser.parse(response)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Assistant response contains malformed structured JSON"
                );
    }

    @Test
    void nonJsonConversation_isRejectedInsteadOfBeingInterpretedAsNormalText() {
        assertThatThrownBy(
                () -> WorkflowJsonParser.parse(
                        "I can help you build workflows."
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "required structured JSON envelope"
                );
    }

    @Test
    void jsonEmbeddedInsideUnstructuredText_isRejected() {
        String response = """
                Here is the workflow:
                {"answer":"Build it","workflowProposal":null}
                """;

        assertThatThrownBy(
                () -> WorkflowJsonParser.parse(response)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "required structured JSON envelope"
                );
    }

    @Test
    void unsupportedTopLevelField_isRejected() {
        String response = """
                {
                  "answer": "Build it",
                  "workflowProposal": null,
                  "extra": "not allowed"
                }
                """;

        assertThatThrownBy(
                () -> WorkflowJsonParser.parse(response)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "unsupported top-level field"
                );
    }
}