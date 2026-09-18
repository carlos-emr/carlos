-- ---------------------------------------------------------------------------
-- CARLOS demo/development data — lab requisition practitioner numbers
--
-- The dev snapshot seeds formLabReq07 with practitionerNo = '0000--00'. That
-- value is not a practitioner number; it is placeholder text, and it makes the
-- form unsaveable behind the packaged front door.
--
-- formlabreq07.jsp posts practitionerNo back as a hidden field on every save,
-- so the stored value is re-submitted verbatim. ModSecurity's libinjection
-- rule (OWASP CRS 942100, "SQL Injection Attack Detected via libinjection")
-- reads '0000--00' as a numeric literal followed by a '--' SQL comment and
-- scores it CRITICAL. CRITICAL is 5, the packaged inbound anomaly threshold in
-- debian/assets/modsecurity/crs-overrides.conf is also 5, so that one argument
-- reaches the threshold on its own and nginx answers 403 before Tomcat sees
-- the request. Reported as issue #3724: "Can't save edits to eforms through
-- EChart, 403 Error".
--
-- formLabReq10's practitionerNo is empty in the same snapshot, which is why
-- that form saves and the 2007 one does not — the difference the reporter
-- noticed and could not explain.
--
-- The WAF is not wrong here and is deliberately left alone: a field whose
-- entire value parses as SQL is exactly what 942100 exists to catch, and
-- carving practitionerNo out of the injection rules to accommodate placeholder
-- text would weaken a control that protects every other form. The data is what
-- is wrong, so the data is what this fixes.
--
-- Prefer the owning provider's real practitioner number so the demo chart stays
-- coherent; fall back to empty (matching formLabReq10) when the provider has
-- none. Idempotent: both statements are scoped to values containing '--', so a
-- second run matches nothing. Nothing outside the demo dataset is touched —
-- a real practitioner number does not contain '--'.
-- ---------------------------------------------------------------------------

UPDATE formLabReq07 f
  JOIN provider p ON p.provider_no = f.provider_no
   SET f.practitionerNo = COALESCE(NULLIF(p.practitionerNo, ''), '')
 WHERE f.practitionerNo LIKE '%--%';

UPDATE formLabReq07
   SET practitionerNo = ''
 WHERE practitionerNo LIKE '%--%';
