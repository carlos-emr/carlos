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
package io.github.carlos_emr.carlos.demographic.cdsexport.cli;

import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessPersistence;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.*;
import io.github.carlos_emr.carlos.commn.dao.CdsExportRunDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportEventDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.ProviderFacilityDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDaoImpl;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDaoImpl;
import io.github.carlos_emr.carlos.daos.security.SecobjprivilegeDaoImpl;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDaoImpl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.db.FlywaySchemaValidator;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionInitializer;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Dedicated foreground/status context; dispatcher starts are never invoked and both worker properties are forced false by bootstrap. */
@Configuration(proxyBeanMethods = false)
@Profile("cds-export-cli")
@EnableTransactionManagement(proxyTargetClass = true)
@org.springframework.context.annotation.EnableAspectJAutoProxy(proxyTargetClass = true)
@Import({CdsExportImportOptionsSourceReader.class, CdsExportPersonalContactProjectionService.class, CdsExportPharmacyProjectionService.class, io.github.carlos_emr.carlos.commn.dao.ContactDaoImpl.class, io.github.carlos_emr.carlos.commn.dao.DemographicContactDaoImpl.class, io.github.carlos_emr.carlos.commn.dao.DemographicPharmacyDaoImpl.class, io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDaoImpl.class, CdsExportContactSourceReader.class, CdsExportDemographicContactSourceReader.class, CdsExportPharmacyLinkSourceReader.class, CdsExportPharmacySourceReader.class, CdsExportDemographicPhoneProjectionService.class, CdsExportEnrolmentProjectionService.class, CdsExportClinicalZoneService.class, CdsExportClinicalZoneSourceReader.class, CdsExportAppointmentOccurrenceService.class, CdsExportImmunizationOccurrenceService.class, CdsExportImmunizationCaptureValidator.class, CdsExportImmunizationProjectionService.class, io.github.carlos_emr.carlos.commn.dao.PreventionExtDaoImpl.class, CdsExportImmunizationSourceReader.class, CdsExportImmunizationExtSourceReader.class, CdsExportImmunizationPrecisionSourceReader.class, CdsExportPreventionConfigurationLoader.class, CdsExportPreventionSnapshotService.class, CdsExportPreventionConfigurationSourceReader.class, io.github.carlos_emr.carlos.commn.dao.PreventionDaoImpl.class, CdsExportPreventionAccessService.class, CdsExportPreventionScopeSourceReader.class, CdsExportPreventionClassificationService.class, CdsExportPreventionConfigurationService.class, io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDaoImpl.class, io.github.carlos_emr.carlos.commn.dao.CVCMappingDaoImpl.class, CdsExportCvcImmunizationSourceReader.class, CdsExportCvcMappingSourceReader.class, io.github.carlos_emr.carlos.demographic.cds.security.CdsFreshAuthorizationReadAspect.class, CdsExportRunDao.class, CdsExportEventDao.class, io.github.carlos_emr.carlos.commn.dao.DemographicDaoImpl.class, SecurityDaoImpl.class, ProviderFacilityDaoImpl.class,
        ProviderDaoImpl.class, SecObjPrivilegeDaoImpl.class, SecobjprivilegeDaoImpl.class, SecuserroleDaoImpl.class, SecurityInfoManagerImpl.class,
        CdsExportConfigurationService.class, CdsExportAccessService.class, CdsExportLedgerService.class,
        CdsExportEngineRegistry.class, CdsExportExecutionService.class, CdsExportService.class, CdsExportStagingFactory.class,
        CdsExportSelectionFileService.class, CdsExportSelectionService.class, CdsExportNativeCaptureService.class, CdsExportDemographicSourceReader.class, CdsExportCohortService.class,
        io.github.carlos_emr.carlos.commn.dao.DemographicMergedDaoImpl.class, CdsExportDemographicMergeSourceReader.class,
        io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDaoImpl.class, CdsExportDemographicArchiveSourceReader.class,
        io.github.carlos_emr.carlos.commn.dao.DemographicExtDaoImpl.class, io.github.carlos_emr.carlos.commn.dao.DemographicCustDaoImpl.class,
        CdsExportDemographicExtSourceReader.class, CdsExportDemographicCustSourceReader.class, CdsExportAllergySourceReader.class, CdsExportMedicationSourceReader.class, CdsExportMedicationProjectionService.class,
        io.github.carlos_emr.carlos.commn.dao.PartialDateDaoImpl.class, CdsExportPartialDateSourceReader.class, CdsExportAllergyProjectionService.class,
        io.github.carlos_emr.carlos.commn.dao.CdsImportFieldDao.class, io.github.carlos_emr.carlos.commn.dao.CdsImportUnitDao.class,
        io.github.carlos_emr.carlos.commn.dao.CdsImportPreparationDao.class, io.github.carlos_emr.carlos.commn.dao.CdsImportDependencyDao.class,
        io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao.class, CdsExportRetainedFieldSourceReader.class,
        io.github.carlos_emr.carlos.commn.dao.CdsImportFileDao.class, io.github.carlos_emr.carlos.commn.dao.CdsImportFileReferenceDao.class,
        CdsExportRetainedFileSourceReader.class, CdsExportRetainedFileReferenceSourceReader.class,
        CdsExportRetainedUnitSourceReader.class, CdsExportRetainedPreparationSourceReader.class,
        CdsExportRetainedDependencySourceReader.class, CdsExportRetainedPatientSourceReader.class, CdsExportRetainedPayloadService.class,
        CdsExportRetainedValuesService.class, CdsExportRetainedPreparationReader.class, CdsExportClinicalOccurrenceService.class, CdsExportDemographicOccurrenceService.class,
        io.github.carlos_emr.carlos.commn.dao.CdsImportRunDao.class, CdsExportRetainedRunSourceReader.class, CdsExportRetainedIndexService.class,
        io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDaoImpl.class, CdsExportAppointmentSourceReader.class,
        io.github.carlos_emr.carlos.commn.dao.AppointmentStatusDaoImpl.class, CdsExportAppointmentStatusSourceReader.class, CdsExportPublicProviderSourceReader.class, CdsExportAppointmentProjectionService.class, CdsExportNativeRowReceiptReader.class, CdsExportRetainedReceiptVerificationService.class,
        io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDaoImpl.class, CdsExportAppointmentArchiveSourceReader.class, CdsExportDemographicProjectionService.class})
