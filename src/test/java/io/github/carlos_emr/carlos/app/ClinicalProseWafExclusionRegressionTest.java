/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the packaged WAF's per-argument exemptions for clinician free text outside the note
 * route (exclusion ids 1100-1199 in the BEFORE-CRS file).
 *
 * <p>Every route and argument below was measured on a packaged Ubuntu 26.04 install: posted
 * through the front door with six prose shapes, each was answered 403 on the same three that
 * blocked the chart note (932110 on a pasted link carrying {@code &cmd}, 932115/942350 on
 * everyday clinical wording, 931100 on a value beginning with an internal IP link). The table
 * here is the policy contract: a route or argument that leaves the exclusion file without
 * leaving this table fails the build, and vice versa.</p>
 */
@Tag("unit")
@Tag("security")
class ClinicalProseWafExclusionRegressionTest {

    private static final Path EXCLUSIONS = resolveProjectPath(
            Path.of("debian", "assets", "modsecurity", "REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf"));
    private static final Path AFTER_CRS_EXCLUSIONS = resolveProjectPath(
            Path.of("debian", "assets", "modsecurity", "RESPONSE-999-EXCLUSION-RULES-AFTER-CRS.conf"));
    /**
     * The four per-row prose fields whose names carry a row counter. They cannot be literal
     * ctl targets, so they are config-time regex exclusions in the AFTER-CRS file; each
     * pattern must stay anchored at both ends so only the exact page-generated shape matches.
     */
    private static final List<String> PER_ROW_PROSE_PATTERNS = List.of(
            "^comments-[0-9]+$",
            "^test_[0-9]+[.]labnotes$",
            "^contact_[0-9]+[.]note$",
            "^waitingListBean[[][0-9]+[]][.]note$");
    /** The only non-prose exclusions the AFTER-CRS file may carry (CSRF token, session cookie). */
    private static final List<String> AFTER_CRS_INFRASTRUCTURE_TARGETS = List.of(
            "!ARGS:CSRF-TOKEN", "!REQUEST_COOKIES:/^JSESSIONID$/");

    /**
     * The six tag families that misread prose. Unlike exclusion 1010 on the note route,
     * attack-xss is NOT removed anywhere in the survey block, the per-row patterns or the
     * form list: the CRS XSS rules did not fire on any measured prose shape at paranoia
     * level 1, and legacy views still render some stored text raw, so that layer stays on.
     */
    private static final String[] CONTENT_ATTACK_TAGS = {
            "attack-sqli", "attack-rce",
            "attack-injection-php", "attack-protocol", "attack-lfi", "attack-rfi"};

