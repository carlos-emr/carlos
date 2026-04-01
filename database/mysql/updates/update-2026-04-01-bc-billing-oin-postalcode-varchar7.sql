-- Expand billingmaster.oin_postalcode from varchar(6) to varchar(7).
-- Canadian postal codes use the format "A1B 2C3" (with a space), which is
-- 7 characters. The previous varchar(6) limit caused truncation errors when
-- creating BC private billing for patients whose postal code was stored with
-- the standard space separator.
-- Reference: OSCAR 19 commits 3fd4e7f066, 5f889292ca, ec606eda68.
-- Idempotent: safe to run multiple times.

ALTER TABLE billingmaster MODIFY oin_postalcode varchar(7) DEFAULT '';
