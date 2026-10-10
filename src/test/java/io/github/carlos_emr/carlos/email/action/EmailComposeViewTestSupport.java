/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.action;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import io.github.carlos_emr.carlos.email.core.EmailAttachmentSettings;
import io.github.carlos_emr.carlos.email.core.EmailComposeStaging;
import jakarta.servlet.http.HttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Follows the redirect {@link EmailCompose2Action#prepareComposeEFormMailer()} issues and renders
 * that prepared view, for tests that only care about the rendered compose screen (#3632), and stages
 * a test's compose fields as the keyed draft the eForm save stages (#4101).
 */
final class EmailComposeViewTestSupport {

    private EmailComposeViewTestSupport() {
    }

    /**
     * Renders the view that a completed prepare redirected to. The caller's ServletActionContext
     * must still return the request whose session prepared it.
     *
     * @return the render result, "compose" when the view resolved
     */
    static String renderPreparedView(MockHttpServletResponse prepareResponse) {
        String location = prepareResponse.getRedirectedUrl();
        String marker = EmailCompose2Action.EMAIL_COMPOSE_VIEW_PARAM + "=";
        assertThat(location).as("prepare redirects to its view").contains(marker);
        String viewId = URLDecoder.decode(location.substring(location.indexOf(marker) + marker.length()),
                StandardCharsets.UTF_8);
        return new EmailCompose2Action().renderPreparedCompose(viewId);
    }

    /**
     * Moves the compose fields a test set as session attributes into a keyed draft, the way the eForm
     * save stages it (#4101), and passes its key on the request. The attributes stay, as stale leftovers
     * of an earlier version would; prepare must clear them and never read them. Unset encryption flags
     * stay encrypted, as before.
     */
    static String stageSessionFieldsAsDraft(MockHttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Object patient = session.getAttribute("demographicId");
        EmailAttachmentSettings settings = new EmailAttachmentSettings(
                (String) session.getAttribute("fdid"), patient == null ? null : String.valueOf(patient),
                (String[]) session.getAttribute("attachedEForms"), (String[]) session.getAttribute("attachedDocuments"),
                (String[]) session.getAttribute("attachedLabs"), (String[]) session.getAttribute("attachedHRMDocuments"),
                (String[]) session.getAttribute("attachedForms"),
                Boolean.TRUE.equals(session.getAttribute("attachEFormItSelf")),
                Boolean.TRUE.equals(session.getAttribute("openEFormAfterEmail")),
                !Boolean.FALSE.equals(session.getAttribute("isEmailEncrypted")),
                !Boolean.FALSE.equals(session.getAttribute("isEmailAttachmentEncrypted")),
                Boolean.TRUE.equals(session.getAttribute("isEmailAutoSend")),
                Boolean.TRUE.equals(session.getAttribute("deleteEFormAfterEmail")),
                (String) session.getAttribute("senderEmail"), (String) session.getAttribute("subjectEmail"),
                (String) session.getAttribute("bodyEmail"), (String) session.getAttribute("encryptedMessageEmail"),
                (String) session.getAttribute("emailPatientChartOption"));
        String key = EmailComposeStaging.stage(session, request.getParameter("fid"), settings);
        request.setParameter(EmailComposeStaging.DRAFT_PARAMETER, key);
        return key;
    }
}
