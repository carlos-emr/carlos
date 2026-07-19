-- for document upload, review.

ALTER TABLE `document` ADD COLUMN report_media INT;
ALTER TABLE `document` ADD COLUMN sent_date_time DATETIME;

CREATE TABLE IF NOT EXISTS `document_review` (
        `id` int auto_increment primary key,
        `document_no` int(20) not null,
        `provider_no` varchar(6) not null,
        `date_reviewed` datetime,
        foreign key(document_no) references document(document_no),
        foreign key(provider_no) references provider(provider_no)
    );

INSERT INTO `document_review` (`document_no`, `provider_no`, `date_reviewed`)
SELECT d.document_no, d.reviewer, d.reviewdatetime
FROM `document` d
WHERE d.reviewer IS NOT NULL AND d.reviewer != '' AND d.reviewer != 'null';
