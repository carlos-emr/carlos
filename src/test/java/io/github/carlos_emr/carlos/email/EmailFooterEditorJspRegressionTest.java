/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shared Edit footer window (issue #3981): its browser-side cleaning matches the server's
 * allow-list, nothing reaches {@code innerHTML} uncleaned, and the clinic logo card on Configure
 * Email offers its upload only to administrators with write rights.
 *
 * @since 2026-10-08
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("Email footer editor and logo card")
class EmailFooterEditorJspRegressionTest {

    private static final Path MODAL = Path.of("src/main/webapp/WEB-INF/jsp/email/footerEditorModal.jspf");
    private static final Path SCRIPT = Path.of("src/main/webapp/share/javascript/email/footerEditor.js");
    private static final Path CONFIGURE = Path.of("src/main/webapp/WEB-INF/jsp/admin/configureEmail.jsp");
    private static final List<String> LOCALES = List.of("en", "es", "fr", "pl", "pt_BR");

    @Test
    @DisplayName("should load DOMPurify before the editor and take the limit from the constant the server enforces")
    void shouldLoadSanitizerFirst_andUseServerLimit() throws IOException {
        String modal = Files.readString(MODAL, StandardCharsets.UTF_8);

        int purify = modal.indexOf("/library/dompurify/purify.min.js");
        assertThat(purify).isGreaterThanOrEqualTo(0).isLessThan(modal.indexOf("/share/javascript/email/footerEditor.js"));
        assertThat(Path.of("src/main/webapp/library/dompurify/purify.min.js")).exists();
        assertThat(modal)
                .contains("data-max-length=\"<%= io.github.carlos_emr.carlos.email.core.EmailData.FOOTER_MAX_LENGTH %>\"")
                .contains("id=\"footerEditorText\" class=\"form-control\" contenteditable=\"true\"")
                .contains("<fmt:message key=\"${footerEditorScopeKey}\"/>")
                .contains("<fmt:message key=\"${footerEditorApplyKey}\"/>");
    }

    @Test
    @DisplayName("should clean in the browser with the server's tags and link schemes")
    void shouldMirrorServerAllowList_inBrowserCleaning() throws IOException {
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        assertThat(script)
                .contains("ALLOWED_TAGS: ['b', 'strong', 'i', 'em', 'br', 'p', 'div', 'a']")
                .contains("ALLOWED_ATTR: ['href']")
                .contains("ALLOW_DATA_ATTR: false")
                .contains("ALLOW_ARIA_ATTR: false")
                .contains("ALLOWED_URI_REGEXP: /^(?:https:|mailto:)/i")
                // Link addresses with hidden characters lose their address, as on the server, and the
                // link box refuses them with its usual message.
                .contains("var HIDDEN_CHARACTERS = /[\\p{Cc}\\p{Cf}\\uFFFD]/u;")
                .contains("window.DOMPurify.addHook('uponSanitizeAttribute', dropHiddenCharacterAddresses);")
                .contains("window.DOMPurify.removeHook('uponSanitizeAttribute', dropHiddenCharacterAddresses);")
                .contains("|| HIDDEN_CHARACTERS.test(address)) {")
                // Without DOMPurify nothing is put into the page as HTML, and editing is switched off.
                .contains("if (!window.DOMPurify || !html) {")
                .contains("opener.disabled = true;")
                // A footer link in a preview never navigates away from an unsent email.
                .contains("event.target.closest('.footer-editor-mail a')")
                .contains("String(html || '').replace(/<[^>]*>/g, ' ')")
                .doesNotContain("parseFromString")
                // Pasted text keeps no formatting.
                .contains("getData('text/plain')")
                .contains("document.execCommand('insertText', false, text)");
    }

    @Test
    @DisplayName("should put only cleaned HTML into innerHTML")
    void shouldAssignOnlyCleanedHtml_toInnerHtml() throws IOException {
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        Matcher assignment = Pattern.compile("\\.innerHTML = ([^;]+);").matcher(script);
        int count = 0;
        while (assignment.find()) {
            count++;
            String value = assignment.group(1).strip();
            // Either a clean(...) call, or the local "html" that the line above set from clean(...).
            assertThat(value).as(assignment.group()).matches("clean\\(.*\\)|html");
        }
        assertThat(count).isEqualTo(4);
        assertThat(script).containsSubsequence("var html = clean(value);", "preview.innerHTML = html;")
                .containsSubsequence("var html = clean(editor.innerHTML);", "previewFooter.innerHTML = html;");
    }

    @Test
    @DisplayName("should take the logo address from the page's own markup, never from text read out of the page")
    void shouldCloneLogoFromTemplate_withoutCopyingPageText() throws IOException {
        String modal = Files.readString(MODAL, StandardCharsets.UTF_8);
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        // Nothing inside a template loads, so the logo is still fetched only when the window opens.
        assertThat(modal)
                .contains("<template id=\"footerEditorPreviewLogoTemplate\"><img id=\"footerEditorPreviewLogo\"")
                // Firefox fetches a template's images early unless they are lazy.
                .contains("loading=\"lazy\" src=\"${carlos:forHtmlAttribute(ctx)}/email/clinicEmailLogo\"></template>")
                .doesNotContain("data-logo-url");
        assertThat(script)
                // Listeners first, then into the page, where the image starts loading.
                .containsSubsequence("source.cloneNode(true)", "previewLogo.loading = 'eager';", "addEventListener('load'",
                        "addEventListener('error'", "previewLogoTemplate.replaceWith(previewLogo)")
                .containsSubsequence("addEventListener('show.bs.modal'", "loadLogo();")
                .doesNotContain(".src =")
                .doesNotContain("setAttribute('src'")
                .doesNotContain("data-logo-url");
    }

    @Test
    @DisplayName("should offer the logo upload only inside the _admin write check, as a multipart POST")
    void shouldGuardLogoForm_withAdminWriteCheck() throws IOException {
        String jsp = Files.readString(CONFIGURE, StandardCharsets.UTF_8);

        int guard = jsp.indexOf("<security:oscarSec roleName=\"<%=roleName$%>\" objectName=\"_admin\" rights=\"w\"",
                jsp.indexOf("id=\"clinicLogoCard\""));
        int form = jsp.indexOf("<form action=\"${ctx}/admin/saveClinicEmailLogo\" method=\"post\" enctype=\"multipart/form-data\"");
        assertThat(guard).isGreaterThanOrEqualTo(0);
        assertThat(form).isGreaterThan(guard).isLessThan(jsp.indexOf("</security:oscarSec>", guard));
        assertThat(jsp)
                .contains("accept=\"image/png,image/jpeg\"")
                .contains("<img src=\"${ctx}/email/clinicEmailLogo\"")
                .doesNotContain("${param.logoError}");
    }

    @Test
    @DisplayName("should show a translated message for every reason a logo is refused")
    void shouldShowMessage_forEveryRejection() throws IOException {
        String jsp = Files.readString(CONFIGURE, StandardCharsets.UTF_8);

        for (EmailFooterLogoService.Rejection reason : EmailFooterLogoService.Rejection.values()) {
            assertThat(jsp).as(reason.name()).contains("<c:when test=\"${param.logoError eq '" + reason.name() + "'}\">");
        }
        List<String> keys = List.of("admin.configureEmail.logo.errorEmpty", "admin.configureEmail.logo.errorTooBig",
                "admin.configureEmail.logo.errorNotImage", "admin.configureEmail.logo.errorTooLarge",
                "admin.configureEmail.logo.errorCopyTooBig", "admin.configureEmail.logo.errorUploadFailed");
        Properties english = bundle("en");
        for (String key : keys) {
            assertThat(jsp).contains("<fmt:message key=\"" + key + "\"/>");
            for (String locale : LOCALES) {
                String text = bundle(locale).getProperty(key);
                assertThat(text).as(locale + " " + key).isNotBlank();
                if (!"en".equals(locale)) {
                    assertThat(text).as(locale + " " + key).isNotEqualTo(english.getProperty(key));
                }
            }
        }
    }

    private static Properties bundle(String locale) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(
                Path.of("src/main/resources/oscarResources_" + locale + ".properties"))) {
            properties.load(in);
        }
        return properties;
    }
}
