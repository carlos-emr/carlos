/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.gate;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EFormViewRoutes Tests")
@Tag("unit")
@Tag("eform")
class EFormViewRoutesTest {

    @Test
    void shouldResolveRepresentativeRoutes() {
        assertThat(EFormViewRoutes.resolve("eform/efmformmanager"))
                .isEqualTo(new EFormViewRoutes.Route(
                        "/WEB-INF/jsp/eform/efmformmanager.jsp",
                        EFormViewRoutes.Privilege.EFORM_WRITE));

        assertThat(EFormViewRoutes.resolve("eform/efmpatientformlist"))
                .isEqualTo(new EFormViewRoutes.Route(
                        "/WEB-INF/jsp/eform/efmpatientformlist.jsp",
                        EFormViewRoutes.Privilege.EFORM_READ));
    }

    @Test
    void shouldResolveNonJspViews() {
        assertThat(EFormViewRoutes.resolve("eform/Eform_dbtags"))
                .isEqualTo(new EFormViewRoutes.Route(
                        "/WEB-INF/jsp/eform/Eform_dbtags.html",
                        EFormViewRoutes.Privilege.EFORM_WRITE));

        assertThat(EFormViewRoutes.resolve("eform/eformFloatingToolbar/eform_floating_toolbar"))
                .isEqualTo(new EFormViewRoutes.Route(
                        "/WEB-INF/jsp/eform/eformFloatingToolbar/eform_floating_toolbar.jspf",
                        EFormViewRoutes.Privilege.EFORM_READ));
    }

    @Test
    void shouldResolveAdminOnlyView() {
        assertThat(EFormViewRoutes.resolve("eform/visualEformEditor"))
                .isEqualTo(new EFormViewRoutes.Route(
                        "/WEB-INF/jsp/eform/visualEformEditor.jsp",
                        EFormViewRoutes.Privilege.ADMIN_EFORM_WRITE));

        assertThat(EFormViewRoutes.resolve("eform/eformGenerator"))
                .isEqualTo(new EFormViewRoutes.Route(
                        "/WEB-INF/jsp/eform/eformGenerator.jsp",
                        EFormViewRoutes.Privilege.ADMIN_EFORM_WRITE));
    }

    @Test
    void shouldUseNamespacedSavedFormPreviewLinksInFieldNoteSelector() throws Exception {
        String selectorJsp = Files.readString(Path.of(
                "src/main/webapp/WEB-INF/jsp/eform/fieldNoteReport/fieldnoteselect.jsp"));

        assertThat(selectorJsp).contains("/eform/efmshowform_data?fid=");
        assertThat(selectorJsp).doesNotContain("request.getContextPath() %>/efmshowform_data?fid=");
    }

    @Test
    void shouldReturnNullForUnknownRoute() {
        assertThat(EFormViewRoutes.resolve("eform/doesNotExist")).isNull();
    }
}
