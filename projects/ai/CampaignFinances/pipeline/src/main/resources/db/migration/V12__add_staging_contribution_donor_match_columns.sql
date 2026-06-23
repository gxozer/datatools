-- Donor-match prep columns for the dedup job (PR-156), per docs/TDS_PHASE1.md §5.5.
-- These cover only the part of the normalization rule MySQL can express as an
-- indexable generated column (uppercase, trim, collapse whitespace, zip5).
-- Punctuation/suffix stripping (JR/SR/II, etc. — TDS §5 step 1) is NOT done
-- here; that full normalization is PR-156's pure-function module. Don't treat
-- normalized_name as the final donor-match key on its own.
ALTER TABLE staging_contribution
    ADD COLUMN normalized_name VARCHAR(200)
        GENERATED ALWAYS AS (UPPER(TRIM(REGEXP_REPLACE(contributor_name, '[[:space:]]+', ' ')))) VIRTUAL,
    ADD COLUMN zip5 CHAR(5)
        GENERATED ALWAYS AS (LEFT(zip_code, 5)) VIRTUAL,
    ADD INDEX idx_staging_contribution_donor_match (normalized_name, zip5);
