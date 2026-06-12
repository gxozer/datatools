CREATE TABLE staging_committee (
    cmte_id       CHAR(9)      NOT NULL,
    cmte_nm       VARCHAR(200) NULL,
    cmte_tp       CHAR(1)      NULL,
    cmte_dsgn     CHAR(1)      NULL,
    source        VARCHAR(16)  NOT NULL,
    ingest_run_id BIGINT UNSIGNED NULL
);
