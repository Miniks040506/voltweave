ALTER TABLE flexibility_candidates
    ADD COLUMN source_power_kw NUMERIC(14, 3),
    ADD CONSTRAINT flexibility_candidates_source_power_check CHECK (
        source_power_kw IS NULL OR source_power_kw >= raw_upward_flexibility_kw
    );

ALTER TABLE optimization_candidates
    ADD COLUMN source_power_kw NUMERIC(14, 3),
    ADD CONSTRAINT optimization_candidates_source_power_check CHECK (
        source_power_kw IS NULL OR source_power_kw >= available_power_kw
    );
