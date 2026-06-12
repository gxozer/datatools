CREATE TABLE donor_link (
    donor_id        BIGINT UNSIGNED NOT NULL,
    contribution_id BIGINT UNSIGNED NOT NULL,
    match_rule      VARCHAR(64)     NOT NULL,
    PRIMARY KEY (donor_id, contribution_id),
    CONSTRAINT fk_dl_donor FOREIGN KEY (donor_id) REFERENCES donor (id),
    CONSTRAINT fk_dl_contribution FOREIGN KEY (contribution_id) REFERENCES contribution (id)
);
