ALTER TABLE `eform`
    ADD COLUMN `stable` tinyint(1) NOT NULL DEFAULT 1,
    ADD COLUMN `errorLog` tinyblob NULL;