    /**
     * One row per rule: id, route, method, exempted prose arguments. The route is the exact
     * Struts action path; the method is what the rule chains on. Keep this in the same order
     * as the file so a mismatch reads naturally.
     */
    static Stream<Arguments> exemptedProseRoutes() {
        return Stream.of(
                Arguments.of("1100", "/carlos/encounter/RequestConsultation", "POST",
                        List.of("reasonForConsultation", "clinicalInformation", "concurrentProblems",
                                "currentMedications", "allergies", "appointmentNotes")),
                Arguments.of("1101", "/carlos/encounter/AddSpecialist", "POST", List.of("annotation", "address")),
                Arguments.of("1102", "/carlos/encounter/AddInstitution", "POST", List.of("annotation")),
                Arguments.of("1103", "/carlos/encounter/AddDepartment", "POST", List.of("annotation")),
                Arguments.of("1104", "/carlos/tickler/DbTicklerAdd", "POST", List.of("ticklerMessage")),
                Arguments.of("1105", "/carlos/tickler/EditTickler", "POST", List.of("newMessage")),
                Arguments.of("1106", "/carlos/web/dashboard/display/AssignTickler", "POST",
                        List.of("messageAppend", "comments")),
                Arguments.of("1107", "/carlos/rx/writeScript", "POST", List.of("special", "customName")),
                Arguments.of("1108", "/carlos/rx/WriteScript", "POST", List.of("specialInstruction")),
                Arguments.of("1109", "/carlos/rx/UpdateScript", "POST", List.of("instruction")),
                Arguments.of("1110", "/carlos/rx/deleteRx", "POST", List.of("comment", "reason", "drugSpecial")),
                Arguments.of("1111", "/carlos/rx/ViewAddRxComment", "POST", List.of("comment")),
                Arguments.of("1112", "/carlos/rx/updateFavorite2", "POST", List.of("special", "customName")),
                Arguments.of("1113", "/carlos/rx/addAllergy2", "POST", List.of("reactionDescription")),
                Arguments.of("1114", "/carlos/rx/RxReason", "POST", List.of("comments")),
                Arguments.of("1115", "/carlos/rx/managePharmacy", "POST", List.of("pharmacyNotes")),
                Arguments.of("1116", "/carlos/encounter/MeasurementData", "POST", List.of("instruction")),
                Arguments.of("1117", "/carlos/prevention/AddPrevention", "POST", List.of("comments")),
                Arguments.of("1118", "/carlos/documentManager/ManageDocument", "POST", List.of("documentDescription")),
                Arguments.of("1119", "/carlos/documentManager/ViewIncomingDocs", "POST", List.of("documentDescription")),
                Arguments.of("1120", "/carlos/oscarMDS/UpdateStatus", "POST", List.of("comment")),
                Arguments.of("1121", "/carlos/lab/CA/ALL/UnlinkDemographic", "POST", List.of("reason")),
                Arguments.of("1122", "/carlos/hospitalReportManager/Modify", "POST", List.of("comment", "description")),
                Arguments.of("1123", "/carlos/messenger/CreateMessage", "POST", List.of("message", "subject")),
                Arguments.of("1124", "/carlos/email/emailSendAction", "POST", List.of("bodyEmail", "internalComment")),
                Arguments.of("1125", "/carlos/appointment/AddRecord", "POST", List.of("reason", "notes")),
                Arguments.of("1126", "/carlos/appointment/UpdateRecord", "POST", List.of("reason", "notes")),
                Arguments.of("1127", "/carlos/appointment/CutRecord", "POST", List.of("reason", "notes")),
                Arguments.of("1128", "/carlos/appointment/appointmentcopyrecord", "POST", List.of("reason", "notes")),
                Arguments.of("1129", "/carlos/appointment/DeleteRecord", "POST", List.of("reason", "notes")),
                Arguments.of("1130", "/carlos/appointment/appointmentgrouprecords", "POST", List.of("reason", "notes")),
                Arguments.of("1131", "/carlos/demographic/DemographicUpdate", "POST",
                        List.of("alert", "notes", "phoneComment")),
                Arguments.of("1132", "/carlos/demographic/DemographicAddRecord", "POST",
                        List.of("phoneComment", "cust3", "content")),
                Arguments.of("1133", "/carlos/demographic/AddRelation", "POST", List.of("notes")),
                Arguments.of("1134", "/carlos/billing/CA/ON/BillingONSave", "POST", List.of("comment")),
                Arguments.of("1135", "/carlos/billing/CA/ON/UpdateBillingONCorrection", "POST", List.of("comment")),
                Arguments.of("1136", "/carlos/billing/CA/ON/ViewBillingONDisplay", "POST", List.of("comment")),
                Arguments.of("1137", "/carlos/billing/CA/BC/reprocessBill", "POST", List.of("notes", "messageNotes")),
                Arguments.of("1138", "/carlos/fax/faxAction", "POST", List.of("comments")),
                Arguments.of("1139", "/carlos/PMmodule/ProgramManagerView", "POST",
                        List.of("admission.admissionNotes", "admission.dischargeNotes")),
                // The one GET: the patient page's custom telephone-encounter reason travels on the
                // GET link that opens the chart (demographic/edit.jsp add2url).
                Arguments.of("1140", "/carlos/encounter/IncomingEncounter", "GET", List.of("reason")),
                // The other GET: the tickler list's DataTables search term rides the query string.
                Arguments.of("1141", "/carlos/tickler/ListTicklers", "GET", List.of("search[value]")));
    }

