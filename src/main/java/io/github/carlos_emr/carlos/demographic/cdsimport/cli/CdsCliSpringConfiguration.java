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
package io.github.carlos_emr.carlos.demographic.cdsimport.cli;

import io.github.carlos_emr.carlos.commn.dao.AllergyDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.CdsLaboratoryResultDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicPharmacyDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DocumentDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DrugDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.PreventionDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalContactDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.ProviderFacilityDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.SecurityDaoImpl;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAOImpl;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAOImpl;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAOImpl;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.CdsLegacyProjectionDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDaoImpl;
import io.github.carlos_emr.carlos.daos.security.SecobjprivilegeDaoImpl;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDaoImpl;
import io.github.carlos_emr.carlos.db.FlywaySchemaValidator;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionInitializer;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.time.Duration;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Dedicated one-shot context: shared import services/JPA/security, with no servlet, feeds, fax or scheduler contexts. */
@Configuration(proxyBeanMethods = false)
@Profile("cds-import-cli")
@EnableTransactionManagement(proxyTargetClass=true)
@ComponentScan(basePackages = "io.github.carlos_emr.carlos.demographic.cdsimport.service")
@ComponentScan(basePackages = "io.github.carlos_emr.carlos.commn.dao", useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "io\\.github\\.carlos_emr\\.carlos\\.commn\\.dao\\.Cds(Import|Upload).*Dao"))
@Import({DrugDaoImpl.class, PartialDateDaoImpl.class, PreventionDaoImpl.class,
        PreventionExtDaoImpl.class, SecObjPrivilegeDaoImpl.class, SecuserroleDaoImpl.class,
        SecobjprivilegeDaoImpl.class, SecurityInfoManagerImpl.class, ProviderDaoImpl.class,
        CdsLegacyProjectionDao.class, ContactDaoImpl.class, DemographicContactDaoImpl.class,
        MeasurementDaoImpl.class, MeasurementTypeDaoImpl.class, MeasurementsExtDaoImpl.class,
        DemographicDaoImpl.class, DemographicCustDaoImpl.class, DemographicExtDaoImpl.class, AdmissionDaoImpl.class,
        ProfessionalContactDaoImpl.class, PharmacyInfoDaoImpl.class, DemographicPharmacyDaoImpl.class,
        OscarAppointmentDaoImpl.class, DocumentDaoImpl.class, CtlDocumentDaoImpl.class,
        Hl7TextMessageDaoImpl.class, Hl7TextInfoDaoImpl.class, PatientLabRoutingDaoImpl.class, CdsLaboratoryResultDao.class,
        SecurityDaoImpl.class, ProviderFacilityDaoImpl.class})
public class CdsCliSpringConfiguration {
    @Bean(name="allergyDao")
    public AllergyDaoImpl allergyDao() { return new AllergyDaoImpl(); }

    // Match the established WAR qualifiers without registering its duplicate legacy aliases.
    @Bean(name="CaseManagementNoteDAO")
    public CaseManagementNoteDAO caseManagementNoteDao() { return new CaseManagementNoteDAOImpl(); }

    @Bean(name="CaseManagementIssueDAO")
    public CaseManagementIssueDAO caseManagementIssueDao() { return new CaseManagementIssueDAOImpl(); }

    @Bean(name="IssueDAO")
    public IssueDAO issueDao() { return new IssueDAOImpl(); }

