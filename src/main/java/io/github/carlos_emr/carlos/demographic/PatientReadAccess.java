/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.model.security.Secobjprivilege;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** Patient ownership checks for demographic, billing and tickler read entrypoints. */
public final class PatientReadAccess {
    public static final String AUTHORIZED_PATIENT_ATTRIBUTE = "authorizedPatientReadNo";
    public static final String DENIED_PATIENTS_ATTRIBUTE = "patientReadDeniedPatients";
    private record Exclusions(String provider, String object, String privilege, Set<Integer> patients) { }

    private PatientReadAccess() { }

    /** Resolve every value of both native aliases, refusing conflicting or invalid identities. */
    public static int requestedPatient(HttpServletRequest request, boolean required) {
        return requestedPatient(request, required, false);
    }

    /** The global tickler worklist also uses the existing All Clients selector. */
    public static int requestedListPatient(HttpServletRequest request) {
        return requestedPatient(request, false, true);
    }

    // FindSecBugs IMPROPER_UNICODE: "All Clients" is the tickler page's literal no-patient selection, compared without case; not an authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive match of the literal 'All Clients' selection; not a security or authorization decision")
    private static int requestedPatient(HttpServletRequest request, boolean required, boolean allClients) {
        Integer selected = null;
        for (String alias : List.of("demographicNo", "demographic_no")) {
            String[] values = request.getParameterValues(alias);
            if (values == null) continue;
            for (String value : values) {
                int patient = 0;
                if (value == null) throw new IllegalArgumentException("Invalid patient identity");
                if (!value.isEmpty() && !(allClients && "All Clients".equalsIgnoreCase(value))) {
                    if (!value.matches("[0-9]+")) throw new IllegalArgumentException("Invalid patient identity");
                    try { patient = Integer.parseInt(value); }
                    catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid patient identity"); }
                    if (patient < 0) throw new IllegalArgumentException("Invalid patient identity");
                }
                if (selected != null && selected.intValue() != patient) throw new IllegalArgumentException("Invalid patient identity");
                selected = patient;
            }
        }
        if (required && (selected == null || selected == 0)) throw new IllegalArgumentException("Invalid patient identity");
        return selected == null ? 0 : selected;
    }

    /** A module read/write right alone does not override a demographic or chart deny lock. */
    public static boolean mayReadPatient(SecurityInfoManager security, LoggedInInfo user,
            int patient, String object, String privilege) {
        return user != null && patient > 0
                && security.isAllowedAccessToPatientRecord(user, patient)
                && security.hasPrivilege(user, object, privilege, Integer.toString(patient));
    }

    public static void requirePatient(SecurityInfoManager security, LoggedInInfo user,
            int patient, String object, String privilege) {
        if (!mayReadPatient(security, user, patient, object, privilege)) throw denied();
    }

    /** Canonical optional selection for a native JSP reached through more than one action. */
    public static String authorizedOptionalPatient(HttpServletRequest request,
            SecurityInfoManager security, LoggedInInfo user, String object, String privilege) {
        int patient = requestedPatient(request, false);
        if (patient == 0) return null;
        requirePatient(security, user, patient, object, privilege);
        return Integer.toString(patient);
    }

    /**
     * Discover possible record locks from active-role/provider metadata, then ask the existing
     * canonical record-access predicate first, then the shared read-only targeted-module policy.
     * This does not infer access from raw deny rows or load any patient data. The read-only
     * privilege evaluator preserves module overrides without changing session.accountLocked
     * while filtering records for other patients. Explicit native selections keep their existing
     * stateful module checks; no privilege logic is duplicated here.
     */
    public static Set<Integer> deniedPatients(SecurityInfoManager security, LoggedInInfo user,
            String object, String privilege) {
        if (user == null || !security.hasPrivilege(user, object, privilege, (String) null)) throw denied();
        List<Secobjprivilege> objects = security.getSecurityObjects(user);
        if (objects == null) throw denied();
        Pattern scopedObject = Pattern.compile("^(?:_demographic|_echart|" + Pattern.quote(object)
                + ")\\$([0-9]+)$", Pattern.CASE_INSENSITIVE);
        Set<Integer> candidates = new HashSet<>();
        for (Secobjprivilege entry : objects) {
            if (entry == null || entry.getObjectname_code() == null) continue;
            Matcher match = scopedObject.matcher(entry.getObjectname_code().trim());
            if (!match.matches()) continue;
            try {
                int patient = Integer.parseInt(match.group(1));
                if (patient > 0) candidates.add(patient);
            } catch (NumberFormatException outsidePatientDomain) {
                // There is no signed-int patient corresponding to this metadata entry.
            }
        }
        Set<Integer> deniedPatients = new HashSet<>();
        for (int patient : candidates) {
            if (!security.isAllowedAccessToPatientRecord(user, patient)
                    || !security.hasPrivilegeReadOnly(user, object, privilege, Integer.toString(patient))) {
                deniedPatients.add(patient);
            }
        }
        return Set.copyOf(deniedPatients);
    }

    /** Internal request cache is typed and scoped to the exact security object and privilege. */
    public static Set<Integer> deniedPatientsFromRequest(HttpServletRequest request,
            SecurityInfoManager security, LoggedInInfo user, String object, String privilege) {
        if (user == null) throw denied();
        Object cached = request.getAttribute(DENIED_PATIENTS_ATTRIBUTE);
        if (cached instanceof Exclusions exclusions && Objects.equals(exclusions.provider(), user.getLoggedInProviderNo())
                && exclusions.object().equals(object)
                && exclusions.privilege().equals(privilege)) return exclusions.patients();
        Set<Integer> patients = deniedPatients(security, user, object, privilege);
        request.setAttribute(DENIED_PATIENTS_ATTRIBUTE, new Exclusions(user.getLoggedInProviderNo(), object, privilege, patients));
        return patients;
    }

    private static SecurityException denied() {
        return new SecurityException("missing required patient read access");
    }
}
