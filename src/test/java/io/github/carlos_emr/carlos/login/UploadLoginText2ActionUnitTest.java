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
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.commn.service.AcceptableUseAgreementManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@DisplayName("UploadLoginText2Action")
@Tag("unit")
@Tag("web")
@Tag("login")
class UploadLoginText2ActionUnitTest extends CarlosWebTestBase {

    @TempDir
    private Path documentDir;

    @TempDir
    private Path uploadDir;

    private String originalDocumentDir;

    @BeforeEach
    void setUpDocumentDir() {
        getMockRequest().setMethod("POST");
        originalDocumentDir = CarlosProperties.getInstance().getProperty("BASE_DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("BASE_DOCUMENT_DIR", documentDir.toString());
        AcceptableUseAgreementManager.invalidateCache();
    }

    @AfterEach
    void restoreDocumentDir() {
        AcceptableUseAgreementManager.invalidateCache();
        if (originalDocumentDir == null) {
            CarlosProperties.getInstance().remove("BASE_DOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("BASE_DOCUMENT_DIR", originalDocumentDir);
        }
    }

    @Test
    @DisplayName("should return success when upload file is missing")
    void shouldReturnSuccess_whenUploadFileIsMissing() throws Exception {
        addValidDurationParameters();
        UploadLoginText2Action action = new UploadLoginText2Action();
        action.setImportFile(null);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(getMockRequest().getAttribute("error")).isEqualTo(false);
        assertThat(documentDir.resolve("login/AcceptableUseAgreement.txt")).doesNotExist();
    }

    @Test
    @DisplayName("should write login text when upload file is present")
    void shouldWriteLoginText_whenUploadFileIsPresent() throws Exception {
        addValidDurationParameters();
        Path uploadFile = Files.createTempFile(uploadDir, "login-text-", ".txt");
        Files.writeString(uploadFile, "updated login text", StandardCharsets.UTF_8);
        UploadLoginText2Action action = new UploadLoginText2Action();
        action.setImportFile(uploadFile.toFile());

        assertThat(AcceptableUseAgreementManager.getAUAText()).isNull(); // cache the missing-file result
        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(getMockRequest().getAttribute("error")).isEqualTo(false);
        assertThat(documentDir.resolve("login/AcceptableUseAgreement.txt"))
                .hasContent("updated login text");
        assertThat(AcceptableUseAgreementManager.getAUAText()).isEqualTo("updated login text");
        assertThat(documentDir.resolve("OSCARloginText.txt")).doesNotExist();
    }

    @Test
    @DisplayName("should set error when configured document directory is invalid")
    void shouldSetError_whenDocumentDirectoryIsInvalid() throws Exception {
        addValidDurationParameters();
        Path invalidDocumentDir = Files.createTempFile(documentDir, "not-a-dir-", ".txt");
        Files.writeString(invalidDocumentDir, "not a directory", StandardCharsets.UTF_8);
        CarlosProperties.getInstance().setProperty("BASE_DOCUMENT_DIR", invalidDocumentDir.toString());
        Path uploadFile = Files.createTempFile(uploadDir, "login-text-", ".txt");
        Files.writeString(uploadFile, "updated login text", StandardCharsets.UTF_8);
        UploadLoginText2Action action = new UploadLoginText2Action();
        action.setImportFile(uploadFile.toFile());

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(getMockRequest().getAttribute("error")).isEqualTo(true);
        assertThat(invalidDocumentDir).hasContent("not a directory");
    }

    @Test
    @DisplayName("should preserve existing login text when upload read fails")
    void shouldPreserveLoginText_whenUploadReadFails() throws Exception {
        addValidDurationParameters();
        Path existingLoginText = documentDir.resolve("login/AcceptableUseAgreement.txt");
        Files.createDirectories(existingLoginText.getParent());
        Files.writeString(existingLoginText, "existing login text", StandardCharsets.UTF_8);
        Path uploadFile = Files.createTempFile(uploadDir, "login-text-", ".txt");
        Files.writeString(uploadFile, "updated login text", StandardCharsets.UTF_8);
        UploadLoginText2Action action = new UploadLoginText2Action();
        action.setImportFile(uploadFile.toFile());
        Files.delete(uploadFile);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(getMockRequest().getAttribute("error")).isEqualTo(true);
        assertThat(existingLoginText).hasContent("existing login text");
        try (Stream<Path> documentFiles = Files.list(existingLoginText.getParent())) {
            assertThat(documentFiles)
                    .extracting(path -> path.getFileName().toString())
                    .containsExactly("AcceptableUseAgreement.txt");
        }
    }

    @Test
    void shouldRejectMalformedUtf8_beforeReplacingActiveAgreement() throws Exception {
        addValidDurationParameters();
        Path agreement = documentDir.resolve("login/AcceptableUseAgreement.txt");
        Files.createDirectories(agreement.getParent());
        Files.writeString(agreement, "Existing agreement", StandardCharsets.UTF_8);
        String originalPolicy = CarlosProperties.getInstance().getProperty("show_aua");
        CarlosProperties.getInstance().setProperty("show_aua", "true");
        try {
            assertThat(AcceptableUseAgreementManager.hasAUA()).isTrue();
            Path upload = Files.write(uploadDir.resolve("malformed.txt"), new byte[] {(byte) 0xC3, 0x28});
            UploadLoginText2Action action = new UploadLoginText2Action();
            action.setImportFile(upload.toFile());

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
            assertThat(getMockRequest().getAttribute("error")).isEqualTo(true);
            assertThat(agreement).hasContent("Existing agreement");
            assertThat(AcceptableUseAgreementManager.hasAUA()).isTrue();
            assertThat(AcceptableUseAgreementManager.getAUAText()).isEqualTo("Existing agreement");
            try (Stream<Path> files = Files.list(agreement.getParent())) {
                assertThat(files.map(path -> path.getFileName().toString()))
                        .containsExactly("AcceptableUseAgreement.txt");
            }
        } finally {
            if (originalPolicy == null) CarlosProperties.getInstance().remove("show_aua");
            else CarlosProperties.getInstance().setProperty("show_aua", originalPolicy);
        }
    }

    @Test
    void shouldRestorePreviousAgreement_whenValidityLookupFails() throws Exception {
        addValidDurationParameters();
        Path agreement = documentDir.resolve("login/AcceptableUseAgreement.txt");
        Files.createDirectories(agreement.getParent());
        Files.writeString(agreement, "Existing agreement", StandardCharsets.UTF_8);
        Path upload = Files.writeString(uploadDir.resolve("replacement.txt"), "Replacement agreement", StandardCharsets.UTF_8);
        UploadLoginText2Action action = new UploadLoginText2Action();
        action.setImportFile(upload.toFile());

        try (MockedStatic<AcceptableUseAgreementManager> manager =
                mockStatic(AcceptableUseAgreementManager.class, CALLS_REAL_METHODS)) {
            manager.when(AcceptableUseAgreementManager::findLatestProperty)
                    .thenThrow(new IllegalStateException("Synthetic property lookup outage"));
            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
            assertThat(getMockRequest().getAttribute("error")).isEqualTo(true);
            assertThat(agreement).hasContent("Existing agreement");
            assertThat(AcceptableUseAgreementManager.getAUAText()).isEqualTo("Existing agreement");
        }
    }

    @Test
    void shouldRemoveNewAgreement_whenValidityPersistenceFailsWithoutPreviousFile() throws Exception {
        addValidDurationParameters();
        Path agreement = documentDir.resolve("login/AcceptableUseAgreement.txt");
        Path upload = Files.writeString(uploadDir.resolve("new.txt"), "New agreement", StandardCharsets.UTF_8);
        PropertyDao failingDao = mock(PropertyDao.class);
        doThrow(new IllegalStateException("Synthetic property write outage"))
                .when(failingDao).persist(any(Property.class));
        replaceSpringUtilsBean(PropertyDao.class, failingDao);
        UploadLoginText2Action action = new UploadLoginText2Action();
        action.setImportFile(upload.toFile());

        try (MockedStatic<AcceptableUseAgreementManager> manager =
                mockStatic(AcceptableUseAgreementManager.class, CALLS_REAL_METHODS)) {
            manager.when(AcceptableUseAgreementManager::findLatestProperty).thenReturn(null);
            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
            assertThat(getMockRequest().getAttribute("error")).isEqualTo(true);
            assertThat(agreement).doesNotExist();
            assertThat(AcceptableUseAgreementManager.getAUAText()).isNull();
        }
    }

    @Test
    void shouldRefreshExistingText_withoutEnablingAgreementPolicy() throws Exception {
        addValidDurationParameters();
        Path agreement = documentDir.resolve("login/AcceptableUseAgreement.txt");
        Files.createDirectories(agreement.getParent());
        Files.writeString(agreement, "Previous agreement", StandardCharsets.UTF_8);
        assertThat(AcceptableUseAgreementManager.getAUAText()).isEqualTo("Previous agreement");
        Path upload = Files.writeString(uploadDir.resolve("replacement.txt"), "Updated café agreement", StandardCharsets.UTF_8);
        String originalPolicy = CarlosProperties.getInstance().getProperty("show_aua");
        CarlosProperties.getInstance().setProperty("show_aua", "false");
        try {
            UploadLoginText2Action action = new UploadLoginText2Action();
            action.setImportFile(upload.toFile());
            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
            assertThat(getMockRequest().getAttribute("error")).isEqualTo(false);
            assertThat(AcceptableUseAgreementManager.getAUAText()).isEqualTo("Updated café agreement");
            assertThat(AcceptableUseAgreementManager.hasAUA()).isFalse();
            assertThat(CarlosProperties.getInstance().getProperty("show_aua")).isEqualTo("false");
        } finally {
            if (originalPolicy == null) CarlosProperties.getInstance().remove("show_aua");
            else CarlosProperties.getInstance().setProperty("show_aua", originalPolicy);
        }
    }

    @Test
    void shouldNoticeRestoredAndRemovedText_withoutServingStaleAgreement() throws Exception {
        Path agreement = documentDir.resolve("login/AcceptableUseAgreement.txt");
        Files.createDirectories(agreement.getParent());
        Files.writeString(agreement, "Original agreement", StandardCharsets.UTF_8);
        assertThat(AcceptableUseAgreementManager.getAUAText()).isEqualTo("Original agreement");
        Files.writeString(agreement, "A replacement agreement with different content", StandardCharsets.UTF_8);
        assertThat(AcceptableUseAgreementManager.getAUAText()).isEqualTo("A replacement agreement with different content");
        Files.delete(agreement);
        assertThat(AcceptableUseAgreementManager.getAUAText()).isNull();
    }

    @Test
    void shouldRenderReadOnlyGet_withoutAgreementMutation() throws Exception {
        getMockRequest().setMethod("GET");
        UploadLoginText2Action action = new UploadLoginText2Action();
        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.hasActionErrors()).isFalse();
        assertThat(documentDir.resolve("login/AcceptableUseAgreement.txt")).doesNotExist();
    }

    @Test
    void shouldRejectGetWithMutationParameters_beforeChangingAgreement() throws Exception {
        getMockRequest().setMethod("GET");
        addValidDurationParameters();
        UploadLoginText2Action action = new UploadLoginText2Action();
        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(405);
        assertThat(getMockResponse().getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void shouldRejectInvalidValidity_withoutWritingUploadedFile() throws Exception {
        getMockRequest().setMethod("POST");
        addRequestParameter("validDurationNumber", "not-a-number");
        addRequestParameter("validDurationPeriod", "days");
        Path upload = Files.writeString(uploadDir.resolve("invalid-policy.txt"), "Must not publish", StandardCharsets.UTF_8);
        UploadLoginText2Action action = new UploadLoginText2Action();
        action.setImportFile(upload.toFile());
        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.hasActionErrors()).isTrue();
        assertThat(getMockRequest().getAttribute("error")).isEqualTo(true);
        assertThat(documentDir.resolve("login/AcceptableUseAgreement.txt")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "19", "2147483648"})
    void shouldRejectOutOfRangeDuration(String duration) throws Exception {
        addRequestParameter("validDurationNumber", duration);
        addRequestParameter("validDurationPeriod", "days");
        UploadLoginText2Action action = new UploadLoginText2Action();
        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.hasActionErrors()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-date", "2026-02-30 00:00:00", "2026-09-19", "2026-09-19 00:00:00 extra"})
    void shouldRejectInvalidForeverDate(String date) throws Exception {
        addRequestParameter("validForever", "forever");
        addRequestParameter("foreverFrom", date);
        UploadLoginText2Action action = new UploadLoginText2Action();
        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.hasActionErrors()).isTrue();
    }

    @Test
    void shouldAcceptValidForeverDate() throws Exception {
        addRequestParameter("validForever", "forever");
        addRequestParameter("foreverFrom", "2026-09-19 00:00:00");
        UploadLoginText2Action action = new UploadLoginText2Action();
        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.hasActionErrors()).isFalse();
        assertThat(getMockRequest().getAttribute("error")).isEqualTo(false);
    }

    private void addValidDurationParameters() {
        addRequestParameter("validDurationNumber", "1");
        addRequestParameter("validDurationPeriod", "year");
    }
}
