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
    /** Program grant rows one statement reads; it decides how they are read, never how many. */
    private static final int GRANT_PAGE = 4096;

    /**
     * The complete note scope of a patient, in note order; a hidden or malformed note refuses the
     * whole read rather than disappearing. A chart of any number of notes is read: {@code page}
     * is how many notes one statement returns (1 to 8192), never how many the chart may have.
     * Each statement continues after the last note of the one before it, by the primary key.
     */
    @SuppressWarnings("unchecked")
    public List<CdsExportNoteAccessDto> readScopes(int patient, int page) {
        if (patient < 1 || page < 1 || page > 8192)
            throw new IllegalArgumentException("CDS_EXPORT_NOTE_ACCESS_SCOPE_INVALID");
        if (hasForeignRevision(patient, null)) deny();
        var result = new java.util.ArrayList<CdsExportNoteAccessDto>();
        long previous = 0;
        while (true) {
            List<Object[]> rows =
                    waiting(
                                    entityManager()
                                            .createNativeQuery(
                                                    "SELECT note_id, program_no,"
                                                        + " reporter_caisi_role, locked FROM"
                                                        + " casemgmt_note WHERE demographic_no ="
                                                        + " :patient AND note_id > :after ORDER BY"
                                                        + " note_id",
                                                    Object[].class))
                            .setParameter("patient", patient)
                            .setParameter("after", previous)
                            .setMaxResults(page)
                            .getResultList();
            for (Object[] row : rows) {
                if (!(row[0] instanceof Number)
                        || row[3] != null
                                && !"0".equals(row[3])
                                && !Character.valueOf('0').equals(row[3])) deny();
                long id = ((Number) row[0]).longValue();
                if (id <= previous) deny();
                previous = id;
                try {
                    result.add(
                            new CdsExportNoteAccessDto(
                                    id, patient, (String) row[1], (String) row[2]));
                } catch (IllegalArgumentException invalid) {
                    deny();
                }
            }
            if (rows.size() < page) break;
        }
        return List.copyOf(result);
    }

    /** Returns the live physical access scope, or null for a missing/unsupported note. */
    @SuppressWarnings("unchecked")
    public CdsExportNoteAccessDto findScope(int patient, long noteId) {
        if (patient < 1 || noteId < 1 || hasForeignRevision(patient, noteId)) return null;
        List<Object[]> rows =
                waiting(
                                entityManager()
                                        .createNativeQuery(
                                                "SELECT program_no, reporter_caisi_role, locked FROM"
                                                    + " casemgmt_note WHERE note_id = :note AND"
                                                    + " demographic_no = :patient",
                                                Object[].class))
                        .setParameter("note", noteId)
                        .setParameter("patient", patient)
                        .setMaxResults(2)
                        .getResultList();
        if (rows.size() != 1) return null;
        Object[] row = rows.getFirst();
        // The ordinary note reader hides locked bodies. Unknown legacy flag spellings cannot grant
        // disclosure.
        if (row[2] != null && !"0".equals(row[2]) && !Character.valueOf('0').equals(row[2]))
            return null;
        try {
            return new CdsExportNoteAccessDto(noteId, patient, (String) row[0], (String) row[1]);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /**
     * Exact live scopes for one batch of a patient's notes, including ownership conflicts. At
     * most 8192 notes a call: that is the size of one statement's list, and the access service
     * asks in as many batches as a chart needs.
     */
    @SuppressWarnings("unchecked")
    public List<CdsExportNoteAccessDto> findScopes(int patient, List<Long> noteIds) {
        var ids = List.copyOf(noteIds);
        if (patient < 1
                || ids.isEmpty()
                || ids.size() > 8192
                || new java.util.HashSet<>(ids).size() != ids.size()
                || ids.stream().anyMatch(id -> id < 1)) deny();
        List<Object[]> rows =
                waiting(
                                entityManager()
                                        .createNativeQuery(
                                """
                                SELECT n.note_id,n.program_no,n.reporter_caisi_role,n.locked,
                                EXISTS (SELECT 1 FROM casemgmt_note other
                                WHERE n.uuid IS NOT NULL AND n.uuid<>'' AND other.uuid=n.uuid
                                AND (other.demographic_no IS NULL OR other.demographic_no<>:patient))
                                FROM casemgmt_note n WHERE n.demographic_no=:patient AND n.note_id IN (:notes)
                                ORDER BY n.note_id
                                """,
                                Object[].class))
                        .setParameter("patient", patient)
                        .setParameter("notes", ids)
                        .setMaxResults(ids.size() + 1)
                        .getResultList();
        if (rows.size() != ids.size()) deny();
        var result = new java.util.ArrayList<CdsExportNoteAccessDto>(rows.size());
        long previous = 0;
        for (var row : rows) {
            if (!(row[0] instanceof Number number)
                    || number.longValue() <= previous
                    || row[3] != null
                            && !"0".equals(row[3])
                            && !Character.valueOf('0').equals(row[3])
                    || truth(row[4])) deny();
            long id = ((Number) row[0]).longValue();
            try {
                result.add(
                        new CdsExportNoteAccessDto(id, patient, (String) row[1], (String) row[2]));
            } catch (IllegalArgumentException invalid) {
                deny();
            }
            previous = id;
        }
        return List.copyOf(result);
    }

    /**
     * Fresh scalar equivalent of the active chart's program-domain and filterNotes rules.
     * Unscoped/zero-program chart reads still require the patient program domain. A scoped
     * note uses its own program context, one unambiguous membership role, the same-role
     * default or the active "read ROLE notes" grants, and the configured facility boundary.
     * No dormant first-space/"ROLEnotes" compatibility grant is consulted.
     */
    @SuppressWarnings("unchecked")
    public boolean canRead(
            String provider,
            CdsExportNoteAccessDto scope,
            Integer facility,
            boolean filterOnFacility) {
        if (provider == null
                || provider.isEmpty()
                || scope == null
                || !inPatientDomain(provider, scope.patient())) return false;
        if (scope.program().isEmpty() || scope.program().equals("0")) return true;
        long program = Long.parseLong(scope.program());
        Long membership = programRole(provider, program, facility, filterOnFacility);
        if (membership == null) return false;
        long role = membership;
        List<String> reporter =
                waiting(
                                entityManager()
                                        .createNativeQuery(
                                                "SELECT role_name FROM secRole WHERE role_no = :role",
                                                String.class))
                        .setParameter("role", Long.parseLong(scope.reporterRole()))
                        .setMaxResults(2)
                        .getResultList();
        List<Long> ownRole =
                waiting(
                                entityManager()
                                        .createNativeQuery(
                                                "SELECT role_no FROM secRole WHERE role_no = :role",
                                                Long.class))
                        .setParameter("role", role)
                        .setMaxResults(2)
                        .getResultList();
        if (reporter.size() != 1
                || reporter.getFirst() == null
                || reporter.getFirst().isBlank()
                || ownRole.size() != 1) return false;
        if (role == Long.parseLong(scope.reporterRole())) return true;
        String accessName = "read " + folded(reporter.getFirst()) + " notes";
        // RoleProgramAccessDAO.hasAccess uses the database's equality semantics and does not filter
        // AccessType.type.
        List<?> defaults =
                waiting(
                                entityManager()
                                        .createNativeQuery(
                                                "SELECT 1 FROM default_role_access d JOIN"
                                                    + " access_type a ON a.access_id = d.access_id"
                                                    + " WHERE d.role_id = :role AND a.name ="
                                                    + " :name"))
                        .setParameter("role", role)
                        .setParameter("name", accessName)
                        .setMaxResults(1)
                        .getResultList();
        if (!defaults.isEmpty()) return true;
        // Every grant row the database's equality matches is read, in id order a page at a time
        // (#58: more than 4,096 rows used to refuse the check, which held the patient). The
        // decision is the one a short list gives: exactly one row whose name is the access name
        // decides, and a second one leaves no stable owning-reader precedence, so the note stays
        // unreadable.
        Object[] selected = null;
        long after = Long.MIN_VALUE;
        while (true) {
            List<Object[]> grants =
                    waiting(
                                    entityManager()
                                            .createNativeQuery(
                                                    """
                                                    SELECT g.id, a.name, g.all_roles,
                                                    EXISTS (SELECT 1 FROM program_access_roles r WHERE r.id = g.id AND r.role_id = :role)
                                                    FROM program_access g JOIN access_type a ON a.access_id = g.access_type_id
                                                    WHERE g.program_id = :program AND a.name = :name AND g.id > :after
                                                    ORDER BY g.id
                                                    """,
                                                    Object[].class))
                            .setParameter("role", role)
                            .setParameter("program", program)
                            .setParameter("name", accessName)
                            .setParameter("after", after)
                            .setMaxResults(GRANT_PAGE)
                            .getResultList();
            for (Object[] grant : grants) {
                after = ((Number) grant[0]).longValue();
                if (grant[1] instanceof String name && accessName.equals(folded(name))) {
                    if (selected != null)
                        return false; // duplicate map keys have no stable owning-reader precedence
                    selected = grant;
                }
            }
            if (grants.size() < GRANT_PAGE) break;
        }
        return selected != null && (truth(selected[2]) || truth(selected[3]));
    }

    /**
     * Lower-case form of a role or access-type name for the "read ROLE notes" rule. The fold is
     * pinned to {@link java.util.Locale#ROOT}: who may read a note must not depend on the JVM's
     * default locale. Under a Turkish default, the I of "ADMIN" folds to a dotless i and the
     * role would no longer equal its own "read admin notes" grant. The folded names are compared
     * exactly, so a name that differs in any other way (another script, another normalization
     * form) is not matched and the note stays unreadable.
     */
    // FindSecBugs IMPROPER_UNICODE: Role and access-type names of an authorization rule, folded with Locale.ROOT and then compared exactly; a name that does not fold to the same characters is denied.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "role and access-type names of an authorization rule, folded with Locale.ROOT and then compared exactly; a name that does not fold to the same characters is denied")
    static String folded(String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The program part of {@link #canRead} for a patient case-management record that carries a
     * program but no reporter role: a patient issue ({@code casemgmt_issue.program_id}). The
     * chart lists every patient issue, so this is not a stricter issue filter; it decides whether
     * the program is one the provider works in (the same patient domain, configured facility
     * boundary and single unambiguous membership a scoped note requires), as opposed to a
     * foreign program whose issue the exporter must hold for review. An unscoped value ({@code ""}
     * or {@code "0"}) needs only the patient domain, as an unscoped note does.
     */
    public boolean canReadProgram(
            String provider,
            int patient,
            String program,
            Integer facility,
            boolean filterOnFacility) {
        if (provider == null
                || provider.isEmpty()
                || patient < 1
                || program == null
                || !program.isEmpty() && !program.matches("0|[1-9][0-9]{0,9}")
                || !program.isEmpty() && Long.parseLong(program) > Integer.MAX_VALUE
                || !inPatientDomain(provider, patient)) return false;
        if (program.isEmpty() || program.equals("0")) return true;
        return programRole(provider, Long.parseLong(program), facility, filterOnFacility) != null;
    }

    /**
     * Whether the provider works in the program a record is restricted to (a document, or a
     * legacy prevention row): any {@code program_provider} membership in it, the rule the chart's
     * document list and document links apply ({@code EDocUtil} program filtering,
     * {@code DocumentPatientLink}). It reads no record and decides nothing about any other
     * program.
     */
    public boolean canReadRestrictedProgram(String provider, int program) {
        if (provider == null || provider.isEmpty()) return false;
        return !waiting(
                        entityManager()
                                .createNativeQuery(
                                        "SELECT 1 FROM program_provider WHERE provider_no ="
                                            + " :provider AND program_id = :program"))
                .setParameter("provider", provider)
                .setParameter("program", program)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    /** The provider's one membership role in a program inside the facility boundary, or null. */
    @SuppressWarnings("unchecked")
    private Long programRole(
            String provider, long program, Integer facility, boolean filterOnFacility) {
        if (filterOnFacility) {
            if (facility == null) return null;
            List<Integer> locations =
                    waiting(
                                    entityManager()
                                            .createNativeQuery(
                                                    "SELECT facilityId FROM program WHERE id ="
                                                        + " :program",
                                                    Integer.class))
                            .setParameter("program", program)
                            .setMaxResults(2)
                            .getResultList();
            if (locations.size() != 1 || !facility.equals(locations.getFirst())) return null;
        }
        List<Long> memberships =
                waiting(
                                entityManager()
                                        .createNativeQuery(
                                                "SELECT DISTINCT role_id FROM program_provider"
                                                    + " WHERE provider_no = :provider AND"
                                                    + " program_id = :program",
                                                Long.class))
                        .setParameter("provider", provider)
                        .setParameter("program", program)
                        .setMaxResults(2)
                        .getResultList();
        // The live reader takes its first membership. Ambiguous different roles cannot grant export
        // by arbitrary row order.
        if (memberships.size() != 1 || memberships.getFirst() == null || memberships.getFirst() < 1)
            return null;
        return memberships.getFirst();
    }

    /**
     * A read that waits for its answer: a statement the server ends at its deadline is sent
     * again in the same transaction, so neither a long chart's note scopes nor a program or role
     * lookup that waits for a lock fails for the time one statement takes, and none is cut off
     * by the connection's socket timeout ({@link
     * io.github.carlos_emr.carlos.commn.dao.CdsExportStatementWaitReadSupport}). Every statement
     * of this class is sent through it.
     */
    private jakarta.persistence.Query waiting(jakarta.persistence.Query query) {
        return io.github.carlos_emr.carlos.commn.dao.CdsExportStatementWaitReadSupport.waiting(
                entityManager(), query);
    }

    /**
     * The chart groups revisions globally by UUID. A UUID shared with another patient's
     * row is an unresolved chart-ownership conflict, not a patient-local revision chain.
     * The existence projection never reads the other patient's body or identifiers.
     */
    private boolean hasForeignRevision(int patient, Long note) {
        String sql =
                "SELECT 1 FROM casemgmt_note n JOIN casemgmt_note other ON other.uuid = n.uuid"
                    + " WHERE n.demographic_no = :patient AND n.uuid IS NOT NULL AND n.uuid <> ''"
                    + " AND (other.demographic_no IS NULL OR other.demographic_no <> :patient)";
        if (note != null) sql += " AND n.note_id = :note";
        var query = waiting(entityManager().createNativeQuery(sql)).setParameter("patient", patient);
        if (note != null) query.setParameter("note", note);
        return !query.setMaxResults(1).getResultList().isEmpty();
    }

    /** Active chart domain gate independent of whether any note exists; metadata only. */
    public boolean canReadChart(String provider, int patient) {
        return provider != null
                && !provider.isEmpty()
                && patient > 0
                && inPatientDomain(provider, patient);
    }

    private boolean inPatientDomain(String provider, int patient) {
        List<?> admissions =
                waiting(
                                entityManager()
                                        .createNativeQuery(
                                                "SELECT 1 FROM admission WHERE client_id ="
                                                    + " :patient"))
                        .setParameter("patient", patient)
                        .setMaxResults(1)
                        .getResultList();
        if (admissions.isEmpty()) return true;
        List<?> member =
                waiting(
                                entityManager()
                                        .createNativeQuery(
                                                "SELECT 1 FROM program_provider p JOIN admission a"
                                                    + " ON a.program_id = p.program_id WHERE"
                                                    + " p.provider_no = :provider AND a.client_id"
                                                    + " = :patient"))
                        .setParameter("provider", provider)
                        .setParameter("patient", patient)
                        .setMaxResults(1)
                        .getResultList();
        if (!member.isEmpty()) return true;
        return !waiting(
                        entityManager()
                                .createNativeQuery(
                                        "SELECT 1 FROM program_provider p JOIN program_queue q ON"
                                            + " q.program_id = p.program_id WHERE p.provider_no ="
                                            + " :provider AND q.client_id = :patient AND q.status"
                                            + " = 'active'"))
                .setParameter("provider", provider)
                .setParameter("patient", patient)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    private static boolean truth(Object value) {
        return Boolean.TRUE.equals(value)
                || value instanceof Number number && number.intValue() != 0;
    }

    private static void deny() {
        throw new SecurityException("CDS_EXPORT_NOTE_AUTHORIZATION_DENIED");
    }
}
