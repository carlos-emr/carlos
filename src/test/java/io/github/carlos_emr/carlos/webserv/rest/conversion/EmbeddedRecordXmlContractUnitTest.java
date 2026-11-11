/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.webserv.rest.to.model.*;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.SchemaOutputResolver;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag("unit")
class EmbeddedRecordXmlContractUnitTest {
    @XmlRootElement(name="records") @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder={"patient","provider","specialist","measurement","issue"})
    public static class EntityEnvelope {
        public Demographic patient;
        public Provider provider;
        public ProfessionalSpecialist specialist;
        public Measurement measurement;
        public CaseManagementIssue issue;
    }
    @XmlRootElement(name="records") @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder={"patient","provider","specialist","measurement","issue"})
    public static class SnapshotEnvelope {
        public EmbeddedDemographicTo1 patient;
        public EmbeddedProviderTo1 provider;
        public EmbeddedProfessionalSpecialistTo1 specialist;
        public EmbeddedMeasurementTo1 measurement;
        public EmbeddedCaseManagementIssueTo1 issue;
    }
    private static JAXBContext context(String provider, Class<?> root) throws Exception {
        return provider.equals("glassfish")
                ? org.glassfish.jaxb.runtime.v2.ContextFactory.createContext(new Class<?>[]{root},Map.of())
                : org.eclipse.persistence.jaxb.JAXBContextFactory.createContext(new Class<?>[]{root},Map.of());
    }
    private static Schema schema(JAXBContext context) throws Exception {
        var documents = new ArrayList<StringWriter>();
        context.generateSchema(new SchemaOutputResolver() {
            @Override public Result createOutput(String namespace,String filename) {
                var document = new StringWriter(); documents.add(document);
                var result = new StreamResult(document); result.setSystemId("memory:/"+filename); return result;
            }
        });
        var factory = javax.xml.validation.SchemaFactory.newDefaultInstance();
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        return factory.newSchema(documents.stream().map(document -> new StreamSource(new StringReader(document.toString()))).toArray(StreamSource[]::new));
    }
    @ParameterizedTest @CsvSource({"glassfish,glassfish","glassfish,moxy","moxy,glassfish","moxy,moxy"})
    void shouldValidateDetachedXmlAgainstTheEntitySchema_andReadItWithTheExistingEntityBindings(String writer,String reader) throws Exception {
        var patient = new Demographic(42,"Synthetic","Patient");
        patient.setYearOfBirth("1980"); patient.setMonthOfBirth("07"); patient.setDateOfBirth("15");
        patient.setSin("PRIVATE_SIN"); patient.setHin("PRIVATE_HIN"); patient.setDateJoined(new Date(123456789));
        var provider = new Provider("p","Clinician","doctor","F","","Test");
        var specialist = new ProfessionalSpecialist(9); specialist.setFirstName("Specialist"); specialist.setLastName("Test");
        var measurement = new Measurement(1); measurement.setType("BP"); measurement.setDataField("120/80"); measurement.setDateObserved(new Date(123456789));
        var issue = new CaseManagementIssue(); issue.setId(3L); issue.setIssue_id(8); issue.setDemographic_no(42);
        var definition = new Issue(); definition.setId(8L); definition.setCode("A"); definition.setDescription("Clinical issue"); issue.setIssue(definition);
        var snapshots = new SnapshotEnvelope();
        snapshots.patient = EmbeddedRecordSnapshots.demographic(null,patient);
        snapshots.provider = EmbeddedRecordSnapshots.provider(null,provider);
        snapshots.specialist = EmbeddedRecordSnapshots.professionalSpecialist(null,specialist);
        snapshots.measurement = EmbeddedRecordSnapshots.measurement(null,measurement);
        snapshots.issue = EmbeddedRecordSnapshots.caseManagementIssue(null,issue);
        var xml = new StringWriter(); context(writer,SnapshotEnvelope.class).createMarshaller().marshal(snapshots,xml);
        assertThat(xml.toString()).doesNotContain("PRIVATE_SIN","PRIVATE_HIN");
        JAXBContext existingBindings = context(reader,EntityEnvelope.class);
        Schema schema = schema(existingBindings);
        schema.newValidator().validate(new StreamSource(new StringReader(xml.toString())));
        var unmarshaller = existingBindings.createUnmarshaller(); unmarshaller.setSchema(schema);
        unmarshaller.setEventHandler(event -> false);
        var result = (EntityEnvelope) unmarshaller.unmarshal(new StringReader(xml.toString()));
        assertThat(result.patient.getDemographicNo()).isEqualTo(42);
        assertThat(result.patient.getFirstName()).isEqualTo("Synthetic");
        assertThat(result.patient.getDateJoined()).isEqualTo(patient.getDateJoined());
        assertThat(result.provider.getProviderNo()).isEqualTo("p");
        var legacyEnvelope = new EntityEnvelope(); legacyEnvelope.patient = patient; legacyEnvelope.provider = provider;
        legacyEnvelope.specialist = specialist; legacyEnvelope.measurement = measurement; legacyEnvelope.issue = issue;
        var oldXml = new StringWriter(); existingBindings.createMarshaller().marshal(legacyEnvelope,oldXml);
        var oldRead = (EntityEnvelope) unmarshaller.unmarshal(new StringReader(oldXml.toString()));
        // Existing bindings also write the organization alias through setFirstName during a native round trip.
        assertThat(oldRead.specialist.getFirstName()).isEqualTo("Specialist Test");
        assertThat(result.specialist.getFirstName()).isEqualTo(oldRead.specialist.getFirstName());
        assertThat(result.measurement.getDataField()).isEqualTo("120/80");
        assertThat(result.measurement.getDateObserved()).isEqualTo(measurement.getDateObserved());
        assertThat(result.issue.getIssue_id()).isEqualTo(8);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"glassfish","moxy"})
    void shouldRetainEveryExistingOwnerSchemaSequence_whenCompatibilityAccessorsUseDifferentJavaNames(String provider) throws Exception {
        var oracle = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                getClass().getResourceAsStream("/xml/rest-embedded-release-baseline.json"));
        for (var fields = oracle.path("rootOrders").fields(); fields.hasNext();) {
            var entry = fields.next(); Class<?> owner = Class.forName("io.github.carlos_emr.carlos.webserv.rest.to.model."+entry.getKey());
            var schemas = new ArrayList<StringWriter>();
            context(provider,owner).generateSchema(new SchemaOutputResolver() {
                @Override public Result createOutput(String ns,String name) {
                    var output = new StringWriter(); schemas.add(output); var result = new StreamResult(output);
                    result.setSystemId("memory:/"+name); return result;
                }
            });
            var actual = new ArrayList<String>();
            String type = Character.toLowerCase(entry.getKey().charAt(0))+entry.getKey().substring(1);
            for (var output : schemas) {
                var factory = io.github.carlos_emr.carlos.utility.XmlUtils.createSecureDocumentBuilderFactory();
                factory.setNamespaceAware(true);
                var document = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new StringReader(output.toString())));
                var types = document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI,"complexType");
                for (int i=0;i<types.getLength();i++) {
                    var candidate = (org.w3c.dom.Element) types.item(i); if (!type.equals(candidate.getAttribute("name"))) continue;
                    for (var node=candidate.getFirstChild();node!=null;node=node.getNextSibling()) {
                        if (!(node instanceof org.w3c.dom.Element sequence) || !"sequence".equals(sequence.getLocalName())) continue;
                        for (var child=sequence.getFirstChild();child!=null;child=child.getNextSibling()) {
                            if (child instanceof org.w3c.dom.Element element && "element".equals(element.getLocalName())) actual.add(element.getAttribute("name"));
                        }
                    }
                }
            }
            var expected = new ArrayList<String>(); entry.getValue().forEach(value -> expected.add(value.asText()));
            if (owner == AppointmentTo1.class) {
                // This branch-local snapshot included the later JSON editing token in XML.
                // The independently pinned beta6 schemas/old readers reject that extra element;
                // keep the frozen input intact while enforcing the published default contract.
                assertThat(expected).contains("editVersion");
                expected.remove("editVersion");
                assertThat(actual).doesNotContain("editVersion");
            }
            assertThat(actual).as(provider+" "+owner.getSimpleName()).containsExactlyElementsOf(expected);
        }
    }

}
