-- The Messenger keeps the text of the chart PDFs it attaches (each as base64) in messagetbl.pdfattachment.
-- A chart PDF with its fonts embedded is about 40 KB, 55 KB as base64, and a BLOB holds 65,535 bytes: with
-- sql_mode not strict, as on a packaged install, MariaDB cut a longer text off without a word, so the second
-- PDF of a message was stored in two. MEDIUMBLOB holds 16,777,215 bytes; MessageTbl.PDF_ATTACHMENT_MAX_BYTES
-- is raised to match, and the application still refuses what does not fit.
-- Widening is lossless for every stored message and keeps NULL semantics. Retrying it is safe: MODIFY to the
-- type the column already has changes nothing. InnoDB copies the table once, so on a large message table it
-- takes as long as that copy.
ALTER TABLE messagetbl MODIFY COLUMN pdfattachment MEDIUMBLOB DEFAULT NULL;
