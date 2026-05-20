/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@Tag("unit")
@DisplayName("Report table field captions")
class RptTableFieldNameCaptionUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should update existing captions")
    void shouldUpdate_existingCaptions() {
        TestableRptTableFieldNameCaption caption = new TestableRptTableFieldNameCaption(true);

        assertThat(caption.insertOrUpdateRecord()).isTrue();

        assertThat(caption.insertCalled).isFalse();
        assertThat(caption.updateCalled).isTrue();
    }

    @Test
    @DisplayName("should insert missing captions")
    void shouldInsert_missingCaptions() {
        TestableRptTableFieldNameCaption caption = new TestableRptTableFieldNameCaption(false);

        assertThat(caption.insertOrUpdateRecord()).isTrue();

        assertThat(caption.insertCalled).isTrue();
        assertThat(caption.updateCalled).isFalse();
    }

    private static final class TestableRptTableFieldNameCaption extends RptTableFieldNameCaption {
        private final boolean exists;
        private boolean insertCalled;
        private boolean updateCalled;

        private TestableRptTableFieldNameCaption(boolean exists) {
            this.exists = exists;
        }

        @Override
        protected boolean recordExists() throws SQLException {
            return exists;
        }

        @Override
        public boolean insertRecord() {
            insertCalled = true;
            return true;
        }

        @Override
        public boolean updateRecord() {
            updateCalled = true;
            return true;
        }
    }
}
