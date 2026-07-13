CREATE TABLE IF NOT EXISTS erefer_attachment (
    id INT PRIMARY KEY AUTO_INCREMENT,
    demographic_no INT,
    created DATETIME,
    archived BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS erefer_attachment_data (
    erefer_attachment_id INT,
    lab_id INT,
    lab_type VARCHAR(20),
    PRIMARY KEY(erefer_attachment_id, lab_id, lab_type)
);