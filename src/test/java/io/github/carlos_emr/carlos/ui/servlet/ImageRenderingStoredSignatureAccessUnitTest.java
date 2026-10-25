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
package io.github.carlos_emr.carlos.ui.servlet;

import io.github.carlos_emr.carlos.casemgmt.dao.ClientImageDAO;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Actual stored-signature bytes must respect real module rights and central record exclusions.
 * Only persisted role rows and signature metadata/image storage are replaced with owned fixtures.
 *
 * @since 2026-10-25
 */
@Tag("unit")
@Tag("security")
class ImageRenderingStoredSignatureAccessUnitTest extends CarlosUnitTestBase {
    private final Map<String, String> grants = new HashMap<>();
    private SecurityInfoManagerImpl policy;
    private DigitalSignatureManager signatures;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private byte[] privateBytes;
    private byte[] otherBytes;

    @BeforeEach
    void prepareActualServletAndRecordPolicy() {
        createAndRegisterMock(ClientImageDAO.class);
        signatures = createAndRegisterMock(DigitalSignatureManager.class);
        policy = new SecurityInfoManagerImpl();
        SecuserroleDao roles = mock(SecuserroleDao.class);
        Secuserrole role = new Secuserrole();
        role.setProviderNo("999998"); role.setRoleName("signature-reader");
        when(roles.findActiveByProviderNo("999998")).thenReturn(List.of(role));
        ReflectionTestUtils.setField(policy, "secUserRoleDao", roles);
        SecObjPrivilegeDao rows = createAndRegisterMock(SecObjPrivilegeDao.class);
        when(rows.findByObjectNames(any())).thenAnswer(invocation -> {
            Collection<String> names = invocation.getArgument(0);
            List<SecObjPrivilege> result = new ArrayList<>();
            for (String name : names) if (grants.containsKey(name)) {
                SecObjPrivilege row = new SecObjPrivilege();
                row.setId(new SecObjPrivilegePrimaryKey("signature-reader", name));
                row.setPrivilege(grants.get(name)); row.setPriority(0); result.add(row);
            }
            return result;
        });
        registerMock(SecurityInfoManager.class, policy);
        grants.put("_demographic", "r"); grants.put("_eChart", "r");
        request = new MockHttpServletRequest("GET", "/imageRenderingServlet");
        request.setParameter("source", "signature_stored"); request.setParameter("digitalSignatureId", "41");
        request.getSession().setAttribute(SessionConstants.LOGGED_IN_PROVIDER, new Provider("999998"));
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        when(login.getSession()).thenReturn(request.getSession());
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        response = new MockHttpServletResponse();
        privateBytes = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==");
        otherBytes = privateBytes.clone(); otherBytes[13] = (byte) 0xff;
    }

    static Stream<Arguments> signaturePolicyCases() {
        return Stream.of(ModuleType.CONSULTATION, ModuleType.PRESCRIPTION, ModuleType.E_FORM).flatMap(module ->
                Stream.of("demographic-exclusion", "chart-exclusion", "allowed", "module-only", "module-denied",
                        "posted-open-for-excluded", "posted-excluded-for-open", "no-patient", "unknown-module", "stale-login")
                        .map(boundary -> Arguments.of(module, boundary)));
    }

