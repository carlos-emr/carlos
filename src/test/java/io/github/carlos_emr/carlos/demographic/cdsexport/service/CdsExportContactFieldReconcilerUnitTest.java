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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Source parser/normalizer plus independently constructed historical witnesses; installed qualification is separate. */
@Tag("unit")
@Tag("cds-export")
class CdsExportContactFieldReconcilerUnitTest {
    private static final String RUN = "11111111-1111-4111-8111-111111111111",
            ROOT = "/OmdCds[1]/PatientRecord[1]/Demographics[1]",
            HASH = "a".repeat(64);
    private static final String PATIENT = CdsImportKeys.hash("fixture-source", "27", "default"),
            PARENT = CdsImportKeys.hash(PATIENT, ROOT, "NATIVE", "0"),
            PAGE = CdsImportKeys.hash(PATIENT, ROOT, "FIELDS", "1"),
            CHILD = CdsImportKeys.hash(PATIENT, ROOT + "/Contact[1]", "_CONTACT", "1"),
            ADDRESS = RUN + ":" + CHILD;
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;
    private final Map<String, Map<String, CdsExportCapturedTextRowDto>> tables =
            new LinkedHashMap<>();
    private final List<CdsExportRetainedFieldDto> fields = new ArrayList<>();
    private Map<String, String> oldContact, oldAssociation, contact, association;
    private CdsContactDto source;
    private CdsExportRetainedIndexDto index;
    private CdsExportRetainedReceiptVerificationDto checked;
    private boolean active = true, baseline = true;

    @BeforeEach
    void setup() throws Exception {
        fixture("Middle😀", "0012", true);
    }

    @Test
    void shouldVerifyUnchangedClinicalDependenciesPreserveMiddleNamePurposesAndSeparatePhoneComponents_forContactFieldReconcilerContract()
            throws Exception {
        var result = invoke();
        assertThat(result.representable()).isTrue();
        assertThat(result.changes()).isEmpty();
        assertThat(result.retainedPaths()).hasSize(8);
        String xml = render(result);
        assertThat(xml)
                .contains(
                        "<cdsd:MiddleName>Middle😀</cdsd:MiddleName>",
                        "<cdsd:extension>0012</cdsd:extension>");
        assertThat(xml)
                .contains(
                        "<cdsd:PurposeAsEnum>NK</cdsd:PurposeAsEnum>",
                        "<cdsd:PurposeAsEnum>CG</cdsd:PurposeAsEnum>");
        assertThat(result.fields()).hasSize(40);
        assertThat(result.toString()).doesNotContain("Middle", RUN, "0012");
    }

    @Test
    void shouldVerifyAChangedEmailDoesNotFlattenTheNamePurposeOrTelephone_forContactFieldReconcilerContract() throws Exception {
        contact.put("email", "current@example.test");
        var result = invoke();
        assertThat(result.representable()).isTrue();
        assertThat(result.currentPaths()).containsExactly("EmailAddress");
        String xml = render(result);
        assertThat(xml)
                .contains(
                        "current@example.test",
                        "<cdsd:MiddleName>Middle😀</cdsd:MiddleName>",
                        "<cdsd:extension>0012</cdsd:extension>")
                .doesNotContain("old@example.test");
    }

    @Test
    void
            shouldVerifyAnUnchangedCombinedNativeNameMayExceedOneWireNameField_withoutTruncatingTheSeparateSourceNames()
                    throws Exception {
        fixture("M".repeat(50), "0012", true);
        var result = invoke();
        assertThat(result.representable()).isTrue();
        assertThat(render(result))
                .contains(
                        "<cdsd:FirstName>Ada</cdsd:FirstName>",
                        "<cdsd:MiddleName>" + "M".repeat(50) + "</cdsd:MiddleName>");
    }

    @Test
    void shouldVerifyClearingEmailDoesNotRestoreItsHistoricalValue_forContactFieldReconcilerContract() throws Exception {
        contact.put("email", null);
        var result = invoke();
        assertThat(result.representable()).isTrue();
        assertThat(render(result)).doesNotContain("EmailAddress", "old@example.test");
    }

