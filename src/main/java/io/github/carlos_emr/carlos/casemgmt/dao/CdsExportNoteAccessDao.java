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
    /** Complete bounded patient note scope; a hidden or malformed note refuses the whole read rather than disappearing. */
    @SuppressWarnings("unchecked")
    public List<CdsExportNoteAccessDto> readScopes(int patient, int maximum) {
        if (patient < 1 || maximum < 1 || maximum > 8192) throw new IllegalArgumentException("CDS_EXPORT_NOTE_ACCESS_SCOPE_INVALID");
        if (hasForeignRevision(patient, null)) deny();
        List<Object[]> rows = entityManager().createNativeQuery("SELECT note_id, program_no, reporter_caisi_role, locked FROM casemgmt_note WHERE demographic_no = :patient ORDER BY note_id", Object[].class)
                .setParameter("patient", patient).setHint("jakarta.persistence.query.timeout", 30_000).setMaxResults(maximum + 1).getResultList();
        if (rows.size() > maximum) throw new io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException(
                io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException.Reason.RESOURCE_LIMIT);
        var result = new java.util.ArrayList<CdsExportNoteAccessDto>(); long previous = 0;
        for (Object[] row : rows) {
            if (!(row[0] instanceof Number) || row[3] != null && !"0".equals(row[3]) && !Character.valueOf('0').equals(row[3])) deny();
            long id = ((Number) row[0]).longValue(); if (id <= previous) deny(); previous = id;
            try { result.add(new CdsExportNoteAccessDto(id, patient, (String) row[1], (String) row[2])); }
            catch (IllegalArgumentException invalid) { deny(); }
        }
        return List.copyOf(result);
    }
    /** Returns the live physical access scope, or null for a missing/unsupported note. */
    @SuppressWarnings("unchecked")
    public CdsExportNoteAccessDto findScope(int patient, long noteId) {
        if (patient < 1 || noteId < 1 || hasForeignRevision(patient, noteId)) return null;
        List<Object[]> rows = entityManager().createNativeQuery("SELECT program_no, reporter_caisi_role, locked FROM casemgmt_note WHERE note_id = :note AND demographic_no = :patient", Object[].class)
                .setParameter("note", noteId).setParameter("patient", patient).setMaxResults(2).getResultList();
        if (rows.size() != 1) return null;
        Object[] row = rows.getFirst();
        // The ordinary note reader hides locked bodies. Unknown legacy flag spellings cannot grant disclosure.
        if (row[2] != null && !"0".equals(row[2]) && !Character.valueOf('0').equals(row[2])) return null;
        try { return new CdsExportNoteAccessDto(noteId, patient, (String) row[0], (String) row[1]); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    /**
     * Fresh scalar equivalent of the active chart's program-domain and filterNotes rules.
     * Unscoped/zero-program chart reads still require the patient program domain. A scoped
     * note uses its own program context, one unambiguous membership role, the same-role
     * default or the active "read ROLE notes" grants, and the configured facility boundary.
     * No dormant first-space/"ROLEnotes" compatibility grant is consulted.
     */
    @SuppressWarnings("unchecked")
    public boolean canRead(String provider, CdsExportNoteAccessDto scope, Integer facility, boolean filterOnFacility) {
        if (provider == null || provider.isEmpty() || scope == null || !inPatientDomain(provider, scope.patient())) return false;
        if (scope.program().isEmpty() || scope.program().equals("0")) return true;
        long program = Long.parseLong(scope.program());
        if (filterOnFacility) {
            if (facility == null) return false;
            List<Integer> locations = entityManager().createNativeQuery("SELECT facilityId FROM program WHERE id = :program", Integer.class)
                    .setParameter("program", program).setMaxResults(2).getResultList();
            if (locations.size() != 1 || !facility.equals(locations.getFirst())) return false;
        }
        List<Long> memberships = entityManager().createNativeQuery("SELECT DISTINCT role_id FROM program_provider WHERE provider_no = :provider AND program_id = :program", Long.class)
                .setParameter("provider", provider).setParameter("program", program).setMaxResults(2).getResultList();
        // The live reader takes its first membership. Ambiguous different roles cannot grant export by arbitrary row order.
        if (memberships.size() != 1 || memberships.getFirst() == null || memberships.getFirst() < 1) return false;
        long role = memberships.getFirst();
        List<String> reporter = entityManager().createNativeQuery("SELECT role_name FROM secRole WHERE role_no = :role", String.class)
                .setParameter("role", Long.parseLong(scope.reporterRole())).setMaxResults(2).getResultList();
        List<Long> ownRole = entityManager().createNativeQuery("SELECT role_no FROM secRole WHERE role_no = :role", Long.class)
                .setParameter("role", role).setMaxResults(2).getResultList();
        if (reporter.size() != 1 || reporter.getFirst() == null || reporter.getFirst().isBlank() || ownRole.size() != 1) return false;
        if (role == Long.parseLong(scope.reporterRole())) return true;
        String accessName = "read " + reporter.getFirst().toLowerCase() + " notes";
        // RoleProgramAccessDAO.hasAccess uses the database's equality semantics and does not filter AccessType.type.
        List<?> defaults = entityManager().createNativeQuery("SELECT 1 FROM default_role_access d JOIN access_type a ON a.access_id = d.access_id WHERE d.role_id = :role AND a.name = :name")
                .setParameter("role", role).setParameter("name", accessName).setMaxResults(1).getResultList();
        if (!defaults.isEmpty()) return true;
        List<Object[]> grants = entityManager().createNativeQuery("""
                SELECT g.id, a.name, g.all_roles,
                EXISTS (SELECT 1 FROM program_access_roles r WHERE r.id = g.id AND r.role_id = :role)
                FROM program_access g JOIN access_type a ON a.access_id = g.access_type_id
                WHERE g.program_id = :program AND a.name = :name
                """, Object[].class).setParameter("role", role).setParameter("program", program).setParameter("name", accessName).setMaxResults(4097).getResultList();
        if (grants.size() > 4096) deny();
        Object[] selected = null;
        for (Object[] grant : grants) if (grant[1] instanceof String name && accessName.equals(name.toLowerCase())) {
            if (selected != null) return false; // duplicate map keys have no stable owning-reader precedence
            selected = grant;
        }
        return selected != null && (truth(selected[2]) || truth(selected[3]));
    }
    /**
     * The chart groups revisions globally by UUID. A UUID shared with another patient's
     * row is an unresolved chart-ownership conflict, not a patient-local revision chain.
     * The existence projection never reads the other patient's body or identifiers.
     */
    private boolean hasForeignRevision(int patient, Long note) {
        String sql = "SELECT 1 FROM casemgmt_note n JOIN casemgmt_note other ON other.uuid = n.uuid "
                + "WHERE n.demographic_no = :patient AND n.uuid IS NOT NULL AND n.uuid <> '' "
                + "AND (other.demographic_no IS NULL OR other.demographic_no <> :patient)";
        if (note != null) sql += " AND n.note_id = :note";
        var query = entityManager().createNativeQuery(sql).setParameter("patient", patient);
        if (note != null) query.setParameter("note", note);
        return !query.setHint("jakarta.persistence.query.timeout", 30_000).setMaxResults(1).getResultList().isEmpty();
    }
    /** Active chart domain gate independent of whether any note exists; metadata only. */
    public boolean canReadChart(String provider, int patient) {
        return provider != null && !provider.isEmpty() && patient > 0 && inPatientDomain(provider, patient);
    }
    private boolean inPatientDomain(String provider, int patient) {
        List<?> admissions = entityManager().createNativeQuery("SELECT 1 FROM admission WHERE client_id = :patient")
                .setParameter("patient", patient).setMaxResults(1).getResultList();
        if (admissions.isEmpty()) return true;
        List<?> member = entityManager().createNativeQuery("SELECT 1 FROM program_provider p JOIN admission a ON a.program_id = p.program_id WHERE p.provider_no = :provider AND a.client_id = :patient")
                .setParameter("provider", provider).setParameter("patient", patient).setMaxResults(1).getResultList();
        if (!member.isEmpty()) return true;
        return !entityManager().createNativeQuery("SELECT 1 FROM program_provider p JOIN program_queue q ON q.program_id = p.program_id WHERE p.provider_no = :provider AND q.client_id = :patient AND q.status = 'active'")
                .setParameter("provider", provider).setParameter("patient", patient).setMaxResults(1).getResultList().isEmpty();
    }
    private static boolean truth(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue() != 0; }
    private static void deny() { throw new SecurityException("CDS_EXPORT_NOTE_AUTHORIZATION_DENIED"); }
}
