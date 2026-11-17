// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@Tag("dao")
class LabAttachmentSourceDaoIntegrationTest extends CarlosTestBase {
    @PersistenceContext private EntityManager entityManager;
    @Autowired private ConsultDocsDao consultDocs;
    @Autowired private EFormDocsDao eformDocs;
    @Autowired private ConsultResponseDocDao responseDocs;

    private void routed(int number, String source, int patient) {
        entityManager.persist(new PatientLabRouting(number, source, patient));
    }

    @Test
    void shouldReadOnlyMatchingSourceAndPatient_whenConsultLabIdsCollide() {
        ConsultationRequest owner = new ConsultationRequest();
        owner.setDemographicId(42);
        entityManager.persist(owner);
        routed(777, "HL7", 42);
        routed(777, "MDS", 42);
        routed(777, "HL7", 43);
        ConsultDocs lab = new ConsultDocs(owner.getId(), 777, "L", "provider");
        lab.setLabType("MDS");
        consultDocs.persist(lab);
        entityManager.flush();
        entityManager.clear();
        var results = consultDocs.findLabs(owner.getId());
        assertThat(results).hasSize(1);
        assertThat(((PatientLabRouting) results.getFirst()[1]).getLabType()).isEqualTo("MDS");
        assertThat(((PatientLabRouting) results.getFirst()[1]).getDemographicNo()).isEqualTo(42);
        assertThat(((ConsultDocs) results.getFirst()[0]).getLabType()).isEqualTo("MDS");
    }

    @Test
    void shouldRejectAmbiguousLegacyWrite_whenConsultPatientHasTwoSources() {
        ConsultationRequest owner = new ConsultationRequest();
        owner.setDemographicId(42);
        entityManager.persist(owner);
        routed(777, "HL7", 42);
        routed(777, "MDS", 42);
        assertThatThrownBy(() -> consultDocs.persist(new ConsultDocs(owner.getId(), 777, "L", "provider")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ambiguous");
    }

    @Test
    void shouldInferUniquePatientSource_whenLegacyEformClientUsesBareId() {
        EFormData owner = new EFormData();
        owner.setDemographicId(42);
        entityManager.persist(owner);
        routed(777, "HL7", 43);
        routed(777, "MDS", 42);
        EFormDocs lab = new EFormDocs(owner.getId(), 777, "L", "provider");
        eformDocs.persist(lab);
        entityManager.flush();
        entityManager.clear();
        assertThat(lab.getLabType()).isEqualTo("MDS");
        var results = eformDocs.findLabs(owner.getId());
        assertThat(results).hasSize(1);
        assertThat(((PatientLabRouting) results.getFirst()[1]).getLabType()).isEqualTo("MDS");
        assertThat(((PatientLabRouting) results.getFirst()[1]).getDemographicNo()).isEqualTo(42);
    }

    @Test
    void shouldReadOnlyOwnSource_whenResponseLabIdsCollide() {
        ConsultationResponse owner = new ConsultationResponse();
        owner.setDemographicNo(42);
        entityManager.persist(owner);
        routed(777, "HL7", 42);
        routed(777, "CML", 42);
        routed(777, "CML", 43);
        ConsultResponseDoc lab = new ConsultResponseDoc(owner.getId(), 777, "L", "provider");
        lab.setLabType("CML");
        responseDocs.persist(lab);
        entityManager.flush();
        entityManager.clear();
        var results = responseDocs.findLabs(owner.getId());
        assertThat(results).hasSize(1);
        assertThat(((PatientLabRouting) results.getFirst()[1]).getLabType()).isEqualTo("CML");
        assertThat(((PatientLabRouting) results.getFirst()[1]).getDemographicNo()).isEqualTo(42);
    }

    @Test
    void shouldOmitReassignedLab_whenRoutingNoLongerMatchesAttachmentPatient() {
        ConsultationRequest owner = new ConsultationRequest();
        owner.setDemographicId(42);
        entityManager.persist(owner);
        PatientLabRouting routing = new PatientLabRouting(777, "HL7", 42);
        entityManager.persist(routing);
        ConsultDocs lab = new ConsultDocs(owner.getId(), 777, "L", "provider");
        lab.setLabType("HL7");
        consultDocs.persist(lab);
        routing.setDemographicNo(43);
        entityManager.flush();
        entityManager.clear();
        assertThat(consultDocs.findLabs(owner.getId())).isEmpty();
        assertThat(consultDocs.findByRequestIdDocType(owner.getId(), "L")).hasSize(1);
    }
}
