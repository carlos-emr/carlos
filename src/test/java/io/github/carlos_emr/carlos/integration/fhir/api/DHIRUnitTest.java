/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.fhir.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.Immunization;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Reference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** Tests the exact gateway payload used by the live prevention submission JSP. */
@Tag("unit")
@Tag("fhir")
class DHIRUnitTest {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES).build();

    @ParameterizedTest
    @ValueSource(strings = {"0.123456789012345678901", "2.500", "0.0000001", "0.0000001000"})
    void shouldPreserveClinicalPayload_whenEncodingGatewayEnvelope(String amount) throws Exception {
        Patient patient = new Patient();
        patient.addName().setFamily("Synthetic Élodie 李").addGiven(" ").addGiven(" Kept ");
        patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue("\t");
        Immunization dose = new Immunization().setLotNumber(" ");
        dose.getDoseQuantity().setValue(new BigDecimal(amount));
        dose.addPractitioner().setActor(new Reference("Practitioner/1").setDisplay(" "));
        Bundle bundle = new Bundle().setType(Bundle.BundleType.MESSAGE);
        bundle.addEntry().setFullUrl("urn:uuid:patient").setResource(patient);
        bundle.addEntry().setFullUrl("urn:uuid:dose").setResource(dose);
        String destination = "https://example.invalid/dhir?source=\"synthetic\"";
        String requestId = "synthetic-request\n\"id\"";

        String payload = DHIR.encodeSubmission(bundle, destination, requestId);
        var envelope = JSON.readTree(payload);
        var body = envelope.path("body");
        var encodedPatient = body.path("entry").get(0).path("resource");
        var encodedDose = body.path("entry").get(1).path("resource");

        assertThat(envelope.size()).isEqualTo(5);
        assertThat(envelope.path("url").asText()).isEqualTo(destination);
        assertThat(envelope.path("service").asText()).isEqualTo("DHIR");
        assertThat(envelope.path("client-request-id").asText()).isEqualTo(requestId);
        assertThat(envelope.path("client-app-desc").asText()).isEqualTo("EMR");
        assertThat(body.path("resourceType").asText()).isEqualTo("Bundle");
        assertThat(body.path("entry").size()).isEqualTo(2);
        assertThat(body.path("entry").get(0).path("fullUrl").asText()).isEqualTo("urn:uuid:patient");
        assertThat(encodedPatient.path("name").get(0).path("given"))
                .isEqualTo(JSON.readTree("[\" Kept \"]"));
        assertThat(encodedPatient.path("telecom").get(0).has("value")).isFalse();
        assertThat(encodedDose.has("lotNumber")).isFalse();
        assertThat(encodedDose.path("practitioner").get(0).path("actor").has("display")).isFalse();
        assertThat(encodedDose.path("doseQuantity").path("value").decimalValue()).isEqualTo(new BigDecimal(amount));
        assertThat(payload).contains(amount + "\n");
        assertThat(dose.getLotNumber()).isEqualTo(" ");
        assertThat(patient.getNameFirstRep().getGiven().get(0).getValue()).isEqualTo(" ");
    }

    @Test
    void shouldRefuseGatewayPayload_whenBundleCannotBeNormalized() {
        Immunization dose = new Immunization();
        dose.getDoseQuantity().setValue(new BigDecimal("1".repeat(1001) + ".5"));
        Bundle bundle = new Bundle();
        bundle.addEntry().setResource(dose);

        assertThatIllegalStateException().isThrownBy(() -> DHIR.encodeSubmission(bundle,
                "https://example.invalid/dhir", "synthetic-request"))
                .withMessage("Unable to encode the FHIR JSON compatibility contract");
    }
}
