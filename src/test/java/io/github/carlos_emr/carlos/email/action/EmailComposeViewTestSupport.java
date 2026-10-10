/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.action;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Follows the redirect {@link EmailCompose2Action#prepareComposeEFormMailer()} issues and renders
 * that prepared view, for tests that only care about the rendered compose screen (#3632).
 */
final class EmailComposeViewTestSupport {

    private EmailComposeViewTestSupport() {
    }

    /** Supply a synthetic authenticated provider when a valid compose fixture has no user yet. */
    static void ensureLoggedInUser(jakarta.servlet.http.HttpServletRequest request) {
        if (io.github.carlos_emr.carlos.utility.LoggedInInfo.getLoggedInInfoFromSession(request) == null) {
            var provider = new io.github.carlos_emr.carlos.commn.model.Provider(
                    "101", "FAKE", "doctor", "U", "", "Fixture");
            var user = new io.github.carlos_emr.carlos.utility.LoggedInInfo();
            user.setLoggedInProvider(provider);
            io.github.carlos_emr.carlos.utility.LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        }
    }

    /**
     * Renders the view that a completed prepare redirected to. The caller's ServletActionContext
     * must still return the request whose session prepared it.
     *
     * @return the render result, "compose" when the view resolved
     */
    static String renderPreparedView(MockHttpServletResponse prepareResponse) {
        ensureLoggedInUser(org.apache.struts2.ServletActionContext.getRequest());
        String location = prepareResponse.getRedirectedUrl();
        String marker = EmailCompose2Action.EMAIL_COMPOSE_VIEW_PARAM + "=";
        assertThat(location).as("prepare redirects to its view").contains(marker);
        String viewId = URLDecoder.decode(location.substring(location.indexOf(marker) + marker.length()),
                StandardCharsets.UTF_8);
        return new EmailCompose2Action().renderPreparedCompose(viewId);
    }
}
