CREATE TABLE candidate_committee (
    candidate_id BIGINT UNSIGNED NOT NULL,
    committee_id BIGINT UNSIGNED NOT NULL,
    linkage_type CHAR(1)         NOT NULL,
    PRIMARY KEY (candidate_id, committee_id, linkage_type),
    CONSTRAINT fk_cc_candidate FOREIGN KEY (candidate_id) REFERENCES candidate (id),
    CONSTRAINT fk_cc_committee FOREIGN KEY (committee_id) REFERENCES committee (id)
);
