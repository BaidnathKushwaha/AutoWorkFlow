-- Phase 4: secure OAuth state, Gmail trigger cursor, authoritative template metadata.

CREATE TABLE oauth_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);
CREATE INDEX idx_oauth_states_expires ON oauth_states(expires_at);

CREATE TABLE gmail_trigger_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL UNIQUE REFERENCES workflows(id) ON DELETE CASCADE,
    history_id VARCHAR(80) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE templates ADD COLUMN IF NOT EXISTS category VARCHAR(80);
ALTER TABLE templates ADD COLUMN IF NOT EXISTS difficulty VARCHAR(40);
ALTER TABLE templates ADD COLUMN IF NOT EXISTS integration_requirements TEXT;

UPDATE templates SET
    category = CASE name
        WHEN 'AI Email Router' THEN 'Email & AI'
        WHEN 'Resume Matcher' THEN 'Recruiting'
        WHEN 'GitHub PR Smart Reviewer' THEN 'Developer Tools'
        WHEN 'Trend Generator' THEN 'Content'
        ELSE COALESCE(category, 'Automation')
    END,
    difficulty = CASE name
        WHEN 'AI Email Router' THEN 'Intermediate'
        WHEN 'Resume Matcher' THEN 'Advanced'
        ELSE COALESCE(difficulty, 'Beginner')
    END,
    integration_requirements = CASE name
        WHEN 'AI Email Router' THEN 'gmail,google_sheets'
        WHEN 'Resume Matcher' THEN 'gmail,google_sheets'
        WHEN 'GitHub PR Smart Reviewer' THEN 'github'
        WHEN 'Trend Generator' THEN 'notion'
        ELSE COALESCE(integration_requirements, '')
    END;

UPDATE templates
SET description = 'Classify new Gmail messages as Personal, Official, or Spam and append the result to Google Sheets.',
    trigger_icon_key = 'email_received',
    target_icon_key = 'google_sheets',
    canvas_nodes = '[
      {"id":"1","type":"email_received","position":{"x":100,"y":100},"data":{"label":"Gmail Received","fromFilter":"","subjectFilter":""}},
      {"id":"2","type":"classifier","position":{"x":420,"y":100},"data":{"label":"Classify Email","provider":"default","labels":["Personal","Official","Spam"],"textField":"body"}},
      {"id":"3","type":"google_sheets","position":{"x":740,"y":100},"data":{"label":"Log Classification","operation":"append","spreadsheetId":"","range":"Sheet1!A1","values":["{{sender}}","{{subject}}","{{label}}","{{messageId}}"]}}
    ]'::jsonb,
    canvas_edges = '[
      {"id":"e1-2","source":"1","target":"2"},
      {"id":"e2-3","source":"2","target":"3"}
    ]'::jsonb
WHERE name = 'AI Email Router';

UPDATE templates
SET description = 'Match a resume received through Gmail against a configurable job description and append a structured candidate score to Google Sheets.',
    trigger_icon_key = 'email_received',
    target_icon_key = 'google_sheets',
    canvas_nodes = '[
      {"id":"1","type":"email_received","position":{"x":100,"y":100},"data":{"label":"Resume Email","fromFilter":"","subjectFilter":"Resume","jobDescription":""}},
      {"id":"2","type":"ai","position":{"x":420,"y":100},"data":{"label":"Resume Matcher","provider":"default","structuredOutput":true,"prompt":"Match the resume in {{body}} against this job description: {{jobDescription}}. Return JSON with candidateName, candidateEmail, matchPercentage (0-100), matchedSkills (array), missingSkills (array), experienceMatch, and reasoning."}},
      {"id":"3","type":"google_sheets","position":{"x":740,"y":100},"data":{"label":"Append Candidate","operation":"append","spreadsheetId":"","range":"Sheet1!A1","values":["{{candidateName}}","{{candidateEmail}}","{{matchPercentage}}","{{matchedSkills}}","{{missingSkills}}","{{experienceMatch}}","{{reasoning}}"]}}
    ]'::jsonb,
    canvas_edges = '[
      {"id":"e1-2","source":"1","target":"2"},
      {"id":"e2-3","source":"2","target":"3"}
    ]'::jsonb
WHERE name = 'Resume Matcher';
