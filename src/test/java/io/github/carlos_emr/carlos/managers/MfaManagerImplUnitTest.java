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
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MfaManagerImpl QR issuer branding")
@Tag("unit")
@Tag("security")
class MfaManagerImplUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NAME_PROPERTY = "mfa.registration.qrcode.provider.name";
    private static final String LOGO_PATH_PROPERTY = "mfa.registration.qrcode.logo.path";
    private static final String CARLOS_FAVICON_PATH = "loginResource/carlos_favicon.png";

    @Mock
    private SecurityDao securityDao;

    @Mock
    private SecurityManager securityManager;

    @Mock
    private CarlosProperties properties;

    private MfaManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = spy(new MfaManagerImpl(securityDao, securityManager));

        Security security = new Security();
        security.setUserName("alice");
        when(securityDao.find(42)).thenReturn(security);
    }

    @Test
    @DisplayName("should use CARLOS EMR fallback when MFA QR provider name property is absent")
    void shouldUseCarlosEmrFallback_whenMfaQrProviderNamePropertyIsAbsent() {
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty(PROVIDER_NAME_PROPERTY, "CARLOS EMR"))
                    .thenAnswer(invocation -> invocation.getArgument(1));
            when(properties.getProperty(LOGO_PATH_PROPERTY)).thenReturn(null);
            doReturn("qr".getBytes(StandardCharsets.UTF_8))
                    .when(manager).getQRCodeImageData("CARLOS EMR", "alice", "secret");

            String result = manager.getQRCodeImageData(42, "secret");

            verify(properties).getProperty(PROVIDER_NAME_PROPERTY, "CARLOS EMR");
            verify(manager).getQRCodeImageData("CARLOS EMR", "alice", "secret");
            assertThat(result).isEqualTo("data:image/png;base64,"
                    + Base64.getEncoder().encodeToString("qr".getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test
    @DisplayName("should use configured MFA QR provider name when property is set")
    void shouldUseConfiguredProviderName_whenMfaQrProviderNamePropertyIsSet() {
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty(PROVIDER_NAME_PROPERTY, "CARLOS EMR")).thenReturn("Clinic MFA");
            when(properties.getProperty(LOGO_PATH_PROPERTY)).thenReturn(null);
            doReturn("qr".getBytes(StandardCharsets.UTF_8))
                    .when(manager).getQRCodeImageData("Clinic MFA", "alice", "secret");

            String result = manager.getQRCodeImageData(42, "secret");

            verify(manager).getQRCodeImageData("Clinic MFA", "alice", "secret");
            assertThat(result).isEqualTo("data:image/png;base64,"
                    + Base64.getEncoder().encodeToString("qr".getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test
    @DisplayName("should apply the CARLOS favicon logo when configured for MFA QR codes")
    void shouldApplyCarlosFaviconLogo_whenConfiguredForMfaQrCodes() {
        MfaManagerImpl realManager = new MfaManagerImpl(securityDao, securityManager);

        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty(PROVIDER_NAME_PROPERTY, "CARLOS EMR")).thenReturn("CARLOS EMR");
            when(properties.getProperty(LOGO_PATH_PROPERTY)).thenReturn(CARLOS_FAVICON_PATH, null);

            assertThat(MfaManagerImpl.class.getClassLoader().getResource(CARLOS_FAVICON_PATH)).isNotNull();

            String qrCodeWithLogo = realManager.getQRCodeImageData(42, "secret");
            String qrCodeWithoutLogo = realManager.getQRCodeImageData(42, "secret");

            assertThat(qrCodeWithLogo).startsWith("data:image/png;base64,");
            assertThat(qrCodeWithoutLogo).startsWith("data:image/png;base64,");
            assertThat(qrCodeWithLogo).isNotEqualTo(qrCodeWithoutLogo);
        }
    }

    @Test
    @DisplayName("should leave the QR code unchanged when the configured logo resource is missing")
    void shouldLeaveQrCodeUnchanged_whenConfiguredLogoResourceIsMissing() {
        MfaManagerImpl realManager = new MfaManagerImpl(securityDao, securityManager);

        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty(PROVIDER_NAME_PROPERTY, "CARLOS EMR")).thenReturn("CARLOS EMR");
            when(properties.getProperty(LOGO_PATH_PROPERTY)).thenReturn("loginResource/missing-carlos-favicon.png", null);

            String qrCodeWithMissingLogo = realManager.getQRCodeImageData(42, "secret");
            String qrCodeWithoutLogo = realManager.getQRCodeImageData(42, "secret");

            assertThat(MfaManagerImpl.class.getClassLoader().getResource("loginResource/missing-carlos-favicon.png")).isNull();
            assertThat(qrCodeWithMissingLogo).startsWith("data:image/png;base64,");
            assertThat(qrCodeWithMissingLogo).isEqualTo(qrCodeWithoutLogo);
        }
    }
}
