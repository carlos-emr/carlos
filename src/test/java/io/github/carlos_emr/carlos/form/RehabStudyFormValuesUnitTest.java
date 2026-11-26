/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.form.model.rehab.FormSelfAdministered;
import io.github.carlos_emr.carlos.form.model.rehab.RehabStudyColumn;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The Properties contract of the retired rehabilitation-study {@code Frm*Record} classes, on the
 * Self-Administered questionnaire, which has checkbox, text and date answers.
 *
 * @since 2026-11-26
 */
@Tag("unit")
@Tag("form")
@DisplayName("Rehabilitation study form values")
class RehabStudyFormValuesUnitTest {
    @SuppressWarnings("unchecked")
    private static final RehabStudyFormType<FormSelfAdministered> TYPE =
            (RehabStudyFormType<FormSelfAdministered>) RehabStudyForms.forFormClass("SelfAdministered").orElseThrow();
    private static final Date EDITED = new Date(1_709_226_896_000L);

    private static Properties post() {
        Properties post = new Properties();
        post.setProperty("formCreated", "2024/02/29");
        post.setProperty("studyID", "N/A");
        return post;
    }

    private static FormSelfAdministered saved(Properties post) {
        return RehabStudyFormValues.fromProperties(TYPE, post, EDITED);
    }

    private static RehabStudyColumn.Checkbox<FormSelfAdministered> checkbox(String column) {
        return (RehabStudyColumn.Checkbox<FormSelfAdministered>) column(column);
    }

    private static RehabStudyColumn<FormSelfAdministered> column(String column) {
        return TYPE.columns().stream().filter(c -> c.name().equals(column)).findFirst().orElseThrow();
    }

    private static Byte stored(FormSelfAdministered form, String column) {
        return checkbox(column).reader().apply(form);
    }

    @ParameterizedTest(name = "posted \"{0}\" is a tick")
    @ValueSource(strings = {"on", "ON", "checked='checked'", "CHECKED='Checked'"})
    void shouldStoreOne_whenTheBoxWasPostedTicked(String posted) {
        Properties post = post();
        post.setProperty("healthEx", posted);
        assertThat(stored(saved(post), "healthEx")).isEqualTo((byte) 1);
    }

    @ParameterizedTest(name = "posted \"{0}\" is not a tick")
    @NullSource
    @ValueSource(strings = {"", "off", "1", "true", "checked", " on"})
    void shouldStoreZero_whenTheBoxWasNotPostedTicked(String posted) {
        Properties post = post();
        if (posted != null) post.setProperty("healthEx", posted);
        assertThat(stored(saved(post), "healthEx")).isEqualTo((byte) 0);
    }

    @Test
    void shouldShowOnlyAStoredOneAsTicked_whenReopened() {
        Properties post = post();
        post.setProperty("healthEx", "on");
        FormSelfAdministered form = saved(post);
        checkbox("healthG").writer().accept(form, (byte) 2);
        checkbox("healthF").writer().accept(form, null);
        Properties reopened = RehabStudyFormValues.toProperties(TYPE, form);
        assertThat(reopened.getProperty("healthEx")).isEqualTo(RehabStudyFormValues.CHECKED);
        assertThat(reopened.getProperty("healthVG")).as("a stored 0 is not a tick").isEmpty();
        assertThat(reopened.getProperty("healthG")).as("a stored 2 is not a tick").isEmpty();
        assertThat(reopened.getProperty("healthF")).as("a NULL box is not a tick").isEmpty();
    }

    @Test
    void shouldStoreTextAsPostedAndShowItTrimmed_whenReopened() {
        Properties post = post();
        post.setProperty("sex", " F ");
        FormSelfAdministered form = saved(post);
        assertThat(text(form, "sex")).isEqualTo(" F ");
        assertThat(RehabStudyFormValues.toProperties(TYPE, form).getProperty("sex")).isEqualTo("F");
    }

    @Test
    void shouldStoreNullAndShowEmpty_whenTextWasNotPosted() {
        FormSelfAdministered form = saved(post());
        assertThat(text(form, "sex")).isNull();
        assertThat(RehabStudyFormValues.toProperties(TYPE, form).getProperty("sex")).isEmpty();
    }

    @ParameterizedTest(name = "posted date \"{0}\"")
    @ValueSource(strings = {"2024/02/29", "2024-02-29"})
    void shouldReadSlashAndIsoDates_andShowThemWithSlashes(String posted) {
        Properties post = post();
        post.setProperty("dob", posted);
        post.setProperty("formCreated", posted);
        Properties reopened = RehabStudyFormValues.toProperties(TYPE, saved(post));
        assertThat(reopened.getProperty("dob")).isEqualTo("2024/02/29");
        assertThat(reopened.getProperty("formCreated")).isEqualTo("2024/02/29");
    }

    @ParameterizedTest(name = "posted date \"{0}\" is not stored")
    @NullSource
    @ValueSource(strings = {"", "invalid", "29/02"})
    void shouldStoreNullAndShowEmpty_whenTheDateCannotBeRead(String posted) {
        Properties post = post();
        if (posted != null) post.setProperty("dob", posted);
        FormSelfAdministered form = saved(post);
        assertThat(RehabStudyFormValues.toProperties(TYPE, form).getProperty("dob")).isEmpty();
    }

    @Test
    void shouldNotReadTrustedIdentifiers_fromThePost() {
        Properties post = post();
        post.setProperty("ID", "77");
        post.setProperty("demographic_no", "88");
        post.setProperty("provider_no", "99");
        post.setProperty("formEdited", "2001-01-01 00:00:00");
        FormSelfAdministered form = saved(post);
        assertThat(form.getId()).isNull();
        assertThat(form.getDemographicNo()).isNull();
        assertThat(form.getProviderNo()).isNull();
        assertThat(form.getFormEdited()).isEqualTo(EDITED);
        assertThat(form.getStudyId()).isEqualTo("N/A");
    }

    @Test
    void shouldShowEveryColumnAndTheHeader_whenReopened() {
        FormSelfAdministered form = saved(post());
        form.setDemographicNo(695693);
        form.setProviderNo(999998);
        Properties reopened = RehabStudyFormValues.toProperties(TYPE, form);
        assertThat(reopened).hasSize(TYPE.columns().size() + 6);
        assertThat(reopened.getProperty("ID")).isEmpty();
        assertThat(reopened.getProperty("demographic_no")).isEqualTo("695693");
        assertThat(reopened.getProperty("provider_no")).isEqualTo("999998");
        assertThat(reopened.getProperty("studyID")).isEqualTo("N/A");
        assertThat(reopened.getProperty("formEdited"))
                .isEqualTo(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(EDITED));
        form.setProviderNo(null);
        assertThat(RehabStudyFormValues.toProperties(TYPE, form).getProperty("provider_no")).isEmpty();
    }

    private static String text(FormSelfAdministered form, String column) {
        return ((RehabStudyColumn.Text<FormSelfAdministered>) column(column)).reader().apply(form);
    }
}
