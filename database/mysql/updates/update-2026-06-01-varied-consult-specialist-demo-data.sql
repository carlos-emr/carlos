-- Vary the local/demo consultation service-specialist mappings so specialist
-- filtering can be validated against services with distinct result sets.
--
-- Guard this update to the small mock dataset used in dev/test environments.
-- Real provincial specialist imports and production data should not match the
-- exact service IDs, service names, and mock specialist names below.

SET @has_demo_consult_seed := (
  SELECT COUNT(*) = 6
  FROM consultationServices
  WHERE (serviceId = 3 AND serviceDesc = 'Acarology')
     OR (serviceId = 2 AND serviceDesc = 'Cardiology')
     OR (serviceId = 4 AND serviceDesc = 'Cetology')
     OR (serviceId = 5 AND serviceDesc = 'Embryology')
     OR (serviceId = 6 AND serviceDesc = 'Geology')
     OR (serviceId = 1 AND serviceDesc = 'Radiology')
);

SET @spec_john_c := (
  SELECT specId
  FROM professionalSpecialists
  WHERE fName = 'John' AND lName = 'C'
  ORDER BY specId
  LIMIT 1
);

SET @spec_danny_l := (
  SELECT specId
  FROM professionalSpecialists
  WHERE fName = 'Danny' AND lName = 'L'
  ORDER BY specId
  LIMIT 1
);

SET @spec_test_1 := (
  SELECT specId
  FROM professionalSpecialists
  WHERE fName = '1' AND lName = 'Test'
  ORDER BY specId
  LIMIT 1
);

DELETE FROM serviceSpecialists
WHERE @has_demo_consult_seed = 1
  AND @spec_john_c IS NOT NULL
  AND @spec_danny_l IS NOT NULL
  AND @spec_test_1 IS NOT NULL
  AND serviceId IN (1, 2, 3, 4, 5, 6)
  AND specId IN (@spec_john_c, @spec_danny_l, @spec_test_1);

INSERT INTO serviceSpecialists (serviceId, specId)
SELECT serviceId, specId
FROM (
  SELECT 3 AS serviceId, @spec_john_c AS specId UNION ALL
  SELECT 2, @spec_danny_l UNION ALL
  SELECT 4, @spec_john_c UNION ALL
  SELECT 4, @spec_danny_l UNION ALL
  SELECT 5, @spec_test_1 UNION ALL
  SELECT 6, @spec_john_c UNION ALL
  SELECT 1, @spec_danny_l UNION ALL
  SELECT 1, @spec_test_1
) demo_mappings
WHERE @has_demo_consult_seed = 1
  AND @spec_john_c IS NOT NULL
  AND @spec_danny_l IS NOT NULL
  AND @spec_test_1 IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM serviceSpecialists ss
    WHERE ss.serviceId = demo_mappings.serviceId
      AND ss.specId = demo_mappings.specId
  );
