ALTER TABLE assistant_messages
    ADD COLUMN workflow_proposal_json JSONB;

ALTER TABLE assistant_messages
    ADD COLUMN workflow_proposal_validation_json JSONB;