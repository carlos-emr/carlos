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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.ws.rs.HttpMethod;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tripwire for the JAX-RS {@code hasPrivilege} sweep of issue #2798.
 *
 * <p>The {@code /ws/rs} and {@code /ws/services} interceptors only establish <em>who</em> the caller
 * is. Authorization is each resource method's job, and #2798 found most methods in these services
 * skipped it. This contract keeps the sweep from regressing: every JAX-RS resource method of a
 * swept service must reach a {@code SecurityInfoManager.hasPrivilege} call, either in its own body
 * or through a method of the same class it calls, or be listed in {@link #EXEMPT_ENDPOINTS} with
 * the reason it needs no security object.</p>
 *
 * <p>The check reads the service source with comments and string literals removed. It proves a
 * privilege decision is reachable, not that it uses the right object or blocks the right path;
 * the per-service unit and endpoint tests pin those semantics. A new endpoint in a swept service
 * that has no privilege check fails here until it gains one or an exemption is justified in review.
 * Other REST services can join the contract by adding them to {@link #SWEPT_SERVICES} once each of
 * their endpoints has been audited.</p>
 *
 * @since 2026-10-08
 */
@Tag("unit")
@Tag("rest")
@Tag("security")
@DisplayName("REST privilege guard contract (#2798)")
class RestPrivilegeGuardContractUnitTest {

    private static final Path SOURCE_ROOT =
            Path.of("src/main/java/io/github/carlos_emr/carlos/webserv/rest");

    /**
     * The services listed in #2798. {@code ReportByTemplateService} was on that list too; it declared
     * no endpoints and was deleted rather than guarded.
     */
    private static final List<Class<?>> SWEPT_SERVICES = List.of(
            AppService.class,
            ConsentService.class,
            DemographicMergeService.class,
            DiseaseRegistryService.class,
            EFormService.class,
            EFormsService.class,
            FormsService.class,
            OscarJobService.class,
            PatientDetailStatusService.class,
            PersonaService.class,
            PharmacyService.class,
            ProgramService.class,
            RecordUxService.class,
            ReportingService.class,
            ResourceService.class,
            RxLookupService.class,
            StatusService.class);

    /**
     * Endpoints that intentionally run without a security-object check, keyed
     * {@code SimpleClassName#method}. Every one is self-scoped to the authenticated caller or
     * delegates to a manager that enforces the right itself; each still fails closed for anonymous
     * callers because {@code getLoggedInInfo()} throws without one.
     */
    private static final Map<String, String> EXEMPT_ENDPOINTS = Map.of(
            "StatusService#checkIfAuthed",
            "authentication probe that returns only the caller's own provider number",
            "PersonaService#getMyRights",
            "authorization primitive reporting the caller's own roles and privileges",
            "PersonaService#isAllowedAccessToPatientRecord",
            "authorization primitive answering only for the caller",
            "PersonaService#getMyNavbar",
            "the caller's own navigation menus and program domain",
            "PersonaService#setDefaultProgram",
            "switches the caller's own current program; ProgramManager2 rejects programs outside their domain",
            "PersonaService#getMyPatientLists",
            "the caller's own patient-list tab layout",
            "PersonaService#getMyPatientListConfig",
            "the caller's own patient-list preferences",
            "PersonaService#getPreferences",
            "the caller's own dashboard preferences",
            "PersonaService#getDashboardMenu",
            "DashboardManager.getDashboards enforces _dashboardManager read and returns nothing when denied");

    /** A method declaration up to its opening parenthesis; group 1 is the method name. */
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "\\b(?:public|protected|private)\\s+(?:(?:static|final|synchronized|abstract)\\s+)*"
                    + "(?:<[^>]*>\\s+)?[\\w.$<>\\[\\],?\\s]+?\\s+(\\w+)\\s*\\(");

    private static final Pattern CALL = Pattern.compile("\\b(\\w+)\\s*\\(");

    static Stream<Arguments> sweptEndpoints() throws IOException {
        List<Arguments> endpoints = new ArrayList<>();
        for (Class<?> service : SWEPT_SERVICES) {
            Set<String> guarded = guardedMethods(service);
            for (String endpoint : resourceMethodNames(service)) {
                endpoints.add(Arguments.of(service.getSimpleName() + "#" + endpoint, guarded.contains(endpoint)));
            }
        }
        return endpoints.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sweptEndpoints")
    @DisplayName("should reach a hasPrivilege check or carry a documented exemption")
    void shouldReachPrivilegeCheck_forEverySweptEndpoint(String endpoint, boolean reachesPrivilegeCheck) {
        if (EXEMPT_ENDPOINTS.containsKey(endpoint)) {
            return;
        }
        assertThat(reachesPrivilegeCheck)
                .as("%s is a JAX-RS endpoint with no SecurityInfoManager.hasPrivilege check. Gate it on the "
                        + "security object for the data it returns or changes, or add it to EXEMPT_ENDPOINTS "
                        + "with the reason it is safe for every authenticated caller (#2798).", endpoint)
                .isTrue();
    }

    @Test
    @DisplayName("should name a live endpoint and give a reason for every exemption")
    void shouldNameLiveEndpoint_forEveryExemption() throws IOException {
        Set<String> endpoints = new HashSet<>();
        for (Class<?> service : SWEPT_SERVICES) {
            for (String endpoint : resourceMethodNames(service)) {
                endpoints.add(service.getSimpleName() + "#" + endpoint);
            }
        }

        assertThat(endpoints).as("exemptions must not outlive the endpoint they excuse")
                .containsAll(EXEMPT_ENDPOINTS.keySet());
        assertThat(EXEMPT_ENDPOINTS.values()).allSatisfy(reason -> assertThat(reason).isNotBlank());
    }

    @Test
    @DisplayName("should find the endpoints of every swept service in its source")
    void shouldFindEndpoints_forEverySweptService() throws IOException {
        // Guards the scanner itself: a service whose endpoints it cannot see would pass vacuously.
        for (Class<?> service : SWEPT_SERVICES) {
            Map<String, List<String>> bodies = methodBodies(service);
            assertThat(resourceMethodNames(service)).as("%s declares JAX-RS endpoints", service.getSimpleName())
                    .isNotEmpty()
                    .allSatisfy(endpoint -> assertThat(bodies).as("source body of %s#%s",
                            service.getSimpleName(), endpoint).containsKey(endpoint));
        }
    }

    @Test
    @DisplayName("should treat a helper-only privilege check as reachable from the endpoint")
    void shouldFollowSameClassHelpers_toPrivilegeCheck() throws IOException {
        // RecordUxService's summary shortcuts check nothing themselves; they delegate to getFullSummmary.
        assertThat(guardedMethods(RecordUxService.class)).contains("getFamilyHistory", "getAllergies", "print");
        // FormsService's single-argument requireRead delegates to its two-argument sibling.
        assertThat(guardedMethods(FormsService.class)).contains("requireRead", "getAllEFormNames");
        assertThat(guardedMethods(StatusService.class)).doesNotContain("checkIfAuthed");
    }

    /** Names of the public methods carrying a JAX-RS request-method designator such as {@code @GET}. */
    private static Set<String> resourceMethodNames(Class<?> service) {
        Set<String> names = new TreeSet<>();
        for (Method method : service.getDeclaredMethods()) {
            for (Annotation annotation : method.getAnnotations()) {
                if (annotation.annotationType().isAnnotationPresent(HttpMethod.class)) {
                    names.add(method.getName());
                }
            }
        }
        return names;
    }

    /**
     * Methods of {@code service} whose body calls {@code hasPrivilege} or, transitively, calls another
     * method of the same class that does. Overloads share a name, so a name counts as guarded only
     * when every body declared under it is; a body that calls its own name (an overload delegating
     * to a sibling, such as {@code requireRead(obj)} to {@code requireRead(obj, null)}) is guarded
     * once a sibling is.
     */
    private static Set<String> guardedMethods(Class<?> service) throws IOException {
        Map<String, List<String>> bodies = methodBodies(service);
        Map<String, boolean[]> guardedBodies = new HashMap<>();
        bodies.forEach((name, overloads) -> guardedBodies.put(name, new boolean[overloads.size()]));
        Set<String> guarded = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, List<String>> entry : bodies.entrySet()) {
                String name = entry.getKey();
                boolean[] state = guardedBodies.get(name);
                for (int i = 0; i < state.length; i++) {
                    if (state[i]) {
                        continue;
                    }
                    String body = entry.getValue().get(i);
                    boolean siblingGuarded = false;
                    for (int j = 0; j < state.length; j++) {
                        siblingGuarded |= j != i && state[j];
                    }
                    if (body.contains("hasPrivilege(") || callsAny(body, guarded)
                            || (siblingGuarded && callsAny(body, Set.of(name)))) {
                        state[i] = true;
                        changed = true;
                    }
                }
                boolean allGuarded = true;
                for (boolean b : state) {
                    allGuarded &= b;
                }
                if (allGuarded && guarded.add(name)) {
                    changed = true;
                }
            }
        }
        return guarded;
    }

    private static boolean callsAny(String body, Set<String> methodNames) {
        Matcher call = CALL.matcher(body);
        while (call.find()) {
            if (methodNames.contains(call.group(1))) {
                return true;
            }
        }
        return false;
    }

    /** Method name to the bodies (one per overload) declared in the service's source file. */
    private static Map<String, List<String>> methodBodies(Class<?> service) throws IOException {
        String source = stripCommentsAndLiterals(
                Files.readString(SOURCE_ROOT.resolve(service.getSimpleName() + ".java"), StandardCharsets.UTF_8));
        Map<String, List<String>> bodies = new HashMap<>();
        Matcher declaration = METHOD_DECLARATION.matcher(source);
        int searchFrom = 0;
        while (declaration.find(searchFrom)) {
            searchFrom = declaration.end();
            int parametersEnd = matching(source, declaration.end() - 1, '(', ')');
            if (parametersEnd < 0) {
                continue;
            }
            // Skip an optional throws clause; an abstract or interface method ends with ';' instead.
            int cursor = parametersEnd + 1;
            while (cursor < source.length() && source.charAt(cursor) != '{' && source.charAt(cursor) != ';') {
                cursor++;
            }
            if (cursor >= source.length() || source.charAt(cursor) != '{') {
                continue;
            }
            int bodyEnd = matching(source, cursor, '{', '}');
            if (bodyEnd < 0) {
                continue;
            }
            bodies.computeIfAbsent(declaration.group(1), _ -> new ArrayList<>())
                    .add(source.substring(cursor, bodyEnd + 1));
        }
        return bodies;
    }

    /** Index of the bracket closing the one at {@code open}, or -1 when unbalanced. */
    private static int matching(String source, int open, char opening, char closing) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == opening) {
                depth++;
            } else if (c == closing && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Blanks comments and string/char literals (keeping their length) so braces and words inside
     * them, such as a JSON sample in a comment or {@code "hasPrivilege("} in a message, cannot skew
     * the scan.
     */
    private static String stripCommentsAndLiterals(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && next == '*') {
                int end = source.indexOf("*/", i + 2);
                end = end < 0 ? source.length() : end + 2;
                blank(out, source, i, end);
                i = end;
            } else if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                end = end < 0 ? source.length() : end + 3;
                blank(out, source, i, end);
                i = end;
            } else if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < source.length() && source.charAt(end) != c) {
                    end += source.charAt(end) == '\\' ? 2 : 1;
                }
                end = Math.min(end + 1, source.length());
                blank(out, source, i, end);
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static void blank(StringBuilder out, String source, int start, int end) {
        for (int i = start; i < end; i++) {
            out.append(source.charAt(i) == '\n' ? '\n' : ' ');
        }
    }
}
