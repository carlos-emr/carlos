// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ConcurrentModificationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
class SpecialistContactEditPersisterUnitTest {
    @Test
    void shouldRejectStaleForm_withoutReplacingTheNewerAddress() {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        LoggedInInfo info = mock(LoggedInInfo.class);
        EntityManager em = mock(EntityManager.class);
        var persister = new SpecialistContactEditPersister(security);
        ReflectionTestUtils.setField(persister, "entityManager", em);
        when(security.hasPrivilege(info, "_demographic", "w", (String) null)).thenReturn(true);
        ProfessionalContact submitted = new ProfessionalContact(); submitted.setId(12);
        ProfessionalSpecialist current = new ProfessionalSpecialist(); current.setStreetAddress("Original");
        String oldVersion = SpecialistContactEditVersion.of(current);
        current.setStreetAddress("Newer saved address");
        when(em.find(ProfessionalSpecialist.class, 12, LockModeType.PESSIMISTIC_WRITE)).thenReturn(current);
        assertThatThrownBy(() -> persister.update(info, submitted, oldVersion))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThat(current.getStreetAddress()).isEqualTo("Newer saved address");
        verify(em, never()).flush();
    }
}
