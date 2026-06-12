CREATE TABLE staging_linkage (
    cand_id       CHAR(9)     NOT NULL,
    cmte_id       CHAR(9)     NOT NULL,
    cmte_tp       CHAR(1)     NULL,
    cmte_dsgn     CHAR(1)     NULL,
    linkage_id    VARCHAR(12) NULL,
    source        VARCHAR(16) NOT NULL,
    ingest_run_id BIGINT UNSIGNED NULL
);
