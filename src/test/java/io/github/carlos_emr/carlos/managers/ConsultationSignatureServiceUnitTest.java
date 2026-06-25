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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.utility.DigitalSignatureUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Consultation signature service")
@Tag("unit")
class ConsultationSignatureServiceUnitTest {

    @TempDir
    private Path tempDir;

    @Mock
    private DigitalSignatureManager digitalSignatureManager;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private LoggedInInfo loggedInInfo;

    private ConsultationSignatureService service;
    private boolean hadEformImagesDir;
    private Object originalEformImagesDir;

    @BeforeEach
    void setUp() {
        CarlosProperties props = CarlosProperties.getInstance();
        hadEformImagesDir = props.containsKey("EFORM_IMAGES_DIR");
        originalEformImagesDir = props.get("EFORM_IMAGES_DIR");
        props.setProperty("EFORM_IMAGES_DIR", tempDir.toString());

        Facility facility = new Facility();
        facility.setId(11);
        facility.setEnableDigitalSignatures(true);
        when(loggedInInfo.getCurrentFacility()).thenReturn(facility);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        service = new ConsultationSignatureService(digitalSignatureManager, securityInfoManager);
    }

    @AfterEach
    void tearDown() {
        CarlosProperties props = CarlosProperties.getInstance();
        if (hadEformImagesDir) {
            props.put("EFORM_IMAGES_DIR", originalEformImagesDir);
        } else {
            props.remove("EFORM_IMAGES_DIR");
        }
    }

    @Test
    @DisplayName("saves the selected consultation provider stamp when consultation write access is present")
    void shouldSaveSelectedProviderStamp_whenConsultationWriteGranted() throws Exception {
        byte[] stampBytes = new byte[] {7, 8, 9};
        Files.write(tempDir.resolve("consult_sig_123456.png"), stampBytes);
        DigitalSignature saved = new DigitalSignature();
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_con"), eq("w"), isNull())).thenReturn(true);
        when(digitalSignatureManager.saveDigitalSignature(eq(11), eq("123456"), eq(44), eq(stampBytes), eq(ModuleType.CONSULTATION)))
                .thenReturn(saved);

        ConsultationStampOutcome result = service.saveConsultationStamp(loggedInInfo, "123456", 44);

        assertThat(result.isSaved()).isTrue();
        assertThat(result.signature()).isSameAs(saved);
        verify(digitalSignatureManager).saveDigitalSignature(11, "123456", 44, stampBytes, ModuleType.CONSULTATION);
    }

    @Test
    @DisplayName("does not save another provider stamp without consultation write access")
    void shouldRejectSelectedProviderStamp_withoutConsultationWrite() throws Exception {
        Files.write(tempDir.resolve("consult_sig_123456.png"), new byte[] {1, 2, 3});
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_con"), eq("w"), isNull())).thenReturn(false);

        ConsultationStampOutcome result = service.saveConsultationStamp(loggedInInfo, "123456", 44);

        assertThat(result.status()).isEqualTo(ConsultationStampOutcome.Status.NOT_PERMITTED);
        verify(digitalSignatureManager, never()).saveDigitalSignature(
                eq(11), eq("123456"), eq(44), org.mockito.ArgumentMatchers.any(), eq(ModuleType.CONSULTATION));
    }

    @Test
    @DisplayName("rejects a non-numeric provider without consulting the signature manager or privilege check")
    void shouldRejectStamp_forNonNumericProvider() {
        ConsultationStampOutcome result = service.saveConsultationStamp(loggedInInfo, "../999998", 44);

        assertThat(result.status()).isEqualTo(ConsultationStampOutcome.Status.NOT_PERMITTED);
        verify(securityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any());
        verify(digitalSignatureManager, never()).saveDigitalSignature(
                anyInt(), anyString(), anyInt(), any(), any(ModuleType.class));
    }

    @Test
    @DisplayName("returns SIGNATURES_DISABLED when the facility has digital signatures off")
    void shouldReturnDisabled_whenFacilitySignaturesOff() {
        loggedInInfo.getCurrentFacility().setEnableDigitalSignatures(false);

        ConsultationStampOutcome result = service.saveConsultationStamp(loggedInInfo, "999998", 44);

        assertThat(result.status()).isEqualTo(ConsultationStampOutcome.Status.SIGNATURES_DISABLED);
        assertThat(result.isGenuineFailure()).isFalse();
    }

    @Test
    @DisplayName("returns NO_SESSION when there is no logged-in session or facility")
    void shouldReturnNoSession_whenSessionOrFacilityMissing() {
        assertThat(service.saveConsultationStamp(null, "999998", 44).status())
                .isEqualTo(ConsultationStampOutcome.Status.NO_SESSION);

        when(loggedInInfo.getCurrentFacility()).thenReturn(null);
        assertThat(service.saveConsultationStamp(loggedInInfo, "999998", 44).status())
                .isEqualTo(ConsultationStampOutcome.Status.NO_SESSION);
    }

    @Test
    @DisplayName("returns STAMP_FILE_MISSING when the provider has no stamp file")
    void shouldReturnStampFileMissing_whenNoStampOnDisk() {
        ConsultationStampOutcome result = service.saveConsultationStamp(loggedInInfo, "999998", 44);

        assertThat(result.status()).isEqualTo(ConsultationStampOutcome.Status.STAMP_FILE_MISSING);
        assertThat(result.isGenuineFailure()).isTrue();
    }

    @Test
    @DisplayName("leaves persisted signature IDs to the normal PDF rendering path")
    void shouldNotCreatePreviewOverride_forStoredSignatureId() throws Exception {
        Files.write(tempDir.resolve("consult_sig_999998.png"), new byte[] {1, 2, 3});

        byte[] override = service.resolvePreviewSignatureImage(loggedInInfo, false, "123", "", "999998");

        assertThat(override).isNull();
    }

