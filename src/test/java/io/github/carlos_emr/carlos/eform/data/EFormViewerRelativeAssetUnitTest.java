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
package io.github.carlos_emr.carlos.eform.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the viewer-relative ({@code ../}) asset re-anchoring performed by the render-path DOM pass.
 *
 * <p>Stored eForm HTML is authored against the interactive viewer URL
 * ({@code /<context>/eform/efmshowform_data}, two segments below the origin). The PDF render page is
 * served one segment below the origin ({@code /<context>/EFormViewForPdfGenerationServlet}), so the
 * same {@code ../css/x.css} that resolves correctly in the viewer resolves to the origin ROOT during
 * a render and 404s — which the render's content gate then reports as missing content for an asset
 * that is present and correctly referenced.</p>
 */
@DisplayName("EForm viewer-relative asset re-anchoring")
@Tag("unit")
@Tag("fast")
class EFormViewerRelativeAssetUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void registerConvertToEdocDependencies() {
        // ConvertToEdoc resolves NioFileManager in a static-final initializer; register the mock
        // BEFORE anything touches that class or its static init fails and the DOM pass no-ops.
        registerMock(NioFileManager.class, Mockito.mock(NioFileManager.class));
    }

    private static EForm renderNormalizedEform(String bodyHtml) {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body>" + bodyHtml + "</body></html>");
        eform.setContextPath("/carlos");
        eform.enableRenderNormalization(); // DOM pass is render-path opt-in
        return eform;
    }

    @Test
    @DisplayName("should anchor a viewer-relative stylesheet to the webapp context")
    void shouldAnchorRelativeStylesheet_toContextPath() {
        EForm eform = renderNormalizedEform(
                "<link rel=\"stylesheet\" href=\"../css/fontawesome-all.min.css\">");

        assertThat(eform.getFormHtml())
                .contains("/carlos/css/fontawesome-all.min.css")
                .doesNotContain("\"../css/fontawesome-all.min.css\"");
    }

    @Test
    @DisplayName("should anchor viewer-relative references across every subresource attribute")
    void shouldAnchorRelativeReferences_forEverySubresourceAttribute() {
        EForm eform = renderNormalizedEform(
                "<script src=\"../library/eforms/stampControl.js\"></script>"
                        + "<img src=\"../images/logo.png\">"
                        + "<iframe src=\"../share/calendar/cal.jsp\"></iframe>");

        String html = eform.getFormHtml();
        assertThat(html)
                .contains("/carlos/library/eforms/stampControl.js")
                .contains("/carlos/images/logo.png")
                .contains("/carlos/share/calendar/cal.jsp");
    }

    @Test
    @DisplayName("should resolve multiple parent hops exactly as the viewer URL does")
    void shouldResolveMultipleParentHops_likeBrowser() {
        assertThat(EForm.anchorViewerRelativePath("../../../css/x.css", "/carlos"))
                .isEqualTo("/css/x.css");
        assertThat(EForm.anchorViewerRelativePath("../css/x.css?v=2#font", "/carlos"))
                .isEqualTo("/carlos/css/x.css?v=2#font");
    }

    @Test
    @DisplayName("should leave absolute, context-rooted and non-relative references untouched")
    void shouldLeaveNonRelativeReferences_unchanged() {
        assertThat(EForm.anchorViewerRelativePath("https://cdn.example/x.css", "/carlos")).isNull();
        assertThat(EForm.anchorViewerRelativePath("/carlos/css/x.css", "/carlos")).isNull();
        assertThat(EForm.anchorViewerRelativePath("css/x.css", "/carlos")).isNull();
        assertThat(EForm.anchorViewerRelativePath("data:image/png;base64,AAAA", "/carlos")).isNull();
        assertThat(EForm.anchorViewerRelativePath("..\\css\\x.css", "/carlos")).isNull();
        assertThat(EForm.anchorViewerRelativePath("../", "/carlos")).isEqualTo("/carlos/");
        assertThat(EForm.anchorViewerRelativePath(null, "/carlos")).isNull();
    }

    @Test
    @DisplayName("should leave navigation links untouched because they fetch nothing during a render")
    void shouldLeaveNavigationLinks_unchanged() {
        EForm eform = renderNormalizedEform("<a href=\"../eform/efmformmanager\">Manager</a>");

        assertThat(eform.getFormHtml()).contains("href=\"../eform/efmformmanager\"");
    }
}
