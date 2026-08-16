ALTER TABLE users
    ADD COLUMN ai_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO';

UPDATE users
SET ai_mode =
        CASE
            WHEN ai_provider IS NULL
                OR LOWER(TRIM(ai_provider)) = 'auto'
                THEN 'AUTO'

            WHEN LOWER(TRIM(ai_provider)) = 'openrouter'
                AND TRIM(ai_model) = 'google/gemini-2.5-flash'
                THEN 'SPECIFIC'

            WHEN LOWER(TRIM(ai_provider)) = 'gemini'
                AND TRIM(ai_model) = 'gemini-3.6-flash'
                THEN 'SPECIFIC'

            WHEN LOWER(TRIM(ai_provider)) = 'openai'
                AND TRIM(ai_model) IN (
                                       'gpt-4o-mini',
                                       'gpt-4o',
                                       'gpt-4-turbo',
                                       'gpt-3.5-turbo'
                    )
                THEN 'SPECIFIC'

            ELSE 'AUTO'
            END;

UPDATE users
SET ai_provider = NULL,
    ai_model = NULL
WHERE ai_mode = 'AUTO';

ALTER TABLE users
    ADD CONSTRAINT chk_users_ai_mode
        CHECK (ai_mode IN ('AUTO', 'SPECIFIC'));

ALTER TABLE users
    ADD CONSTRAINT chk_users_ai_preference_state
        CHECK (
            (
                ai_mode = 'AUTO'
                    AND ai_provider IS NULL
                    AND ai_model IS NULL
                )
                OR
            (
                ai_mode = 'SPECIFIC'
                    AND ai_provider IS NOT NULL
                    AND ai_model IS NOT NULL
                )
            );