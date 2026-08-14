package com.autoworkflow.execution.validation;

public record WorkflowValidationResult(boolean isValid, String error) {
    public static WorkflowValidationResult valid() {
        return new WorkflowValidationResult(true, null);
    }
    public static WorkflowValidationResult invalid(String error) {
        return new WorkflowValidationResult(false, error);
    }
}