    @Test
    @DisplayName("uses stamp bytes as a non-mutating preview override when the request has no stored signature")
    void shouldCreatePreviewOverride_forStampModeWithoutStoredSignature() throws Exception {
        byte[] stampBytes = new byte[] {3, 2, 1};
        Files.write(tempDir.resolve("consult_sig_999998.png"), stampBytes);

        byte[] override = service.resolvePreviewSignatureImage(loggedInInfo, false, "", "9999981000", "999998");

        assertThat(override).containsExactly(stampBytes);
    }

    @Test
    @DisplayName("denies a stamp preview for another provider without consultation write access")
    void shouldDenyPreviewOverride_forCrossProviderStampWithoutWrite() throws Exception {
        Files.write(tempDir.resolve("consult_sig_123456.png"), new byte[] {3, 2, 1});
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_con"), eq("w"), isNull())).thenReturn(false);

        byte[] override = service.resolvePreviewSignatureImage(loggedInInfo, false, "", "", "123456");

        assertThat(override).isNull();
    }

    @Test
    @DisplayName("returns no preview override when stamp mode has no stamp file on disk")
    void shouldReturnNoOverride_forStampModeWithoutStampFile() {
        byte[] override = service.resolvePreviewSignatureImage(loggedInInfo, false, "", "", "999998");

        assertThat(override).isNull();
    }

    @Test
    @DisplayName("returns no preview override when a manual re-sign has no captured bytes")
    void shouldReturnNoOverride_whenManualReSignHasNoCapturedBytes() {
        byte[] override = service.resolvePreviewSignatureImage(loggedInInfo, true, "123", "", "999998");

        assertThat(override).isNull();
    }

    @Test
    @DisplayName("returns null and reads no bytes when path validation blocks the stamp read")
    void shouldReturnNoOverride_whenPathValidationBlocksStampRead() throws Exception {
        Files.write(tempDir.resolve("consult_sig_999998.png"), new byte[] {9, 9, 9});

        try (var pathValidation = mockStatic(PathValidationUtils.class)) {
            pathValidation.when(() -> PathValidationUtils.validatePath(anyString(), any()))
                    .thenThrow(new SecurityException("blocked traversal"));

            byte[] override = service.resolvePreviewSignatureImage(loggedInInfo, false, "", "", "999998");

            assertThat(override).isNull();
        }
    }

    @Test
    @DisplayName("uses temporary manual signature bytes for non-mutating print preview")
    void shouldCreatePreviewOverride_forManualSignatureTempFile() throws Exception {
        String requestId = "9999981000";
        byte[] manualBytes = new byte[] {4, 5, 6};
        Path tempSignature = Path.of(DigitalSignatureUtils.getTempFilePath(requestId));
        Files.write(tempSignature, manualBytes);

        try {
            byte[] override = service.resolvePreviewSignatureImage(loggedInInfo, true, requestId, requestId, "999998");

            assertThat(override).containsExactly(manualBytes);
        } finally {
            Files.deleteIfExists(tempSignature);
        }
    }

    @Test
    @DisplayName("returns ERROR when stamp persistence yields no signature")
    void shouldReturnError_whenPersistenceReturnsNull() throws Exception {
        Files.write(tempDir.resolve("consult_sig_999998.png"), new byte[] {1, 2, 3});
        when(digitalSignatureManager.saveDigitalSignature(eq(11), eq("999998"), eq(44), any(), eq(ModuleType.CONSULTATION)))
                .thenReturn(null);

        ConsultationStampOutcome result = service.saveConsultationStamp(loggedInInfo, "999998", 44);

        assertThat(result.status()).isEqualTo(ConsultationStampOutcome.Status.ERROR);
        assertThat(result.isGenuineFailure()).isTrue();
    }

    @Test
    @DisplayName("returns ERROR when stamp persistence throws")
    void shouldReturnError_whenPersistenceThrows() throws Exception {
        Files.write(tempDir.resolve("consult_sig_999998.png"), new byte[] {1, 2, 3});
        when(digitalSignatureManager.saveDigitalSignature(eq(11), eq("999998"), eq(44), any(), eq(ModuleType.CONSULTATION)))
                .thenThrow(new RuntimeException("db down"));

        ConsultationStampOutcome result = service.saveConsultationStamp(loggedInInfo, "999998", 44);

        assertThat(result.status()).isEqualTo(ConsultationStampOutcome.Status.ERROR);
    }

    @Test
    @DisplayName("reports a captured manual signature when a non-empty temp file is present")
    void shouldReportManualCaptured_whenTempFilePresent() throws Exception {
        String requestId = "9999982000";
        Path tempSignature = Path.of(DigitalSignatureUtils.getTempFilePath(requestId));
        Files.write(tempSignature, new byte[] {1, 2, 3});

        try {
            assertThat(service.wasManualSignatureCaptured(requestId)).isTrue();
        } finally {
            Files.deleteIfExists(tempSignature);
        }
    }

    @Test
    @DisplayName("reports no captured manual signature when the temp file is absent or the id blank")
    void shouldReportNoManualCapture_whenTempFileAbsent() {
        assertThat(service.wasManualSignatureCaptured("9999983000")).isFalse();
        assertThat(service.wasManualSignatureCaptured("")).isFalse();
    }

    @Test
    @DisplayName("resolves only numeric provider numbers for consultation stamps")
    void shouldResolveOnlyNumericProviderNumbers_fromSubmittedValues() {
        assertThat(service.resolveSignatureProviderNo("../123", "abc", "999998")).isEqualTo("999998");
        assertThat(service.resolveSignatureProviderNo("123456", "999998", "999998")).isEqualTo("123456");
    }
}
