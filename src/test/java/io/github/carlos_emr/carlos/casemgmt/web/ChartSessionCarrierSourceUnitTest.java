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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No code keeps one chart's working state, or a patient's identity, in a login-wide session
 * attribute any more (issue #4673). Each of these attributes was written by one patient's window
 * and read by a request from another: the patient a request fell back to, the read-only right,
 * the remembered note filters the print applies, the text the last save wrote into the legacy
 * eChart, the draft-restore signal, a patient's message and lab lists, the legacy editor's bean,
 * and the patient number and data that the Disease Registry, the waiting list, the Rx allergy page
 * and the PMM client view copied into the session under generic names, and the appointment last
 * opened from the day sheet, which tied one patient's Health Tracker save and multisite
 * prescription address to another patient's visit, the Messenger's message, PDF list and
 * compose attachments, which a second Messenger window replaced or sent, the BC bill being
 * built, which a second billing window replaced so the first billed the other patient, the BC
 * invoice last viewed, which the receipt after "Update Invoice" showed in place of the one
 * updated, the Ontario year-end statement's totals, which another window's search replaced
 * before the PDF was printed, and the note a PMM client search attaches, which another window's
 * search redirected. The scan covers all main Java and JSP sources, with comments removed, so a
 * reintroduction anywhere fails here.
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("No login-wide chart state in the session")
class ChartSessionCarrierSourceUnitTest extends CarlosUnitTestBase {

    private static final Path ROOT = Path.of(System.getProperty("basedir", "")).toAbsolutePath();

    /**
     * Each pattern is a session read or write of a login-wide name that the fix replaced. A
     * receiver other than {@code request} is a session ({@code session}, {@code se},
     * {@code request.getSession()}, {@code getSession(false)}); request attributes live for one
     * request and are allowed.
     */
    private static final String SESSION_ACCESS = "(?<!request)\\.(get|set|remove)Attribute\\(\\s*\"";
    private static final String SESSION_RECEIVER =
            "\\b(session|se|httpSession|sess|getSession\\(\\s*(true|false)?\\s*\\))";

    /** Every login-wide name the fix replaced, for the session forms the patterns above do not cover. */
    private static final String REPLACED_NAMES = "(casemgmt_DemoNo|readonly|lastSavedNoteString|restoring"
            + "|casemgmt_msgBeans|casemgmt_labsbeans|casemgmt_oscar_bean|demographicNo|demographicId|clientAge"
            + "|clientGender|demoInfo|patientWaitingList|allDiagnostics|cur_appointment_no|PDFAttachment"
            + "|billingSessionBean|attachedDemographics|viewMessage[A-Za-z]*|CaseManagementViewAction_filter_\\w*"
            + "|billingViewBean|summary|noteId)";
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile(SESSION_ACCESS + "casemgmt_DemoNo\""),
            Pattern.compile("sessionScope\\.readonly"),
            Pattern.compile("(se|session|getSession\\(\\))\\.setAttribute\\(\\s*\"readonly\""),
            Pattern.compile(SESSION_ACCESS + "lastSavedNoteString\""),
            Pattern.compile(SESSION_ACCESS + "restoring\""),
            Pattern.compile(SESSION_ACCESS + "CaseManagementViewAction_filter_"),
            Pattern.compile(SESSION_ACCESS + "casemgmt_msgBeans\""),
            Pattern.compile(SESSION_ACCESS + "casemgmt_labsbeans\""),
            Pattern.compile("(?<!request)\\.setAttribute\\(\\s*\"casemgmt_oscar_bean\"\\s*,"),
            // A patient number, or a patient's data, under a generic session name: every page that
            // resolves ${demographicNo} (or the others) through EL falls back to it whenever its own
            // action sets no attribute of that name, i.e. to whichever patient's page wrote it last.
            Pattern.compile(SESSION_RECEIVER + "\\.(get|set)Attribute\\(\\s*\"(demographicNo|demographicId|"
                    + "clientAge|clientGender|demoInfo|patientWaitingList|allDiagnostics)\""),
            // The day sheet's last-opened appointment, for any patient. It is now filed per patient
            // (ChartSessionKeys.currentAppointment) and read back only for that patient.
            Pattern.compile(SESSION_ACCESS + "cur_appointment_no\""),
            // The Messenger viewer's message and PDF list: request attributes, and the PDF
            // download names its message.
            Pattern.compile(SESSION_ACCESS + "PDFAttachment\""),
            Pattern.compile(SESSION_RECEIVER + "\\.setAttribute\\(\\s*\"(viewMessage[A-Za-z]*|attachedDemographics)\""),
            // The BC bill being built: one per window under its own key (BcBillStaging), handed to
            // the pages as a request attribute; findAttribute would fall back to the session.
            Pattern.compile(SESSION_ACCESS + "billingSessionBean\""),
            Pattern.compile("findAttribute\\(\\s*\"billingSessionBean\""),
            // The BC invoice a view renders, the Ontario year-end statement's totals and the note
            // a PMM client search attaches: each request names or recomputes its own.
            Pattern.compile(SESSION_RECEIVER + "\\.(get|set|remove)Attribute\\(\\s*\"(billingViewBean|summary|noteId)\""),
            Pattern.compile("findAttribute\\(\\s*\"billingViewBean\""),
            // The same names through EL's session scope, JSTL or a page-context session write.
            Pattern.compile("sessionScope\\." + REPLACED_NAMES + "\\b"),
            Pattern.compile("sessionScope\\[\\s*['\"]" + REPLACED_NAMES + "['\"]\\s*\\]"),
            Pattern.compile("setAttribute\\(\\s*\"" + REPLACED_NAMES + "\"[^;]*SESSION_SCOPE"));

    /**
     * The login's Messenger bean no longer carries compose attachments: each compose window stages
     * its own (MsgAttachmentStaging). In the Messenger sources the session bean is always named
     * {@code bean}; the attachment builder in MsgAttachPDF2Action is a scratch instance.
     */
    private static final Pattern MESSENGER_BEAN_ATTACHMENT = Pattern.compile(
            "\\bbean\\.(nullAttachment|nullPDFAttachment|getAttachment|getPDFAttachment|setAttachment"
                    + "|setPDFAttachment|setAppendPDFAttachment)\\(");

    @Test
    void shouldFindNoLoginWideChartState_inAnyMainSource() {
        List<String> offenders = new ArrayList<>();
        for (Path source : sources()) {
            String code = withoutComments(read(source), source.toString().endsWith(".java"));
            if (hasForbiddenSessionTag(code)) {
                offenders.add(ROOT.relativize(source) + " writes a login-wide session tag");
            }
            for (Pattern pattern : FORBIDDEN) {
                if (pattern.matcher(code).find()) {
                    offenders.add(ROOT.relativize(source) + " matches " + pattern.pattern());
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    void shouldKeepComposeAttachmentsOffTheLoginsMessengerBean_inMessengerSources() {
        List<String> offenders = new ArrayList<>();
        for (Path source : sources()) {
            String path = source.toString().replace('\\', '/');
            if (!path.contains("/messenger/")) {
                continue;
            }
            if (MESSENGER_BEAN_ATTACHMENT.matcher(withoutComments(read(source), path.endsWith(".java"))).find()) {
                offenders.add(ROOT.relativize(source).toString());
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    void shouldRememberOnlyAHandPickedPrescriptionAddress_inTheSession() {
        // RX_ADDR is the prescriber's own last address choice. The prescription preview used to
        // store the site of the last day-sheet appointment there as well, so one patient's visit
        // site became the default address on the next patient's prescription.
        Pattern write = Pattern.compile(SESSION_RECEIVER + "\\.setAttribute\\(\\s*\"RX_ADDR\"");
        List<String> writers = new ArrayList<>();
        for (Path source : sources()) {
            if (write.matcher(withoutComments(read(source), source.toString().endsWith(".java"))).find()) {
                writers.add(ROOT.relativize(source).toString().replace('\\', '/'));
            }
        }
        assertThat(writers).containsExactly("src/main/webapp/WEB-INF/jsp/rx/setDefaultAddr.jsp");
    }

    @Test
    void shouldReadTheReadOnlyRightFromTheRequest_onTheOldChartViewAndNoteSearch() {
        for (String page : new String[]{"CaseManagementView.jsp", "search.jsp"}) {
            String jsp = read(ROOT.resolve("src/main/webapp/WEB-INF/jsp/casemgmt").resolve(page));
            assertThat(jsp).as(page).contains("requestScope.readonly");
        }
        String action = read(ROOT.resolve("src/main/java/io/github/carlos_emr/carlos/casemgmt/web/CaseManagementView2Action.java"));
        assertThat(action).contains("request.setAttribute(\"readonly\"");
    }

    @Test
    void shouldPromptOnlyForTheBookedPatientsWaitingList_onTheAppointmentAddedPage() {
        // The waiting-list page leaves the list it last showed in the session as waitingListName.
        // Read through bare EL, it offered to take a patient on no waiting list off that list.
        String jsp = withoutComments(read(ROOT.resolve(
                "src/main/webapp/WEB-INF/jsp/appointment/appointmentaddarecord.jsp")), false);
        assertThat(jsp).contains("${not empty requestScope.waitingListName}");
        assertThat(hasUnscopedWaitingListExpression(jsp)).isFalse();
    }

    @Test
    void shouldKeepTheLabDropdownEmpty_onTheLegacyNavigation() {
        // The page used to put the patient's labs in the session for a dropdown that read another
        // name and never listed one. The dropdown's loop wrote lab text unencoded into URLs without
        // the context path, so it stays as the users always saw it: empty, with no lab query.
        String jsp = withoutComments(read(ROOT.resolve("src/main/webapp/WEB-INF/jsp/casemgmt/navigation.jsp")), false);
        assertThat(jsp).doesNotContain("populateLabResultsData")
                .doesNotContain("casemgmtLabsbeans")
                .doesNotContain("casemgmt_labsbeans")
                .doesNotContain("${labrst");
    }

    /** Parse tag attributes once, including quoted > characters and either attribute order. */
    private static boolean hasForbiddenSessionTag(String code) {
        if (!code.contains("session")) {
            return false;
        }
        Pattern replacedName = Pattern.compile(REPLACED_NAMES);
        for (Element element : Jsoup.parse(code, "", Parser.xmlParser()).getAllElements()) {
            if ("session".equals(element.attr("scope"))
                    && ("bean".equals(element.attr("var"))
                    || ("c:set".equals(element.tagName())
                    && replacedName.matcher(element.attr("var")).matches()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnscopedWaitingListExpression(String code) {
        Pattern bareName = Pattern.compile("(?<![\\w.])waitingList(Name|Id|DemographicNo)\\b");
        int cursor = 0;
        int open;
        while ((open = code.indexOf("${", cursor)) >= 0) {
            int close = code.indexOf('}', open + 2);
            if (close < 0) {
                return false;
            }
            if (bareName.matcher(code).region(open + 2, close).find()) {
                return true;
            }
            cursor = close + 1;
        }
        return false;
    }

    @Test
    void shouldDetectSessionTags_withEitherAttributeOrderAndQuotedDelimiters() {
        assertThat(hasForbiddenSessionTag("<c:set scope='session' value='a>b' var='demographicNo'/>")).isTrue();
        assertThat(hasForbiddenSessionTag("<c:set var='noteId' scope='session'/>")).isTrue();
        assertThat(hasForbiddenSessionTag("<c:set var='bean' scope='session'/>")).isTrue();
        assertThat(hasForbiddenSessionTag("<script><c:set scope='session' var='noteId'/></script>")).isTrue();
        assertThat(hasForbiddenSessionTag("<c:set var='noteId' scope='request'/>")).isFalse();
        assertThat(hasForbiddenSessionTag("<c:set var='unrelatedPreference' scope='session'/>")).isFalse();
        assertThat(hasForbiddenSessionTag("<c:set " + "x ".repeat(100_000))).isFalse();
    }

    @Test
    void shouldDetectBareWaitingListNames_insideBoundedExpressions() {
        assertThat(hasUnscopedWaitingListExpression("${not empty waitingListName}")).isTrue();
        assertThat(hasUnscopedWaitingListExpression("${waitingListId}")).isTrue();
        assertThat(hasUnscopedWaitingListExpression("${requestScope.waitingListDemographicNo}")).isFalse();
        assertThat(hasUnscopedWaitingListExpression("${not empty requestScope.waitingListName}")).isFalse();
        assertThat(hasUnscopedWaitingListExpression("${".repeat(100_000))).isFalse();
    }

    private static List<Path> sources() {
        List<Path> files = new ArrayList<>();
        for (String dir : new String[]{"src/main/java", "src/main/webapp"}) {
            try (Stream<Path> walk = Files.walk(ROOT.resolve(dir))) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".jsp")
                                || p.toString().endsWith(".jspf"))
                        .forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        assertThat(files).hasSizeGreaterThan(1000);
        return files;
    }

    /**
     * The source with comments blanked out (newlines kept). JSP comments are removed from every
     * file; Java comments only from Java files, and only outside string and character literals, so
     * a {@code "/*"} inside a string cannot hide the code after it. JSPs keep their Java and
     * JavaScript block comments: telling them apart from template text would need a JSP parser, and
     * a false match there only means a closer look.
     */
    static String withoutComments(String code, boolean java) {
        String stripped = code.replaceAll("(?s)<%--.*?--%>", "");
        return java ? withoutJavaComments(stripped) : stripped;
    }

    private static String withoutJavaComments(String code) {
        StringBuilder out = new StringBuilder(code.length());
        int i = 0;
        while (i < code.length()) {
            char c = code.charAt(i);
            char next = i + 1 < code.length() ? code.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < code.length() && code.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                int close = code.indexOf("*/", i + 2);
                int stop = close < 0 ? code.length() : close + 2;
                for (int k = i; k < stop; k++) {
                    if (code.charAt(k) == '\n') {
                        out.append('\n');
                    }
                }
                i = stop;
            } else if (c == '"' && code.startsWith("\"\"\"", i)) {
                int close = code.indexOf("\"\"\"", i + 3);
                int stop = close < 0 ? code.length() : close + 3;
                out.append(code, i, stop);
                i = stop;
            } else if (c == '"' || c == '\'') {
                int k = i + 1;
                while (k < code.length() && code.charAt(k) != c && code.charAt(k) != '\n') {
                    k += code.charAt(k) == '\\' ? 2 : 1;
                }
                int stop = Math.min(k + 1, code.length());
                out.append(code, i, stop);
                i = stop;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    @Test
    void shouldKeepCodeAfterACommentMarkerInAString_whenStrippingJavaComments() {
        String code = "String glob = \"src/*\";\nsession.setAttribute(\"demographicNo\", x); /* c */ // d\n";

        String stripped = withoutComments(code, true);

        assertThat(stripped).contains("session.setAttribute(\"demographicNo\", x);")
                .doesNotContain("/* c */").doesNotContain("// d");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
