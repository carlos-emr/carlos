/* Copyright (c) 2026 CARLOS Contributors. Published under the GPL GNU General Public License. */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises real JPQL and detached reads so mocks cannot hide missing fetch joins. */
@Tag("integration")
@Tag("dao")
class ConsultationRequestFetchIntegrationTest extends CarlosTestBase {
    @PersistenceContext private EntityManager entityManager;
    @Autowired private ConsultRequestDao detailDao;
    @Autowired @Qualifier("consultationRequestDao") private ConsultationRequestDao listDao;

    @Test
    void shouldKeepPrimaryKeyRelationshipsLazy_whenLoadingOrdinaryRequest() {
        int id = persistRequest(true);
        ConsultationRequest request = detailDao.find(id);
        assertThat(Hibernate.isInitialized(request.getProfessionalSpecialist())).isFalse();
        assertThat(Hibernate.isInitialized(request.getDemographicContact())).isFalse();
        // A non-primary-key lookup resolves eagerly with the current Hibernate mapping.
        assertThat(Hibernate.isInitialized(request.getLookupListItem())).isTrue();
    }

    @Test
    void shouldReadAllDetailAssociations_whenPersistenceContextClosed() {
        int id = persistRequest(true);
        ConsultationRequest request = detailDao.findWithAssociations(id);
        entityManager.clear();
        assertThat(request.getProfessionalSpecialist().getLastName()).isEqualTo("Synthetic specialist");
        assertThat(request.getDemographicContact().getContactId()).isEqualTo("987");
        assertThat(request.getAppointmentInstructionsLabel()).isEqualTo("Synthetic instruction");
    }

    @Test
    void shouldRetainRequest_whenOptionalAssociationsAreAbsent() {
        int id = persistRequest(false);
        ConsultationRequest request = detailDao.findWithAssociations(id);
        entityManager.clear();
        assertThat(request).isNotNull();
        assertThat(request.getProfessionalSpecialist()).isNull();
        assertThat(request.getDemographicContact()).isNull();
        assertThat(request.getAppointmentInstructionsLabel()).isEmpty();
        assertThat(request.getAppointmentInstructions()).isNull();
    }

    @Test
    void shouldReturnNull_whenDetailRequestDoesNotExist() {
        assertThat(detailDao.findWithAssociations(Integer.MAX_VALUE)).isNull();
    }

    @Test
    void shouldRetainSpecialistAndPatientRow_whenOrderingProviderMissing() {
        int id = persistRequest(true);
        var rows = listDao.getConsults(999887);
        entityManager.clear();
        assertThat(rows).filteredOn(r -> r.getId().equals(id)).singleElement()
                .satisfies(r -> assertThat(r.getProfessionalSpecialist().getLastName())
                        .isEqualTo("Synthetic specialist"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void shouldReadSpecialistAfterDetaching_whenUsingPagedList(boolean associations) {
        int id = persistRequest(associations);
        var rows = listDao.getConsults("pr2545fetch", true, null, null, "6", "1", null, 0, 10);
        entityManager.clear();
        assertThat(rows).singleElement().satisfies(request -> {
            assertThat(request.getId()).isEqualTo(id);
            if (associations) {
                assertThat(request.getProfessionalSpecialist().getLastName()).isEqualTo("Synthetic specialist");
            } else {
                assertThat(request.getProfessionalSpecialist()).isNull();
            }
        });
    }

    private int persistRequest(boolean associations) {
        ConsultationRequest request = new ConsultationRequest();
        request.setDemographicId(999887);
        request.setProviderNo("999997");
        request.setSendTo("pr2545fetch");
        request.setReferralDate(new Date());
        if (associations) {
            ProfessionalSpecialist specialist = new ProfessionalSpecialist();
            specialist.setFirstName("Synthetic");
            specialist.setLastName("Synthetic specialist");
            entityManager.persist(specialist);
            DemographicContact contact = new DemographicContact();
            contact.setDemographicNo(999887);
            contact.setContactId("987");
            contact.setType(DemographicContact.TYPE_CONTACT);
            contact.setCategory(DemographicContact.CATEGORY_PROFESSIONAL);
            entityManager.persist(contact);
            LookupListItem item = new LookupListItem();
            item.setValue(UUID.randomUUID().toString());
            item.setLabel("Synthetic instruction");
            entityManager.persist(item);
            request.setProfessionalSpecialist(specialist);
            request.setDemographicContact(contact);
            request.setAppointmentInstructions(item.getValue());
        }
        entityManager.persist(request);
        entityManager.flush();
        int id = request.getId();
        entityManager.clear();
        return id;
    }
}
