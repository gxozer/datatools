CREATE TABLE staging_contribution (
    sub_id          VARCHAR(19)    NOT NULL,
    cmte_id         CHAR(9)        NOT NULL,
    contributor_name VARCHAR(200)  NULL,
    city            VARCHAR(30)    NULL,
    state           CHAR(2)        NULL,
    zip_code        VARCHAR(9)     NULL,
    employer        VARCHAR(38)    NULL,
    occupation      VARCHAR(38)    NULL,
    transaction_dt  CHAR(8)        NULL,
    transaction_amt DECIMAL(14, 2) NULL,
    source          VARCHAR(16)    NOT NULL,
    ingest_run_id   BIGINT UNSIGNED NULL,
    KEY idx_staging_contribution_sub_id (sub_id)
);