    @Bean(destroyMethod = "close")
    public OscarTrackingBasicDataSource dataSource(@Qualifier("cdsImportProperties") Properties properties) {
        OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource();
        source.setBoundedCdsConnections(true);
        source.setDriverClassName(required(properties, "db_driver"));
        String metadata = properties.getProperty("db_default_use_information_schema");
        if (metadata != null && !metadata.isBlank()) source.setDefaultUseInformationSchema(
                org.springframework.core.convert.support.DefaultConversionService.getSharedInstance().convert(metadata, Boolean.class));
        source.setUrl(required(properties, "db_uri") + required(properties, "db_name"));
        source.setUsername(required(properties, "db_username"));
        source.setPassword(required(properties, "db_password"));
        source.setDefaultAutoCommit(true);
        source.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        // This dedicated context changes transaction state through JDBC setters,
        // including Spring/Hibernate. Raw SQL must not change that cached state.
        source.addConnectionProperty("useLocalSessionState", "true");
        // This pool serves only imports. Establish an additive strict baseline once
        // per physical connection; guarded units still verify and restore sessions.
        var initialization=new java.util.ArrayList<String>();
        if (source.getConnectionInitSqls()!=null) initialization.addAll(source.getConnectionInitSqls());
        initialization.add("SET SESSION sql_mode=IF(FIND_IN_SET('STRICT_ALL_TABLES',@@SESSION.sql_mode)>0,"
                + "@@SESSION.sql_mode,CONCAT_WS(',',NULLIF(@@SESSION.sql_mode,''),'STRICT_ALL_TABLES'))");
        source.setConnectionInitSqls(initialization);
        // CLI has one foreground worker and a bounded progress reader, never the
        // web server's full pool or abandoned-transaction recovery heuristics.
        source.setMaxTotal(4); source.setMaxIdle(2); source.setMinIdle(0);
        source.setMaxWait(Duration.ofSeconds(30));
        source.setValidationQuery("SELECT 1"); source.setValidationQueryTimeout(Duration.ofSeconds(10));
        source.setTestOnBorrow(true); source.setPoolPreparedStatements(false);
        return source;
    }

    @Bean
    public FlywaySchemaValidator cdsCliSchemaGate(OscarTrackingBasicDataSource source,
            @Qualifier("cdsImportProperties") Properties properties) {
        // Validation only: this command never applies an implicit migration.
        return new FlywaySchemaValidator(source, "validate", properties.getProperty("carlos.flyway.locations", ""),
                properties.getProperty("billregion", "ON"));
    }

    @Bean(initMethod = "initialize")
    @DependsOn("cdsCliSchemaGate")
    public CdsJdbcAdmissionInitializer cdsCliAdmissionInitializer(OscarTrackingBasicDataSource source,
            @Qualifier("cdsImportProperties") Properties properties) {
        // This dedicated runtime always understands the guard, including status
        // queries after admission is disabled. It never starts ordinary writers.
        return new CdsJdbcAdmissionInitializer(source, true, "validate");
    }

    @Bean(name = "entityManagerFactory")
    @DependsOn("cdsCliAdmissionInitializer")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(OscarTrackingBasicDataSource source,
            @Qualifier("cdsImportProperties") Properties properties) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source); factory.setPersistenceUnitName("entityManagerFactory");
        // Use the same association-complete model packages as spring_jpa.xml.
        factory.setPackagesToScan("io.github.carlos_emr.carlos.commn.model", "io.github.carlos_emr.carlos.model",
                "io.github.carlos_emr.carlos.PMmodule.model", "io.github.carlos_emr.carlos.casemgmt.model",
                "io.github.carlos_emr.carlos.entities", "io.github.carlos_emr.carlos.billing",
                "io.github.carlos_emr.carlos.billings", "io.github.carlos_emr.carlos.hospitalReportManager.model",
                "io.github.carlos_emr.carlos.form.model", "io.github.carlos_emr.carlos.decisionSupport.model");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties jpa = new Properties();
        jpa.setProperty("hibernate.dialect", properties.getProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect"));
        jpa.setProperty("hibernate.show_sql", "false"); jpa.setProperty("hibernate.generate_statistics", "false");
        jpa.setProperty("hibernate.cache.use_second_level_cache", "false");
        jpa.setProperty("hibernate.cache.use_query_cache", "false");
        jpa.setProperty("hibernate.hbm2ddl.auto", "none");
        jpa.setProperty("hibernate.max_fetch_depth", "3");
        factory.setJpaProperties(jpa); return factory;
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("CDS_DATABASE_CONFIGURATION_REQUIRED");
        return value;
    }
}
