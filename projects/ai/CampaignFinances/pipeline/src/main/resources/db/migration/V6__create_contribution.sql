CREATE TABLE contribution (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    source            VARCHAR(16)    NOT NULL,
    source_record_id  VARCHAR(19)    NOT NULL,
    amount            DECIMAL(14, 2) NOT NULL,
    contribution_date DATE           NULL,
    donor_id          BIGINT UNSIGNED NULL,
    committee_id      BIGINT UNSIGNED NOT NULL,
    UNIQUE KEY uk_contribution_source (source, source_record_id),
    KEY idx_contribution_committee (committee_id),
    KEY idx_contribution_donor (donor_id),
    CONSTRAINT fk_contribution_committee FOREIGN KEY (committee_id) REFERENCES committee (id),
    CONSTRAINT fk_contribution_donor FOREIGN KEY (donor_id) REFERENCES donor (id)
);
