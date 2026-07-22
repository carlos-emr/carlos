-- Ensure outbound archive foreign-key targets use an engine that supports FKs.
ALTER TABLE `document` ENGINE=InnoDB;
ALTER TABLE `emailConfig` ENGINE=InnoDB;
ALTER TABLE `emailLog` ENGINE=InnoDB;
ALTER TABLE `emailAttachment` ENGINE=InnoDB;
