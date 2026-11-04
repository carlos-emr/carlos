/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.upload;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

/** Real JDBC identifiers must select the intended provider, even when another provider has the same OHIP number. */
@Tag("integration")
@Isolated("Temporarily configures the global lab matching properties")
class MessageUploaderProviderLookupIntegrationTest extends CarlosTestBase {
    private static final int REPORT = 9834410;
    private static final String INTENDED = "983441";
    private static final String OHIP_OWNER = "983442";
    private static final String IDENTIFIER = "983443";
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    @Autowired private ProviderLabRoutingDao routes;
    private String originalRegion;
    private String originalOtherIdMatching;

    @BeforeEach
    void setUpProviders() {
        CarlosProperties properties = CarlosProperties.getInstance();
        originalRegion = properties.getProperty("billregion");
        originalOtherIdMatching = properties.getProperty("lab.other_id_matching");
        properties.setProperty("billregion", "ON");
        properties.setProperty("lab.other_id_matching", "");
        em.createNativeQuery("CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)").executeUpdate();
        Provider intended = new Provider(INTENDED, "Synthetic", "doctor", "F", "", "Intended");
        intended.setStatus("1");
        intended.setOhipNo("983444");
        intended.setPractitionerNo(IDENTIFIER);
        intended.setHsoNo(IDENTIFIER);
        em.persist(intended);
        Provider other = new Provider(OHIP_OWNER, "Synthetic", "doctor", "F", "", "OhipOnly");
        other.setStatus("1");
        other.setOhipNo(IDENTIFIER);
        em.persist(other);
        em.flush();
    }

    @AfterEach
    void restoreProperties() {
        restoreProperty("billregion", originalRegion);
        restoreProperty("lab.other_id_matching", originalOtherIdMatching);
    }

    private void restoreProperty(String name, String value) {
        if (value == null) CarlosProperties.getInstance().remove(name);
        else CarlosProperties.getInstance().setProperty(name, value);
    }

    @ParameterizedTest
    @CsvSource({"MEDITECH,practitionerNo", "ExcellerisON,practitionerNo", "IHAPOI,hso_no"})
    void shouldRouteToSourceIdentifierOwner_whenOhipBelongsToAnotherProvider(String source, String column) throws Exception {
        routeProviders(source, column);
        assertThat(routes.findAllLabRoutingByIdandType(REPORT, "HL7"))
                .extracting(ProviderLabRoutingModel::getProviderNo).containsExactly(INTENDED);
    }

    @Test
    void shouldKeepDefaultOhipLookup_whenSearchColumnIsNotAllowlisted() throws Exception {
        routeProviders("PATHL7", "provider_no OR 1=1 --");
        assertThat(routes.findAllLabRoutingByIdandType(REPORT, "HL7"))
                .extracting(ProviderLabRoutingModel::getProviderNo).containsExactly(OHIP_OWNER);
    }

    private void routeProviders(String source, String column) throws Exception {
        var method = MessageUploader.class.getDeclaredMethod("providerRouteReport", String.class, ArrayList.class,
                Connection.class, String.class, String.class, String.class, Integer.class, boolean.class, String.class);
        method.setAccessible(true);
        try (Connection connection = LegacyJdbcQuery.getConnection()) {
            method.invoke(null, Integer.toString(REPORT), new ArrayList<>(List.of(IDENTIFIER)), connection,
                    null, source, column, null, false, null);
        }
        em.flush();
        em.clear();
    }
}
