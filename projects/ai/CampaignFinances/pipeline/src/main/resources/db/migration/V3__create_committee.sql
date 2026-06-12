CREATE TABLE committee (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    fec_committee_id CHAR(9)      NOT NULL,
    name             VARCHAR(200) NULL,
    type             CHAR(1)      NULL,
    designation      CHAR(1)      NULL,
    UNIQUE KEY uk_committee_fec_id (fec_committee_id)
);
