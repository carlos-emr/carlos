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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequestArchive;
import io.github.carlos_emr.carlos.commn.model.ConsultationResponse;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.commn.model.ImportedSignatureOwner;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DigitalSignatureDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code DigitalSignatureDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see DigitalSignatureDao
 */
@DisplayName("DigitalSignature Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class DigitalSignatureDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DigitalSignatureDao digitalSignatureDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist digitalsignature with generated ID")
        void shouldPersistDigitalSignature_whenValidDataProvided() throws Exception {
            DigitalSignature entity = new DigitalSignature();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            digitalSignatureDao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find digitalsignature by ID")
        void shouldFindDigitalSignature_whenValidIdProvided() throws Exception {
            DigitalSignature saved = new DigitalSignature();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            digitalSignatureDao.persist(saved);
            DigitalSignature found = digitalSignatureDao.find(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all digitalsignature records")
        void shouldCountAllDigitalSignatures() throws Exception {
            DigitalSignature entity = new DigitalSignature();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            digitalSignatureDao.persist(entity);
            long count = digitalSignatureDao.getCountAll();
            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Metadata queries")
    class MetadataQueries {

        @Test
        @Tag("query")
        @DisplayName("should return explicit module metadata without legacy inference")
        void shouldReturnExplicitModuleMetadata() {
            DigitalSignature saved = legacySignature(123);
            saved.setModuleType(ModuleType.PRESCRIPTION);
            digitalSignatureDao.persist(saved);
            digitalSignatureDao.flush();

            DigitalSignature metadata = digitalSignatureDao.findMetadataById(saved.getId());

            assertThat(metadata.getDemographicId()).isEqualTo(123);
            assertThat(metadata.getModuleType()).isEqualTo(ModuleType.PRESCRIPTION);
            assertThat(metadata.getProviderNo()).isEqualTo("999998");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null metadata when digital signature is missing")
        void shouldReturnNullMetadata_whenDigitalSignatureMissing() {
            DigitalSignature metadata = digitalSignatureDao.findMetadataById(Integer.MAX_VALUE);

            assertThat(metadata).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should infer legacy consultation metadata from consultation request reference")
        void shouldInferLegacyConsultationMetadata_fromConsultationRequestReference() {
            DigitalSignature saved = persistLegacySignature(123);
            ConsultationRequest consult = new ConsultationRequest();
            consult.setDemographicId(123);
            consult.setSignatureImg(String.valueOf(saved.getId()));
            entityManager.persist(consult);
            entityManager.flush();

            DigitalSignature metadata = digitalSignatureDao.findMetadataById(saved.getId());

            assertThat(metadata.getDemographicId()).isEqualTo(123);
            assertThat(metadata.getModuleType()).isEqualTo(ModuleType.CONSULTATION);
        }

        @Test
        @Tag("query")
        @DisplayName("should infer legacy consultation metadata from archived consultation request reference")
        void shouldInferLegacyConsultationMetadata_fromConsultationRequestArchiveReference() {
            DigitalSignature saved = persistLegacySignature(123);
            ConsultationRequestArchive consult = new ConsultationRequestArchive();
            consult.setDemographicId(123);
            consult.setSignatureImg(String.valueOf(saved.getId()));
            entityManager.persist(consult);
            entityManager.flush();

            DigitalSignature metadata = digitalSignatureDao.findMetadataById(saved.getId());

            assertThat(metadata.getDemographicId()).isEqualTo(123);
            assertThat(metadata.getModuleType()).isEqualTo(ModuleType.CONSULTATION);
        }

        @Test
        @Tag("query")
        @DisplayName("should infer legacy consultation metadata from consultation response reference")
        void shouldInferLegacyConsultationMetadata_fromConsultationResponseReference() {
            DigitalSignature saved = persistLegacySignature(123);
            ConsultationResponse consult = new ConsultationResponse();
            consult.setDemographicNo(123);
            consult.setSignatureImg(String.valueOf(saved.getId()));
            entityManager.persist(consult);
            entityManager.flush();

            DigitalSignature metadata = digitalSignatureDao.findMetadataById(saved.getId());

            assertThat(metadata.getDemographicId()).isEqualTo(123);
            assertThat(metadata.getModuleType()).isEqualTo(ModuleType.CONSULTATION);
        }

        @Test
        @Tag("query")
        @DisplayName("should infer legacy prescription metadata from prescription reference")
        void shouldInferLegacyPrescriptionMetadata_fromPrescriptionReference() {
            DigitalSignature saved = persistLegacySignature(456);
            Prescription prescription = new Prescription();
            prescription.setDemographicId(456);
            prescription.setDigitalSignatureId(saved.getId());
            entityManager.persist(prescription);
            entityManager.flush();

            DigitalSignature metadata = digitalSignatureDao.findMetadataById(saved.getId());

            assertThat(metadata.getDemographicId()).isEqualTo(456);
            assertThat(metadata.getModuleType()).isEqualTo(ModuleType.PRESCRIPTION);
            assertThat(metadata.getProviderNo()).isEqualTo("999998");
        }

        @Test
        @Tag("query")
        @DisplayName("should not infer legacy metadata when stored demographic is missing")
        void shouldNotInferLegacyMetadata_whenStoredDemographicIsMissing() {
            DigitalSignature saved = persistLegacySignature(null);
            Prescription prescription = new Prescription();
            prescription.setDemographicId(456);
            prescription.setDigitalSignatureId(saved.getId());
            entityManager.persist(prescription);
            entityManager.flush();

            DigitalSignature metadata = digitalSignatureDao.findMetadataById(saved.getId());

            assertThat(metadata.getDemographicId()).isNull();
            assertThat(metadata.getModuleType()).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should infer legacy eForm metadata from signature value reference")
        void shouldInferLegacyEformMetadata_fromSignatureValueReference() {
            DigitalSignature saved = persistLegacySignature(789);
            EFormValue eFormValue = new EFormValue();
            eFormValue.setDemographicId(789);
            eFormValue.setVarName("signatureValue");
            eFormValue.setVarValue("/imageRenderingServlet?source=signature_stored&digitalSignatureId="
                    + saved.getId() + "&r=42");
            entityManager.persist(eFormValue);
            entityManager.flush();

            DigitalSignature metadata = digitalSignatureDao.findMetadataById(saved.getId());

            assertThat(metadata.getDemographicId()).isEqualTo(789);
            assertThat(metadata.getModuleType()).isEqualTo(ModuleType.E_FORM);
        }

        @Test
        @Tag("query")
        @DisplayName("infers legacy eForm metadata from an HTML-escaped URL reference")
        void shouldInferLegacyEformMetadata_fromEscapedReference() {
            DigitalSignature saved = persistLegacySignature(789);
            EFormValue value = new EFormValue();
            value.setDemographicId(789);
            value.setVarName("signatureValue");
            value.setVarValue("/imageRenderingServlet?source=signature_stored&amp;digitalSignatureId="
                    + saved.getId() + "&amp;r=42");
            entityManager.persist(value);
            entityManager.flush();

            DigitalSignature metadata = digitalSignatureDao.findReadableMetadataById(saved.getId());
            assertThat(metadata.getDemographicId()).isEqualTo(789);
            assertThat(metadata.getModuleType()).isEqualTo(ModuleType.E_FORM);
        }

        @Test
        @Tag("query")
        @DisplayName("resolves an unassigned eForm signature from an HTML-escaped URL reference")
        void shouldResolveUnassignedEformSignature_fromEscapedReference() {
            DigitalSignature saved = persistLegacySignature(-1);
            EFormValue value = new EFormValue();
            value.setDemographicId(789);
            value.setVarName("signatureValue");
            value.setVarValue("/imageRenderingServlet?source=signature_stored&amp;digitalSignatureId="
                    + saved.getId());
            entityManager.persist(value);
            entityManager.flush();

            assertThat(digitalSignatureDao.findMetadataById(saved.getId()).getDemographicId()).isEqualTo(-1);
            // A form value created after import cannot grant ownership.
            assertThat(digitalSignatureDao.findReadableMetadataById(saved.getId())).isNull();
            insertImportedOwner(saved, 789, ModuleType.E_FORM);
            DigitalSignature readable = digitalSignatureDao.findReadableMetadataById(saved.getId());
            assertThat(readable.getDemographicId()).isEqualTo(789);
            assertThat(readable.getModuleType()).isEqualTo(ModuleType.E_FORM);
        }

        @ParameterizedTest
        @Tag("query")
        @ValueSource(strings = {
                "<img src=\"/imageRenderingServlet?source=signature_stored&amp;digitalSignatureId=%s\">",
                "<img src='/imageRenderingServlet?source=signature_stored&digitalSignatureId=%s'>",
                "&lt;img src=&quot;/imageRenderingServlet?source=signature_stored&amp;digitalSignatureId=%s&quot;&gt;"
        })
        @DisplayName("infers a legacy eForm signature from saved letter HTML")
        void shouldInferLegacyEformMetadata_fromLetterContent(String letterHtmlTemplate) {
            DigitalSignature saved = persistLegacySignature(789);
            EFormValue letter = new EFormValue();
            letter.setDemographicId(789);
            letter.setVarName("Letter");
            letter.setVarValue(letterHtmlTemplate.formatted(saved.getId()));
            entityManager.persist(letter);
            entityManager.flush();

            DigitalSignature metadata = digitalSignatureDao.findReadableMetadataById(saved.getId());
            assertThat(metadata.getDemographicId()).isEqualTo(789);
            assertThat(metadata.getModuleType()).isEqualTo(ModuleType.E_FORM);
        }

        @Test
        @Tag("query")
        @DisplayName("does not match a legacy letter signature ID by numeric prefix")
        void shouldNotInferLegacyEformMetadata_fromLongerLetterId() {
            DigitalSignature saved = persistLegacySignature(789);
            EFormValue letter = new EFormValue();
            letter.setDemographicId(789);
            letter.setVarName("Letter");
            letter.setVarValue("<img src=\"/imageRenderingServlet?digitalSignatureId="
                    + saved.getId() + "0\">");
            entityManager.persist(letter);
            entityManager.flush();

            assertThat(digitalSignatureDao.findReadableMetadataById(saved.getId()).getModuleType())
                    .isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("does not treat editable letter text as ownership of an unassigned signature")
        void shouldNotInferUnassignedSignatureOwner_fromLetterContent() {
            DigitalSignature saved = persistLegacySignature(-1);
            EFormValue letter = new EFormValue();
            letter.setDemographicId(789);
            letter.setVarName("Letter");
            letter.setVarValue("&lt;img src=&quot;/imageRenderingServlet?source=signature_stored"
                    + "&amp;digitalSignatureId=" + saved.getId() + "&quot;&gt;");
            entityManager.persist(letter);
            entityManager.flush();

            assertThat(digitalSignatureDao.findReadableMetadataById(saved.getId())).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should leave legacy metadata unscoped when no owner reference exists")
        void shouldLeaveLegacyMetadataUnscoped_whenNoOwnerReferenceExists() {
            DigitalSignature saved = persistLegacySignature(123);

            DigitalSignature metadata = digitalSignatureDao.findMetadataById(saved.getId());

            assertThat(metadata.getDemographicId()).isEqualTo(123);
            assertThat(metadata.getModuleType()).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("resolves an imported -1 signature only for a unique read reference")
        void shouldResolveUnassignedSignature_onlyForRead() {
            DigitalSignature saved = persistLegacySignature(-1);
            ConsultationRequest consult = new ConsultationRequest();
            consult.setDemographicId(123);
            consult.setSignatureImg(String.valueOf(saved.getId()));
            entityManager.persist(consult);
            entityManager.flush();
            insertImportedOwner(saved, 123, ModuleType.CONSULTATION);

            DigitalSignature mutatingMetadata = digitalSignatureDao.findMetadataById(saved.getId());
            DigitalSignature readableMetadata = digitalSignatureDao.findReadableMetadataById(saved.getId());

            assertThat(mutatingMetadata.getDemographicId()).isEqualTo(-1);
            assertThat(readableMetadata.getDemographicId()).isEqualTo(123);
            assertThat(readableMetadata.getModuleType()).isEqualTo(ModuleType.CONSULTATION);
            assertThat(readableMetadata.getProviderNo()).isEqualTo("999998");
        }

        @Test
        @Tag("query")
        @DisplayName("ambiguous references cannot assign a -1 signature to a patient")
        void shouldRefuseUnassignedSignature_whenReferencesAreAmbiguous() {
            DigitalSignature saved = persistLegacySignature(-1);
            for (int patient : new int[] {123, 456}) {
                ConsultationRequest consult = new ConsultationRequest();
                consult.setDemographicId(patient);
                consult.setSignatureImg(String.valueOf(saved.getId()));
                entityManager.persist(consult);
            }
            entityManager.flush();

            DigitalSignature metadata = digitalSignatureDao.findReadableMetadataById(saved.getId());
            assertThat(metadata).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("unreferenced -1 signatures remain unreadable even with a stored module")
        void shouldRefuseUnassignedSignature_whenUnreferenced() {
            DigitalSignature saved = legacySignature(-1);
            saved.setModuleType(ModuleType.CONSULTATION);
            digitalSignatureDao.persist(saved);
            digitalSignatureDao.flush();

            assertThat(digitalSignatureDao.findReadableMetadataById(saved.getId())).isNull();
            assertThat(digitalSignatureDao.findMetadataById(saved.getId()).getDemographicId()).isEqualTo(-1);
        }

        @Test
        @Tag("query")
        @DisplayName("a stored module cannot override a conflicting -1 signature reference")
        void shouldRefuseUnassignedSignature_whenModuleMismatches() {
            DigitalSignature saved = legacySignature(-1);
            saved.setModuleType(ModuleType.PRESCRIPTION);
            digitalSignatureDao.persist(saved);
            digitalSignatureDao.flush();
            ConsultationRequest consult = new ConsultationRequest();
            consult.setDemographicId(123);
            consult.setSignatureImg(String.valueOf(saved.getId()));
            entityManager.persist(consult);
            entityManager.flush();
            insertImportedOwner(saved, 123, ModuleType.CONSULTATION);

            assertThat(digitalSignatureDao.findReadableMetadataById(saved.getId())).isNull();
        }

        private void insertImportedOwner(DigitalSignature signature, int patient, ModuleType module) {
            entityManager.persist(new ImportedSignatureOwner(signature.getId(), patient, module));
            entityManager.flush();
        }

        private DigitalSignature persistLegacySignature(Integer demographicId) {
            DigitalSignature saved = legacySignature(demographicId);
            digitalSignatureDao.persist(saved);
            digitalSignatureDao.flush();
            return saved;
        }

        private DigitalSignature legacySignature(Integer demographicId) {
            DigitalSignature signature = new DigitalSignature();
            signature.setDemographicId(demographicId);
            signature.setFacilityId(1);
            signature.setProviderNo("999998");
            signature.setSignatureImage(new byte[] {1, 2, 3});
            return signature;
        }
    }
}
