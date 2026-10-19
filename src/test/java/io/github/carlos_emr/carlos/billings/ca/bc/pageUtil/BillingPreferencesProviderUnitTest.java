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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingFormData;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingPreference;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingPreferencesDAO;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BC Billing Preferences are the signed-in provider's own. The page's form posted an empty provider number, and the
 * save stored every preference under it: the property rows without a provider, which is how CARLOS stores clinic-wide
 * settings, and the billing_preferences row for provider 0 (issue #4936).
 *
 * @since 2026-10-18
 */
@DisplayName("BC Billing Preferences: the signed-in provider's own")
@Tag("unit")
@Tag("billing")
class BillingPreferencesProviderUnitTest extends CarlosUnitTestBase {

    private static final String SIGNED_IN = "111111";

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
    private final PropertyDao propertyDao = mock(PropertyDao.class);
    private final BillingPreferencesDAO preferencesDao = mock(BillingPreferencesDAO.class);
    private final ProviderDao providerDao = mock(ProviderDao.class);
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;

    @BeforeEach
    void setUp() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(), eq("_billing"), eq("w"), any())).thenReturn(true);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(PropertyDao.class, propertyDao);
        registerMock(BillingPreferencesDAO.class, preferencesDao);
        registerMock(ProviderDao.class, providerDao);
        when(propertyDao.findByNameAndProvider(any(Property.PROPERTY_KEY.class), any())).thenReturn(List.of());
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(SIGNED_IN);
        // The actions read the request in their field initialisers, so these come first.
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(mock(HttpServletResponse.class));
        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoStatic.close();
        servletActionContext.close();
    }

    /** "" is what the page's form posts; "222222" is any other provider's number. */
    @ParameterizedTest
    @ValueSource(strings = {"", "222222"})
    void shouldStoreEveryPreference_underTheSignedInProvider(String postedProviderNo) {
        SaveBillingPreferences2Action save = new SaveBillingPreferences2Action();
        save.setProviderNo(postedProviderNo);
        save.setReferral("2");
        save.setPayeeProviderNo("CUSTOM");
        save.setInvoicePayeeInfo("FAKE payee");
        save.setDefaultBillingForm("ER");
        save.setDefaultBillingProvider("clinicdefault");
        save.setDefaultServiceLocation("A");

        save.execute();

        ArgumentCaptor<Property> properties = ArgumentCaptor.forClass(Property.class);
        verify(propertyDao, atLeastOnce()).persist(properties.capture());
        assertThat(properties.getAllValues()).isNotEmpty()
                .allSatisfy(property -> assertThat(property.getProviderNo()).as(property.getName()).isEqualTo(SIGNED_IN));
        ArgumentCaptor<BillingPreference> preference = ArgumentCaptor.forClass(BillingPreference.class);
        verify(preferencesDao).persist(preference.capture());
        assertThat(preference.getValue().getProviderNo()).isEqualTo(SIGNED_IN);
        verify(request).setAttribute("providerNo", SIGNED_IN);
    }

    @Test
    void shouldKeepTheProvidersAutoPopulateChoice_whileTheClinicWideSettingIsOn() {
        Property clinicWide = new Property();
        clinicWide.setName(Property.PROPERTY_KEY.auto_populate_refer.name());
        clinicWide.setValue("true");
        when(propertyDao.findGlobalByName(Property.PROPERTY_KEY.auto_populate_refer)).thenReturn(List.of(clinicWide));
        Property own = new Property();
        own.setName(Property.PROPERTY_KEY.auto_populate_refer.name());
        own.setProviderNo(SIGNED_IN);
        own.setValue("true");
        when(propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.auto_populate_refer, SIGNED_IN)).thenReturn(List.of(own));
        // The page disables the box then, so the form posts nothing for it.
        SaveBillingPreferences2Action save = new SaveBillingPreferences2Action();
        save.setReferral("1");
        save.setPayeeProviderNo("NONE");
        save.setDefaultBillingForm("clinicdefault");
        save.setDefaultBillingProvider("clinicdefault");
        save.setDefaultServiceLocation("clinicdefault");

        save.execute();

        assertThat(own.getValue()).isEqualTo("true");
        verify(propertyDao, never()).merge(own);
        verify(propertyDao, never()).persist(argThat((Property property) ->
                Property.PROPERTY_KEY.auto_populate_refer.name().equals(property.getName())));
    }

    @Test
    void shouldOfferClinicDefaultAndNoPayee_whenTheProviderHasSavedNone() {
        try (MockedConstruction<BillingFormData> forms = mockConstruction(BillingFormData.class, (data, context) -> {
            when(data.getFormList()).thenReturn(new BillingFormData.BillingForm[0]);
            when(data.getVisitType(anyString())).thenReturn(new ArrayList<>());
        })) {
            BillingPreference fresh = new BillingPreference();
            fresh.setProviderNo(SIGNED_IN);
            when(preferencesDao.getUserBillingPreference(SIGNED_IN)).thenReturn(fresh);
            Provider signedIn = new Provider();
            signedIn.setFirstName("FAKE");
            signedIn.setLastName("Provider");
            when(providerDao.getProvider(SIGNED_IN)).thenReturn(signedIn);
            ViewBillingPreferences2Action view = new ViewBillingPreferences2Action();

            view.execute();

            // The selects otherwise fall to their first option, a real provider, which a Save then stores.
            assertThat(view.getDefaultBillingProvider()).isEqualTo("clinicdefault");
            assertThat(view.getPayeeProviderNo()).isEqualTo("NONE");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "222222"})
    void shouldReadTheSignedInProvidersPreferences_whateverProviderTheRequestNames(String requestedProviderNo) {
        try (MockedConstruction<BillingFormData> forms = mockConstruction(BillingFormData.class, (data, context) -> {
            when(data.getFormList()).thenReturn(new BillingFormData.BillingForm[0]);
            when(data.getVisitType(anyString())).thenReturn(new ArrayList<>());
        })) {
            BillingPreference existing = new BillingPreference();
            existing.setProviderNo(SIGNED_IN);
            when(preferencesDao.getUserBillingPreference(SIGNED_IN)).thenReturn(existing);
            // With no payee information stored, the page shows the provider's own name.
            Provider signedIn = new Provider();
            signedIn.setFirstName("FAKE");
            signedIn.setLastName("Provider");
            when(providerDao.getProvider(SIGNED_IN)).thenReturn(signedIn);
            ViewBillingPreferences2Action view = new ViewBillingPreferences2Action();
            view.setProviderNo(requestedProviderNo);

            view.execute();

            verify(preferencesDao).getUserBillingPreference(SIGNED_IN);
            verify(preferencesDao, never()).getUserBillingPreference(requestedProviderNo);
            verify(preferencesDao, never()).saveUserPreferences(any());
            verify(providerDao, never()).getProvider(requestedProviderNo);
            verify(propertyDao, never()).findByNameAndProvider(any(Property.PROPERTY_KEY.class), eq(requestedProviderNo));
        }
    }
}
