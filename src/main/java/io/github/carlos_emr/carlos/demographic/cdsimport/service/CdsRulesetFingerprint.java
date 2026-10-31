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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.utility.CdsImportKeys;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/** Identifies the actual deployed import implementation, independently of a manually bumped version label. */
public record CdsRulesetFingerprint(String sha256) {
    private static final String BASE = "io/github/carlos_emr/carlos/";
    private static final String ANCHOR =
            BASE + "demographic/cdsimport/service/CdsImportJobService.class";
    private static final String[] NATIVE = {
        "commn/dto/CdsProgramContextDto",
        "commn/dto/CdsProgramMembershipDto",
        "commn/dto/CdsImportControlDto",
        "commn/dto/CdsImportPatientOutcomeDto",
        "commn/dto/CdsImportDiagnosticSummaryDto",
        "commn/dto/CdsImportReviewSummaryDto",
        "commn/dto/CdsImportReviewOriginDto",
        "commn/dto/CdsImportReviewProvenanceDto",
        "commn/dto/CdsImportJobBindingDto",
        "commn/dto/CdsImportReportEntryDto",

        "utility/CdsImportKeys",
        "utility/CdsJdbcConnectionTimeouts",
        "commn/model/Drug",
        "commn/model/Allergy",
        "commn/model/Prevention",
        "commn/model/PartialDate",
        "commn/model/Demographic",
        "commn/dao/DemographicDao",
        "commn/dao/DemographicDaoImpl",
        "PMmodule/model/Program",
        "PMmodule/model/ProgramProvider",
        "commn/model/ProviderDefaultProgram",
        "model/security/Secrole",
        "commn/model/Facility",
        "commn/model/Appointment",
        "commn/dao/OscarAppointmentDao",
        "commn/dao/OscarAppointmentDaoImpl",
        "commn/model/Document",
        "commn/dao/DocumentDao",
        "commn/dao/DocumentDaoImpl",
        "commn/model/CtlDocument",
        "commn/dao/CtlDocumentDao",
        "commn/dao/CtlDocumentDaoImpl",
        "commn/model/CtlDocumentPK",
        "commn/model/Measurement",
        "commn/model/MeasurementType",
        "commn/model/MeasurementsExt",
        "commn/model/Contact",
        "commn/model/DemographicCust",
        "commn/model/PharmacyInfo",
        "commn/model/DemographicPharmacy",
        "commn/model/ProfessionalContact",
        "commn/dao/ProfessionalContactDao",
        "commn/dao/ProfessionalContactDaoImpl",
        "demographic/pageUtil/SpokenLangProperties",
        "commn/dao/DemographicCustDao",
        "commn/dao/DemographicCustDaoImpl",
        "commn/dao/PharmacyInfoDao",
        "commn/dao/PharmacyInfoDaoImpl",
        "commn/dao/DemographicPharmacyDao",
        "commn/dao/DemographicPharmacyDaoImpl",
        "commn/model/PreventionExt",
        "commn/model/DemographicContact",
        "commn/model/DemographicExt",
        "commn/dao/DemographicExtDao",
        "commn/dao/DemographicExtDaoImpl",
        "commn/model/Admission",
        "commn/dao/AdmissionDao",
        "commn/dao/AdmissionDaoImpl",
        "commn/model/Hl7TextMessage",
        "commn/dao/Hl7TextMessageDao",
        "commn/dao/Hl7TextMessageDaoImpl",
        "commn/model/Hl7TextInfo",
        "commn/dao/Hl7TextInfoDao",
        "commn/dao/Hl7TextInfoDaoImpl",
        "commn/model/PatientLabRouting",
        "commn/dao/PatientLabRoutingDao",
        "commn/dao/PatientLabRoutingDaoImpl",
        "casemgmt/model/CaseManagementNote",
        "casemgmt/dao/CaseManagementNoteDAO",
        "casemgmt/dao/CaseManagementNoteDAOImpl",
        "casemgmt/model/CaseManagementIssue",
        "casemgmt/dao/CaseManagementIssueDAO",
        "casemgmt/dao/CaseManagementIssueDAOImpl",
        "casemgmt/model/CaseManagementNoteExt",
        "casemgmt/dao/CaseManagementNoteExtDAO",
        "casemgmt/dao/CaseManagementNoteExtDAOImpl",
        "casemgmt/dao/IssueDAO",
        "casemgmt/dao/IssueDAOImpl",
        "PMmodule/dao/ProviderDao",
        "PMmodule/dao/ProviderDaoImpl",
        "commn/dao/SecurityDao",
        "commn/dao/SecurityDaoImpl",
        "commn/dao/ProviderFacilityDao",
        "commn/dao/ProviderFacilityDaoImpl",
        "commn/dao/MeasurementTypeDao",
        "commn/dao/MeasurementTypeDaoImpl",
        "commn/dao/ValidationsDao",
        "commn/dao/ValidationsDaoImpl",
        "commn/model/Validations",
        "webserv/rest/conversion/DrugConverter",
        "webserv/transfer_objects/DrugTransfer",
        "webserv/rest/conversion/DrugConverterImpl",
        "commn/model/converter/LegacyFalseFlagConverter",
        "commn/model/converter/LegacyZeroFloatConverter",
        "lab/ca/all/parsers/CdsLaboratoryMessageParser",
        "lab/ca/all/parsers/CMLHandler",
        "lab/ca/all/parsers/Factory",
        "utility/PathValidationUtils",
        "utility/XmlUtils",
        "commn/dao/AbstractDaoImpl",
        "commn/dao/EntityIdentifierPaths",
        "dao/AbstractJpaDao",
        "commn/dao/DrugDaoImpl",
        "commn/dao/DrugMergedDemographicDaoImpl",
        "commn/dao/AllergyDaoImpl",
        "commn/dao/AllergyMergedDemographicDaoImpl",
        "commn/dao/PreventionDaoImpl",
        "commn/dao/PreventionMergedDemographicDaoImpl",
        "commn/dao/PartialDateDaoImpl",
        "commn/dao/PreventionExtDaoImpl",
        "commn/dao/CVCMappingDao",
        "commn/dao/CVCMappingDaoImpl",
        "commn/dao/CVCImmunizationDao",
        "commn/dao/CVCImmunizationDaoImpl",
        "commn/model/CVCMapping",
        "commn/model/CVCImmunization",
        "commn/dao/ContactDaoImpl",
        "commn/dao/DemographicContactDaoImpl",
        "commn/dao/MeasurementDaoImpl",
        "commn/dao/MeasurementsExtDaoImpl",
        "report/data/QueryByExampleSqlValidator",
        "commn/model/Provider",
        "utility/OscarTrackingBasicDataSource"
    };

