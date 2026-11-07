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
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.dao.AbstractJpaDao;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scalar note-access reads owned by case management. Never loads a note body, history,
 * password or managed note entity. Call only inside the export service's fresh authorization
 * transaction; a source-capture snapshot cannot establish current program rights.
 */
@Repository
@Transactional(readOnly = true, propagation = Propagation.MANDATORY)
public class CdsExportNoteAccessDao extends AbstractJpaDao {
    /** Returns the live physical access scope, or null for a missing/unsupported note. */
    @SuppressWarnings("unchecked")
    public CdsExportNoteAccessDto findScope(int patient, long noteId) {
        if (patient < 1 || noteId < 1) return null;
        List<Object[]> rows = entityManager().createNativeQuery("SELECT program_no, reporter_caisi_role FROM casemgmt_note WHERE note_id = :note AND demographic_no = :patient", Object[].class)
                .setParameter("note", noteId).setParameter("patient", patient).setMaxResults(2).getResultList();
        if (rows.size() != 1) return null;
        Object[] row = rows.getFirst();
        try { return new CdsExportNoteAccessDto(noteId, patient, (String) row[0], (String) row[1]); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    /**
     * Matches CaseManagementManager.filterNotesByAccess: the reporter role's first literal
     * space is removed, then "notes"/"access" must be granted through a provider membership.
     * Default role grants and explicit program/all-role grants have the same union semantics.
     * Program zero uses the owning reader's end-of-day admission interval, including its
     * historical year-one sentinel. Empty, missing and malformed program/role values deny.
     */
    @SuppressWarnings("unchecked")
    public boolean canRead(String provider, CdsExportNoteAccessDto scope) {
        if (provider == null || provider.isEmpty() || scope == null) return false;
        List<String> roles = entityManager().createNativeQuery("SELECT role_name FROM secRole WHERE role_no = :role", String.class)
                .setParameter("role", Long.valueOf(scope.reporterRole())).setMaxResults(2).getResultList();
        if (roles.size() != 1 || roles.getFirst() == null || roles.getFirst().isEmpty()) return false;
        String name = roles.getFirst(); int space = name.indexOf(' ');
        if (space >= 0) name = name.substring(0, space) + name.substring(space + 1);
        String accessName = name + "notes";
        List<Long> programs;
        if (scope.program().equals("0")) {
            Calendar day = Calendar.getInstance(); day.set(Calendar.HOUR_OF_DAY, 23); day.set(Calendar.MINUTE, 59); day.set(Calendar.SECOND, 59);
            // This deliberately matches DemographicDao.getProgramIdByDemoNo, including milliseconds.
            List<Long> ids = entityManager().createNativeQuery("SELECT DISTINCT program_id FROM admission WHERE client_id = :patient AND admission_date <= :day AND (discharge_date >= :day OR discharge_date IS NULL OR discharge_date = :sentinel)", Long.class)
                    .setParameter("patient", scope.patient()).setParameter("day", day.getTime())
                    .setParameter("sentinel", new GregorianCalendar(1, 0, 1).getTime()).setMaxResults(4097).getResultList();
            if (ids.size() > 4096 || ids.stream().anyMatch(id -> id == null || id < 1)) deny();
            programs = ids;
        } else programs = List.of(Long.valueOf(scope.program()));
        for (Long program : programs) {
            List<Object[]> rights = entityManager().createNativeQuery("""
                    SELECT DISTINCT a.name, a.type FROM program_provider p
                    JOIN default_role_access d ON d.role_id = p.role_id
                    JOIN access_type a ON a.access_id = d.access_id
                    WHERE p.provider_no = :provider AND p.program_id = :program
                    UNION
                    SELECT DISTINCT a.name, a.type FROM program_provider p
                    JOIN program_access g ON g.program_id = p.program_id
                    JOIN access_type a ON a.access_id = g.access_type_id
                    WHERE p.provider_no = :provider AND p.program_id = :program
                    AND (g.all_roles = 1 OR EXISTS (SELECT 1 FROM program_access_roles r WHERE r.id = g.id AND r.role_id = p.role_id))
                    """, Object[].class).setParameter("provider", provider).setParameter("program", program).setMaxResults(4097).getResultList();
            if (rights.size() > 4096) deny();
            for (Object[] right : rights) if (matches(accessName, right)) return true;
        }
        return false;
    }

    // FindSecBugs IMPROPER_UNICODE: preserves the owning note reader's established case-insensitive access-name/type comparison.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Preserves the owning note reader's established case-insensitive access-name/type comparison")
    private static boolean matches(String expected, Object[] right) {
        return right.length == 2 && right[0] instanceof String name && right[1] instanceof String type
                && expected.equalsIgnoreCase(name) && "access".equalsIgnoreCase(type);
    }
    private static void deny() { throw new SecurityException("CDS_EXPORT_NOTE_AUTHORIZATION_DENIED"); }
}
