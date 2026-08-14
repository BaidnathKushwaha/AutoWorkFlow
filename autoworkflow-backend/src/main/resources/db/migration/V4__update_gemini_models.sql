-- Migration to update deprecated/legacy Gemini models (gemini-2.5-flash, gemini-1.5-flash, etc.) to gemini-3.6-flash

UPDATE workflows
SET canvas_nodes = REPLACE(canvas_nodes::text, 'gemini-2.5-flash', 'gemini-3.6-flash')::jsonb
WHERE canvas_nodes::text LIKE '%gemini-2.5-flash%';

UPDATE workflows
SET canvas_nodes = REPLACE(canvas_nodes::text, 'gemini-1.5-flash', 'gemini-3.6-flash')::jsonb
WHERE canvas_nodes::text LIKE '%gemini-1.5-flash%';

UPDATE workflows
SET canvas_nodes = REPLACE(canvas_nodes::text, 'gemini-2.0-flash', 'gemini-3.6-flash')::jsonb
WHERE canvas_nodes::text LIKE '%gemini-2.0-flash%';

UPDATE templates
SET canvas_nodes = REPLACE(canvas_nodes::text, 'gemini-2.5-flash', 'gemini-3.6-flash')::jsonb
WHERE canvas_nodes::text LIKE '%gemini-2.5-flash%';

UPDATE templates
SET canvas_nodes = REPLACE(canvas_nodes::text, 'gemini-1.5-flash', 'gemini-3.6-flash')::jsonb
WHERE canvas_nodes::text LIKE '%gemini-1.5-flash%';

UPDATE templates
SET canvas_nodes = REPLACE(canvas_nodes::text, 'gemini-2.0-flash', 'gemini-3.6-flash')::jsonb
WHERE canvas_nodes::text LIKE '%gemini-2.0-flash%';