    private record Cached(
            CdsRulesetFingerprint fingerprint, CdsRuntimeIdentity.FrozenRuntime runtime) {}

    private static final SuccessfulCache<Cached> CACHE = new SuccessfulCache<>();

    public static CdsRulesetFingerprint current() {
        Cached value = CACHE.get(CdsRulesetFingerprint::calculate);
        value.runtime().verify();
        return value.fingerprint();
    }

    /** Cache only a complete success; an operational failure never poisons class initialization. */
    static final class SuccessfulCache<T> {
        private final AtomicReference<T> value = new AtomicReference<>();

        T get(java.util.function.Supplier<T> calculate) {
            T existing = value.get();
            if (existing != null) return existing;
            synchronized (this) {
                existing = value.get();
                if (existing == null) {
                    existing = java.util.Objects.requireNonNull(calculate.get());
                    value.set(existing);
                }
                return existing;
            }
        }
    }

    /** Uncached compatibility inspection uses the same deployed resource and runtime contract. */
    static CdsRulesetFingerprint forClassLoader(ClassLoader loader, NativeResourceOpener opener) {
        return calculate(loader, opener).fingerprint();
    }

    private static Cached calculate() {
        return calculate(CdsImportJobService.class.getClassLoader(), URL::openStream);
    }

    @FunctionalInterface
    interface NativeResourceOpener {
        InputStream open(URL resource) throws IOException;
    }

