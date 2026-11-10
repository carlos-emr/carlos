/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import io.github.carlos_emr.carlos.commn.model.CdsClientForm;
import io.github.carlos_emr.carlos.web.Cds4ReportUIBean.SingleMultiAdmissions;
import org.apache.commons.collections4.keyvalue.DefaultKeyValue;
import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.collections4.map.MultiValueMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class JdkCollectionCompatibilityUnitTest {
    @Test
    void shouldAppendDuplicateAndNullForms_inBothCohortMaps() {
        SingleMultiAdmissions admissions = new SingleMultiAdmissions();
        MultiValueMap<Integer, CdsClientForm> legacy = new MultiValueMap<>();
        CdsClientForm first = new CdsClientForm();
        CdsClientForm second = new CdsClientForm();
        for (CdsClientForm form : new CdsClientForm[]{first, second, first, null}) {
            legacy.put(2, form);
            admissions.addSingleAdmissionToCohort(2, form);
            admissions.addMultipleAdmissionToCohort(2, form);
        }
        var expected = new ArrayList<>(legacy.getCollection(2));
        assertThat(admissions.singleAdmissionCohortBuckets.get(2)).hasSize(expected.size());
        assertThat(admissions.multipleAdmissionCohortBuckets.get(2)).hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertThat(admissions.singleAdmissionCohortBuckets.get(2).get(i)).isSameAs(expected.get(i));
            assertThat(admissions.multipleAdmissionCohortBuckets.get(2).get(i)).isSameAs(expected.get(i));
        }
        assertThat(admissions.singleAdmissionCohortBuckets.get(3)).isNull();
        assertThat(admissions.multipleAdmissionCohortBuckets.get(3)).isNull();
    }

    @Test
    void shouldKeepDiseaseOrderAndReplacements_forOntarioMd() throws Exception {
        Map<String, String> diseases = new OntarioMD().getDiseaseList();
        LinkedMap<String, String> legacy = new LinkedMap<>();
        // Frozen from release/2026.08 before this migration, not from the new result.
        try (var fixture = getClass().getResourceAsStream("/utility/ontario-md-diseases.tsv")) {
            assertThat(fixture).isNotNull();
            new String(fixture.readAllBytes(), StandardCharsets.UTF_8).lines().forEach(line -> {
                String[] pair = line.split("\t", -1);
                legacy.put(pair[0], pair[1]);
            });
        }
        assertThat(new ArrayList<>(diseases.entrySet())).containsExactlyElementsOf(legacy.entrySet());
        String first = diseases.keySet().iterator().next();
        diseases.put(first, "replacement");
        legacy.put(first, "replacement");
        assertThat(new ArrayList<>(diseases.entrySet())).containsExactlyElementsOf(legacy.entrySet());
        assertThat(new ArrayList<>(diseases.keySet()).subList(0, 3))
                .containsExactly("Acne", "AcuteBronchitis", "AddictionMedicine");
    }

    @Test
    void shouldKeepNullableMutablePairs_forReportFields() {
        for (String key : new String[]{null, "report_measurement_synthetic"}) {
            for (String value : new String[]{null, "", "é"}) {
                var legacy = new DefaultKeyValue<>(key, value);
                Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<>(key, value);
                assertThat(entry.getKey()).isEqualTo(legacy.getKey());
                assertThat(entry.getValue()).isEqualTo(legacy.getValue());
                assertThat(entry.setValue("replacement")).isEqualTo(legacy.setValue("replacement"));
                assertThat(entry.getValue()).isEqualTo(legacy.getValue()).isEqualTo("replacement");
            }
        }
    }
}
