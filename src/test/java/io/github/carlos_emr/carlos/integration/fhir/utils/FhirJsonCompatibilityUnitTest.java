/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.fhir.utils;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.Immunization;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.StringType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** Pins legacy omissions and safe clinical decimal/primitive metadata handling. */
@Tag("unit")
@Tag("fhir")
class FhirJsonCompatibilityUnitTest {
    private static final FhirContext FHIR = FhirContext.forDstu3();
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES).build();

    @Test
    void shouldOmitOnlyBlankPrimitives_withoutMutatingPatientOrXml() throws Exception {
        Patient patient = new Patient();
        patient.setId("synthetic-patient");
        patient.addName().setFamily("Synthetic Élodie 李").addGiven(" ").addGiven(" Kept ").addGiven("\t\n");
        patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue(" ");
        String xml = FHIR.newXmlParser().encodeResourceToString(patient);

        JsonNode encoded = JSON.readTree(FhirJsonCompatibility.encode(FHIR, patient));

        assertThat(encoded).isEqualTo(JSON.readTree("""
                {"resourceType":"Patient","id":"synthetic-patient",
                 "name":[{"family":"Synthetic Élodie 李","given":[" Kept "]}],
                 "telecom":[{"system":"phone"}]}
                """));
        assertThat(patient.getNameFirstRep().getGiven().get(0).getValue()).isEqualTo(" ");
        assertThat(patient.getNameFirstRep().getGiven().get(2).getValue()).isEqualTo("\t\n");
        assertThat(FHIR.newXmlParser().encodeResourceToString(patient)).isEqualTo(xml);
    }

    @Test
    void shouldPreservePrimitiveMetadata_whenBlankRepeatedValuesHaveExtensionsOrIds() throws Exception {
        Patient patient = new Patient();
        var name = patient.addName().setFamily("Synthetic");
        name.addGivenElement().setValue(" ").addExtension(new Extension("https://example.invalid/origin", new StringType("source")));
        name.addGivenElement().setValue("\t").setId("retained-id");
        name.addGiven("Kept");

        JsonNode encoded = JSON.readTree(FhirJsonCompatibility.encode(FHIR, patient)).path("name").get(0);

        assertThat(encoded.path("given")).isEqualTo(JSON.readTree("[null,null,\"Kept\"]"));
        assertThat(encoded.path("_given")).isEqualTo(JSON.readTree("""
                [{"extension":[{"url":"https://example.invalid/origin","valueString":"source"}]},
                 {"id":"retained-id"},null]
                """));
    }

    @Test
    void shouldPreserveOriginCodingAndBundleEntries_whenProviderDisplayIsBlank() throws Exception {
        Immunization dose = new Immunization();
        dose.setId("synthetic-dose");
        dose.setPrimarySource(false);
        dose.getReportOrigin().addCoding().setSystem("http://hl7.org/fhir/immunization-origin")
                .setCode("provider").setDisplay(" ");
        Bundle bundle = new Bundle().setType(Bundle.BundleType.MESSAGE);
        bundle.addEntry().setFullUrl("urn:uuid:synthetic-dose").setResource(dose);

        JsonNode encoded = JSON.readTree(FhirJsonCompatibility.encode(FHIR, bundle));

        assertThat(encoded.path("entry").size()).isEqualTo(1);
        JsonNode resource = encoded.path("entry").get(0).path("resource");
        assertThat(resource.path("primarySource").asBoolean()).isFalse();
        assertThat(resource.path("reportOrigin").path("coding")).isEqualTo(JSON.readTree("""
                [{"system":"http://hl7.org/fhir/immunization-origin","code":"provider"}]
                """));
        assertThat(dose.getReportOrigin().getCodingFirstRep().getDisplay()).isEqualTo(" ");
        assertThat(FHIR.newJsonParser().parseResource(Bundle.class, encoded.toString()).getEntry()).hasSize(1);
    }

    @Test
    void shouldKeepOriginalEncoding_whenAllValuesHaveContent() {
        Patient patient = new Patient();
        patient.addName().setFamily(" Synthetic ").addGiven("Élodie 李");
        assertThat(FhirJsonCompatibility.encode(FHIR, patient))
                .isEqualTo(FHIR.newJsonParser().setPrettyPrint(true).encodeResourceToString(patient));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.123456789012345678901", "2.500", "123456789012345678901234567890.125",
            "0.0000001", "0.0000001000"})
    void shouldPreserveExactClinicalDecimals_whenAnotherFieldIsOmitted(String amount) throws Exception {
        Immunization dose = new Immunization();
        dose.setLotNumber(" ");
        dose.getDoseQuantity().setValue(new BigDecimal(amount));

        String encoded = FhirJsonCompatibility.encode(FHIR, dose);
        assertThat(JSON.readTree(encoded).path("doseQuantity").path("value").decimalValue())
                .isEqualTo(new BigDecimal(amount));
        assertThat(encoded).contains(amount + "\n");
        assertThat(dose.getDoseQuantity().getValue()).isEqualTo(new BigDecimal(amount));
    }

    @Test
    void shouldKeepMetadataAligned_whenAnEarlierBlankValueHasNoExtension() throws Exception {
        Patient patient = new Patient();
        var name = patient.addName();
        name.addGiven(" ");
        name.addGivenElement().setValue("Kept")
                .addExtension(new Extension("https://example.invalid/origin", new StringType("source")));

        JsonNode encoded = JSON.readTree(FhirJsonCompatibility.encode(FHIR, patient)).path("name").get(0);

        assertThat(encoded.path("given")).isEqualTo(JSON.readTree("[\"Kept\"]"));
        assertThat(encoded.path("_given").size()).isEqualTo(1);
        assertThat(encoded.path("_given").get(0).path("extension").get(0).path("valueString").asText())
                .isEqualTo("source");
        Patient decoded = FHIR.newJsonParser().parseResource(Patient.class,
                FhirJsonCompatibility.encode(FHIR, patient));
        assertThat(decoded.getNameFirstRep().getGiven()).hasSize(1);
        assertThat(decoded.getNameFirstRep().getGiven().stream()
                .filter(value -> "Kept".equals(value.getValue())).findFirst().orElseThrow()
                .getExtensionFirstRep().getValue().primitiveValue())
                .isEqualTo("source");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void shouldDropEmptyRepetitions_withMetadataBeforeOrAfterTheBlank(int position) throws Exception {
        Patient patient = new Patient();
        var name = patient.addName();
        for (int index = 0; index < 3; index++) {
            if (index == position) {
                name.addGiven("\t ");
            } else {
                name.addGivenElement().setValue("Kept-" + index)
                        .addExtension(new Extension("https://example.invalid/origin", new StringType("source-" + index)));
            }
        }

        String encoded = FhirJsonCompatibility.encode(FHIR, patient);
        Patient decoded = FHIR.newJsonParser().parseResource(Patient.class, encoded);

        assertThat(decoded.getNameFirstRep().getGiven()).hasSize(2);
        for (var given : decoded.getNameFirstRep().getGiven()) {
            assertThat(given.getExtensionFirstRep().getValue().primitiveValue())
                    .isEqualTo(given.getValue().replace("Kept-", "source-"));
        }
        assertThat(JSON.readTree(encoded).path("name").get(0).path("_given").size()).isEqualTo(2);
    }

    @Test
    void shouldRemoveMultipleEmptySlots_whenOnlyOneValueCarriesMetadata() throws Exception {
        Patient patient = new Patient();
        var name = patient.addName();
        name.addGiven("\t");
        name.addGiven(" ");
        name.addGivenElement().setValue("Kept")
                .addExtension(new Extension("https://example.invalid/origin", new StringType("source")));
        name.addGiven(" ");

        JsonNode encoded = JSON.readTree(FhirJsonCompatibility.encode(FHIR, patient)).path("name").get(0);

        assertThat(encoded).isEqualTo(JSON.readTree("""
                {"given":["Kept"],"_given":[{"extension":[{"url":"https://example.invalid/origin","valueString":"source"}]}]}
                """));
    }

    @Test
    void shouldOmitEmptyArray_whenEveryValueIsBlankWithoutMetadata() throws Exception {
        Patient patient = new Patient();
        patient.addName().setFamily("Kept").addGiven(" ").addGiven("\t");

        JsonNode encoded = JSON.readTree(FhirJsonCompatibility.encode(FHIR, patient)).path("name").get(0);

        assertThat(encoded).isEqualTo(JSON.readTree("{\"family\":\"Kept\"}"));
    }

    @Test
    void shouldMatchHapiLayout_whenRewritingBlankValues() {
        Immunization dose = new Immunization();
        dose.setLotNumber(" ");
        dose.getDoseQuantity().setValue(new BigDecimal("2.500"));
        String encoded = FhirJsonCompatibility.encode(FHIR, dose);
        dose.setLotNumber(null);

        assertThat(encoded).isEqualTo(FHIR.newJsonParser().setPrettyPrint(true).encodeResourceToString(dose));
    }

    @Test
    void shouldRefuseExport_whenNormalizationExceedsReaderLimits() {
        Immunization dose = new Immunization();
        dose.getDoseQuantity().setValue(new BigDecimal("1".repeat(1001) + ".5"));

        assertThatIllegalStateException().isThrownBy(() -> FhirJsonCompatibility.encode(FHIR, dose))
                .withMessage("Unable to encode the FHIR JSON compatibility contract")
                .withCauseInstanceOf(com.fasterxml.jackson.core.exc.StreamConstraintsException.class);
    }

    @Test
    void shouldCentralizeOutboundJsonEncoding_inProductionSources() throws Exception {
        var bypasses = new ArrayList<Path>();
        try (var paths = Files.walk(Path.of("src/main"))) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".jsp"))
                    .filter(p -> !p.endsWith("FhirJsonCompatibility.java")).toList()) {
                String source = Files.readString(path);
                if (source.contains("newJsonParser(") && source.matches("(?s).*encodeResourceTo(String|Writer)\\s*\\(.*")) {
                    bypasses.add(path);
                }
            }
        }
        assertThat(bypasses).as("Outbound FHIR JSON must use the compatibility encoder").isEmpty();
        assertThat(Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/prevention/dhirSubmission.jsp")))
                .contains("DHIR.encodeSubmission(bundle,");
    }

    @Test
    @SuppressWarnings("java:S5838") // Boolean assertions keep a failed check from logging the 20M-character payload.
    void shouldPreserveLargeAttachments_whenNormalizingAnotherPrimitive() {
        // Base64 crosses Jackson's default 20M-character reader limit, although
        // HAPI can encode the existing outbound attachment without that limit.
        byte[] content = new byte[15_000_001];
        content[0] = 14;
        content[content.length - 1] = 127;
        var request = new org.hl7.fhir.dstu3.model.CommunicationRequest();
        request.addPayload().setContent(new org.hl7.fhir.dstu3.model.Attachment()
                .setTitle(" ").setData(content));

        String encoded = FhirJsonCompatibility.encode(FHIR, request);

        // A boolean assertion avoids printing the entire attachment on failure.
        assertThat(encoded.contains(java.util.Base64.getEncoder().encodeToString(content))).isTrue();
        assertThat(encoded.contains("\"title\"")).isFalse();
        assertThat(request.getPayloadFirstRep().getContentAttachment().getTitle()).isEqualTo(" ");
    }
}
