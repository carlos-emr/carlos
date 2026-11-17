/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.ws.rs.core.Response;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.webserv.rest.to.model.FormListTo1;
import io.github.carlos_emr.carlos.webserv.rest.util.SecurityExceptionMapper;

/**
 * Exercises CXF path binding, real patient-specific privilege resolution, and the real
 * encounter-form SQL lookup against isolated H2 data. Only the principal, role/privilege
 * DAO results, and form registry are fixtures; authorization and form retrieval are not mocked.
 *
 * @since 2026-10-19
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@Tag("read")
@DisplayName("Completed encounter forms patient authorization")
class FormsServicePatientAuthorizationEndpointTest extends CarlosRestTestBase {

    private static final String FORMS_OBJECT = "_newCasemgmt.forms";

    @Mock
    private FormsManager mockFormsManager;

    @Mock
    private SecuserroleDao mockRoleDao;

    @Mock
    private SecObjPrivilegeDao mockPrivilegeDao;

    private Connection database;

    @Override
    protected Object getServiceBean() {
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("101");
        when(mockLoggedInInfo.getSession()).thenReturn(mockServletRequest.getSession());
        Secuserrole role = new Secuserrole();
        role.setProviderNo("101");
        role.setRoleName("doctor");
        when(mockRoleDao.findActiveByProviderNo("101")).thenReturn(List.of(role));

        registerMock(SecObjPrivilegeDao.class, mockPrivilegeDao);
        createAndRegisterMock(EncounterFormDao.class);
        List<SecObjPrivilege> privileges = List.of(
                privilege(FORMS_OBJECT, "r"), privilege(FORMS_OBJECT + "$1", "o"));
        when(mockPrivilegeDao.findByObjectNames(any())).thenAnswer(invocation -> {
            Collection<String> names = invocation.getArgument(0);
            return privileges.stream().filter(p -> names.contains(p.getId().getObjectName())).toList();
        });

        SecurityInfoManagerImpl security = new SecurityInfoManagerImpl();
        injectDependency(security, "secUserRoleDao", mockRoleDao);
        FormsService service = new FormsService();
        injectDependency(service, "formsManager", mockFormsManager);
        injectDependency(service, "securityInfoManager", security);
        return service;
    }

    @Override
    protected List<Object> additionalServerProviders() {
        return List.of(new SecurityExceptionMapper());
    }

    @BeforeEach
    void setUpFormDatabase() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:forms-authz-" + UUID.randomUUID());
        database = dataSource.getConnection();
        registerMock(DataSource.class, dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE formGrowth0_36 (ID INT, demographic_no INT, formCreated DATE, formEdited TIMESTAMP)");
        jdbc.update("INSERT INTO formGrowth0_36 VALUES (?, ?, CURRENT_DATE, CURRENT_TIMESTAMP)", 11, 1);
        jdbc.update("INSERT INTO formGrowth0_36 VALUES (?, ?, CURRENT_DATE, CURRENT_TIMESTAMP)", 22, 2);

        EncounterForm form = new EncounterForm();
        form.setFormName("Synthetic encounter form");
        form.setFormTable("formGrowth0_36");
        when(mockFormsManager.getAllEncounterForms()).thenReturn(new ArrayList<>(List.of(form)));
    }

    @AfterEach
    void closeFormDatabase() throws SQLException {
        if (database != null) {
            database.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "01", "001", "+1"})
    @DisplayName("should deny every numeric spelling of a patient with a no-rights rule")
    void shouldDenyCompletedForms_whenPatientSpecificRuleOverridesGeneralRead(String patientId) {
        try (Response response = request().path("/forms/" + patientId + "/completedEncounterForms").get()) {
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.readEntity(String.class)).isEmpty();
        }
        verifyNoInteractions(mockFormsManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "02", "002"})
    @DisplayName("should return only the authorized patient's forms for canonical and leading-zero paths")
    void shouldReturnCompletedForms_whenPatientHasGeneralReadWithoutAnOverride(String patientId) {
        try (Response response = request().path("/forms/" + patientId + "/completedEncounterForms").get()) {
            assertThat(response.getStatus()).isEqualTo(200);
            FormListTo1 forms = response.readEntity(FormListTo1.class);
            assertThat(forms.getList()).singleElement().satisfies(form -> {
                assertThat(form.getDemographicNo()).isEqualTo(2);
                assertThat(form.getFormId()).isEqualTo(22);
                assertThat(form.getName()).isEqualTo("Synthetic encounter form");
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "2147483648"})
    @DisplayName("should reject malformed or overflowing patient IDs before reading forms")
    void shouldRejectCompletedForms_whenPatientIdCannotBindToAnInteger(String patientId) {
        try (Response response = request().path("/forms/" + patientId + "/completedEncounterForms").get()) {
            assertThat(response.getStatus()).isEqualTo(404);
        }
        verifyNoInteractions(mockFormsManager, mockRoleDao, mockPrivilegeDao);
    }

    private static SecObjPrivilege privilege(String objectName, String right) {
        SecObjPrivilege privilege = new SecObjPrivilege();
        privilege.setId(new SecObjPrivilegePrimaryKey("doctor", objectName));
        privilege.setPrivilege(right);
        privilege.setPriority(0);
        return privilege;
    }
}