    @Test
    void shouldVerifyEditingTheCombinedGivenNameRequiresReview_insteadOfInventingASplitOrLosingTheMiddleName()
            throws Exception {
        contact.put("firstName", "Edited compound name");
        var result = invoke();
        assertThat(result.representable()).isFalse();
        assertThat(rule(result, "Contact", "firstName"))
                .isEqualTo("EDITED_COMPOUND_OR_UNPROJECTED_CONTACT_VALUE_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyEditingTheFamilyNamePreservesTheUnchangedGivenAndMiddleNames_forContactFieldReconcilerContract() throws Exception {
        contact.put("lastName", "Current family");
        var result = invoke();
        assertThat(result.representable()).isTrue();
        assertThat(render(result))
                .contains("Current family", "Middle😀")
                .doesNotContain("Source family");
    }

    @Test
    void shouldVerifyASimpleGivenNameCanChange_whenThereIsNoUnprojectedMiddleName() throws Exception {
        fixture(null, "0012", true);
        contact.put("firstName", "Current given");
        var result = invoke();
        assertThat(result.representable()).isTrue();
        assertThat(render(result))
                .contains("Current given")
                .doesNotContain("<cdsd:FirstName>Ada</cdsd:FirstName>");
    }

    @Test
    void shouldVerifyChangingAResidenceNumber_withAnEmbeddedHistoricalExtensionRequiresReview()
            throws Exception {
        contact.put("residencePhone", "613-555-9999");
        var result = invoke();
        assertThat(result.representable()).isFalse();
        assertThat(rule(result, "Contact", "residencePhone"))
                .isEqualTo("EDITED_COMPOUND_OR_UNPROJECTED_CONTACT_VALUE_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyASimpleResidencePhoneCanChange_withoutReverseParsingAnExtension() throws Exception {
        fixture("Middle😀", null, true);
        contact.put("residencePhone", "613-555-9999");
        var result = invoke();
        assertThat(result.representable()).isTrue();
        assertThat(render(result)).contains("613-555-9999").doesNotContain("613-555-0101");
    }

    @Test
    void shouldVerifyChangingTheWorkExtensionUsesItsSeparateNativeColumnAndPreservesResidenceComponents_forContactFieldReconcilerContract()
            throws Exception {
        contact.put("workPhoneExtension", "0003");
        var result = invoke();
        assertThat(result.representable()).isTrue();
        String xml = render(result);
        assertThat(xml)
                .contains(
                        "<cdsd:extension>0003</cdsd:extension>",
                        "<cdsd:extension>0012</cdsd:extension>")
                .doesNotContain("<cdsd:extension>AB</cdsd:extension>");
    }

    @Test
    void shouldVerifyEditedMultiplePurposePlaceholdersCannotEraseUnprojectedSourcePurposes_forContactFieldReconcilerContract() throws Exception {
        association.put("role", "Next of Kin");
        assertThat(invoke().representable()).isFalse();
        association.put("role", oldAssociation.get("role"));
        association.put("ec", null);
        assertThat(invoke().representable()).isFalse();
    }

    @Test
    void shouldVerifyASimplePurposeCanChangeAndProducesTheCurrentChoice_forContactFieldReconcilerContract() throws Exception {
        fixture("Middle😀", "0012", false);
        association.put("role", "Care Giver");
        var result = invoke();
        assertThat(result.representable()).isTrue();
        String xml = render(result);
        assertThat(xml)
                .contains("<cdsd:PurposeAsEnum>CG</cdsd:PurposeAsEnum>")
                .doesNotContain("<cdsd:PurposeAsEnum>NK</cdsd:PurposeAsEnum>");
    }

    @Test
    void shouldVerifyMatchingNoteEditsAreRepresentedOnceAndAnExplicitClearStaysCleared_forContactFieldReconcilerContract() throws Exception {
        contact.put("note", "Current note");
        association.put("note", "Current note");
        var result = invoke();
        assertThat(result.representable()).isTrue();
        assertThat(render(result)).contains("Current note").doesNotContain("Source note");
        contact.put("note", null);
        association.put("note", null);
        result = invoke();
        assertThat(result.representable()).isTrue();
        assertThat(render(result)).doesNotContain("Source note", "<Note>");
    }

    @Test
    void shouldVerifyClearingOneOfTheTwoNoteCopiesCannotResurrectTheOther_forContactFieldReconcilerContract() throws Exception {
        contact.put("note", null);
        var result = invoke();
        assertThat(result.representable()).isFalse();
        assertThat(rule(result, "Contact", "note"))
                .isEqualTo("EDITED_COMPOUND_OR_UNPROJECTED_CONTACT_VALUE_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyNativeConsentHistoryAndAdditionalPopulatedFieldsRemainExplicitReviewRequirements_forContactFieldReconcilerContract()
            throws Exception {
        // Updated for D5: consent "1" is the UI and model default (see CdsExportContactConsent) and
        // no longer holds the chart, and an explicit refusal ("0") is now carried in the native
        // demographic record. Updated again: an imported association removed in Manage Contacts or
        // marked Inactive no longer holds the chart (see the removal tests below); only a state no
        // CARLOS screen writes still requires review.
        for (var entry : List.of(Map.entry("active", "0"), Map.entry("deleted", "1"))) {
            association = new LinkedHashMap<>(oldAssociation);
            association.put(entry.getKey(), entry.getValue());
            assertThat(invoke().representable()).isTrue();
        }
        for (var entry : List.of(Map.entry("active", "2"), Map.entry("deleted", "2"))) {
            association = new LinkedHashMap<>(oldAssociation);
            association.put(entry.getKey(), entry.getValue());
            assertThat(invoke().representable()).isFalse();
        }
        association = new LinkedHashMap<>(oldAssociation);
        association.put("consentToContact", "0");
        assertThat(invoke().representable()).isTrue();
        association = new LinkedHashMap<>(oldAssociation);
        // A contact address has no Demographics/Contact element: it travels in the native
        // demographic NewCategory record and no longer holds the imported chart.
        contact.put("address", "Unmapped contact address");
        assertThat(invoke().representable()).isTrue();
    }

    @Test
    void shouldRemoveTheImportedContactElement_whenManageContactsDeletesItsAssociation()
            throws Exception {
        // Contact2Action.removeContact keeps the association with deleted=1 (merge stamps
        // updateDate): the person is no longer a contact of the chart.
        association.put("deleted", "1");
        association.put("updateDate", "2026-02-02 00:00:00");
        var result = invoke();
        assertThat(result.representable()).isTrue();
        assertThat(result.superseded()).isTrue();
        assertThat(result.changes()).hasSize(1);
        var change = result.changes().getFirst();
        assertThat(change.relativePath()).isEqualTo("Contact[1]");
        assertThat(change.content()).isNull();
        assertThat(change.expectedPriorFields()).isEqualTo(contactFieldIds());
        assertThat(rule(result, "DemographicContact", "deleted"))
                .isEqualTo("REMOVED_IMPORTED_CONTACT_ASSOCIATION_NOT_IN_CURRENT_CHART");
        assertThat(rule(result, "DemographicContact", "role"))
                .isEqualTo("REMOVED_CONTACT_ASSOCIATION_NOT_IN_CURRENT_CHART");
        assertThat(rule(result, "Contact", "firstName"))
                .isEqualTo("CONTACT_OF_A_REMOVED_ASSOCIATION_NOT_IN_CURRENT_CHART");
        var prepared =
                new CdsExportRetainedSectionWriter()
                        .prepareChanges(fields, values(), result.changes(), () -> {});
        assertThat(prepared.replacedFields()).isEqualTo(contactFieldIds());
        assertThat(render(result))
                .doesNotContain("<Contact>", "Source family", "old@example.test", "Middle😀")
                .contains("Fixture");
    }

    @Test
    void shouldCarryAnInactiveImportedContactInTheNativeRecord_insteadOfTheImportedElement()
            throws Exception {
        // The Manage Contacts editor's "Inactive" (active=0): as for a native association, the
        // stored association and its contact travel in the native demographic NewCategory record.
        association.put("active", "0");
        var result = invoke();
        assertThat(result.representable()).isTrue();
        assertThat(result.superseded()).isTrue();
        assertThat(result.changes())
                .singleElement()
                .satisfies(
                        change -> {
                            assertThat(change.relativePath()).isEqualTo("Contact[1]");
                            assertThat(change.content()).isNull();
                            assertThat(change.expectedPriorFields())
                                    .isEqualTo(contactFieldIds());
                        });
        for (var field :
                List.of(
                        key("Contact", "firstName"),
                        key("Contact", "email"),
                        key("DemographicContact", "role"),
                        key("DemographicContact", "active")))
            assertThat(CdsExportDemographicSupportingMapper.carries(result.fields().get(field)))
                    .isTrue();
        assertThat(render(result)).doesNotContain("<Contact>", "Source family");
    }

    @Test
    void shouldExportAnUneditedImportedContactExactlyAsBefore_whenNoAssociationStateChanged()
            throws Exception {
        var result = invoke();
        assertThat(result.superseded()).isFalse();
        assertThat(result.changes()).isEmpty();
        assertThat(render(result))
                .contains("<Contact>", "Source family", "old@example.test", "Middle😀");
    }

    @Test
    void shouldVerifyAnEditedGivenNameCannotBorrowAContradictoryHistoricalPreparation_forContactFieldReconcilerContract() throws Exception {
        source =
                new CdsContactDto(
                        "Wrong given",
                        source.lastName(),
                        source.email(),
                        source.note(),
                        source.phones(),
                        source.roles(),
                        source.emergencyContact(),
                        source.substituteDecisionMaker(),
                        source.diagnostics());
        assertThat(invoke().representable()).isFalse();
    }

    @Test
    void shouldVerifyGeneratedSqlTimestampsRemainOperationalWhileClinicalFieldsUseTheirOwnWitnesses_forContactFieldReconcilerContract()
            throws Exception {
        contact.put("updateDate", "2026-02-01 00:00:00");
        association.put("created", "2026-02-02 00:00:00");
        var result = invoke();
        assertThat(result.representable()).isTrue();
        assertThat(result.fields().get(key("Contact", "updateDate")).outcome())
                .isEqualTo(CdsExportFieldAssessmentDto.Outcome.OPERATIONAL);
        assertThat(result.changes()).isEmpty();
    }

    @Test
    void shouldVerifyAMissingHistoricalBaselineIsNeverBackfilled_fromTodaysRows() {
        baseline = false;
        assertThatThrownBy(this::invoke).hasMessage("CDS_EXPORT_CONTACT_FIELD_BASELINE_REQUIRED");
    }

    @Test
    void shouldVerifyAnotherCapturedRowCannotSubstitute_forTheWitnessBytes() throws Exception {
        build();
        var substituted = new LinkedHashMap<>(contact);
        substituted.put("email", "substituted@example.test");
        assertThatThrownBy(
                        () ->
                                reconcile(
                                        new CdsExportCapturedTextRowDto(
                                                "Contact", nativeKey(7), substituted),
                                        witness("Contact", 7, contact),
                                        row("DemographicContact", 9, association),
                                        witness("DemographicContact", 9, association)))
                .hasMessage("CDS_EXPORT_CONTACT_RECONCILIATION_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyAnExpiredValueScopeFails_beforeAResultEscapes() {
        active = false;
        assertThatThrownBy(this::invoke).hasMessage("EXPIRED_VALUES");
    }

    @Test
    void shouldVerifyADeletedNativeContactRequiresReview_beforeAnyHistoricalValueCanReturn() throws Exception {
        build();
        var slots = new LinkedHashMap<>(checked.units());
        var nativeRefs = new ArrayList<>(slots.get(ADDRESS));
        var original = nativeRefs.getFirst();
        nativeRefs.set(
                0, new CheckedReference(original.expected(), null, Reason.ROW_NOT_IN_CAPTURE));
        slots.put(ADDRESS, nativeRefs);
        checked =
                new CdsExportRetainedReceiptVerificationDto(
                        HASH, slots, checked.unchangedUnits(), checked.reviewUnits());
        assertThatThrownBy(
                        () ->
                                reconcile(
                                        null,
                                        null,
                                        row("DemographicContact", 9, association),
                                        witness("DemographicContact", 9, association)))
                .hasMessage("CDS_EXPORT_CONTACT_DELETION_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyTwoSourceUnitsCannotClaimTheSameNativeContact_forContactFieldReconcilerContract() throws Exception {
        build();
        String alias = RUN + ":" + "f".repeat(64);
        var refs = new LinkedHashMap<>(index.references());
        refs.put(alias, refs.get(ADDRESS));
        index =
                new CdsExportRetainedIndexDto(
                        HASH,
                        index.metadataRows(),
                        index.metadataBytes(),
                        index.tables(),
                        refs.keySet(),
                        refs,
                        index.nativeParents());
        var slots = new LinkedHashMap<>(checked.units());
        slots.put(alias, slots.get(ADDRESS));
        var review = new HashSet<>(checked.reviewUnits());
        review.add(alias);
        checked =
                new CdsExportRetainedReceiptVerificationDto(
                        HASH, slots, checked.unchangedUnits(), review);
        assertThatThrownBy(
                        () ->
                                reconcile(
                                        row("Contact", 7, contact),
                                        witness("Contact", 7, contact),
                                        row("DemographicContact", 9, association),
                                        witness("DemographicContact", 9, association)))
                .hasMessage("CDS_EXPORT_CONTACT_ALIAS_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyCancellation_duringReconciliationIsPropagated() throws Exception {
        build();
        assertThatThrownBy(
                        () ->
                                new CdsExportContactFieldReconciler()
                                        .reconcile(
                                                27,
                                                ADDRESS,
                                                index,
                                                checked,
                                                fields,
                                                row("Contact", 7, contact),
                                                witness("Contact", 7, contact),
                                                row("DemographicContact", 9, association),
                                                witness("DemographicContact", 9, association),
                                                values(),
                                                () -> {
                                                    throw new IllegalStateException("CANCELLED");
                                                }))
                .hasMessage("CANCELLED");
    }

    @Test
    void shouldVerifyAPreparedResultCannotReadSourceValues_afterItsAuthenticatedScopeExpires() throws Exception {
        var result = invoke();
        active = false;
        assertThatThrownBy(() -> render(result)).hasMessage("EXPIRED_VALUES");
    }

    private CdsExportContactFieldReconciler.Result invoke() throws Exception {
        build();
        return reconcile(
                row("Contact", 7, contact),
                witness("Contact", 7, contact),
                row("DemographicContact", 9, association),
                witness("DemographicContact", 9, association));
    }

    private CdsExportContactFieldReconciler.Result reconcile(
            CdsExportCapturedTextRowDto c,
            CdsExportNativeRowReceiptDto cw,
            CdsExportCapturedTextRowDto a,
            CdsExportNativeRowReceiptDto aw)
            throws Exception {
        return new CdsExportContactFieldReconciler()
                .reconcile(27, ADDRESS, index, checked, fields, c, cw, a, aw, values(), () -> {});
    }

    private void fixture(String middle, String extension, boolean multiple) throws Exception {
        tables.clear();
        fields.clear();
        String xml =
                "<OmdCds xmlns=\"cds\" xmlns:dt=\"cds_dt\"><PatientRecord><Demographics><Names><dt:LegalName namePurpose=\"L\"><dt:FirstName><dt:Part>Patient</dt:Part><dt:PartType>GIV</dt:PartType></dt:FirstName><dt:LastName><dt:Part>Fixture</dt:Part><dt:PartType>FAMC</dt:PartType></dt:LastName></dt:LegalName></Names><DateOfBirth>1980-01-01</DateOfBirth><Gender>U</Gender><UniqueVendorIdSequence>27</UniqueVendorIdSequence><Contact><ContactPurpose><dt:PurposeAsEnum>NK</dt:PurposeAsEnum></ContactPurpose>"
                        + (multiple
                                ? "<ContactPurpose><dt:PurposeAsEnum>CG</dt:PurposeAsEnum></ContactPurpose><ContactPurpose><dt:PurposeAsEnum>EC</dt:PurposeAsEnum></ContactPurpose>"
                                : "")
                        + "<Name><dt:FirstName>Ada</dt:FirstName>"
                        + (middle == null ? "" : "<dt:MiddleName>" + middle + "</dt:MiddleName>")
                        + "<dt:LastName>Source family</dt:LastName></Name><PhoneNumber phoneNumberType=\"R\"><dt:phoneNumber>613-555-0101</dt:phoneNumber>"
                        + (extension == null
                                ? ""
                                : "<dt:extension>" + extension + "</dt:extension>")
                        + "</PhoneNumber><PhoneNumber phoneNumberType=\"W\"><dt:phoneNumber>613-555-0102</dt:phoneNumber><dt:extension>AB</dt:extension></PhoneNumber><EmailAddress>old@example.test</EmailAddress><Note>Source note</Note></Contact><PersonStatusCode><PersonStatusAsEnum>A</PersonStatusAsEnum></PersonStatusCode></Demographics></PatientRecord></OmdCds>";
        Path folder = Files.createDirectory(directory.resolve(UUID.randomUUID().toString()));
        Path input = Files.writeString(folder.resolve("input.xml"), xml);
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        input,
                        folder.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        var parser = new CdsFieldParser();
        var record = records.getFirst();
        parser.forEachSubrecord(
                record,
                "Contact",
                child -> {
                    try {
                        source = new CdsContactNormalizationService(parser).normalize(child);
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                });
        parser.forEachField(
                record,
                field -> {
                    try {
                        String path = ROOT + "/" + field.path(),
                                id = CdsImportKeys.hash(PAGE, field.namespace(), path, "1");
                        var v = new HashMap<String, String>();
                        v.putAll(
                                Map.of(
                                        "id",
                                        id,
                                        "run_id",
                                        RUN,
                                        "patient_key",
                                        PATIENT,
                                        "unit_key",
                                        PAGE,
                                        "demographic_no",
                                        "27",
                                        "category",
                                        "Demographics",
                                        "source_path",
                                        path,
                                        "occurrence",
                                        "1",
                                        "source_namespace",
                                        field.namespace(),
                                        "value_type",
                                        field.schemaType()));
                        v.put("original_value", parser.readText(record, field, 32768));
                        v.put("normalized_value", parser.readLogicalText(record, field, 65536));
                        v.put("payload_offset", "0");
                        v.put("payload_length", "0");
                        put(Plan.IMPORT_FIELDS, id, v);
                        fields.add(
                                new CdsExportRetainedFieldMapper()
                                        .map(
                                                27,
                                                Set.of(CdsCategory.DEMOGRAPHICS),
                                                tables.get("cds_import_field").get(id)));
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                });
        oldContact = new LinkedHashMap<>();
        Plan.CONTACT.columns().forEach(c -> oldContact.put(c, null));
        oldContact.putAll(
                Map.of(
                        "id",
                        "7",
                        "type",
                        "Contact",
                        "deleted",
                        "0",
                        "updateDate",
                        "2024-01-01 01:02:03",
                        "firstName",
                        source.firstName(),
                        "lastName",
                        source.lastName(),
                        "email",
                        source.email(),
                        "note",
                        source.note()));
        for (var entry :
                Map.of("R", "residencePhone", "C", "cellPhone", "W", "workPhone").entrySet()) {
            var phone = source.phones().get(entry.getKey());
            if (phone != null)
                oldContact.put(
                        entry.getValue(),
                        phone.number()
                                + (!entry.getKey().equals("W")
                                                && phone.extension() != null
                                                && !phone.extension().isEmpty()
                                        ? " ext " + phone.extension()
                                        : ""));
        }
        oldContact.put("workPhoneExtension", source.phones().get("W").extension());
        oldAssociation = new LinkedHashMap<>();
        Plan.DEMOGRAPHIC_CONTACT.columns().forEach(c -> oldAssociation.put(c, null));
        oldAssociation.putAll(
                Map.of(
                        "id",
                        "9",
                        "demographicNo",
                        "27",
                        "contactId",
                        "7",
                        "type",
                        "2",
                        "category",
                        "personal",
                        "active",
                        "1",
                        "deleted",
                        "0",
                        "mrp",
                        "0",
                        "facilityId",
                        "0"));
        oldAssociation.putAll(
                Map.of(
                        "created",
                        "2024-01-01 01:02:03",
                        "updateDate",
                        "2024-01-01 01:02:03",
                        "creator",
                        "999998",
                        "note",
                        source.note(),
                        "role",
                        source.roles().getFirst()));
        oldAssociation.put("ec", Boolean.TRUE.equals(source.emergencyContact()) ? "true" : null);
        oldAssociation.put(
                "sdm", Boolean.TRUE.equals(source.substituteDecisionMaker()) ? "true" : null);
        contact = new LinkedHashMap<>(oldContact);
        association = new LinkedHashMap<>(oldAssociation);
    }

    private void build() throws Exception {
        String options = "b".repeat(64), rules = "c".repeat(64), sourceDigest = "d".repeat(64);
        put(
                Plan.IMPORT_RUNS,
                RUN,
                Map.of(
                        "id",
                        RUN,
                        "source_namespace",
                        "fixture-source",
                        "options_digest",
                        options,
                        "ruleset_digest",
                        rules));
        put(
                Plan.IMPORT_PATIENTS,
                CdsImportKeys.hash(RUN, PATIENT),
                Map.of(
                        "run_id",
                        RUN,
                        "patient_key",
                        PATIENT,
                        "lineage_key",
                        PATIENT,
                        "source_patient_id",
                        "27",
                        "copy_key",
                        "default",
                        "target_demographic_no",
                        "27",
                        "held",
                        "0"));
        for (var item :
                Map.of(
                                PARENT,
                                "Demographics",
                                PAGE,
                                "Demographics_FIELDS",
                                CHILD,
                                "Demographics_CONTACT")
                        .entrySet())
            put(
                    Plan.IMPORT_UNITS,
                    CdsImportKeys.hash(RUN, item.getKey()),
                    Map.of(
                            "run_id",
                            RUN,
                            "unit_key",
                            item.getKey(),
                            "patient_key",
                            PATIENT,
                            "lineage_key",
                            PATIENT,
                            "ordinal",
                            "1",
                            "state",
                            "COMMITTED",
                            "category",
                            item.getValue(),
                            "source_digest",
                            sourceDigest));
        put(
                Plan.IMPORT_DEPENDENCIES,
                "e".repeat(64),
                Map.of("run_id", RUN, "unit_key", CHILD, "depends_on_unit_key", PARENT));
        var oldStates =
                List.of(
                        state("Contact", 7, oldContact),
                        state("DemographicContact", 9, oldAssociation));
        var refs =
                oldStates.stream()
                        .map(
                                s ->
                                        new CdsExportRetainedIndexDto.Reference(
                                                s.table(), s.id(), s.digest()))
                        .toList();
        String encoded = JSON.writeValueAsString(Map.of("protocol", 2, "refs", refs)),
                target = sha(encoded);
        var unit = tables.get("cds_import_unit").get(CdsImportKeys.hash(RUN, CHILD));
        var raw = new HashMap<>(unit.fields());
        raw.put("target_references", encoded);
        raw.put("target_digest", target);
        raw.put(
                "native_baseline",
                baseline ? CdsNativeBaselineCodec.encode(encoded, target, oldStates) : null);
        put(Plan.IMPORT_UNITS, unit.recordKey(), raw);
        String payload = JSON.writeValueAsString(source), type = CdsContactDto.class.getName();
        put(
                Plan.IMPORT_PREPARATIONS,
                unit.recordKey(),
                Map.of(
                        "id",
                        unit.recordKey(),
                        "run_id",
                        RUN,
                        "unit_key",
                        CHILD,
                        "source_digest",
                        sourceDigest,
                        "options_digest",
                        options,
                        "ruleset_digest",
                        rules,
                        "payload_type",
                        type,
                        "payload",
                        payload,
                        "digest",
                        CdsPreparationSeal.digest(type, sourceDigest, options, rules, payload)));
        var allRefs = new LinkedHashMap<String, List<CdsExportRetainedIndexDto.Reference>>();
        allRefs.put(ADDRESS, refs);
        allRefs.put(
                RUN + ":" + PARENT,
                List.of(new CdsExportRetainedIndexDto.Reference("demographic", "27", HASH)));
        allRefs.put(
                RUN + ":" + PAGE,
                fields.stream()
                        .map(
                                f ->
                                        new CdsExportRetainedIndexDto.Reference(
                                                "cds_import_field", f.id(), HASH))
                        .toList());
        var links = new HashMap<String, String>();
        fields.forEach(f -> links.put(f.id(), RUN + ":" + PARENT));
        index =
                new CdsExportRetainedIndexDto(
                        HASH, 50, 65536, tables, allRefs.keySet(), allRefs, links);
        var slots = new LinkedHashMap<String, List<CheckedReference>>();
        allRefs.forEach(
                (address, rs) ->
                        slots.put(
                                address,
                                rs.stream()
                                        .map(
                                                r ->
                                                        new CheckedReference(
                                                                r,
                                                                r.digest(),
                                                                Reason.UNCHANGED_ROW))
                                        .toList()));
        var nativeRefs = new ArrayList<CheckedReference>();
        for (var old : oldStates) {
            var now =
                    state(
                            old.table(),
                            Integer.parseInt(old.id()),
                            old.table().equals("Contact") ? contact : association);
            nativeRefs.add(
                    new CheckedReference(
                            new CdsExportRetainedIndexDto.Reference(
                                    old.table(), old.id(), old.digest()),
                            now.digest(),
                            Reason.RECEIPT_FIELD_CODEC_UNQUALIFIED,
                            CdsNativeRowComparison.compare(old, now)));
        }
        slots.put(ADDRESS, nativeRefs);
        checked =
                new CdsExportRetainedReceiptVerificationDto(
                        HASH, slots, Set.of(RUN + ":" + PARENT, RUN + ":" + PAGE), Set.of(ADDRESS));
    }

    private void put(Plan plan, String key, Map<String, String> source) {
        var row = new HashMap<String, String>();
        plan.columns().forEach(c -> row.put(c, null));
        row.putAll(source);
        row.put("id", key);
        tables.computeIfAbsent(plan.table(), ignored -> new LinkedHashMap<>())
                .put(key, new CdsExportCapturedTextRowDto(plan.table(), key, row));
    }

    private static String nativeKey(int id) {
        return CdsExportPatientTableSourceReader.key(id);
    }

    private static CdsExportCapturedTextRowDto row(
            String table, int id, Map<String, String> fields) {
        return new CdsExportCapturedTextRowDto(table, nativeKey(id), fields);
    }

    private static CdsExportNativeRowReceiptDto witness(
            String table, int id, Map<String, String> values) {
        var state = state(table, id, values);
        return new CdsExportNativeRowReceiptDto(
                table, Integer.toString(id), nativeKey(id), state.digest(), state);
    }

    private static CdsNativeRowState state(String table, int id, Map<String, String> values) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (String column :
                (table.equals("Contact") ? Plan.CONTACT : Plan.DEMOGRAPHIC_CONTACT).columns()) {
            columns.add(
                    new CdsNativeRowDigest.Column(
                            column,
                            column.equals("updateDate") || column.equals("created")
                                    ? "TIMESTAMP"
                                    : "VARCHAR",
                            255,
                            0));
            String value = values.get(column);
            cells.add(
                    value == null
                            ? new CdsNativeRowState.Cell(true, 0, null)
                            : new CdsNativeRowState.Cell(
                                    false,
                                    value.getBytes(StandardCharsets.UTF_8).length,
                                    sha(value)));
        }
        return new CdsNativeRowState(table, Integer.toString(id), columns, cells);
    }

    private static String sha(String value) {
        return HexFormat.of()
                .formatHex(CdsImportKeys.digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private CdsExportRetainedSectionWriter.Values values() {
        return new CdsExportRetainedSectionWriter.Values() {
            public void requireActive() throws IOException {
                if (!active) throw new IOException("EXPIRED_VALUES");
            }

            public Reader text(CdsExportRetainedFieldDto field) throws IOException {
                requireActive();
                return new StringReader(
                        field.logicalInline() == null
                                ? field.originalInline()
                                : field.logicalInline());
            }

            public InputStream binary(CdsExportRetainedFieldDto field) {
                throw new AssertionError("No binary field");
            }
        };
    }

    private static CdsExportNativeFieldKeyDto key(String table, String column) {
        return new CdsExportNativeFieldKeyDto(
                table, nativeKey(table.equals("Contact") ? 7 : 9), column);
    }

    private Set<String> contactFieldIds() {
        var ids = new HashSet<String>();
        for (var field : fields)
            if (field.sourcePath().startsWith(ROOT + "/Contact[1]/")) ids.add(field.id());
        assertThat(ids).isNotEmpty();
        return ids;
    }

    /** The fixture imported from a source contact with the single purpose NK. */
    void singlePurpose() throws Exception {
        fixture("Middle😀", "0012", false);
    }

    /** One association column as the Manage Contacts screen leaves it, before the graph is built. */
    void editAssociation(String column, String value) {
        association.put(column, value);
    }

    private static String rule(
            CdsExportContactFieldReconciler.Result result, String table, String column) {
        return result.fields().get(key(table, column)).rule();
    }

    record Graph(
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> fields,
            CdsExportCapturedTextRowDto contact,
            CdsExportCapturedTextRowDto association,
            List<CdsExportNativeRowReceiptDto> witnesses) {}

    Graph snapshot() throws Exception {
        build();
        return new Graph(
                index,
                checked,
                List.copyOf(fields),
                row("Contact", 7, contact),
                row("DemographicContact", 9, association),
                List.of(
                        witness("Contact", 7, contact),
                        witness("DemographicContact", 9, association)));
    }

    static CdsExportNativeRowReceiptDto capturedWitness(CdsExportCapturedTextRowDto row) {
        return witness(row.table(), Integer.parseInt(row.fields().get("id")), row.fields());
    }

    private String render(CdsExportContactFieldReconciler.Result result) throws Exception {
        if (!result.representable()) throw new IOException("REQUIRES_REVIEW");
        var prepared =
                new CdsExportRetainedSectionWriter()
                        .prepareChanges(fields, values(), result.changes(), () -> {});
        var selected = Set.of(CdsCategory.DEMOGRAPHICS);
        var output = new ByteArrayOutputStream();
        var identity = new CdsExportIdentityDto(HASH, CdsCategory.DEMOGRAPHICS, "b".repeat(64));
        new CdsExportXmlComposer()
                .compose(
                        output,
                        HASH,
                        HASH,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        List.of(new CdsExportXmlComposer.Section(identity, prepared.content()))
                                .iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        "27",
                        selected,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var ids = new HashSet<>(prepared.retainedFields());
        assertThat(Collections.disjoint(ids, prepared.replacedFields())).isTrue();
        ids.addAll(prepared.replacedFields());
        assertThat(ids)
                .isEqualTo(
                        fields.stream()
                                .map(CdsExportRetainedFieldDto::id)
                                .collect(java.util.stream.Collectors.toSet()));
        return output.toString(StandardCharsets.UTF_8);
    }
}
