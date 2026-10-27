/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.dao.CdsImportIdentityDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportIdentity;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsPatientIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsHealthCardAuthority;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Prevents two vendor identifiers in one sealed batch from claiming the same clinical identity. */
@Service
public class CdsPreflightIdentityService {
    private final CdsImportLedgerService ledger;
    private final CdsImportIdentityDao identities;
    private final TransactionTemplate transaction;
    public CdsPreflightIdentityService(CdsImportLedgerService ledger, CdsImportIdentityDao identities, PlatformTransactionManager manager) {
        this.ledger=ledger; this.identities=identities; transaction=CdsImportLedgerService.template(manager);
    }
    public void verifySchema() {
        CdsImportLedgerService.requireNoTransaction();
        if (!Boolean.TRUE.equals(transaction.execute(status -> identities.clinicalCollationsMatch()))) {
            throw new CdsImportException("PREFLIGHT_IDENTITY_COLLATION_MISMATCH");
        }
    }
    /** Fresh complete scans rebuild only their protected derived inventory in bounded fenced transactions. */
    public void beginScan(String run, long epoch) {
        CdsImportLedgerService.requireNoTransaction();
        int removed;
        do {
            removed=transaction.execute(status -> {
                var owner=ledger.lockPlanningOwned(run, epoch);
                if (owner.isCancelRequested() || Thread.currentThread().isInterrupted()) throw new CdsImportException("IMPORT_CANCELLED");
                var page=identities.page(run, 200);
                for (var identity : page) identities.remove(identity);
                identities.flushAndClear();
                return page.size();
            });
        } while (removed == 200);
    }
    public boolean register(String run, long epoch, String patient, String copy, CdsPatientIdentityDto source) {
        return register(run, epoch, patient, copy, source, true);
    }
    public boolean register(String run, long epoch, String patient, String copy, CdsPatientIdentityDto source, boolean comparable) {
        CdsImportLedgerService.requireNoTransaction();
        if (source.birthDate()==null || source.firstName()==null || source.lastName()==null) throw new CdsImportException("SOURCE_IDENTITY_INCOMPLETE");
        String hin=source.healthNumber()==null || source.healthNumber().isBlank() ? null : source.healthNumber();
        String issuer=CdsHealthCardAuthority.known(source.issuer());
        return Boolean.TRUE.equals(transaction.execute(status -> {
            ledger.lockPlanningOwned(run, epoch);
            String id=CdsImportKeys.hash(run, patient);
            CdsImportIdentity old=identities.get(id);
            if (old!=null) {
                if (!old.getCopyKey().equals(copy) || !old.getFirstName().equals(source.firstName())
                        || !old.getLastName().equals(source.lastName()) || !old.getBirthDate().equals(source.birthDate())
                        || !Objects.equals(old.getHin(), hin) || !Objects.equals(old.getIssuer(), issuer)
                        || old.isComparable()!=comparable) throw new CdsImportException("SOURCE_IDENTITY_REVISION_REVIEW_REQUIRED");
                return false;
            }
            if (comparable && (hin!=null && identities.healthNumberCollision(run, copy, patient, hin, issuer)
                    || identities.nameCollision(run, copy, patient, source.firstName(), source.lastName(), source.birthDate(), hin==null))) {
                throw new CdsImportException("SOURCE_BATCH_IDENTITY_COLLISION");
            }
            CdsImportIdentity value=new CdsImportIdentity(); value.setId(id); value.setRunId(run); value.setPatientKey(patient);
            value.setCopyKey(copy); value.setFirstName(source.firstName()); value.setLastName(source.lastName());
            value.setBirthDate(source.birthDate()); value.setHin(hin); value.setIssuer(issuer); value.setComparable(comparable); identities.persist(value);
            return true;
        }));
    }
}
