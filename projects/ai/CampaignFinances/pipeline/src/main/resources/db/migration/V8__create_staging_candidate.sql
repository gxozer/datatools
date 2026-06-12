CREATE TABLE staging_candidate (
    cand_id              CHAR(9)      NOT NULL,
    cand_name            VARCHAR(200) NULL,
    cand_office          CHAR(1)      NULL,
    cand_pty_affiliation VARCHAR(3)   NULL,
    cand_office_st       CHAR(2)      NULL,
    cand_office_district CHAR(2)      NULL,
    source               VARCHAR(16)  NOT NULL,
    ingest_run_id        BIGINT UNSIGNED NULL
);