    /** Routes whose prose rides a GET query string; everything else must chain to POST. */
    private static final List<String> GET_ROUTES = List.of(
            "/carlos/encounter/IncomingEncounter", "/carlos/tickler/ListTicklers");

    @ParameterizedTest(name = "rule {0}: {2} {1}")
    @MethodSource("exemptedProseRoutes")
    @DisplayName("each prose route should exempt exactly its free-text arguments from the content-attack tags")
    void shouldExemptProseArguments_forRoute(String ruleId, String route, String method, List<String> arguments)
            throws IOException {
        String rule = readExclusionRule(ruleId);

        assertThat(rule)
                .as("rule %s is anchored on its own route and chained to %s", ruleId, method)
                .contains("SecRule REQUEST_URI \"@rx ^" + route + "(?:[;?]|$)\"")
                .contains("SecRule REQUEST_METHOD \"@streq " + method + "\"")
                // Per-argument only: nothing here may drop a signature request-wide.
                .doesNotContain("ctl:ruleRemoveById=")
                .doesNotContain("ctl:ruleRemoveByTag=");

        for (String argument : arguments) {
            for (String tag : CONTENT_ATTACK_TAGS) {
                assertThat(rule)
                        .as("rule %s exempts %s from %s", ruleId, argument, tag)
                        // A delimiter after the name, so "note" cannot be satisfied by "notes".
                        .containsPattern(Pattern.quote("ctl:ruleRemoveTargetByTag=" + tag + ";ARGS:" + argument)
                                + "(?:,|\"|\\\\)");
            }
        }

        // No argument beyond the table: every ctl target in the rule must be one of the listed
        // names, so a name slipped in without a row here fails.
        Stream.of(rule.split("\n"))
                .map(String::trim)
                .filter(line -> line.startsWith("ctl:"))
                .forEach(line -> {
                    String target = line.replaceAll("^ctl:ruleRemoveTargetByTag=[^;]+;ARGS:", "")
                            .replaceAll("[,\"\\\\]+$", "");
                    assertThat(arguments)
                            .as("rule %s names an argument that is not in the table: %s", ruleId, target)
                            .contains(target);
                });
    }

    @Test
    @DisplayName("the survey block and the per-row patterns should leave the XSS family inspected")
    void shouldKeepXssInspected_forSurveyAndPerRowFields() throws IOException {
        String before = read();
        String survey = before.substring(before.indexOf("Clinician free text on the rest of the application"));
        String after = Files.readString(AFTER_CRS_EXCLUSIONS, StandardCharsets.UTF_8);
        assertThat(survey).doesNotContain("attack-xss;ARGS:");
        assertThat(after).doesNotContain("\"attack-xss\"           \"!ARGS:/");
    }

    @Test
    @DisplayName("the survey block should carry no rules beyond the table")
    void shouldMatchTable_forSurveyRuleIds() throws IOException {
        String exclusions = read();
        long rulesInFile = Stream.of(exclusions.split("\n"))
                .filter(line -> line.matches("\\s*\"id:11\\d\\d,phase:1,pass,nolog,chain\""))
                .count();
        long rulesInTable = exemptedProseRoutes().count();

        assertThat(rulesInFile)
                .as("every id:11xx rule in the file has a row in this test's table")
                .isEqualTo(rulesInTable);
    }

    @Test
    @DisplayName("only the two query-string routes should be exempted on GET")
    void shouldChainToPost_forEveryRouteButTheQueryStringOnes() throws IOException {
        String exclusions = read();
        int getRules = 0;
        for (String block : exclusions.split("\n\n")) {
            if (block.contains("id:11") && block.contains("\"@streq GET\"")) {
                getRules++;
                assertThat(GET_ROUTES)
                        .as("a GET-chained survey rule names one of the query-string routes: %s", block)
                        .anySatisfy(route -> assertThat(block).contains(route));
            }
        }
        assertThat(getRules).as("exactly two survey rules are chained to GET").isEqualTo(GET_ROUTES.size());
    }

