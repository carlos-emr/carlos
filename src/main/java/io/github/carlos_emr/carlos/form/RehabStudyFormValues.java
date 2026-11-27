/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.form.model.rehab.RehabStudyAssessment;
import io.github.carlos_emr.carlos.form.model.rehab.RehabStudyColumn;
import io.github.carlos_emr.carlos.form.model.rehab.RehabStudyColumn.Checkbox;
import io.github.carlos_emr.carlos.form.model.rehab.RehabStudyColumn.Day;
import io.github.carlos_emr.carlos.form.model.rehab.RehabStudyColumn.Text;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Date;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

/**
 * The Properties contract between the rehabilitation-study questionnaire pages and their entities.
 *
 * <p>Reproduces the retired {@code Frm*Record} classes column by column. Reading: a checkbox
 * stored as 1 is the attribute text {@code checked='checked'} the pages print inside the
 * {@code <input>}, any other value is empty; text is trimmed, a missing value empty; dates are
 * {@code yyyy/MM/dd}; the edit timestamp is {@code yyyy-MM-dd HH:mm:ss}. Writing: a checkbox
 * posted as {@code on} or {@code checked='checked'} (any case) is 1, anything else, including no
 * post, is 0; text is stored as posted, with absent text and blank integer answers stored as NULL;
 * a date with a slash is read as {@code yyyy/MM/dd}, otherwise as {@code yyyy-MM-dd}, and an
 * unreadable one is NULL.</p>
 *
 * @since 2026-11-26
 */
final class RehabStudyFormValues {
    static final String DATE_FORMAT = "yyyy/MM/dd";
    static final String CHECKED = "checked='checked'";
    private static final String EDITED_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final Byte TICKED = 1;
    private static final Byte UNTICKED = 0;

    private RehabStudyFormValues() { }

    /**
     * Builds a new, unsaved version from a form post. The ID, patient and provider are not read
     * from the post; the caller binds the trusted values.
     *
     * @param type the questionnaire
     * @param values the posted form fields
     * @param edited the edit timestamp to store
     * @param <E> the questionnaire entity
     * @return the unsaved version
     */
    static <E extends RehabStudyAssessment> E fromProperties(RehabStudyFormType<E> type, Properties values,
            Date edited) {
        E form = type.factory().get();
        form.setFormCreated(date(values.getProperty("formCreated")));
        form.setFormEdited(edited);
        form.setStudyId(values.getProperty("studyID"));
        for (RehabStudyColumn<E> column : type.columns()) {
            String value = values.getProperty(column.name());
            switch (column) {
                case Checkbox<E> checkbox -> checkbox.writer().accept(form, ticked(value) ? TICKED : UNTICKED);
                case Text<E> text -> text.writer().accept(form, value);
                case Day<E> day -> day.writer().accept(form, date(value));
            }
        }
        return form;
    }

    /**
     * @param type the questionnaire
     * @param form a saved version
     * @param <E> the questionnaire entity
     * @return every column of the version, keyed by its physical column name
     */
    static <E extends RehabStudyAssessment> Properties toProperties(RehabStudyFormType<E> type, E form) {
        Properties values = new Properties();
        values.setProperty("ID", text(form.getId()));
        values.setProperty("demographic_no", text(form.getDemographicNo()));
        values.setProperty("provider_no", text(form.getProviderNo()));
        values.setProperty("formCreated", UtilDateUtilities.DateToString(form.getFormCreated(), DATE_FORMAT));
        values.setProperty("formEdited", UtilDateUtilities.DateToString(form.getFormEdited(), EDITED_FORMAT));
        values.setProperty("studyID", text(form.getStudyId()));
        for (RehabStudyColumn<E> column : type.columns()) {
            String value = switch (column) {
                case Checkbox<E> checkbox -> TICKED.equals(checkbox.reader().apply(form)) ? CHECKED : "";
                case Text<E> text -> text(text.reader().apply(form));
                case Day<E> day -> UtilDateUtilities.DateToString(day.reader().apply(form), DATE_FORMAT);
            };
            values.setProperty(column.name(), value);
        }
        return values;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of the fixed ASCII value a page posts for a ticked box; not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of the fixed ASCII value a page posts for a ticked box; not a security or authorization decision")
    private static boolean ticked(String value) {
        return "on".equalsIgnoreCase(value) || CHECKED.equalsIgnoreCase(value);
    }

    private static Date date(String value) {
        return UtilDateUtilities.StringToDate(value, value == null || value.contains("/") ? DATE_FORMAT : "yyyy-MM-dd");
    }

    private static String text(Object value) {
        // The retired readers used Misc.getString: trimmed, and empty for NULL.
        return value == null ? "" : StringUtils.trimToEmpty(value.toString());
    }
}
