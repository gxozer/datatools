CREATE TABLE candidate (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    fec_candidate_id CHAR(9)      NOT NULL,
    name             VARCHAR(200) NOT NULL,
    office           CHAR(1)      NOT NULL,
    party            VARCHAR(3)   NULL,
    state            CHAR(2)      NULL,
    district         CHAR(2)      NULL,
    UNIQUE KEY uk_candidate_fec_id (fec_candidate_id)
);
