/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class ConsultationConsultantSelectionIntegrationTest extends CarlosTestBase {
    @Autowired
    private ProfessionalSpecialistDao specialists;

    @Test
    void shouldRequireActivePersistedSpecialist_forConsultationSubmission() {
        ProfessionalSpecialist active = new ProfessionalSpecialist();
        active.setFirstName("Test");
        active.setLastName("Consultant");
        specialists.persist(active);
        ProfessionalSpecialist inactive = new ProfessionalSpecialist();
        inactive.setDeleted(true);
        specialists.persist(inactive);
        hibernateTemplate.flush();
        hibernateTemplate.clear();

        assertThat(resolve(null)).isNull();
        assertThat(resolve("")).isNull();
        assertThat(resolve("not-an-id")).isNull();
        assertThat(resolve("2147483647")).isNull();
        assertThat(resolve(inactive.getId().toString())).isNull();
        assertThat(resolve(active.getId().toString()).specialist().getId()).isEqualTo(active.getId());
    }

    private ConsultationConsultantSelection resolve(String id) {
        return ConsultationConsultantSelection.resolve(specialists, null, null, 1, id, false);
    }
}
