/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.utility.DigitalSignatureUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

        DigitalSignature result = service.saveConsultationStamp(loggedInInfo, "123456", 44);

        assertThat(result).isSameAs(saved);
        verify(digitalSignatureManager).saveDigitalSignature(11, "123456", 44, stampBytes, ModuleType.CONSULTATION);
    }

    @Test
    @DisplayName("does not save another provider stamp without consultation write access")
    void shouldRejectSelectedProviderStamp_withoutConsultationWrite() throws Exception {
        Files.write(tempDir.resolve("consult_sig_123456.png"), new byte[] {1, 2, 3});
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_con"), eq("w"), isNull())).thenReturn(false);

        DigitalSignature result = service.saveConsultationStamp(loggedInInfo, "123456", 44);

        assertThat(result).isNull();
        verify(digitalSignatureManager, never()).saveDigitalSignature(
                eq(11), eq("123456"), eq(44), org.mockito.ArgumentMatchers.any(), eq(ModuleType.CONSULTATION));
    }

    @Test
    @DisplayName("leaves persisted signature IDs to the normal PDF rendering path")
    void shouldNotCreatePreviewOverride_forStoredSignatureId() throws Exception {
        Files.write(tempDir.resolve("consult_sig_999998.png"), new byte[] {1, 2, 3});

        byte[] override = service.resolvePreviewSignatureImage(false, "123", "", "999998");

        assertThat(override).isNull();
    }

    @Test
    @DisplayName("uses stamp bytes as a non-mutating preview override when the request has no stored signature")
    void shouldCreatePreviewOverride_forStampModeWithoutStoredSignature() throws Exception {
        byte[] stampBytes = new byte[] {3, 2, 1};
        Files.write(tempDir.resolve("consult_sig_999998.png"), stampBytes);

        byte[] override = service.resolvePreviewSignatureImage(false, "", "9999981000", "999998");

        assertThat(override).containsExactly(stampBytes);
    }

    @Test
    @DisplayName("uses temporary manual signature bytes for non-mutating print preview")
    void shouldCreatePreviewOverride_forManualSignatureTempFile() throws Exception {
        String requestId = "9999981000";
        byte[] manualBytes = new byte[] {4, 5, 6};
        Path tempSignature = Path.of(DigitalSignatureUtils.getTempFilePath(requestId));
        Files.write(tempSignature, manualBytes);

        byte[] override = service.resolvePreviewSignatureImage(true, requestId, requestId, "999998");

        assertThat(override).containsExactly(manualBytes);
        Files.deleteIfExists(tempSignature);
    }

    @Test
    @DisplayName("resolves only numeric provider numbers for consultation stamps")
    void shouldResolveOnlyNumericProviderNumbers_fromSubmittedValues() {
        assertThat(service.resolveSignatureProviderNo("../123", "abc", "999998")).isEqualTo("999998");
        assertThat(service.resolveSignatureProviderNo("123456", "999998", "999998")).isEqualTo("123456");
    }
}