    /**
     * Returns the whole chained SecRule carrying {@code id:<ruleId>}, from its
     * {@code SecRule REQUEST_URI} line to the blank line that separates it from the next rule.
     * Bounded on the rule's structure, as {@code CaseManagementCppSaveRegressionTest} does, so
     * reordering or appending a clause cannot truncate the slice into vacuous assertions.
     */
    private String readExclusionRule(String ruleId) throws IOException {
        String exclusions = read();

        int idPosition = exclusions.indexOf("id:" + ruleId + ",");
        assertThat(idPosition).as("exclusion %s is present", ruleId).isGreaterThanOrEqualTo(0);

        int ruleStart = exclusions.lastIndexOf("SecRule REQUEST_URI", idPosition);
        assertThat(ruleStart).as("exclusion %s opens with a REQUEST_URI match", ruleId).isGreaterThanOrEqualTo(0);

        int ruleEnd = exclusions.indexOf("\n\n", idPosition);
        if (ruleEnd < 0) {
            ruleEnd = exclusions.length();
        }
        String rule = exclusions.substring(ruleStart, ruleEnd);
        assertThat(rule).as("exclusion %s slice spans its chained rule", ruleId).contains("SecRule REQUEST_METHOD");
        return rule;
    }

    @Test
    @DisplayName("per-row prose fields should be exempted by anchored regex patterns, and nothing broader")
    void shouldExemptPerRowFields_byAnchoredPatternOnly() throws IOException {
        // A regex target is rejected inside a ctl action but accepted by a config-time
        // SecRuleUpdateTargetByTag, which applies to an argument of that name on ANY route.
        // That is the accepted trade-off for these four fields, so what this pins is that
        // the patterns match exactly the page-generated shape (anchored, digits only) and
        // that the file never grows a bare ARGS, an unanchored pattern, or a fifth field
        // without this table changing with it.
        String after = Files.readString(AFTER_CRS_EXCLUSIONS, StandardCharsets.UTF_8).replace("\r\n", "\n");
        Pattern directive = Pattern.compile(
                "^SecRuleUpdateTargetByTag\\s+\"([^\"]+)\"\\s+\"([^\"]+)\"\\s*$", Pattern.MULTILINE);
        Matcher matcher = directive.matcher(after);
        Map<String, Set<String>> tagsByTarget = new HashMap<>();
        while (matcher.find()) {
            tagsByTarget.computeIfAbsent(matcher.group(2), key -> new HashSet<>()).add(matcher.group(1));
        }

        for (String pattern : PER_ROW_PROSE_PATTERNS) {
            String target = "!ARGS:/" + pattern + "/";
            assertThat(pattern).as("pattern %s is anchored at both ends", pattern)
                    .startsWith("^").endsWith("$");
            assertThat(tagsByTarget.get(target))
                    .as("per-row field %s is exempted from exactly the seven content-attack tags", pattern)
                    .containsExactlyInAnyOrder(CONTENT_ATTACK_TAGS);
        }
        Set<String> allowedTargets = new HashSet<>(AFTER_CRS_INFRASTRUCTURE_TARGETS);
        PER_ROW_PROSE_PATTERNS.forEach(pattern -> allowedTargets.add("!ARGS:/" + pattern + "/"));
        assertThat(tagsByTarget.keySet())
                .as("no exclusion target in the AFTER-CRS file beyond the infrastructure pair and the four per-row fields")
                .isSubsetOf(allowedTargets);
        assertThat(after)
                .doesNotContain("\"!ARGS\"")
                .doesNotContain("!ARGS:/^[^/]*[^$]/\"")
                .doesNotContain("SecRuleRemoveByTag")
                .doesNotContain("SecRuleRemoveById");
    }

    private static String read() throws IOException {
        return Files.readString(EXCLUSIONS, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(
                "maven.multiModuleProjectDirectory",
                System.getProperty("user.dir"))).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate " + relativePath + " from " + System.getProperty("user.dir"));
    }
}
