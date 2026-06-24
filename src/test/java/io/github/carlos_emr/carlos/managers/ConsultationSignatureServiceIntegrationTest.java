/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.DigitalSignatureDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Consultation signature service integration")
@Tag("integration")
class ConsultationSignatureServiceIntegrationTest extends CarlosTestBase {

    @Autowired
    private DigitalSignatureDao digitalSignatureDao;

    @TempDir
    private Path tempDir;

    private boolean hadEformImagesDir;
    private Object originalEformImagesDir;
    private boolean hadEncryptionKey;
    private Object originalEncryptionKey;
    private Object originalSecretKeySpec;

    @BeforeEach
    void setUpSignatureDirectory() throws Exception {
        CarlosProperties props = CarlosProperties.getInstance();
        hadEformImagesDir = props.containsKey("EFORM_IMAGES_DIR");
        originalEformImagesDir = props.get("EFORM_IMAGES_DIR");
        props.setProperty("EFORM_IMAGES_DIR", tempDir.toString());

        Field keySpecField = secretKeySpecField();
        originalSecretKeySpec = keySpecField.get(null);
        hadEncryptionKey = props.containsKey(EncryptionUtils.SECRET_KEY_ENV_VAR);
        originalEncryptionKey = props.get(EncryptionUtils.SECRET_KEY_ENV_VAR);
        props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, EncryptionUtils.generateSecretKey());
        EncryptionUtils.prepareSecretKeySpec();
    }

    @AfterEach
    void restoreSignatureDirectory() throws Exception {
        CarlosProperties props = CarlosProperties.getInstance();
        if (hadEformImagesDir) {
            props.put("EFORM_IMAGES_DIR", originalEformImagesDir);
        } else {
            props.remove("EFORM_IMAGES_DIR");
        }
        if (hadEncryptionKey) {
            props.put(EncryptionUtils.SECRET_KEY_ENV_VAR, originalEncryptionKey);
        } else {
            props.remove(EncryptionUtils.SECRET_KEY_ENV_VAR);
        }
        secretKeySpecField().set(null, originalSecretKeySpec);
    }

    @Test
    @DisplayName("persists a consultation stamp as a decryptable DigitalSignature")
    void shouldPersistConsultationStampToDigitalSignatureTable() throws Exception {
        byte[] stampBytes = new byte[] {10, 20, 30, 40};
        Files.write(tempDir.resolve("consult_sig_999998.png"), stampBytes);

        DigitalSignatureManagerImpl digitalSignatureManager = new DigitalSignatureManagerImpl(digitalSignatureDao);
        ConsultationSignatureService service = new ConsultationSignatureService(digitalSignatureManager, mock(SecurityInfoManager.class));

        DigitalSignature saved = service.saveConsultationStamp(loggedInInfo("999998"), "999998", 123);
        digitalSignatureDao.flush();

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isPositive();
        DigitalSignature found = digitalSignatureManager.getDigitalSignature(saved.getId());
        assertThat(found.getProviderNo()).isEqualTo("999998");
        assertThat(found.getDemographicId()).isEqualTo(123);
        assertThat(found.getModuleType()).isEqualTo(ModuleType.CONSULTATION);
        assertThat(found.getSignatureImage()).containsExactly(stampBytes);
    }

    private Field secretKeySpecField() throws Exception {
        Field field = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        field.setAccessible(true);
        return field;
    }

    private LoggedInInfo loggedInInfo(String providerNo) {
        Facility facility = new Facility();
        facility.setId(22);
        facility.setEnableDigitalSignatures(true);
        Provider provider = new Provider(providerNo);

        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setCurrentFacility(facility);
        loggedInInfo.setLoggedInProvider(provider);
        return loggedInInfo;
    }
}