public class CdsExportCliSpringConfiguration {
    @Bean public io.github.carlos_emr.carlos.commn.dao.DrugDaoImpl drugDao() { return new io.github.carlos_emr.carlos.commn.dao.DrugDaoImpl(); }
    @Bean public io.github.carlos_emr.carlos.commn.dao.AllergyDaoImpl allergyDao() { return new io.github.carlos_emr.carlos.commn.dao.AllergyDaoImpl(); }
    @Bean public CacheManager cacheManager() { return new NoOpCacheManager(); }
    @Bean(destroyMethod = "close") public OscarTrackingBasicDataSource dataSource(@Qualifier("cdsExportProperties") Properties properties) {
        return CdsProcessPersistence.dataSource(properties);
    }
    @Bean public FlywaySchemaValidator cdsCliSchemaGate(OscarTrackingBasicDataSource source, @Qualifier("cdsExportProperties") Properties properties) {
        return CdsProcessPersistence.schemaGate(source, properties);
    }
    @Bean(initMethod = "initialize") @DependsOn("cdsCliSchemaGate")
    public CdsJdbcAdmissionInitializer cdsCliAdmissionInitializer(OscarTrackingBasicDataSource source) {
        return CdsProcessPersistence.admissionInitializer(source);
    }
    @Bean(name = "entityManagerFactory") @DependsOn("cdsCliAdmissionInitializer")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(OscarTrackingBasicDataSource source, @Qualifier("cdsExportProperties") Properties properties) {
        return CdsProcessPersistence.entityManagerFactory(source, properties);
    }
    @Bean public PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return CdsProcessPersistence.transactionManager(factory); }
}
