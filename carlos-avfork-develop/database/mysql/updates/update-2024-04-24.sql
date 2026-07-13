CREATE INDEX idx_hrmDocumentId_hd ON HRMDocumentToDemographic(hrmDocumentId);
CREATE INDEX idx_demographicNo_hd ON HRMDocumentToDemographic(demographicNo);
CREATE INDEX idx_hrmDocumentId_hp ON HRMDocumentToProvider(hrmDocumentId);
CREATE INDEX idx_signedOff_providerNo_hp ON HRMDocumentToProvider(signedOff, providerNo);