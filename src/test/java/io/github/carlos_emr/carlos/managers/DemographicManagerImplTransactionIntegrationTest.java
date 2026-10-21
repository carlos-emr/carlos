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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Real-transaction integration coverage for {@link DemographicManagerImpl#createDemographic}: the
 * patient, its program admission and its extensions must be saved all together or not at all.
 *
 * <p>Each test method suspends the surrounding {@code CarlosTestBase} test transaction
 * ({@code Propagation.NOT_SUPPORTED}, as in {@code FaxManagerImplTransactionIntegrationTest}) so
 * the manager's own transaction genuinely commits or rolls back.</p>
 *
 * <p>The shared context's {@code demographicManager} is a mock, so the test wraps a real
 * {@code DemographicManagerImpl} in a proxy that honours {@code @Transactional} the same way
 * production does. If the annotation is missing, the proxy adds no transaction and the rollback
 * test fails.</p>
 *
 * <p>The H2 schema lacks production's {@code uk_demo_ext_single_value} index, so the rollback
 * test adds an equivalent test-only constraint for its duration and sends two extras with the
 * same key (the reported bug). The test seeds its own program because
 * {@code admission.program_id} has a foreign key.</p>
 */
@DisplayName("DemographicManagerImpl transactional integration tests")
@Tag("integration")
@Tag("manager")
class DemographicManagerImplTransactionIntegrationTest extends CarlosTestBase {

    private static final String PROVIDER_NO = "999998";
    /** Test-only copy of production's per-patient key index, which the H2 schema lacks. */
    private static final String TEST_UNIQUE_KEY_CONSTRAINT = "test_uk_demo_ext";
    private static final String DUPLICATE_KEY = "demo_cell";

    @Autowired
    private DemographicDao demographicDao;
    @Autowired
    private DemographicExtDao demographicExtDao;
    @Autowired
    private AdmissionDao admissionDao;
    @Autowired
    private SecurityInfoManager securityInfoManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DataSource dataSource;

    private TransactionTemplate transactionTemplate;
    private DemographicManager manager;
    private Demographic demographicUnderTest;
    private Integer programId;

    @BeforeEach
    void setUpManager() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        manager = transactionalManager();
        programId = seedProgram();
    }

    @AfterEach
    void removeCreatedRows() {
        dropPerPatientKeyConstraint();
        Integer demographicNo = demographicUnderTest == null ? null : demographicUnderTest.getDemographicNo();
        // Children first, in their own committed transaction, so the parent delete below sees them gone.
        transactionTemplate.executeWithoutResult(status -> {
            if (demographicNo != null) {
                demographicExtDao.getDemographicExtByDemographicNo(demographicNo)
                        .forEach(ext -> demographicExtDao.removeDemographicExt(ext.getId()));
                admissionDao.getAdmissions(demographicNo)
                        .forEach(admission -> admissionDao.remove(admission.getId()));
            }
        });
        transactionTemplate.executeWithoutResult(status -> {
            if (demographicNo != null) {
                // DemographicDao has no delete method.
                new JdbcTemplate(dataSource).update("DELETE FROM demographic WHERE demographic_no = ?", demographicNo);
            }
            Program seededProgram = programId == null ? null : hibernateTemplate.get(Program.class, programId);
            if (seededProgram != null) {
                hibernateTemplate.delete(seededProgram);
                hibernateTemplate.flush();
            }
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("should keep the patient, admission and extensions when every write succeeds")
    void shouldKeepWholePatient_whenAllWritesSucceed() {
        demographicUnderTest = newSamplePatient();
        demographicUnderTest.setExtras(new DemographicExt[] { extension(DUPLICATE_KEY, "555-0100") });

        manager.createDemographic(loggedInProvider(), demographicUnderTest, programId);

        Integer demographicNo = demographicUnderTest.getDemographicNo();
        assertThat(demographicExists(demographicNo)).as("demographic row").isTrue();
        assertThat(admissionCount(demographicNo)).as("admission rows").isEqualTo(1);
        assertThat(extensionCount(demographicNo)).as("demographicExt rows").isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("should roll back the patient, admission and extensions when an extension fails to save")
    void shouldRollBackWholePatient_whenAnExtensionFailsToSave() {
        demographicUnderTest = newSamplePatient();
        demographicUnderTest.setExtras(new DemographicExt[] {
            extension(DUPLICATE_KEY, "555-0100"),
            extension(DUPLICATE_KEY, "555-0199")
        });

        addPerPatientKeyConstraint();
        try {
            assertThatThrownBy(() -> manager.createDemographic(loggedInProvider(), demographicUnderTest, programId))
                .isInstanceOf(RuntimeException.class)
                .satisfies(e -> assertThat(rootCauseMessage(e))
                    .as("fails on the duplicate key, not on something else")
                    .containsIgnoringCase(TEST_UNIQUE_KEY_CONSTRAINT));
        } finally {
            dropPerPatientKeyConstraint();
        }

        Integer demographicNo = demographicUnderTest.getDemographicNo();
        assertThat(demographicNo).as("the patient insert ran before the failure").isNotNull();
        assertThat(demographicExists(demographicNo)).as("demographic row").isFalse();
        assertThat(admissionCount(demographicNo)).as("admission rows").isZero();
        assertThat(extensionCount(demographicNo)).as("demographicExt rows").isZero();
    }

    /**
     * A real DemographicManagerImpl with only the collaborators createDemographic uses, wrapped in
     * the same annotation-driven transaction advice production applies.
     */
    private DemographicManager transactionalManager() {
        DemographicManagerImpl target = new DemographicManagerImpl();
        ReflectionTestUtils.setField(target, "demographicDao", demographicDao);
        ReflectionTestUtils.setField(target, "demographicExtDao", demographicExtDao);
        ReflectionTestUtils.setField(target, "admissionDao", admissionDao);
        ReflectionTestUtils.setField(target, "securityInfoManager", securityInfoManager);

        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (DemographicManager) proxyFactory.getProxy();
    }

    private Integer seedProgram() {
        return transactionTemplate.execute(status -> {
            Program program = new Program();
            program.setName("FAKE-TX program");
            program.setType("community");
            program.setProgramStatus("active");
            program.setFacilityId(0);
            hibernateTemplate.save(program);
            // Flush so the identity-generated id is assigned.
            hibernateTemplate.flush();
            return program.getId();
        });
    }

    private void addPerPatientKeyConstraint() {
        new JdbcTemplate(dataSource).execute(
                "ALTER TABLE demographicExt ADD CONSTRAINT " + TEST_UNIQUE_KEY_CONSTRAINT
                        + " UNIQUE (demographic_no, key_val)");
    }

    private void dropPerPatientKeyConstraint() {
        new JdbcTemplate(dataSource).execute(
                "ALTER TABLE demographicExt DROP CONSTRAINT IF EXISTS " + TEST_UNIQUE_KEY_CONSTRAINT);
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root != null && root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root == null ? null : root.getMessage();
    }

    private static LoggedInInfo loggedInProvider() {
        Provider provider = new Provider();
        provider.setProviderNo(PROVIDER_NO);
        Security security = new Security();
        security.setProviderNo(PROVIDER_NO);
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setLoggedInSecurity(security);
        return loggedInInfo;
    }

    private static Demographic newSamplePatient() {
        Demographic demographic = new Demographic();
        demographic.setFirstName("SAMPLE");
        demographic.setLastName("FAKE-TX");
        demographic.setFormattedDob("1980-01-15");
        demographic.setSex("M");
        return demographic;
    }

    private static DemographicExt extension(String key, String value) {
        DemographicExt ext = new DemographicExt();
        ext.setKey(key);
        ext.setValue(value);
        ext.setProviderNo(PROVIDER_NO);
        ext.setDateCreated(new Date());
        return ext;
    }

    private boolean demographicExists(Integer demographicNo) {
        return demographicDao.getDemographicById(demographicNo) != null;
    }

    private int admissionCount(Integer demographicNo) {
        return admissionDao.getAdmissions(demographicNo).size();
    }

    private int extensionCount(Integer demographicNo) {
        return demographicExtDao.getDemographicExtByDemographicNo(demographicNo).size();
    }
}
