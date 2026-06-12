CREATE TABLE donor (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    canonical_name VARCHAR(200) NOT NULL,
    employer       VARCHAR(38)  NULL,
    occupation     VARCHAR(38)  NULL,
    city           VARCHAR(30)  NULL,
    state          CHAR(2)      NULL,
    zip5           CHAR(5)      NULL,
    KEY idx_donor_match (canonical_name, zip5)
);
