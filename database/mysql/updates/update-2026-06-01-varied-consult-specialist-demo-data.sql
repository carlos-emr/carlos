-- Vary the local/demo consultation service-specialist mappings so specialist
-- filtering can be validated against services with distinct result sets.
--
-- Guard this update to the small mock dataset used in dev/test environments.
-- Real provincial specialist imports and production data should not match the
-- exact service names and mock specialist names below.

SET @service_acarology := (
  SELECT serviceId
  FROM consultationServices
  WHERE serviceDesc = 'Acarology'
  ORDER BY serviceId
  LIMIT 1
);

SET @service_cardiology := (
  SELECT serviceId
  FROM consultationServices
  WHERE serviceDesc = 'Cardiology'
  ORDER BY serviceId
  LIMIT 1
);

SET @service_cetology := (
  SELECT serviceId
  FROM consultationServices
  WHERE serviceDesc = 'Cetology'
  ORDER BY serviceId
  LIMIT 1
);

SET @service_embryology := (
  SELECT serviceId
  FROM consultationServices
  WHERE serviceDesc = 'Embryology'
  ORDER BY serviceId
  LIMIT 1
);

SET @service_geology := (
  SELECT serviceId
  FROM consultationServices
  WHERE serviceDesc = 'Geology'
  ORDER BY serviceId
  LIMIT 1
);

SET @service_radiology := (
  SELECT serviceId
  FROM consultationServices
  WHERE serviceDesc = 'Radiology'
  ORDER BY serviceId
  LIMIT 1
);

SET @has_demo_consult_seed := (
  @service_acarology IS NOT NULL
  AND @service_cardiology IS NOT NULL
  AND @service_cetology IS NOT NULL
  AND @service_embryology IS NOT NULL
  AND @service_geology IS NOT NULL
  AND @service_radiology IS NOT NULL
  AND (
    SELECT COUNT(*) = 6
    FROM consultationServices
    WHERE serviceDesc IN (
      'Acarology',
      'Cardiology',
      'Cetology',
      'Embryology',
      'Geology',
      'Radiology'
    )
  )
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
  WHERE fName = 'Test' AND lName = '1'
  ORDER BY specId
  LIMIT 1
);

DELETE FROM serviceSpecialists
WHERE @has_demo_consult_seed = 1
  AND @spec_john_c IS NOT NULL
  AND @spec_danny_l IS NOT NULL
  AND @spec_test_1 IS NOT NULL
  AND serviceId IN (
    @service_acarology,
    @service_cardiology,
    @service_cetology,
    @service_embryology,
    @service_geology,
    @service_radiology
  )
  AND specId IN (@spec_john_c, @spec_danny_l, @spec_test_1);

INSERT INTO serviceSpecialists (serviceId, specId)
SELECT serviceId, specId
FROM (
  SELECT @service_acarology AS serviceId, @spec_john_c AS specId UNION ALL
  SELECT @service_cardiology, @spec_danny_l UNION ALL
  SELECT @service_cetology, @spec_john_c UNION ALL
  SELECT @service_cetology, @spec_danny_l UNION ALL
  SELECT @service_embryology, @spec_test_1 UNION ALL
  SELECT @service_geology, @spec_john_c UNION ALL
  SELECT @service_radiology, @spec_danny_l UNION ALL
  SELECT @service_radiology, @spec_test_1
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
