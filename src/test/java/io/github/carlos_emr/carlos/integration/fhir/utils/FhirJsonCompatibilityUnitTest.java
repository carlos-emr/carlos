package io.github.carlos_emr.carlos.integration.fhir.utils;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.Immunization;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.StringType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Expectations established with HAPI 8.4.3 before the dependency upgrade. */
@Tag("unit")
@Tag("fhir")
class FhirJsonCompatibilityUnitTest {
    private static final FhirContext FHIR = FhirContext.forDstu3();
    private static final ObjectMapper JSON = new ObjectMapper();

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
}
