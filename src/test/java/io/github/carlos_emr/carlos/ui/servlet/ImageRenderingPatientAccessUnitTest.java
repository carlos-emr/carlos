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
import io.github.carlos_emr.carlos.casemgmt.model.ClientImage;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Drives actual photo bytes through the public servlet with the real privilege/record policy.
 * Only role/privilege and image persistence boundaries are replaced with isolated test fixtures.
 *
 * @since 2026-10-25
 */
@Tag("unit")
class ImageRenderingPatientAccessUnitTest extends CarlosUnitTestBase {
    private final Map<String, String> grants = new HashMap<>();
    private ClientImageDAO images;
    private Object originalImageDao;
    private SecurityInfoManagerImpl policy;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private byte[] privatePhoto;
    private byte[] readablePhoto;

    @BeforeEach
    void initializeIsolatedRolesAndImages() {
        images = createAndRegisterMock(ClientImageDAO.class);
        policy = new SecurityInfoManagerImpl();
        SecuserroleDao roles = mock(SecuserroleDao.class);
        Secuserrole role = new Secuserrole();
        role.setProviderNo("999998"); role.setRoleName("photo-reader");
        when(roles.findActiveByProviderNo("999998")).thenReturn(List.of(role));
        ReflectionTestUtils.setField(policy, "secUserRoleDao", roles);
        SecObjPrivilegeDao privilegeRows = createAndRegisterMock(SecObjPrivilegeDao.class);
        when(privilegeRows.findByObjectNames(any())).thenAnswer(invocation -> {
            Collection<String> names = invocation.getArgument(0);
            List<SecObjPrivilege> result = new ArrayList<>();
            for (String name : names) if (grants.containsKey(name)) {
                SecObjPrivilege row = new SecObjPrivilege();
                row.setId(new SecObjPrivilegePrimaryKey("photo-reader", name));
                row.setPrivilege(grants.get(name)); row.setPriority(0);
                result.add(row);
            }
            return result;
        });
        registerMock(SecurityInfoManager.class, policy);
        grants.put("_eChart", "r"); grants.put("_demographic", "r"); grants.put("_newCasemgmt.photo", "r");
        request = new MockHttpServletRequest("GET", "/imageRenderingServlet");
        request.setParameter("source", "local_client");
        request.setParameter("clientId", "101");
        Provider provider = new Provider("999998");
        request.getSession().setAttribute(SessionConstants.LOGGED_IN_PROVIDER, provider);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        when(login.getSession()).thenReturn(request.getSession());
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        response = new MockHttpServletResponse();
        privatePhoto = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==");
        readablePhoto = privatePhoto.clone(); readablePhoto[13] = (byte) 0xff;
        when(images.getClientImage(101)).thenReturn(photo(101, privatePhoto));
        when(images.getClientImage(202)).thenReturn(photo(202, readablePhoto));
        // The legacy servlet caches its DAO statically. Own and restore only that fixture reference.
        originalImageDao = ReflectionTestUtils.getField(ImageRenderingServlet.class, "clientImageDAO");
        ReflectionTestUtils.setField(ImageRenderingServlet.class, "clientImageDAO", images);
    }

    @AfterEach
    void restoreServletDao() {
        if (originalImageDao != null) ReflectionTestUtils.setField(ImageRenderingServlet.class, "clientImageDAO", originalImageDao);
    }

    private static ClientImage photo(int patient, byte[] bytes) {
        ClientImage photo = new ClientImage();
        photo.setDemographic_no(patient); photo.setImage_type("image/gif"); photo.setImage_data(bytes);
        return photo;
    }

    @ParameterizedTest
    @ValueSource(strings = {"allowed", "classic-without-optional-photo-grant"})
    void shouldReturnTheExactPatientPhoto_whenRealChartAndRecordPolicyAllowIt(String caller) throws Exception {
        if (caller.startsWith("classic")) grants.remove("_newCasemgmt.photo");
        assertThat(policy.hasPrivilege(login, "_eChart", "r", "101")).isTrue();
        assertThat(policy.isAllowedAccessToPatientRecord(login, 101)).isTrue();

        new ImageRenderingServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/gif");
        assertThat(response.getContentAsByteArray()).containsExactly(privatePhoto);
        verify(images).getClientImage(101);
    }

    @ParameterizedTest
    @ValueSource(strings = {"_demographic$101", "_eChart$101"})
    void shouldRefuseExcludedPatientBytes_beforeImageLookupWhileAnotherPatientRemainsReadable(String record) throws Exception {
        grants.put(record, "o");
        assertThat(policy.isAllowedAccessToPatientRecord(login, 101)).isFalse();
        if (record.startsWith("_demographic")) assertThat(policy.hasPrivilege(login, "_eChart", "r", "101")).isTrue();
        new ImageRenderingServlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verifyNoInteractions(images);
        request.setParameter("clientId", "202");
        response = new MockHttpServletResponse();
        assertThat(policy.isAllowedAccessToPatientRecord(login, 202)).isTrue();
        new ImageRenderingServlet().doGet(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).containsExactly(readablePhoto);
        verify(images).getClientImage(202);
    }

    @Test
    void shouldRefusePhotoRead_whenChartPrivilegeIsMissingEvenWithOtherModuleGrants() throws Exception {
        grants.remove("_eChart");
        assertThat(policy.hasPrivilege(login, "_eChart", "r", "101")).isFalse();
        assertThat(policy.isAllowedAccessToPatientRecord(login, 101)).isTrue();
        new ImageRenderingServlet().doGet(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verifyNoInteractions(images);
    }

    @Test
    void shouldRefuseAStaleProviderSession_withoutAnAuthenticatedLogin() throws Exception {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), null);
        new ImageRenderingServlet().doGet(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verifyNoInteractions(images);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-a-patient", "2147483648", ""})
    void shouldRetainImageNotFoundSemantics_withoutLookingUpMalformedPatients(String raw) throws Exception {
        request.setParameter("clientId", raw);
        new ImageRenderingServlet().doGet(request, response);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verifyNoInteractions(images);
    }

    @Test
    void shouldNotSelectTheFirstPatient_whenRawPhotoIdentitiesConflict() throws Exception {
        request.setParameter("clientId", new String[]{"101", "202"});
        new ImageRenderingServlet().doGet(request, response);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verifyNoInteractions(images);
    }

    @Test
    void shouldAcceptEquivalentCanonicalPatientValues_withoutUsingAnyRememberedPatient() throws Exception {
        request.setParameter("clientId", new String[]{"00101", "101"});
        request.getSession().setAttribute("clientId", "202");
        new ImageRenderingServlet().doGet(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).containsExactly(privatePhoto);
        verify(images).getClientImage(101);
        verify(images, never()).getClientImage(202);
    }
}
