/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.webserv.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.FavoriteTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.MedicationDetails;
import io.github.carlos_emr.carlos.webserv.rest.util.RestXmlAnnotationIntrospector;
import jakarta.xml.bind.JAXBContext;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class MedicationDetailsUnitTest {
    @Test
    void strictLegacyShapesOmitTheOptInDetails() throws Exception {
        ObjectMapper mapper = restMapper();
        JAXBContext xml = JAXBContext.newInstance(DrugTo1.class, FavoriteTo1.class);
        for (Object legacy : List.of(new DrugTo1(), new FavoriteTo1())) {
            assertThat(mapper.writeValueAsString(legacy)).doesNotContain("medicationDetails");
            StringWriter body = new StringWriter();
            xml.createMarshaller().marshal(legacy, body);
            assertThat(body.toString()).doesNotContain("medicationDetails");
        }
    }

    @Test
    void jsonAndXmlKeepTheRecordedAmountAndEveryComponent() throws Exception {
        var entered = new MedicationDetails();
        entered.setQuantityText(" 7.50 ");
        entered.setDispenseUnit("mL");
        entered.setDosageText("250.0 mg/5 mL + 31.25 mg/5 mL");
        entered.setComponents(List.of(new MedicationDetails.Component("Ingredient A", "250.0", "mg/5 mL"),
                new MedicationDetails.Component("Ingredient B", "31.25", "mg/5 mL")));
        var stored = MedicationDetails.read(entered.getQuantityText(), entered.getDispenseUnit(),
                entered.getDosageText(), entered.encodeComponents());
        assertThat(stored.isComponentDataComplete()).isTrue();
        DrugTo1 dto = new DrugTo1();
        dto.setMedicationDetails(stored);
        ObjectMapper mapper = restMapper();
        String json = mapper.writeValueAsString(dto);
        var details = mapper.readTree(json).path("medicationDetails");
        assertThat(details.path("components").isArray()).isTrue();
        assertThat(details.has("component")).isFalse();
        assertComplete(mapper.readValue(json, DrugTo1.class).getMedicationDetails());
        JAXBContext xml = JAXBContext.newInstance(DrugTo1.class);
        StringWriter body = new StringWriter();
        xml.createMarshaller().marshal(dto, body);
        assertComplete(((DrugTo1) xml.createUnmarshaller().unmarshal(new StringReader(body.toString()))).getMedicationDetails());
        assertThatThrownBy(() -> stored.validateQuantity(7)).isInstanceOf(IllegalArgumentException.class);
        assertThat(MedicationDetails.read("1/2 bottle", null, "250 mg", null).isComponentDataComplete()).isFalse();
    }

    @Test
    void shouldConvertFavoriteWithExactDetailsAndLegacyStrength_viaFavoriteConverter() {
        var converter = new io.github.carlos_emr.carlos.webserv.rest.conversion.FavoriteConverterImpl();
        var loggedInInfo = new io.github.carlos_emr.carlos.utility.LoggedInInfo();
        var provider = new io.github.carlos_emr.carlos.commn.model.Provider();
        provider.setProviderNo("999998");
        loggedInInfo.setLoggedInProvider(provider);

        var optIn = new FavoriteTo1();
        optIn.setFavoriteName("Opt-in Suspension");
        optIn.setBrandName("Synthetic Suspension");
        optIn.setGenericName("Synthetic Suspension");
        optIn.setTakeMin(1f);
        optIn.setTakeMax(1f);
        optIn.setPrn(false);
        var details = new MedicationDetails();
        details.setQuantityText("7.5");
        details.setDispenseUnit("mL");
        details.setDosageText("250 mg/5 mL");
        details.setComponents(List.of(new MedicationDetails.Component("Ingredient A", "250", "mg/5 mL")));
        optIn.setMedicationDetails(details);

        var convertedOptIn = converter.getAsDomainObject(loggedInInfo, optIn);
        assertThat(convertedOptIn.isExactMedicationDetails()).isTrue();
        assertThat(convertedOptIn.getQuantity()).isEqualTo("7.5");
        assertThat(convertedOptIn.getUnitName()).isEqualTo("mL");
        assertThat(convertedOptIn.getDosage()).isEqualTo("250 mg/5 mL");
        assertThat(convertedOptIn.getMedicationComponents()).contains("Ingredient A");

        var clearedComponents = new FavoriteTo1();
        clearedComponents.setFavoriteName("Cleared Components");
        clearedComponents.setTakeMin(1f);
        clearedComponents.setTakeMax(1f);
        clearedComponents.setPrn(false);
        var emptyDetails = new MedicationDetails();
        emptyDetails.setQuantityText("30");
        emptyDetails.setDosageText("500 mg");
        clearedComponents.setMedicationDetails(emptyDetails);

        var convertedCleared = converter.getAsDomainObject(loggedInInfo, clearedComponents);
        assertThat(convertedCleared.isExactMedicationDetails()).isTrue();
        assertThat(convertedCleared.getMedicationComponents()).isNull();

        var legacy = new FavoriteTo1();
        legacy.setFavoriteName("Legacy Tablet");
        legacy.setTakeMin(1f);
        legacy.setTakeMax(1f);
        legacy.setPrn(false);
        legacy.setQuantity(30);
        legacy.setStrength(500f);
        legacy.setStrengthUnit("mg");
        legacy.setRepeats(2);

        var convertedLegacy = converter.getAsDomainObject(loggedInInfo, legacy);
        assertThat(convertedLegacy.isExactMedicationDetails()).isFalse();
        assertThat(convertedLegacy.getDosage()).isEqualTo("500 mg");
        assertThat(convertedLegacy.getQuantity()).isEqualTo("30");
        assertThat(convertedLegacy.getRepeat()).isEqualTo(2);

        convertedOptIn.setId(42);
        convertedOptIn.setDuration("10");
        var back = converter.getAsTransferObject(loggedInInfo, convertedOptIn);
        assertThat(back.getDuration()).isEqualTo(10);
        assertThat(back.getQuantity()).isNull();

        var omittedPrnAndName = new FavoriteTo1();
        omittedPrnAndName.setProviderNo(12345);
        omittedPrnAndName.setTakeMin(1f);
        omittedPrnAndName.setTakeMax(1f);
        var convertedSparse = converter.getAsDomainObject(loggedInInfo, omittedPrnAndName);
        assertThat(convertedSparse.getProviderNo()).isEqualTo("999998");
        assertThat(convertedSparse.getName()).isEmpty();
        assertThat(convertedSparse.isPrn()).isFalse();

        var originalDrug = new io.github.carlos_emr.carlos.commn.model.Drug();
        originalDrug.setGenericName("Amoxicillin");
        originalDrug.setBrandName("Amoxil");
        originalDrug.setDosage("250 mg/5 mL");
        originalDrug.setUnit("mg");
        var updatedExact = new io.github.carlos_emr.carlos.commn.model.Drug();
        updatedExact.setGenericName("Amoxicillin");
        updatedExact.setBrandName("Amoxil");
        updatedExact.setExactMedicationDetails("7.5", "mL", "250 mg/5 mL", null);
        updatedExact.preserveUnchangedMedicationValues(originalDrug);
        assertThat(updatedExact.getUnit()).isEqualTo("mg");
    }

    private static ObjectMapper restMapper() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.setAnnotationIntrospector(new AnnotationIntrospectorPair(new JacksonAnnotationIntrospector(),
                new RestXmlAnnotationIntrospector(mapper.getTypeFactory())));
    }

    private static void assertComplete(MedicationDetails details) {
        assertThat(details.getQuantityText()).isEqualTo(" 7.50 ");
        assertThat(details.getDispenseUnit()).isEqualTo("mL");
        assertThat(details.getDosageText()).isEqualTo("250.0 mg/5 mL + 31.25 mg/5 mL");
        assertThat(details.getComponents()).extracting(MedicationDetails.Component::getName)
                .containsExactly("Ingredient A", "Ingredient B");
        assertThat(details.getComponents()).extracting(MedicationDetails.Component::getStrength)
                .containsExactly("250.0", "31.25");
        assertThat(details.getComponents()).extracting(MedicationDetails.Component::getUnit)
                .containsOnly("mg/5 mL");
    }
}