    @ParameterizedTest
    @MethodSource("signaturePolicyCases")
    void shouldApplyStoredMetadataModuleAndRecordAuthority_beforeReturningAnyPatientSignature(
            ModuleType module, String boundary) throws Exception {
        String right = module == ModuleType.CONSULTATION ? "_con" : module == ModuleType.PRESCRIPTION ? "_rx" : "_eform";
        grants.put(right, "r");
        boolean recordDenied = List.of("demographic-exclusion", "chart-exclusion", "posted-open-for-excluded").contains(boundary);
        if (recordDenied || "posted-excluded-for-open".equals(boundary)) {
            grants.put(("chart-exclusion".equals(boundary) ? "_eChart" : "_demographic") + "$101", "o");
            assertThat(policy.isAllowedAccessToPatientRecord(login, 101)).isFalse();
        }
        if ("module-only".equals(boundary)) {
            grants.remove("_eChart"); grants.remove("_demographic");
            assertThat(policy.hasPrivilege(login, "_eChart", "r", "101")).isFalse();
        }
        if ("module-denied".equals(boundary)) grants.remove(right);
        int patient = "posted-excluded-for-open".equals(boundary) ? 202 : 101;
        byte[] expected = patient == 101 ? privateBytes : otherBytes;
        DigitalSignature metadata = artifact("unknown-module".equals(boundary) ? ModuleType.getByName("FAKE_UNKNOWN") : module,
                "no-patient".equals(boundary) ? null : patient, null);
        DigitalSignature stored = artifact(module, patient, expected.clone());
        when(signatures.getDigitalSignatureMetadata(41)).thenReturn(metadata);
        when(signatures.getDigitalSignature(41)).thenReturn(stored);
        if (boundary.startsWith("posted-") || "no-patient".equals(boundary)) {
            String posted = "posted-excluded-for-open".equals(boundary) ? "101" : "202";
            request.setParameter("demographicNo", posted); request.setParameter("demographic_no", posted);
            request.setParameter("clientId", posted); request.setParameter("providerNo", "999997");
        }
        boolean allowed = List.of("allowed", "module-only", "posted-excluded-for-open").contains(boundary);
        assertThat(policy.hasPrivilege(login, right, "r", Integer.toString(patient))).isEqualTo(!"module-denied".equals(boundary));
        if (!recordDenied) assertThat(policy.isAllowedAccessToPatientRecord(login, patient)).isTrue();
        if ("stale-login".equals(boundary)) LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), null);
        Map<String, Object> sessionBefore = new HashMap<>();
        for (String name : java.util.Collections.list(request.getSession().getAttributeNames())) {
            sessionBefore.put(name, request.getSession().getAttribute(name));
        }

        new ImageRenderingServlet().doGet(request, response);

        if (allowed) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).isEqualTo("image/gif");
            assertThat(response.getContentAsByteArray()).containsExactly(expected);
            verify(signatures).getDigitalSignature(41);
        } else {
            org.junit.jupiter.api.Assertions.assertAll(
                    () -> assertThat(response.getContentAsByteArray()).as("Excluded or unauthorized signature must not return its private image bytes").isEmpty(),
                    () -> assertThat(response.getStatus()).isEqualTo(403),
                    () -> verify(signatures, never()).getDigitalSignature(41));
        }
        verify(signatures).getDigitalSignatureMetadata(41);
        assertThat(metadata.getSignatureImage()).isNull();
        assertThat(stored.getSignatureImage()).containsExactly(expected);
        assertThat(java.util.Collections.list(request.getSession().getAttributeNames())).containsExactlyInAnyOrderElementsOf(sessionBefore.keySet());
        sessionBefore.forEach((name, value) -> assertThat(request.getSession().getAttribute(name)).isSameAs(value));
        if (recordDenied) {
            DigitalSignature other = artifact(module, 202, otherBytes.clone());
            when(signatures.getDigitalSignatureMetadata(42)).thenReturn(artifact(module, 202, null));
            when(signatures.getDigitalSignature(42)).thenReturn(other);
            request.setParameter("digitalSignatureId", "42");
            response = new MockHttpServletResponse();
            assertThat(policy.isAllowedAccessToPatientRecord(login, 202)).isTrue();
            new ImageRenderingServlet().doGet(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsByteArray()).containsExactly(otherBytes);
            verify(signatures).getDigitalSignatureMetadata(42);
            verify(signatures).getDigitalSignature(42);
        }
    }

    @Test
    void shouldRefuseUnauthenticatedProviderContext_beforeAnyMetadataOrImageLookup() throws Exception {
        request.getSession().removeAttribute(SessionConstants.LOGGED_IN_PROVIDER);
        new ImageRenderingServlet().doGet(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verifyNoInteractions(signatures);
    }

    private DigitalSignature artifact(ModuleType module, Integer patient, byte[] bytes) {
        DigitalSignature signature = new DigitalSignature();
        signature.setProviderNo("999998"); signature.setModuleType(module);
        signature.setDemographicId(patient); signature.setSignatureImage(bytes);
        return signature;
    }
}
