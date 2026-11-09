/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.MeasurementResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.*;
import io.github.carlos_emr.carlos.webserv.rest.util.ProductionRestObjectMappers;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("unit")
class EmbeddedRecordSnapshotsUnitTest extends CarlosUnitTestBase {
    private LoggedInInfo actor;
    private SecurityInfoManager security;
    @BeforeEach void permissions() {
        actor = mock(LoggedInInfo.class); security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class,security);
    }
    static Stream<ObjectMapper> mappers() {
        return Stream.of(ProductionRestObjectMappers.sessionSurface(),ProductionRestObjectMappers.oauthSurface());
    }
    private Demographic patient() {
        Demographic patient = new Demographic(); patient.setDemographicNo(42);
        patient.setFirstName("Synthetic"); patient.setLastName("Patient");
        patient.setYearOfBirth("1980"); patient.setMonthOfBirth("07"); patient.setDateOfBirth("15");
        patient.setSin("PRIVATE_SIN"); patient.setHin("PRIVATE_HIN"); patient.setSubRecord(Set.of(43,44));
        patient.setDateJoined(new Date(123456789)); return patient;
    }
    private Provider provider() {
        Provider provider = new Provider(); provider.setProviderNo("7"); provider.setFirstName("Test"); provider.setLastName("Clinician");
        provider.setBillingNo("PRIVATE_BILLING"); provider.setOhipNo("PRIVATE_OHIP"); provider.setHsoNo("PRIVATE_HSO"); provider.setRmaNo("PRIVATE_RMA");
        provider.setDob(new Date(123456789)); return provider;
    }
    @ParameterizedTest @MethodSource("mappers")
    void shouldRetainEveryEstablishedPatientMemberAndValue_exceptMinimizedIdentifiersAndIdentifierText(ObjectMapper mapper) throws Exception {
        Demographic original = patient();
        var detached = EmbeddedRecordSnapshots.demographic(actor,original);
        ObjectNode expected = mapper.valueToTree(original), actual = mapper.valueToTree(detached);
        expected.remove(List.of("sin","hin","standardIdentificationHtml","label")); actual.remove(List.of("sin","hin","standardIdentificationHtml","label"));
        assertThat(actual).isEqualTo(expected);
        assertThat(mapper.writeValueAsString(detached)).doesNotContain("PRIVATE_SIN","PRIVATE_HIN");
        assertThat(detached.getLabel()).contains("Patient", "Synthetic");
        assertThat(original.getSin()).isEqualTo("PRIVATE_SIN"); assertThat(original.getHin()).isEqualTo("PRIVATE_HIN");
    }
    @ParameterizedTest @MethodSource("mappers")
    void shouldPreserveAuthorizedProviderValues_andRedactBillingWithoutPermission(ObjectMapper mapper) throws Exception {
        Provider source = provider();
        when(security.hasPrivilege(actor,"_billing","r",(String)null)).thenReturn(true);
        assertThat(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(EmbeddedRecordSnapshots.provider(actor,source))).isEqualTo(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(source));
        when(security.hasPrivilege(actor,"_billing","r",(String)null)).thenReturn(false);
        assertThat(mapper.writeValueAsString(EmbeddedRecordSnapshots.provider(actor,source))).doesNotContain("PRIVATE_");
        assertThat(source.getBillingNo()).isEqualTo("PRIVATE_BILLING");
    }
    @ParameterizedTest @MethodSource("mappers")
    void shouldRetainSpecialistAndMeasurementWireValues_withoutRawEntityFields(ObjectMapper mapper) {
        var specialist = new ProfessionalSpecialist(9); specialist.setFirstName("Test"); specialist.setLastName("Specialist");
        assertThat(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(EmbeddedRecordSnapshots.professionalSpecialist(actor,specialist))).isEqualTo(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(specialist));
        var measurement = new Measurement(1); measurement.setType("BP"); measurement.setDataField("120/80");
        measurement.setDateObserved(new Date(123456789));
        assertThat(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(EmbeddedRecordSnapshots.measurement(actor,measurement))).isEqualTo(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(measurement));
        var response = new MeasurementResponse(); response.addMeasurements(List.of(measurement));
        assertThat(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(response).at("/measurements/BP/0")).isEqualTo(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(measurement));
        measurement.setDataField("changed"); assertThat(response.getMeasurementSnapshots().get("BP").getFirst().getDataField()).isEqualTo("120/80");
    }
    @ParameterizedTest @MethodSource("mappers")
    void shouldExposeTheEstablishedNestedNames_withoutCompatibilityAccessorDuplicates(ObjectMapper mapper) throws Exception {
        var appointment = new AppointmentTo1(); appointment.setDemographic(patient()); appointment.setProvider(provider());
        var wire = mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(appointment);
        assertThat(wire.at("/demographic/firstName").asText()).isEqualTo("Synthetic");
        assertThat(wire.at("/provider/providerNo").asText()).isEqualTo("7");
        assertThat(wire.has("demographicSnapshot")).isFalse(); assertThat(wire.has("providerSnapshot")).isFalse();
        var decoded = mapper.readValue(mapper.writeValueAsBytes(appointment),AppointmentTo1.class);
        assertThat(decoded.getDemographic().getDemographicNo()).isEqualTo(42);
        assertThat(decoded.getProvider().getProviderNo()).isEqualTo("7");
        assertThat(mapper.writeValueAsString(decoded)).doesNotContain("PRIVATE_");
    }
    @ParameterizedTest @MethodSource("mappers")
    void shouldSnapshotNoteIssues_withoutTraversingTheReverseClinicalNotesCollection(ObjectMapper mapper) throws Exception {
        var issue = new Issue(); issue.setId(8L); issue.setCode("A"); issue.setDescription("Retained issue");
        var row = spy(new CaseManagementIssue()); row.setId(3L); row.setDemographic_no(42); row.setIssue_id(8); row.setIssue(issue);
        doThrow(new AssertionError("Reverse notes graph must not be traversed")).when(row).getNotes();
        var note = new NoteTo1(); note.setIssues(Set.of(row));
        row.setResolved(true); issue.setDescription("changed");
        var json = mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(note);
        assertThat(json.at("/issues/0/issue/description").asText()).isEqualTo("Retained issue");
        assertThat(json.at("/issues/0/resolved").asBoolean()).isFalse();
        assertThat(json.has("issueSnapshots")).isFalse(); verify(row,never()).getNotes();
        var decoded = mapper.readValue(mapper.writeValueAsBytes(note),NoteTo1.class);
        assertThat(decoded.getIssues()).hasSize(1); assertThat(decoded.getIssues().iterator().next().getIssue_id()).isEqualTo(8);
    }
    @Test void shouldRefuseUninitializedIssues_insteadOfReportingAnEmptyCollection() {
        var issues = new org.hibernate.collection.spi.PersistentSet<CaseManagementIssue>();
        assertThatThrownBy(() -> new NoteTo1().setIssues(issues)).isInstanceOf(ConversionException.class);
    }
    @Test void shouldRefuseUninitializedEntityProxies_beforeReadingAnyFields() {
        var source = mock(Demographic.class,withSettings().extraInterfaces(org.hibernate.proxy.HibernateProxy.class));
        var initializer = mock(org.hibernate.proxy.LazyInitializer.class);
        when(((org.hibernate.proxy.HibernateProxy)source).getHibernateLazyInitializer()).thenReturn(initializer);
        when(initializer.isUninitialized()).thenReturn(true);
        when(((org.hibernate.proxy.HibernateProxy)source).asHibernateProxy()).thenReturn((org.hibernate.proxy.HibernateProxy)source);
        assertThatThrownBy(() -> EmbeddedRecordSnapshots.demographic(actor,source)).isInstanceOf(ConversionException.class);
        verify(source,never()).getDemographicNo();
    }
    @Test void shouldOwnMutableDateValues_inBothDirections() {
        var source = provider(); var snapshot = EmbeddedRecordSnapshots.provider(null,source);
        long expected = source.getDob().getTime(); source.getDob().setTime(0);
        assertThat(snapshot.getDob().getTime()).isEqualTo(expected);
        snapshot.getDob().setTime(0); assertThat(snapshot.getDob().getTime()).isEqualTo(expected);
    }
    @Test void shouldRetainIncompleteIdentity_withoutInvokingTheLegacyHtmlFormatter() {
        var source = mock(Demographic.class); when(source.getDemographicNo()).thenReturn(42);
        var snapshot = EmbeddedRecordSnapshots.demographic(actor,source);
        assertThat(snapshot.getDemographicNo()).isEqualTo(42);
        assertThat(snapshot.getFirstName()).isNull(); assertThat(snapshot.getLastName()).isNull();
        assertThat(snapshot.getStandardIdentificationHtml()).isNull();
    }

    @Test void shouldContainNoJpaEntityFields_inTheNamedResponseGraphs() {
        var pending = new java.util.ArrayDeque<java.lang.reflect.Type>();
        pending.addAll(List.of(AppointmentTo1.class,ConsultationRequestSearchResult.class,
                ConsultationResponseSearchResult.class,MeasurementResponse.class,NoteTo1.class));
        var visited = new java.util.HashSet<java.lang.reflect.Type>();
        while (!pending.isEmpty()) {
            var type = pending.remove(); if (!visited.add(type)) continue;
            if (type instanceof java.lang.reflect.ParameterizedType parameterized) {
                pending.add(parameterized.getRawType()); pending.addAll(List.of(parameterized.getActualTypeArguments()));
            } else if (type instanceof Class<?> cls) {
                assertThat(cls.isAnnotationPresent(jakarta.persistence.Entity.class)).as(cls.getName()).isFalse();
                if (cls.isArray()) { pending.add(cls.getComponentType()); continue; }
                if (!cls.getName().startsWith("io.github.carlos_emr.") || cls.isEnum()) continue;
                for (var field : cls.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) pending.add(field.getGenericType());
                }
            }
        }
    }

    @ParameterizedTest @MethodSource("mappers")
    void shouldPreserveClinicalExtensionValues_withoutSharingTheirEntityOrLosingTimestampShape(ObjectMapper mapper) throws Exception {
        var source = patient();
        var extension = new io.github.carlos_emr.carlos.commn.model.DemographicExt("p",42,"PHU","Retained extension");
        extension.setDateCreated(new Date(0));
        source.setExtras(new io.github.carlos_emr.carlos.commn.model.DemographicExt[]{extension});
        var detached = EmbeddedRecordSnapshots.demographic(actor,source);
        extension.setValue("changed");
        var wire = mapper.readTree(mapper.writeValueAsBytes(detached));
        assertThat(wire.at("/extras/0/value").asText()).isEqualTo("Retained extension");
        assertThat(wire.at("/extras/0/dateCreated").isIntegralNumber()).isTrue();
        assertThat(wire.at("/extras/0/dateCreated").longValue()).isZero();
    }

    @Test void shouldSerializeTheDetachedResponseGraphs_withoutIntroducingNonserializableEntityReplacements() throws Exception {
        var appointment = new AppointmentTo1(); appointment.setDemographic(patient()); appointment.setProvider(provider());
        var billing = new BillingDetailTo1(); var line = new io.github.carlos_emr.carlos.commn.model.BillingONItem(7); line.setFee("12.00");
        billing.setBillingItems(List.of(line)); appointment.setBillingDetail(billing);
        var note = new NoteTo1(); var issue = new CaseManagementIssue(); issue.setId(3L); issue.setIssue(new Issue()); note.setIssues(Set.of(issue));
        var measurements = new MeasurementResponse(); measurements.addMeasurements(List.of(new Measurement(9)));
        for (Object response : List.of(appointment,note,measurements)) {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var output = new java.io.ObjectOutputStream(bytes)) { output.writeObject(response); }
            try (var input = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
                assertThat(input.readObject()).isExactlyInstanceOf(response.getClass());
            }
        }
        assertThat(java.io.ObjectStreamClass.lookup(Measurement.class).getSerialVersionUID()).isEqualTo(2859566890374252474L);
        assertThat(java.io.ObjectStreamClass.lookup(ProfessionalSpecialist.class).getSerialVersionUID()).isEqualTo(-8082870337148089173L);
        assertThat(java.io.ObjectStreamClass.lookup(io.github.carlos_emr.carlos.commn.model.BillingONItem.class).getSerialVersionUID()).isEqualTo(4758912463499371165L);
    }

}
