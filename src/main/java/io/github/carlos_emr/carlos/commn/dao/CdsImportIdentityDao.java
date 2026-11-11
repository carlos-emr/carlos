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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.CdsImportIdentity;
import java.time.LocalDate;
import org.springframework.stereotype.Repository;

/** Bounded indexed equality queries use the actual demographic column collations. */
@Repository
public class CdsImportIdentityDao extends AbstractDaoImpl<CdsImportIdentity> {
    public CdsImportIdentityDao() { super(CdsImportIdentity.class); }
    public CdsImportIdentity get(String id) { return entityManager.find(CdsImportIdentity.class, id); }
    public boolean healthNumberCollision(String run, String copy, String patient, String hin, String issuer) {
        var query=issuer == null
                ? entityManager.createQuery("select p.id from CdsImportIdentity p where p.runId=:run and p.copyKey=:copy "
                        + "and p.comparable=true and p.hin=:hin and p.patientKey<>:patient", String.class)
                : entityManager.createQuery("select p.id from CdsImportIdentity p where p.runId=:run and p.copyKey=:copy "
                        + "and p.comparable=true and p.hin=:hin and p.patientKey<>:patient "
                        + "and (p.issuer=:issuer or p.issuer is null)", String.class);
        query.setParameter("run", run).setParameter("copy", copy).setParameter("hin", hin)
                .setParameter("patient", patient).setMaxResults(1);
        if (issuer != null) query.setParameter("issuer", issuer);
        return !query.getResultList().isEmpty();
    }
    public boolean nameCollision(String run, String copy, String patient, String first, String last, LocalDate birth, boolean missingHin) {
        var query=missingHin
                ? entityManager.createQuery("select p.id from CdsImportIdentity p where p.runId=:run and p.copyKey=:copy "
                        + "and p.comparable=true and p.lastName=:last and p.firstName=:first and p.birthDate=:birth and p.patientKey<>:patient", String.class)
                : entityManager.createQuery("select p.id from CdsImportIdentity p where p.runId=:run and p.copyKey=:copy "
                        + "and p.comparable=true and p.lastName=:last and p.firstName=:first and p.birthDate=:birth "
                        + "and p.patientKey<>:patient and p.hin is null", String.class);
        return !query
                .setParameter("run", run).setParameter("copy", copy).setParameter("patient", patient)
                .setParameter("first", first).setParameter("last", last).setParameter("birth", birth)
                .setMaxResults(1).getResultList().isEmpty();
    }
    public boolean clinicalCollationsMatch() {
        Number columns=(Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM information_schema.COLUMNS a "
                + "JOIN information_schema.COLUMNS b ON b.TABLE_SCHEMA=a.TABLE_SCHEMA AND b.COLUMN_NAME=a.COLUMN_NAME "
                + "WHERE a.TABLE_SCHEMA=DATABASE() AND a.TABLE_NAME='demographic' AND b.TABLE_NAME='cds_import_identity' "
                + "AND a.COLUMN_NAME IN ('first_name','last_name','hin') AND a.COLLATION_NAME=b.COLLATION_NAME "
                + "AND a.CHARACTER_SET_NAME=b.CHARACTER_SET_NAME").getSingleResult();
        Number authority=(Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM information_schema.COLUMNS a "
                + "JOIN information_schema.COLUMNS b ON b.TABLE_SCHEMA=a.TABLE_SCHEMA "
                + "WHERE a.TABLE_SCHEMA=DATABASE() AND a.TABLE_NAME='demographic' AND a.COLUMN_NAME='cds_identity_issuer' "
                + "AND b.TABLE_NAME='cds_import_identity' AND b.COLUMN_NAME='issuer' "
                + "AND a.COLLATION_NAME=b.COLLATION_NAME AND a.CHARACTER_SET_NAME=b.CHARACTER_SET_NAME").getSingleResult();
        return columns.intValue()==3 && authority.intValue()==1;
    }
    public java.util.List<CdsImportIdentity> page(String run, int limit) {
        return entityManager.createQuery("select i from CdsImportIdentity i where i.runId=:run order by i.id", CdsImportIdentity.class).setParameter("run", run).setMaxResults(limit).getResultList();
    }
    public void flushAndClear() { entityManager.flush(); entityManager.clear(); }
}
