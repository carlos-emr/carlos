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
package io.github.carlos_emr.carlos.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Click actions that carry encoded data must live in {@code onclick} handlers, not in
 * {@code href="javascript:..."} links. A {@code javascript:} URL is URL-decoded by the
 * browser before it runs, so the data is not handled the same way as in an event handler.
 *
 * <p>This scans every JSP asset for {@code href="javascript:..."} values that also contain an
 * encoder call or encoded-value tag and fails if any are found.
 */
@DisplayName("JSP javascript: links with encoded data")
@Tag("unit")
class JspJavascriptHrefRegressionTest {
    private static final Pattern HREF_JAVASCRIPT =
            Pattern.compile("href\\s*=\\s*([\"'])\\s*javascript:", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENCODED_DATA = Pattern.compile(
            "context\\s*=\\s*[\"'](?:uriComponent|javaScriptAttribute|javaScript)[\"']"
                    + "|forUriComponent|forJavaScript|URLEncoder|SafeEncode\\.|Encode\\.for");
    /** Form pages whose only encoded value is an int parsed from the request (digits only). */
    private static final Pattern DIGITS_ONLY_FILES = Pattern.compile("form/formrourke[p]?\\d\\.jsp$");
    private static final Path WEBAPP_ROOT = resolveProjectPath(Path.of("src/main/webapp"));

    @Test
    @DisplayName("should not combine href javascript: with encoded data in any JSP")
    void shouldNotUseEncodedData_inJavascriptHref() throws Exception {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(WEBAPP_ROOT)) {
            for (Path path : (Iterable<Path>) files.filter(Files::isRegularFile)
                    .filter(JspJavascriptHrefRegressionTest::isJspAsset)::iterator) {
                if (DIGITS_ONLY_FILES.matcher(path.toString().replace('\\', '/')).find()) {
                    continue;
                }
                String text = Files.readString(path);
                Matcher m = HREF_JAVASCRIPT.matcher(text);
                while (m.find()) {
                    char quote = m.group(1).charAt(0);
                    int end = endOfAttribute(text, m.end(), quote);
                    if (ENCODED_DATA.matcher(text.substring(m.end(), end)).find()) {
                        int line = 1 + (int) text.substring(0, m.start()).chars().filter(c -> c == '\n').count();
                        offenders.add(WEBAPP_ROOT.relativize(path) + ":" + line);
                    }
                }
            }
        }
        assertThat(offenders)
                .as("use a real href with onclick=\"...(this.href); return false;\", or a <button type=\"button\"> for an action")
                .isEmpty();
    }

