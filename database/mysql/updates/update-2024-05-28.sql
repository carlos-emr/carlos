CREATE TABLE emailConfig (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    emailType VARCHAR(20),
    emailProvider VARCHAR(20),
    active BOOLEAN,
    senderFirstName VARCHAR(50),
    senderLastName VARCHAR(50),
    senderEmail VARCHAR(255),
    configDetails VARCHAR(1000)
);

-- Example email configurations for Gmail and Outlook.
-- INSERT INTO emailConfig (emailType, emailProvider, active, senderFirstName, senderLastName, senderEmail, configDetails) VALUES ('SMTP', 'GMAIL', true, 'FIRSTNAME', 'LASTNAME', 'example@gmail.com', '{\"host\":\"smtp.gmail.com\",\"port\":\"587\",\"username\":\"example@gmail.com\",\"password\":\"12345\"}');
-- INSERT INTO emailConfig (emailType, emailProvider, active, senderFirstName, senderLastName, senderEmail, configDetails) VALUES ('SMTP', 'OUTLOOK', true, 'FIRSTNAME', 'LASTNAME', 'example@outlook.com', '{\"host\":\"smtp.office365.com\",\"port\":\"587\",\"username\":\"example@outlook.com\",\"password\":\"12345\"}');
CREATE TABLE emailLog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    configId BIGINT,
    fromEmail VARCHAR(255),
    toEmail VARCHAR(255),
    subject VARCHAR(1024),
    body BLOB,
    status VARCHAR(20),
    errorMessage VARCHAR(1000),
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    encryptedMessage BLOB,
    password VARCHAR(50),
    passwordClue VARCHAR(1024),
    isEncrypted BOOLEAN,
    isAttachmentEncrypted BOOLEAN,
    chartDisplayOption VARCHAR(20),
    transactionType VARCHAR(20),
    demographicNo INT,
    providerNo INT,
    additionalParams VARCHAR(1000),
    FOREIGN KEY (configId) REFERENCES emailConfig (id)
);

CREATE TABLE emailAttachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    logId BIGINT,
    fileName VARCHAR(100),
    filePath VARCHAR(500),
    documentType VARCHAR(20),
    documentId INT,
    FOREIGN KEY (logId) REFERENCES emailLog (id)
);

INSERT INTO `property`(`name`, `value`, `provider_no`) VALUES ('email_communication', 'electronic_communication_consent', NULL);

INSERT INTO `secObjectName` (`objectName`, `description`, `orgapplicable`) VALUES ('_admin.email', 'Configure & Manage Emails', '0');
INSERT INTO `secObjPrivilege` VALUES ('admin','_admin.email','x',0,'999998');
INSERT INTO `secObjectName`(`objectName`, `description`, `orgapplicable`) VALUES ('_email', 'Send and Receive Emails', 0);
INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('-1', '_email', 'x', 0, '999999');
INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('admin', '_email', 'x', 0, '999998');
INSERT INTO `secObjPrivilege`(`roleUserGroup`, `objectName`, `privilege`, `priority`, `provider_no`) VALUES ('doctor', '_email', 'x', 0, '999998');