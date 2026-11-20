/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The document page's "Send to myCarlos" dialog, read from its sources: where it is rendered, how its
 * attributes are encoded, which pages load its scripts, and its copy in every bundle.
 *
 * @since 2026-11-06
 */
@Tag("unit")
@Tag("fast")
@Tag("security")
@DisplayName("Send to myCarlos dialog sources")
class MyCarlosSendDialogJspRegressionTest {

    private static final Path DIALOG = Path.of("src/main/webapp/WEB-INF/jsp/documentManager/mycarlosSendDialog.jspf");
    private static final Path SHOW_DOCUMENT = Path.of("src/main/webapp/WEB-INF/jsp/documentManager/showDocument.jsp");
    private static final Path INBOX_LIST = Path.of("src/main/webapp/WEB-INF/jsp/oscarMDS/documentsInQueues.jsp");
    private static final Path INBOX_INDEX = Path.of("src/main/webapp/WEB-INF/jsp/oscarMDS/Index.jsp");
    private static final Path SEND_JS = Path.of("src/main/webapp/share/javascript/demographic/mycarlos-send.js");
    private static final Path DIALOG_JS = Path.of("src/main/webapp/share/javascript/demographic/mycarlos-send-dialog.js");
    private static final Path STANDALONE = Path.of("src/main/webapp/WEB-INF/jsp/demographic/mycarlosSend.jsp");
    /** The version query every reference to the dialog's scripts and stylesheet carries. */
    private static final String VERSION = "?v=${carlos:forUriComponent(mycarlosSendVersion)}";
    private static final String VERSION_SET = "<c:set var=\"mycarlosSendVersion\" value=\"<%= "
            + "io.github.carlos_emr.carlos.integration.patientportal.web.MyCarlosSendAssets.version(application) %>\"/>";
    /** A src or href naming one of the three assets, up to its closing quote. */
    private static final Pattern ASSET_REFERENCE =
            Pattern.compile("(?:src|href)=\"[^\"]*/share/(?:javascript/demographic/mycarlos-send(?:-dialog)?\\.js|css/mycarlos-send\\.css)[^\"]*\"");
    /** A URL-bearing or data attribute whose value starts with the JavaScript-string encoder. */
    private static final Pattern JS_ENCODER_IN_ATTRIBUTE =
            Pattern.compile("(src|href|action|data-[a-z-]+)=\"\\$\\{carlos:forJavaScript\\(");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should encode every dialog attribute for HTML and never with the JavaScript encoder")
    void shouldEncodeDialogAttributes_forHtmlAttributes() throws IOException {
        String dialog = read(DIALOG);
        assertThat(JS_ENCODER_IN_ATTRIBUTE.matcher(dialog).find()).isFalse();
        assertThat(dialog).doesNotContain("forJavaScript");
        assertThat(dialog).contains("data-context=\"${carlos:forHtmlAttribute(mycarlosSendCtx)}\"");
        assertThat(dialog).contains("action=\"${carlos:forHtmlAttribute(mycarlosSendCtx)}/demographic/PortalDelivery\"");
        assertThat(dialog).contains("data-patient=\"${carlos:forHtmlAttribute(pageScope.mycarlosSendPatient)}\"");
        assertThat(dialog).contains("data-document=\"${carlos:forHtmlAttribute(mycarlosSendDoc)}\"");
        assertThat(dialog).doesNotContain("<script");
        assertThat(JS_ENCODER_IN_ATTRIBUTE.matcher(read(SHOW_DOCUMENT)).find()).isFalse();
    }

