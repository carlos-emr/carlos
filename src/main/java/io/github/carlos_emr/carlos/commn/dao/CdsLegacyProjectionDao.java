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

import io.github.carlos_emr.carlos.commn.model.CdsImportField;
import java.util.Set;
import org.springframework.stereotype.Repository;

/** Existence-only checks: legacy projection admission never loads protected source values. */
@Repository
public class CdsLegacyProjectionDao extends AbstractDaoImpl<CdsImportField> {
    // These values are Flyway versions bound to a version column, never network addresses.
    @SuppressWarnings("java:S1313")
    private static final String SOURCE_FIELD_MIGRATION_VERSION = "1.0.59.8.1";
    @SuppressWarnings("java:S1313")
    private static final String LABORATORY_MIGRATION_VERSION = "1.0.59.10";

    public CdsLegacyProjectionDao() { super(CdsImportField.class); }
    public boolean hasSourceFields(int patient, Set<String> categories) {
        if (!representationTableAvailable("cds_import_field", SOURCE_FIELD_MIGRATION_VERSION)) return false;
        var query=categories.isEmpty()
                ? entityManager.createQuery("select f.id from CdsImportField f where f.demographicNo=:patient", String.class)
                : entityManager.createQuery("select f.id from CdsImportField f where f.demographicNo=:patient and f.category in :categories", String.class);
        query.setParameter("patient", patient).setMaxResults(1);
        if (!categories.isEmpty()) query.setParameter("categories", categories);
        return !query.getResultList().isEmpty();
    }
    public boolean hasTypedLaboratoryResults(int patient) {
        if (!representationTableAvailable("cds_laboratory_result", LABORATORY_MIGRATION_VERSION)) return false;
        return !entityManager.createQuery("select l.id from CdsLaboratoryResult l where l.demographicNo=:patient", String.class)
                .setParameter("patient", patient).setMaxResults(1).getResultList().isEmpty();
    }
    public boolean hasAnySourceFields(Set<String> categories) {
        if (!representationTableAvailable("cds_import_field", SOURCE_FIELD_MIGRATION_VERSION)) return false;
        return !entityManager.createQuery("select f.id from CdsImportField f where f.category in :categories", String.class)
                .setParameter("categories", categories).setMaxResults(1).getResultList().isEmpty();
    }
    private boolean representationTableAvailable(String table, String migration) {
        if (tableExists(table)) return true;
        // A new WAR with this optional feature unactivated can still serve a pre-CDS schema.
        // Missing a table after its migration was recorded is corruption, never an empty chart.
        if (tableExists("flyway_schema_history") && !entityManager.createNativeQuery(
                "SELECT installed_rank FROM flyway_schema_history WHERE version=:version AND success=TRUE")
                .setParameter("version", migration).setMaxResults(1).getResultList().isEmpty()) {
            throw new io.github.carlos_emr.carlos.commn.model.CdsLegacyProjectionException();
        }
        return false;
    }
    private boolean tableExists(String table) {
        return !entityManager.createNativeQuery("SELECT TABLE_NAME FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=:table")
                .setParameter("table", table).setMaxResults(1).getResultList().isEmpty();
    }
}
