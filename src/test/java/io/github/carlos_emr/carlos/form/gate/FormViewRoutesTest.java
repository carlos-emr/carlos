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
package io.github.carlos_emr.carlos.form.gate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FormViewRoutes Tests")
@Tag("unit")
@Tag("form")
class FormViewRoutesTest {

    @Test
    void shouldResolveActionRoutes_forEncounterFormDbValues() {
        // Regression: these strings are stored in the encounterForm.form_value column
        // (seeded in database/mysql/oscarinit.sql and database/mysql/oscardata.sql).
        // EctDisplayForm2Action concatenates them with the demographic number to
        // build the "add new form" popup URL, so they must resolve to a valid
        // extensionless Struts action route (not the moved /form/*.jsp path which
        // now returns 404).
        assertThat(FormViewRoutes.resolveActionPath("../form/formrourke2017complete.jsp?demographic_no="))
                .isEqualTo("/form/formrourke2017complete?demographic_no=");
        assertThat(FormViewRoutes.resolveActionPath("../form/formrourke2020complete.jsp?demographic_no="))
                .isEqualTo("/form/formrourke2020complete?demographic_no=");
        assertThat(FormViewRoutes.resolveActionPath("../form/formrourke2009complete.jsp?demographic_no="))
                .isEqualTo("/form/formrourke2009complete?demographic_no=");
        assertThat(FormViewRoutes.resolveActionPath("../form/formannual.jsp?demographic_no="))
                .isEqualTo("/form/formannual?demographic_no=");
    }

    @Test
    void shouldResolveLegacyFormJspToActionRoute() {
        assertThat(FormViewRoutes.resolveActionPath("/form/formannual.jsp?demographic_no="))
                .isEqualTo("/form/formannual?demographic_no=");
    }

    @Test
    void shouldResolveSpecialLegacyRoutes() {
        assertThat(FormViewRoutes.resolveActionPath("../form/forwardshortcutname.jsp?formname=Rourke"))
                .isEqualTo("/form/forwardshortcutname?formname=Rourke");
        assertThat(FormViewRoutes.resolveActionPath("/form/pharmaForms/formBPMH.jsp"))
                .isEqualTo("/formBPMH");
    }

    @Test
    void shouldReturnNull_forRemovedEcaresRoute() {
        assertThat(FormViewRoutes.resolveActionPath("/form/eCARES/formeCARES.jsp")).isNull();
    }

    @Test
    void shouldRejectUnsafeLegacyFormPath() {
        assertThat(FormViewRoutes.resolveActionPath("/form/../../WEB-INF/web.xml")).isNull();
        assertThat(FormViewRoutes.resolveActionPath("/WEB-INF/jsp/form/formannual.jsp")).isNull();
    }

    @Test
    void shouldRejectLegacyPathsOutsideWildcardAllowlist() {
        assertThat(FormViewRoutes.resolveActionPath("/form/eCARES/sections/info.jsp")).isNull();
        assertThat(FormViewRoutes.resolveActionPath("/form/formSaveAndExit.jsp")).isNull();
        assertThat(FormViewRoutes.isAllowedWildcardFormView("formannual")).isTrue();
        assertThat(FormViewRoutes.isAllowedWildcardFormView("eCARES/sections/info")).isFalse();
    }

    @Test
    void shouldResolveInternalViewFromFormLink() {
        assertThat(FormViewRoutes.resolveInternalViewFromFormLink("formannual.jsp"))
                .isEqualTo("/WEB-INF/jsp/form/formannual.jsp");
        assertThat(FormViewRoutes.resolveInternalViewFromFormLink("../formannual.jsp")).isNull();
    }
}