    private static Cached calculate(ClassLoader loader, NativeResourceOpener opener) {
        try {
            URL anchor = loader.getResource(ANCHOR);
            if (anchor == null) throw unavailable();
            String anchorUrl = anchor.toExternalForm();
            int location = anchorUrl.lastIndexOf(ANCHOR);
            if (location < 0
                    || !(anchorUrl.startsWith("file:")
                            || anchorUrl.startsWith("jar:file:")
                            || anchorUrl.startsWith("war:file:")
                            || anchorUrl.startsWith("jar:war:file:"))) throw unavailable();
            String origin = anchorUrl.substring(0, location);
            byte[] buffer = new byte[65536];
            Map<String, String> classes = new TreeMap<>();
            var resolver = new PathMatchingResourcePatternResolver(loader);
            for (String pattern :
                    new String[] {
                        "demographic/cdsimport/**/*.class",
                        "commn/model/Cds*.class",
                        "commn/dao/Cds*.class"
                    }) {
                Resource[] resources = resolver.getResources("classpath*:" + BASE + pattern);
                if (resources.length > 4096) throw unavailable();
                for (Resource resource : resources) {
                    String url = resource.getURL().toExternalForm();
                    // Test classes or a second artifact on the classpath must not affect production
                    // provenance.
                    if (!url.startsWith(origin + BASE)) continue;
                    String name = url.substring(origin.length());
                    try (InputStream input = resource.getInputStream()) {
                        add(classes, name, input, buffer);
                    }
                }
            }
            if (classes.isEmpty()) throw unavailable();
            for (String name : NATIVE) {
                URL nativeClass = loader.getResource(BASE + name + ".class");
                if (nativeClass == null || !nativeClass.toExternalForm().startsWith(origin + BASE))
                    throw unavailable();
                try (InputStream input = opener.open(nativeClass)) {
                    add(classes, BASE + name + ".class", input, buffer);
                }
                Resource[] nested = resolver.getResources("classpath*:" + BASE + name + "$*.class");
                if (nested.length > 256) throw unavailable();
                for (Resource resource : nested) {
                    String url = resource.getURL().toExternalForm();
                    if (!url.startsWith(origin + BASE)) continue;
                    try (InputStream input = resource.getInputStream()) {
                        add(classes, url.substring(origin.length()), input, buffer);
                    }
                }
                if (classes.size() > 8192) throw unavailable();
            }
            for (String name :
                    new String[] {
                        "cds-import/schema/EMR_Data_Migration_Schema.xsd",
                        "cds-import/schema/EMR_Data_Migration_Schema_DT.xsd",
                        "spoken_languages_codes.properties",
                        "oscar/prevention/PreventionItems.xml",
                        BASE + "lab/ca/all/parsers/message_config.xml"
                    }) {
                URL resource = loader.getResource(name);
                if (resource == null || !resource.toExternalForm().startsWith(origin))
                    throw unavailable();
                try (InputStream input = resource.openStream()) {
                    add(classes, name, input, buffer);
                }
            }
            MessageDigest digest = CdsImportKeys.digest();
            CdsImportKeys.add(digest, "carlos-cds-deployed-implementation-v1");
            for (var entry : classes.entrySet()) {
                CdsImportKeys.add(digest, entry.getKey());
                CdsImportKeys.add(digest, entry.getValue());
            }
            var runtime = CdsRuntimeIdentity.addTo(digest, loader, buffer);
            return new Cached(
                    new CdsRulesetFingerprint(java.util.HexFormat.of().formatHex(digest.digest())),
                    runtime);
        } catch (IOException failure) {
            throw unavailable();
        }
    }

    private static void add(
            Map<String, String> classes, String name, InputStream input, byte[] buffer)
            throws IOException {
        MessageDigest digest = CdsImportKeys.digest();
        long bytes = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            bytes = Math.addExact(bytes, count);
            if (bytes > (16L << 20)) throw unavailable();
            digest.update(buffer, 0, count);
        }
        String hash = java.util.HexFormat.of().formatHex(digest.digest());
        String previous = classes.putIfAbsent(name, hash);
        if (previous != null && !previous.equals(hash)) throw unavailable();
    }

    private static CdsImportException unavailable() {
        return new CdsImportException("IMPORT_RULESET_IMPLEMENTATION_UNAVAILABLE");
    }
}
