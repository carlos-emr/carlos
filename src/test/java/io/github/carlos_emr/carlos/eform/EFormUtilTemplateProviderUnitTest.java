/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * An eForm action template keeps the provider it names under both the OSCAR spellings
 * ({@code <provider>}, {@code $te{eform.provider}}) and the ones a 2025 package rename gave them
 * ({@code <providers>}, {@code $te{eform.providers}}).
 *
 * <p>Each test runs {@link EFormUtil#writeEformTemplate} on a template holding one prevention,
 * the only template section that needs just one bean, and reads back the prevention it records.</p>
 */
@Tag("unit")
@DisplayName("eForm template provider tags")
class EFormUtilTemplateProviderUnitTest extends CarlosUnitTestBase {

    private static final String SUBMITTER = "111";
    private static final String NAMED = "222";

    private PreventionManager preventionManager;

    @BeforeEach
    void registerPreventionManager() {
        preventionManager = createAndRegisterMock(PreventionManager.class);
        when(preventionManager.getPreventionTypeList()).thenReturn(new ArrayList<>(List.of("MMR")));
    }

    /** Submits an eForm whose template is {@code <prevention>body</prevention>} and returns what it records. */
    private Recorded submit(String preventionBody) {
        EForm eForm = new EForm();
        eForm.setFormName("FAKE template form");
        eForm.setDemographicNo("7");
        eForm.setProviderNo(SUBMITTER);
        eForm.setFormDate("2026-10-16");
        eForm.setFormHtml("<html><body><form></form><!-- <template><prevention><type>MMR</type>" + preventionBody
                + "</prevention></template> --></body></html>");

        EFormUtil.writeEformTemplate(null, new ArrayList<>(), new ArrayList<>(), eForm, "5", null, "/carlos");

        ArgumentCaptor<Prevention> prevention = ArgumentCaptor.forClass(Prevention.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HashMap<String, String>> exts = ArgumentCaptor.forClass(HashMap.class);
        verify(preventionManager).addPreventionWithExts(prevention.capture(), exts.capture());
        return new Recorded(prevention.getValue().getProviderNo(), exts.getValue().get("comments"));
    }

    private record Recorded(String providerNo, String comments) { }

    @Test
    @DisplayName("should record the prevention under the provider named by the OSCAR <provider> tag")
    void shouldRecordNamedProvider_whenTemplateUsesProviderTag() {
        assertThat(submit("<provider>" + NAMED + "</provider>").providerNo()).isEqualTo(NAMED);
    }

    @Test
    @DisplayName("should record the prevention under the provider named by the <providers> tag")
    void shouldRecordNamedProvider_whenTemplateUsesProvidersTag() {
        assertThat(submit("<providers>" + NAMED + "</providers>").providerNo()).isEqualTo(NAMED);
    }

    @Test
    @DisplayName("should read the <provider> tag first when a template has both")
    void shouldPreferProviderTag_whenTemplateHasBoth() {
        assertThat(submit("<providers>333</providers><provider>" + NAMED + "</provider>").providerNo())
                .isEqualTo(NAMED);
    }

    @Test
    @DisplayName("should record the prevention under the submitting provider when no provider is named")
    void shouldRecordSubmitter_whenTemplateNamesNoProvider() {
        assertThat(submit("<date>2026-01-05</date>").providerNo()).isEqualTo(SUBMITTER);
    }

    @Test
    @DisplayName("should fill $te{eform.provider} with the submitting provider")
    void shouldFillSubmitter_forOscarProviderField() {
        assertThat(submit("<comments>by $te{eform.provider}</comments>").comments()).isEqualTo("by " + SUBMITTER);
    }

    @Test
    @DisplayName("should fill $te{eform.providers} with the submitting provider")
    void shouldFillSubmitter_forProvidersField() {
        assertThat(submit("<comments>by $te{eform.providers}</comments>").comments()).isEqualTo("by " + SUBMITTER);
    }
}
