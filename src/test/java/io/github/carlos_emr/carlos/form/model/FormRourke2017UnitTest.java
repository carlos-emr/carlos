/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.model;

import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("fast")
class FormRourke2017UnitTest {
    @Test
    void shouldRenderBlankProperties_whenImportedRowHasNullFields() {
        FormRourke2017 form = new FormRourke2017();
        form.setDemographicNo(1);
        Properties properties = form.toProperties();
        assertThat(properties).containsEntry("formEdited", "").containsEntry("c_birthWeight", "")
                .containsEntry("c_APGAR1min", "").containsEntry("p1_wt1w", "")
                .containsEntry("p2_wt2m", "").containsEntry("p3_wt9m", "")
                .containsEntry("p4_wt18m", "");
        assertThat(properties.values()).doesNotContain("null");
    }

    @Test
    void shouldPreserveRecordedValues_whenOtherPagesHaveNullFields() {
        FormRourke2017 form = new FormRourke2017();
        form.setProviderNo("999998");
        form.setDemographicNo(1);
        form.setC_APGAR1min(8);
        form.setC_birthWeight("3.250");
        Properties properties = form.toProperties();
        assertThat(properties).containsEntry("provider_no", "999998").containsEntry("demographic_no", "1")
                .containsEntry("c_APGAR1min", "8").containsEntry("c_birthWeight", "3.250")
                .containsEntry("c_APGAR5min", "");
    }
}
