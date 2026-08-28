-- Digital signatures (consultation stamps and signature-pad capture on
-- consultations and prescriptions) are gated per facility by
-- Facility.enableDigitalSignatures, and the seeded Default Facility shipped
-- with it OFF - so on a stock install the signature UI silently never
-- appears until an operator finds Administration > Facility > Enable
-- Digital Signatures. Signatures are a supported, tested workflow; make
-- them the default. Runs once: a site that prefers them off can disable
-- the setting afterwards and it will not be flipped back.
UPDATE Facility SET enableDigitalSignatures = 1;
