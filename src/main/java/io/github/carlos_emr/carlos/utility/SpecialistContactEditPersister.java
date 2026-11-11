// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.ConcurrentModificationException;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checks the rendered version and replaces contact fields while holding the specialist row lock. */
@Service
public class SpecialistContactEditPersister {
    @PersistenceContext
    private EntityManager entityManager;
    private final SecurityInfoManager security;

    public SpecialistContactEditPersister(SecurityInfoManager security) { this.security = security; }

    @Transactional
    public ProfessionalSpecialist update(LoggedInInfo info, ProfessionalContact contact, String expectedVersion) {
        if (!security.hasPrivilege(info, "_demographic", SecurityInfoManager.WRITE, (String) null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        ProfessionalSpecialist current = entityManager.find(ProfessionalSpecialist.class, contact.getId(),
                LockModeType.PESSIMISTIC_WRITE);
        if (current == null || expectedVersion == null || !SpecialistContactEditVersion.of(current).equals(expectedVersion)) {
            throw new ConcurrentModificationException("Specialist contact has changed");
        }
        // The converter finds the same managed entity in this transaction, retaining the lock.
        ProfessionalSpecialist updated = DemographicContactCreator.convertProfessionalContactAsProfessionalSpecialist(info, contact);
        updated.setLastUpdated(new Date());
        entityManager.flush();
        return updated;
    }
}