    /**
     * Pages converted away from javascript: links. An anchor with href="#" and an onclick is still a
     * button dressed as a link (Sonar Web:S6844): a popup gets its real URL as the href and opens
     * this.href, and an action on the page is a button.
     */
    private static final List<String> CONVERTED_PAGES = List.of(
            "admin/demographicmergerecord.jsp", "billing/CA/BC/billStatus.jsp", "billing/CA/BC/billingDigSearch.jsp",
            "billing/CA/BC/genTAS00.jsp", "billing/CA/BC/genTAS00ByOfficeNo.jsp", "billing/CA/BC/genTAS01.jsp",
            "billing/CA/ON/billingDigSearch.jsp", "billing/CA/ON/billingON3rdPayments.jsp", "casemgmt/attachClient.jsp",
            "encounter/immunization/config/AdministrateImmunizationSets.jsp",
            "encounter/oscarConsultationRequest/DisplayDemographicConsultationRequests.jsp",
            "form/formpalliativecare.jsp", "lab/CA/ALL/labDisplay.jsp", "lab/CA/ALL/labDisplayAjax.jsp",
            "lab/CA/BC/demo_select.jsp", "lab/CA/BC/labDisplay.jsp", "lab/CA/ON/CMLDisplay.jsp", "lab/DemographicLab.jsp",
            "messenger/ViewMessage.jsp", "oscarMDS/Index.jsp", "oscarMDS/OpenEChart.jsp", "oscarMDS/Page.jsp",
            "oscarMDS/SegmentDisplay.jsp", "rx/ChooseDrug.jsp", "rx/StaticScript2.jsp", "rx/ViewScript2.jsp",
            "rx/WriteScript.jsp", "tickler/ticklerDemoMain.jsp", "web/inboxhub/InboxhubTopbar.jsp");
    private static final Pattern HASH_ANCHOR_WITH_ONCLICK =
            Pattern.compile("<a\\b[^>]*?href\\s*=\\s*([\"'])#\\1[^>]*?\\bonclick\\s*=", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("should open the inbox consultation popup with a defined window name")
    void shouldOpenConsultationPopup_withDefinedWindowName() throws Exception {
        // The inbox's consult link now has a real href: if popupConsultation throws, the browser
        // follows that href in place. msgConsReq was never defined, so it always threw.
        for (String file : new String[] {"share/javascript/oscarMDSIndex.js", "WEB-INF/jsp/oscarMDS/documentsInQueues.jsp"}) {
            assertThat(Files.readString(WEBAPP_ROOT.resolve(file)))
                    .as(file)
                    .doesNotContain("window.open(page, msgConsReq")
                    .contains("window.open(page, 'consultationRequest', windowprops)");
        }
    }

    @Test
    @DisplayName("should open an inbox report link without encoding it a second time")
    void shouldOpenReportLink_withoutReencodingIt() throws Exception {
        // The converted inbox links pass this.href, which already carries %2C, %27 or %25 from
        // forUriComponent; encodeURI turned those into %252C, %2527 and %2525, so the servlet
        // received the escape text (a patient name such as Smith%2C%20John) instead of the value.
        assertThat(Files.readString(WEBAPP_ROOT.resolve("share/javascript/oscarMDSIndex.js")))
                .doesNotContain("window.open(encodeURI(page)")
                .contains("window.open(normalizeReportUrl(page), \"labreport\", windowprops)")
                .contains("new URL(page, document.baseURI).href");
    }

    @Test
    @DisplayName("should give converted links a real href or make them buttons")
    void shouldNotUseHashAnchorAsButton_inConvertedPages() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String page : CONVERTED_PAGES) {
            String text = Files.readString(WEBAPP_ROOT.resolve("WEB-INF/jsp").resolve(page));
            Matcher m = HASH_ANCHOR_WITH_ONCLICK.matcher(text);
            while (m.find()) {
                offenders.add(page + ":" + (1 + (int) text.substring(0, m.start()).chars().filter(c -> c == '\n').count()));
            }
        }
        assertThat(offenders).as("href=\"#\" anchors with an onclick").isEmpty();
    }

    /** Finds the closing quote, ignoring quotes inside JSP scriptlets, EL and custom-tag elements. */
    private static int endOfAttribute(String t, int from, char quote) {
        int i = from;
        while (i < t.length()) {
            int skipped = skipJspConstruct(t, i);
            if (skipped > i) {
                i = skipped;
            } else if (t.charAt(i) == quote) {
                return i;
            } else {
                i++;
            }
        }
        return t.length();
    }

    private static int skipJspConstruct(String t, int i) {
        if (t.startsWith("<%", i)) {
            int e = t.indexOf("%>", i);
            return e < 0 ? t.length() : e + 2;
        }
        if (t.startsWith("${", i)) {
            int e = t.indexOf('}', i);
            return e < 0 ? t.length() : e + 1;
        }
        if (t.startsWith("<carlos:", i) || t.startsWith("<c:", i) || t.startsWith("<fmt:", i)) {
            int j = i;
            while (j < t.length()) {
                if (t.startsWith("<%", j)) {
                    int e = t.indexOf("%>", j);
                    j = e < 0 ? t.length() : e + 2;
                } else if (t.startsWith("/>", j)) {
                    return j + 2;
                } else {
                    j++;
                }
            }
            return t.length();
        }
        return i;
    }

    private static boolean isJspAsset(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".jsp") || name.endsWith(".jspf") || name.endsWith(".tag");
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty("basedir", System.getProperty("user.dir")))
                .toAbsolutePath().normalize();
        for (int checked = 0; current != null && checked < 6; checked++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath);
    }
}
