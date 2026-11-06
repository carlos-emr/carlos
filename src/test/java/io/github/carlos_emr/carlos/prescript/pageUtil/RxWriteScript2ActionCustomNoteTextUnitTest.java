/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The stored text of a custom note (the Rx page's Note button) when its Name box is left empty (#4873).
 */
@DisplayName("RxWriteScript2Action custom note text")
@Tag("unit")
@Tag("prescript")
@Tag("regression")
class RxWriteScript2ActionCustomNoteTextUnitTest extends CarlosUnitTestBase {

    private static final String NOTE = "bring the old pill bottles";

    @BeforeEach
    void setUp() {
        // The action's static fields look these up when the class is first loaded.
        registerMock(UserPropertyDAO.class, mock(UserPropertyDAO.class));
        registerMock(PartialDateDao.class, mock(PartialDateDao.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("should store the instructions alone when the note has no name")
    void shouldStoreInstructionsAlone_whenNoteHasNoName(String customName) {
        String text = RxWriteScript2Action.customNoteText(customName, NOTE, "\n");

        assertThat(text).isEqualTo(NOTE).doesNotContain("null");
    }

    @Test
    @DisplayName("should keep the name on its own line when the note has one")
    void shouldKeepNameLine_whenNoteHasName() {
        assertThat(RxWriteScript2Action.customNoteText("Reminder", NOTE, "\n")).isEqualTo("Reminder\n" + NOTE);
    }

    @Test
    @DisplayName("should not write null when the note has no instructions either")
    void shouldNotWriteNull_whenNoteHasNoInstructions() {
        assertThat(RxWriteScript2Action.customNoteText(null, null, "\n")).isEmpty();
        assertThat(RxWriteScript2Action.customNoteText("Reminder", null, "\n")).isEqualTo("Reminder\n");
    }

    @Test
    @DisplayName("should print the note without a null line on the prescription outline")
    void shouldPrintNoteWithoutNullLine_onPrescriptionOutline() {
        // The print preview and the stored prescription text are built from this outline.
        String outline = RxPrescriptionData.getFullOutLine(RxWriteScript2Action.customNoteText(null, NOTE, "\n"));

        assertThat(outline).isEqualTo(NOTE);
    }
}
