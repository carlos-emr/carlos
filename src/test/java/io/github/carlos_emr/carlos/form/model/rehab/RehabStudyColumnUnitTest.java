/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form.model.rehab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link RehabStudyColumn#narrow} re-types a mapped superclass's columns for an entity that
 * extends it: every kind keeps its name and order and reads and writes the entity's own fields.
 *
 * @since 2026-11-26
 */
@Tag("unit")
@Tag("form")
@DisplayName("Rehabilitation study columns of a shared answer superclass")
class RehabStudyColumnUnitTest {

    @Test
    void shouldKeepEveryKindNameAndOrder_whenNarrowedToAnExtendingEntity() {
        // Columns declared on the abstract SF-36 superclass, one of each kind.
        List<RehabStudyColumn<SF36Answers>> declared = List.of(
                SF36Answers.answerColumns().getFirst(),
                new RehabStudyColumn.Text<>("studyID", RehabStudyAssessment::getStudyId, RehabStudyAssessment::setStudyId),
                new RehabStudyColumn.Day<>("formCreated", RehabStudyAssessment::getFormCreated,
                        RehabStudyAssessment::setFormCreated));

        List<RehabStudyColumn<FormSF36Caregiver>> narrowed = RehabStudyColumn.narrow(declared);

        assertThat(narrowed).extracting(RehabStudyColumn::name).containsExactly("Q1Ex", "studyID", "formCreated");
        assertThat(narrowed.get(0)).isInstanceOf(RehabStudyColumn.Checkbox.class);
        assertThat(narrowed.get(1)).isInstanceOf(RehabStudyColumn.Text.class);
        assertThat(narrowed.get(2)).isInstanceOf(RehabStudyColumn.Day.class);

        FormSF36Caregiver form = new FormSF36Caregiver();
        Date day = new Date(0);
        for (RehabStudyColumn<FormSF36Caregiver> column : narrowed) {
            switch (column) {
                case RehabStudyColumn.Checkbox<FormSF36Caregiver> checkbox -> checkbox.writer().accept(form, (byte) 1);
                case RehabStudyColumn.Text<FormSF36Caregiver> text -> text.writer().accept(form, "S-5693");
                case RehabStudyColumn.Day<FormSF36Caregiver> date -> date.writer().accept(form, day);
            }
        }
        assertThat(((RehabStudyColumn.Checkbox<FormSF36Caregiver>) narrowed.get(0)).reader().apply(form)).isEqualTo((byte) 1);
        assertThat(((RehabStudyColumn.Text<FormSF36Caregiver>) narrowed.get(1)).reader().apply(form)).isEqualTo("S-5693");
        assertThat(((RehabStudyColumn.Day<FormSF36Caregiver>) narrowed.get(2)).reader().apply(form)).isSameAs(day);
        assertThat(form.getStudyId()).isEqualTo("S-5693");
        assertThat(form.getFormCreated()).isSameAs(day);
    }

    @Test
    void shouldGiveEachSf36EntityItsOwnCopy_ofTheSharedAnswerColumns() {
        assertThat(FormSF36.columns()).extracting(RehabStudyColumn::name)
                .containsExactlyElementsOf(SF36Answers.answerColumns().stream().map(RehabStudyColumn::name).toList());
        assertThat(FormSF36Caregiver.columns()).extracting(RehabStudyColumn::name)
                .containsExactlyElementsOf(SF36Answers.answerColumns().stream().map(RehabStudyColumn::name).toList());
        assertThat(FormSF36.columns()).isUnmodifiable().hasSize(204);
    }
}
