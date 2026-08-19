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
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The one place a portal identity is built from real CARLOS privileges.
 *
 * <p>{@link PatientPortalStaffContext} documents that its permissions must reflect what the provider
 * actually holds, and cannot enforce it — any caller can pass any set. These tests pin the class
 * that can, because a resolver that quietly granted everything would leave the portal's per-action
 * authorization decorative while every other test in the package still passed.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("PortalStaffContextResolver")
class PortalStaffContextResolverUnitTest {

    private static final String PROVIDER_NO = "999998";

    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private PortalStaffContextResolver resolver;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        Provider provider = mock(Provider.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        when(loggedInInfo.getLoggedInProvider()).thenReturn(provider);
        when(provider.getFormattedName()).thenReturn("Dr Example");
        resolver = new PortalStaffContextResolver(securityInfoManager);
    }

    private void grant(String... objects) {
        when(securityInfoManager.hasPrivilege(any(), any(), any(), isNull())).thenReturn(false);
        for (String object : objects) {
            when(securityInfoManager.hasPrivilege(any(), eq(object), eq("r"), isNull()))
                    .thenReturn(true);
        }
    }

    @Test
    @DisplayName("should grant only the permission behind the object the provider holds")
    void shouldGrantOnePermission_whenProviderHoldsOneObject() {
        grant(PortalStaffContextResolver.OBJECT_INVITE);

        PatientPortalStaffContext staff = resolver.resolve(loggedInInfo);

        assertThat(staff.permissions())
                .containsExactly(PatientPortalStaffContext.PERMISSION_INVITE_MANAGE);
        assertThat(staff.permissionHeaderValue()).isEqualTo("portal.invite.manage");
    }

    /**
     * The failure this class exists to prevent: a caller hardcoding the full permission set. If the
     * resolver ever grants a permission whose object the provider lacks, the portal's authorization
     * is being told something untrue and this assertion is the only thing that notices.
     */
    @Test
    @DisplayName("should never grant a permission whose object the provider lacks")
    void shouldWithholdPermissions_whenProviderLacksTheirObjects() {
        grant(PortalStaffContextResolver.OBJECT_INVITE, PortalStaffContextResolver.OBJECT_ACCOUNT);

        PatientPortalStaffContext staff = resolver.resolve(loggedInInfo);

        assertThat(staff.permissions())
                .containsExactlyInAnyOrder(
                        PatientPortalStaffContext.PERMISSION_INVITE_MANAGE,
                        PatientPortalStaffContext.PERMISSION_ACCOUNT_MANAGE);
        assertThat(staff.permissions())
                .doesNotContain(
                        PatientPortalStaffContext.PERMISSION_SECRET_MANAGE,
                        PatientPortalStaffContext.PERMISSION_ACCOUNT_UNLOCK,
                        PatientPortalStaffContext.PERMISSION_CONTACT_REVIEW);
    }

    @Test
    @DisplayName("should treat unlock as separate from general account management")
    void shouldWithholdUnlock_whenProviderHoldsOnlyAccountManagement() {
        grant(PortalStaffContextResolver.OBJECT_ACCOUNT);

        PatientPortalStaffContext staff = resolver.resolve(loggedInInfo);

        assertThat(staff.permissions())
                .containsExactly(PatientPortalStaffContext.PERMISSION_ACCOUNT_MANAGE);
        assertThat(staff.permissions())
                .doesNotContain(PatientPortalStaffContext.PERMISSION_ACCOUNT_UNLOCK);
    }

    @Test
    @DisplayName("should grant every permission to a provider holding every object")
    void shouldGrantAllPermissions_whenProviderHoldsEveryObject() {
        grant(
                PortalStaffContextResolver.OBJECT_INVITE,
                PortalStaffContextResolver.OBJECT_ACCOUNT,
                PortalStaffContextResolver.OBJECT_ACCOUNT_UNLOCK,
                PortalStaffContextResolver.OBJECT_SECRET,
                PortalStaffContextResolver.OBJECT_CONTACT_REVIEW);

        PatientPortalStaffContext staff = resolver.resolve(loggedInInfo);

        assertThat(staff.permissions()).hasSize(5);
    }

    /**
     * An empty permission set would be rejected by the portal as a malformed identity and recorded
     * there as an authentication failure, which reads as CARLOS being misconfigured rather than as
     * the authorization refusal it actually is.
     */
    @Test
    @DisplayName("should refuse to build an identity for a provider holding no portal object")
    void shouldThrow_whenProviderHoldsNoPortalPrivilege() {
        grant();

        assertThatThrownBy(() -> resolver.resolve(loggedInInfo))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should identify the provider by their durable provider number")
    void shouldUseProviderNumber_asThePortalIdentity() {
        grant(PortalStaffContextResolver.OBJECT_INVITE);

        PatientPortalStaffContext staff = resolver.resolve(loggedInInfo);

        assertThat(staff.providerId()).isEqualTo(PROVIDER_NO);
        assertThat(staff.providerName()).isEqualTo("Dr Example");
    }

    @Test
    @DisplayName("should fall back to the provider number when no name is on the session")
    void shouldFallBackToProviderNumber_whenNoDisplayNameIsAvailable() {
        when(loggedInInfo.getLoggedInProvider()).thenReturn(null);
        grant(PortalStaffContextResolver.OBJECT_INVITE);

        PatientPortalStaffContext staff = resolver.resolve(loggedInInfo);

        assertThat(staff.providerName()).isEqualTo(PROVIDER_NO);
    }
}
