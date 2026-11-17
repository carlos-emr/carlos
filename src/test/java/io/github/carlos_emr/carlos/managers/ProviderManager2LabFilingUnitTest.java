/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import java.util.List;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ProviderManager2LabFilingUnitTest {
    private final ProviderManager2 manager = new ProviderManager2();
    private final PropertyDao properties = mock(PropertyDao.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    @BeforeEach void fixture() {
        ReflectionTestUtils.setField(manager, "propertyDao", properties);
        ReflectionTestUtils.setField(manager, "securityInfoManager", security);
        when(security.hasPrivilege(info, "_lab", "r", null)).thenReturn(true);
    }
    @Test void shouldDefaultOfferToOff_whenPreferenceIsAbsent() {
        assertThat(manager.isHl7OfferFileForOthers(info, "100001")).isFalse();
    }
    @Test void shouldOfferFiling_whenProviderExplicitlyOptsIn() {
        var property = new Property(); property.setValue("true");
        when(properties.findByNameAndProvider(Property.PROPERTY_KEY.hl7_offer_file_for_others, "100001"))
                .thenReturn(List.of(property));
        assertThat(manager.isHl7OfferFileForOthers(info, "100001")).isTrue();
    }
    @Test void shouldDefaultConsentToOff_whenPreferenceIsAbsent() {
        assertThat(manager.isHl7AllowOthersFileForYou(info, "100001")).isFalse();
    }
}