    @Test
    @DisplayName("should name the dialog by its heading and suffix every id with the document")
    void shouldLabelDialogByHeading_withDocumentScopedIds() throws IOException {
        String dialog = read(DIALOG);
        assertThat(dialog).contains("aria-labelledby=\"mycarlos-send-title-${carlos:forHtmlAttribute(mycarlosSendDoc)}\"");
        assertThat(dialog).contains("<h2 id=\"mycarlos-send-title-${carlos:forHtmlAttribute(mycarlosSendDoc)}\">");
        // Described by the document's title, so a screen reader says which document is being sent.
        assertThat(dialog).contains("aria-describedby=\"mycarlos-send-document-${carlos:forHtmlAttribute(mycarlosSendDoc)}\"");
        assertThat(dialog).contains("<p class=\"document-title\" id=\"mycarlos-send-document-${carlos:forHtmlAttribute(mycarlosSendDoc)}\"");
        var ids = Pattern.compile("\\bid=\"([^\"]*)\"").matcher(dialog);
        int count = 0;
        while (ids.find()) {
            assertThat(ids.group(1)).endsWith("-${carlos:forHtmlAttribute(mycarlosSendDoc)}");
            count++;
        }
        assertThat(count).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("should render the dialog after the document's last form, and keep the link as the fallback")
    void shouldRenderDialog_outsideTheDocumentForms() throws IOException {
        String page = read(SHOW_DOCUMENT);
        int include = page.indexOf("<%@ include file=\"/WEB-INF/jsp/documentManager/mycarlosSendDialog.jspf\" %>");
        int lastFormEnd = page.lastIndexOf("</form>");
        assertThat(include).isGreaterThan(lastFormEnd).isGreaterThan(0);
        int button = page.indexOf("data-mycarlos-dialog=\"mycarlos-send-dialog-");
        assertThat(button).isPositive();
        String link = page.substring(button, page.indexOf("</a>", button));
        assertThat(link).contains("/demographic/PortalDelivery?demographicNo=").contains("target=\"_blank\"");
    }

    @Test
    @DisplayName("should load the dialog scripts on both inbox pages, which insert documents without running their scripts")
    void shouldLoadDialogScripts_onBothInboxHosts() throws IOException {
        for (Path host : new Path[] {INBOX_LIST, INBOX_INDEX}) {
            String jsp = read(host);
            assertThat(jsp).contains("/share/javascript/demographic/mycarlos-send.js" + VERSION + "\"></script>");
            assertThat(jsp).contains("/share/javascript/demographic/mycarlos-send-dialog.js" + VERSION + "\"></script>");
            assertThat(jsp).contains("/share/javascript/csrfTokenFetch.js\"></script>");
            assertThat(jsp).contains("/share/css/mycarlos-send.css" + VERSION + "\">");
        }
    }

    @Test
    @DisplayName("should keep the document page URL and write portal text as text")
    void shouldNeverRewriteDocumentUrl_orWriteMarkup() throws IOException {
        String send = read(SEND_JS);
        String dialog = read(DIALOG_JS);
        assertThat(dialog).doesNotContain("replaceState").doesNotContain("innerHTML");
        assertThat(send).doesNotContain("innerHTML");
        int persist = send.indexOf("if (settings.persistInUrl && root.dataset.delivery)");
        assertThat(persist).isPositive();
        assertThat(send.indexOf("replaceState")).isGreaterThan(persist);
        assertThat(send).contains("persistInUrl: false");
        assertThat(dialog).contains("persistInUrl: false");
    }

    @Test
    @DisplayName("should block Close while a send runs, as decided, behind one switch")
    void shouldBlockCloseWhileSending_asDecided() throws IOException {
        String dialog = read(DIALOG_JS);
        assertThat(dialog).contains("const CLOSE_WHILE_SENDING = false;");
        assertThat(Pattern.compile("CLOSE_WHILE_SENDING\\s*=").matcher(dialog).results().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("should reference the dialog's scripts and stylesheet only with the build's version query")
    void shouldVersionEveryAssetReference_onEveryHost() throws IOException {
        // A browser kept the previous mycarlos-send.js after an upgrade (no Cache-Control on static
        // files), so the dialog found no CarlosMyCarlosSend and the button opened a new tab instead.
        for (Path host : new Path[] {DIALOG, SHOW_DOCUMENT, INBOX_LIST, INBOX_INDEX, STANDALONE}) {
            String jsp = read(host);
            var references = ASSET_REFERENCE.matcher(jsp).results().map(match -> match.group()).toList();
            assertThat(references).as(host.toString()).isNotEmpty();
            assertThat(references).as(host.toString()).allSatisfy(reference -> assertThat(reference).endsWith(VERSION + "\""));
        }
        // Each page sets the version before its first reference; showDocument.jsp's come after the
        // fragment, which sets it.
        for (Path host : new Path[] {DIALOG, INBOX_LIST, INBOX_INDEX, STANDALONE}) {
            String jsp = read(host);
            int set = jsp.indexOf(VERSION_SET);
            assertThat(set).as(host.toString()).isNotNegative();
            // Without the core taglib, <c:set> is printed as text and the query comes out empty.
            assertThat(jsp).as(host.toString()).contains("<%@ taglib uri=\"jakarta.tags.core\" prefix=\"c\" %>");
            assertThat(set).as(host.toString()).isLessThan(jsp.indexOf(VERSION));
        }
        String page = read(SHOW_DOCUMENT);
        int include = page.indexOf("<%@ include file=\"/WEB-INF/jsp/documentManager/mycarlosSendDialog.jspf\" %>");
        assertThat(include).isNotNegative();
        assertThat(include).isLessThan(page.indexOf(VERSION));
    }

    @Test
    @DisplayName("should have the dialog's copy in every bundle")
    void shouldHaveDialogCopy_inEveryBundle() throws IOException {
        for (String locale : new String[] {"en", "fr", "es", "pl", "pt_BR"}) {
            var bundle = new Properties();
            try (var reader = Files.newBufferedReader(Path.of("src/main/resources/oscarResources_" + locale + ".properties"),
                    StandardCharsets.ISO_8859_1)) {
                bundle.load(reader);
            }
            for (String key : new String[] {"mycarlos.delivery.sending", "mycarlos.delivery.sent", "mycarlos.delivery.close",
                    "mycarlos.delivery.new", "mycarlos.delivery.title"}) {
                assertThat(bundle.getProperty(key)).as(locale + " " + key).isNotBlank();
            }
        }
    }
}
