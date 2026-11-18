/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import jakarta.ws.rs.BadRequestException;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class DemographicWriteProfileUnitTest {
    @Test
    void shouldPreserveHiddenValuesByDefault_andRequireExplicitLegacyNullClears() {
        assertThat(DemographicWriteProfile.parse(null)).isEqualTo(DemographicWriteProfile.PRESERVE_HIDDEN_IDENTIFIERS);
        assertThat(DemographicWriteProfile.parse(List.of("preserve-hidden-identifiers")))
                .isEqualTo(DemographicWriteProfile.PRESERVE_HIDDEN_IDENTIFIERS);
        assertThat(DemographicWriteProfile.parse(List.of("legacy-null-identifiers")))
                .isEqualTo(DemographicWriteProfile.LEGACY_NULL_IDENTIFIERS);
    }

    @Test
    void shouldRejectInvalidOrAmbiguousSelections_withoutEchoingInput() {
        for (List<String> values : List.of(List.<String>of(), List.of(""), List.of("FAKE-PRIVATE-CLIENT"),
                List.of("legacy-null-identifiers", "preserve-hidden-identifiers"),
                List.of("legacy-null-identifiers,preserve-hidden-identifiers"), java.util.Arrays.asList((String) null))) {
            assertThatThrownBy(() -> DemographicWriteProfile.parse(values))
                    .isInstanceOf(BadRequestException.class).hasMessageNotContaining("FAKE-PRIVATE-CLIENT");
        }
    }

    @Test
    void shouldRefuseAnUnsupportedExtensionDaoFreshnessGuarantee_insteadOfSilentlyUsingCachedState() {
        var dao = mock(DemographicDao.class);
        when(dao.findFreshForUpdate(42)).thenCallRealMethod();
        assertThatThrownBy(() -> dao.findFreshForUpdate(42)).isInstanceOf(UnsupportedOperationException.class);
        verify(dao, never()).findForUpdate(42);
    }
    @Test
    void shouldDistinguishContentionFromOtherStorageOrConfigurationFailures() {
        assertThat(DemographicWriteConflict.isConflict(new java.sql.SQLException("private", "40001", 1213))).isTrue();
        assertThat(DemographicWriteConflict.isConflict(new IllegalStateException(
                new java.sql.SQLException("private", "HY000", 1020)))).isTrue();
        assertThat(DemographicWriteConflict.isConflict(new java.sql.SQLException("private", "42000", 1064))).isFalse();
        assertThat(DemographicWriteConflict.isConflict(new UnsupportedOperationException("private"))).isFalse();
    }

    @Test
    void shouldRegisterProfileAcknowledgement_onBothApplicationRestServersOnly() throws Exception {
        int protectedServers = 0;
        var factory = io.github.carlos_emr.carlos.utility.XmlUtils.createSecureDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        for (String resource : List.of("/spring_ws.xml", "/applicationContextREST.xml")) {
            try (var input = getClass().getResourceAsStream(resource)) {
                assertThat(input).isNotNull();
                var document = factory.newDocumentBuilder().parse(input);
                var servers = document.getElementsByTagNameNS("http://cxf.apache.org/jaxrs", "server");
                for (int index = 0; index < servers.getLength(); index++) {
                    var server = (org.w3c.dom.Element) servers.item(index);
                    var beans = server.getElementsByTagNameNS("http://www.springframework.org/schema/beans", "bean");
                    int filters = 0;
                    for (int bean = 0; bean < beans.getLength(); bean++) {
                        if (((org.w3c.dom.Element) beans.item(bean)).getAttribute("class")
                                .equals(DemographicWriteProfileResponseFilter.class.getName())) filters++;
                    }
                    boolean application = List.of("/rs", "/services").contains(server.getAttribute("address"));
                    assertThat(filters).as("%s server %s", resource, server.getAttribute("id")).isEqualTo(application ? 1 : 0);
                    if (application) protectedServers++;
                }
            }
        }
        assertThat(protectedServers).isEqualTo(2);
    }

}
