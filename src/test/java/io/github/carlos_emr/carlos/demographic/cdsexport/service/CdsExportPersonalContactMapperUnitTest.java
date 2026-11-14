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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@Tag("cds-export")
class CdsExportPersonalContactMapperUnitTest {
    @TempDir Path directory;
    private static final String HASH = "a".repeat(64);
    private final CdsExportPersonalContactMapper mapper = new CdsExportPersonalContactMapper();

    @Test
    void shouldCarryAnAssociationWithNoContactRow_withoutHoldingTheChart()
            throws Exception {
        // Was a hold (EXACT_LINKED_CONTACT_MISSING_REQUIRES_REVIEW). There is no name to write,
        // so no Contact element; the stored association travels in the native record.
        var link = changed(association(-1, 9), "contactId", null);
        var result = map(List.of(link), List.of());
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.contacts()).isEmpty();
        assertThat(
                        CdsExportDemographicSupportingMapper.carries(
                                result.fields()
                                        .get(
                                                new CdsExportNativeFieldKeyDto(
                                                        "DemographicContact",
                                                        link.recordKey(),
                                                        "role"))))
                .isTrue();
    }

    @Test
    void shouldAllowAnAbsentRoleAndPhoneExtension_withoutConstructingEmptyWireValues()
            throws Exception {
        var link =
                changed(
                        changed(changed(association(-1, 9), "role", null), "ec", "false"),
                        "sdm",
                        "false");
        var result = map(List.of(link), List.of(changed(contact(9), "workPhoneExtension", "")));
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.contacts().getFirst().phones())
                .allMatch(phone -> phone.extension() == null);
    }

    @Test
    void shouldVerifyFieldDestinationsUseOutputOrdinalsAndKeepNativeIdentities_inProtectedKeys()
            throws Exception {
        var result = map(List.of(association(-1, 9)), List.of(contact(9)));
        assertThat(result.fields().values())
                .noneMatch(field -> field.destination().contains(contact(9).recordKey()));
        var name =
                result.fields()
                        .get(
                                new CdsExportNativeFieldKeyDto(
                                        "Contact", contact(9).recordKey(), "firstName"));
        assertThat(name.destination()).isEqualTo("Contact[1]/Name/FirstName");
    }

    @Test
    void
            shouldVerifyMapsExactExternalIdentityAndAllAvailablePersonalContactFields_forPersonalContactMapperContract()
                    throws Exception {
        var result = map(List.of(association(-1, 9)), List.of(contact(9)));
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.fields()).hasSize(40);
        assertThat(result.contacts()).hasSize(1);
        var value = result.contacts().getFirst();
        assertThat(value.firstName()).isEqualTo("Élodie😀");
        assertThat(value.lastName()).isEqualTo("Contact Fixture");
        assertThat(value.phones()).hasSize(3);
        assertThat(value.phones().getLast().extension()).isEqualTo("0012");
        assertThat(value.purposes())
                .containsExactly(
                        new CdsExportPersonalContactDto.Purpose(true, "NK"),
                        new CdsExportPersonalContactDto.Purpose(true, "EC"),
                        new CdsExportPersonalContactDto.Purpose(true, "SDM"));
    }

    @Test
    void
            shouldVerifyActualSchemaAndReceivingParserPreserveContactsAlongsidePatientAndPharmacy_forPersonalContactMapperContract()
                    throws Exception {
        var record = parse(map(List.of(association(-1, 9)), List.of(contact(9))), HASH);
        var parser = new CdsFieldParser();
        var values = new LinkedHashMap<String, String>();
        parser.forEachField(
                record,
                field -> {
                    if (field.path().startsWith("Contact["))
                        try {
                            values.put(field.path(), parser.readLogicalText(record, field, 1000));
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                });
        assertThat(values)
                .containsEntry("Contact[1]/Name[1]/FirstName[1]", "Élodie😀")
                .containsEntry("Contact[1]/ContactPurpose[1]/PurposeAsEnum[1]", "NK")
                .containsEntry("Contact[1]/ContactPurpose[2]/PurposeAsEnum[1]", "EC")
                .containsEntry("Contact[1]/ContactPurpose[3]/PurposeAsEnum[1]", "SDM")
                .containsEntry("Contact[1]/PhoneNumber[3]/extension[1]", "0012")
                .containsEntry("Contact[1]/Note[1]", "  Exact contact note\r\n😀  ");
        assertThat(parser.firstText(record, "PreferredPharmacy/Name", 50))
                .isEqualTo("Café 😀 Pharmacy");
    }

    @Test
    void
            shouldVerifyMultipleAssociationsCombinePurposesOnlyThroughTheSameExactContactIdentity_forPersonalContactMapperContract()
                    throws Exception {
        var second = changed(changed(association(-2, 9), "role", "Care Giver"), "ec", "false");
        var third = changed(association(-3, 10), "role", "Neighbour");
        var result =
                map(List.of(association(-1, 9), second, third), List.of(contact(10), contact(9)));
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.contacts()).hasSize(2);
        assertThat(result.fields()).hasSize(98);
        assertThat(result.contacts().getFirst().purposes())
                .contains(new CdsExportPersonalContactDto.Purpose(true, "CG"));
        assertThat(result.contacts().getLast().purposes())
                .contains(new CdsExportPersonalContactDto.Purpose(false, "Neighbour"));
        parse(result, HASH);
    }

    @Test
    void
            shouldVerifyFreeTextRoleAndAbsentPurposeFlagsDoNotInventACodeOrAContactPurpose_forPersonalContactMapperContract()
                    throws Exception {
        var link =
                changed(
                        changed(
                                changed(association(-1, 9), "role", "Home support coordinator"),
                                "ec",
                                null),
                        "sdm",
                        null);
        var result = map(List.of(link), List.of(contact(9)));
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.contacts().getFirst().purposes())
                .containsExactly(
                        new CdsExportPersonalContactDto.Purpose(false, "Home support coordinator"));
    }

    @Test
    void
            shouldExportAContactSavedWithoutAName_withItsPurposeAndNoInventedName()
                    throws Exception {
        // Was a hold (CONTACT_NAME_MISSING_OR_UNREPRESENTABLE). Both parts of Contact/Name are
        // optional in the schema: the contact is written with an empty Name, nothing invented.
        var result =
                map(
                        List.of(association(-1, 9)),
                        List.of(changed(changed(contact(9), "firstName", null), "lastName", null)));
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.contacts()).hasSize(1);
        assertThat(result.contacts().getFirst().firstName()).isNull();
        assertThat(result.contacts().getFirst().lastName()).isNull();
        // The composed document is schema-valid and the importer's parser reads it.
        parse(result, HASH);
    }

    @Test
    void
            shouldVerifyAnAvailableSurnameDoesNotRequireAnInventedFirstName_forPersonalContactMapperContract()
                    throws Exception {
        var result =
                map(List.of(association(-1, 9)), List.of(changed(contact(9), "firstName", null)));
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.contacts().getFirst().firstName()).isNull();
        parse(result, HASH);
    }

    @Test
    void shouldAcceptTheDefaultContactConsent_andCarryAnExplicitRefusal() throws Exception {
        // D5: contact.jsp lists "Consent" (1) first and manageHealthCareTeam.jsp posts 1, so a
        // contact added through the UI carries consentToContact 1. CDS 5.2 has no element for it.
        var consenting = changed(association(-1, 9), "consentToContact", "1");
        var result = map(List.of(consenting), List.of(contact(9)));
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "DemographicContact",
                                                consenting.recordKey(),
                                                "consentToContact"))
                                .outcome())
                .isEqualTo(CdsExportFieldAssessmentDto.Outcome.OPERATIONAL);
        parse(result, HASH);
        // "No Consent" is an ordinary choice on the same screen: the contact is exported and the
        // refusal travels exactly in the native demographic record instead of holding the chart.
        var refused = changed(association(-1, 9), "consentToContact", "0");
        var carried = map(List.of(refused), List.of(contact(9)));
        assertThat(carried.nativeRepresentable()).isTrue();
        var decision =
                carried.fields()
                        .get(
                                new CdsExportNativeFieldKeyDto(
                                        "DemographicContact", refused.recordKey(), "consentToContact"));
        assertThat(CdsExportDemographicSupportingMapper.carries(decision)).isTrue();
        assertThat(decision.rule()).isEqualTo(CdsExportContactConsent.REFUSAL_CARRIED_RULE);
    }

    @Test
    void shouldCarryALegacyRelationshipId_insteadOfExportingItsDigitsAsThePurpose()
            throws Exception {
        // D4: older health-care-team data stored CtlRelationships ids ("3" is Parent in the
        // shipped table). The export cannot resolve the site-editable table, so it writes no
        // <PurposeAsPlainText>3</PurposeAsPlainText>; the chart was held, and now the contact
        // is exported and the stored id travels exactly in the native record.
        for (String legacy : List.of("3", " 21 ")) {
            var link = changed(changed(changed(association(-1, 9), "role", legacy), "ec", "false"), "sdm", "false");
            var result = map(List.of(link), List.of(contact(9)));
            assertThat(result.nativeRepresentable()).isTrue();
            assertThat(result.contacts()).hasSize(1);
            assertThat(result.contacts().getFirst().purposes()).isEmpty();
            assertThat(
                            CdsExportDemographicSupportingMapper.carries(
                                    result.fields()
                                            .get(
                                                    new CdsExportNativeFieldKeyDto(
                                                            "DemographicContact",
                                                            link.recordKey(),
                                                            "role"))))
                    .isTrue();
        }
        // A typed relationship longer than PurposeAsPlainText (50; the column holds 100) is
        // carried the same way and was a hold too (CONTACT_PURPOSE_VALUES_REQUIRES_REVIEW).
        var wordy = changed(association(-1, 9), "role", "Neighbour who ".repeat(5).strip());
        var carried = map(List.of(wordy), List.of(contact(9)));
        assertThat(carried.nativeRepresentable()).isTrue();
        // Only the coded purposes of the fixture's own flags remain: no plain-text purpose.
        assertThat(carried.contacts().getFirst().purposes())
                .allMatch(CdsExportPersonalContactDto.Purpose::coded);
        // contact.jsp and AddAlternateContact.jsp write the relationship's text value, which
        // remains an exact plain-text purpose through the schema and receiving parser.
        var current = changed(changed(changed(association(-1, 9), "role", "Parent"), "ec", "false"), "sdm", "false");
        var result = map(List.of(current), List.of(contact(9)));
        assertThat(result.nativeRepresentable()).isTrue();
        var record = parse(result, HASH);
        assertThat(new CdsFieldParser().firstText(record, "Contact/ContactPurpose/PurposeAsPlainText", 50))
                .isEqualTo("Parent");
    }

    @Test
    void shouldVerifyNote_fromOneAssociationCanBeUsedWhenTheSharedContactHasNone()
            throws Exception {
        var result =
                map(
                        List.of(changed(association(-1, 9), "note", "Association context")),
                        List.of(changed(contact(9), "note", null)));
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.contacts().getFirst().note()).isEqualTo("Association context");
        parse(result, HASH);
    }

    @Test
    void
            shouldVerifyMatchingNotesAreMappedOnceAndEachSourcePresenceRemainsAccounted_forPersonalContactMapperContract()
                    throws Exception {
        var result =
                map(
                        List.of(
                                changed(
                                        association(-1, 9),
                                        "note",
                                        contact(9).fields().get("note"))),
                        List.of(contact(9)));
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(
                        result.fields().entrySet().stream()
                                .filter(
                                        entry ->
                                                entry.getKey().column().equals("note")
                                                        && entry.getValue().outcome()
                                                                == Outcome.MAPPED))
                .hasSize(2);
    }

    @Test
    void
            shouldVerifyDistinctOrOverlengthNotesNeedExplicitReconciliationRatherThanConcatenationOrSelection_forPersonalContactMapperContract()
                    throws Exception {
        for (var link :
                List.of(
                        changed(association(-1, 9), "note", "A different note"),
                        changed(association(-1, 9), "note", "X".repeat(201)))) {
            var result = map(List.of(link), List.of(contact(9)));
            // Neither note is chosen, joined or truncated: both travel in the native record.
            assertThat(result.nativeRepresentable()).isTrue();
            assertThat(result.contacts().getFirst().note()).isNull();
            assertThat(
                            result.fields().entrySet().stream()
                                    .filter(entry -> entry.getKey().column().equals("note"))
                                    .filter(entry -> entry.getValue().presence() == Presence.VALUE))
                    .allMatch(entry -> CdsExportDemographicSupportingMapper.carries(entry.getValue()));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "address",
                "address2",
                "city",
                "province",
                "country",
                "postal",
                "fax",
                "cpso",
                "specialty",
                "systemId"
            })
    void
            shouldVerifyOutOfSchemaContactValuesRemainReviewRequirements_forPersonalContactMapperContract(
                    String column) throws Exception {
        var result =
                map(
                        List.of(association(-1, 9)),
                        List.of(changed(contact(9), column, "Additional source value")));
        // Demographics/Contact has no element for it: the value travels in the native record.
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.fields()).hasSize(40);
        assertThat(
                        CdsExportDemographicSupportingMapper.carries(
                                result.fields()
                                        .get(
                                                new CdsExportNativeFieldKeyDto(
                                                        "Contact", contact(9).recordKey(), column))))
                .isTrue();
        parse(result, HASH);
    }

    @Test
    void
            shouldVerifyDeletedInactiveUnknownAndProfessionalAssociationsAreNotSilentlyFiltered_forPersonalContactMapperContract()
                    throws Exception {
        for (var pair :
                List.of(
                        Map.entry("deleted", "1"),
                        Map.entry("active", "0"),
                        Map.entry("active", ""),
                        Map.entry("category", "professional"),
                        Map.entry("type", "0"),
                        Map.entry("type", "1"),
                        Map.entry("type", "3"))) {
            var link = changed(association(-1, 9), pair.getKey(), pair.getValue());
            var rows =
                    Set.of("0", "1", "3").contains(link.fields().get("type"))
                            ? List.<CdsExportCapturedTextRowDto>of()
                            : List.of(contact(9));
            var result = map(List.of(link), rows);
            // A deleted association is not in the chart; an inactive, internal, provider or
            // specialist association travels in the native record. Neither is a Contact element
            // and neither holds the patient.
            assertThat(result.nativeRepresentable()).isTrue();
            assertThat(result.contacts()).isEmpty();
            assertThat(
                            result.fields()
                                    .get(
                                            new CdsExportNativeFieldKeyDto(
                                                    "DemographicContact", link.recordKey(), "role"))
                                    .outcome()
                                    .name())
                    .isEqualTo(pair.getKey().equals("deleted") ? "OPERATIONAL" : "MAPPED");
        }
        // A contact row marked deleted, or of the professional subtype, that a current
        // personal association names is still the patient's contact in the chart: exported,
        // with the stored flag or subtype carried. Both were holds.
        for (var pair : List.of(Map.entry("deleted", "1"), Map.entry("type", "ProfessionalContact"))) {
            var row = changed(contact(9), pair.getKey(), pair.getValue());
            var result = map(List.of(association(-1, 9)), List.of(row));
            assertThat(result.nativeRepresentable()).isTrue();
            assertThat(result.contacts()).hasSize(1);
            assertThat(
                            CdsExportDemographicSupportingMapper.carries(
                                    result.fields()
                                            .get(
                                                    new CdsExportNativeFieldKeyDto(
                                                            "Contact", row.recordKey(), pair.getKey()))))
                    .isTrue();
        }
    }

    @Test
    void
            shouldCarryPaddedAndMissingReferencesAndTheImportMarker_insteadOfAliasResolution()
                    throws Exception {
        // "09" is not contact 9: no alias is resolved, no Contact is written, and the stored
        // association is carried (it was a hold).
        var result = map(List.of(changed(association(-1, 9), "contactId", "09")), List.of());
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.fields()).hasSize(18);
        assertThat(result.contacts()).isEmpty();
        assertThat(map(List.of(association(-1, 9)), List.of()).nativeRepresentable()).isTrue();
        // The importer's own marker for several source purposes is not a relationship: no
        // ContactPurpose is written from it and the stored text is carried. It was a hold
        // (RETAINED_CONTACT_PURPOSE_RECONCILIATION_REQUIRED); the purposes themselves stay in
        // the imported element (CdsExportContactFieldReconciler).
        var retained =
                map(
                        List.of(
                                changed(
                                        association(-1, 9),
                                        "role",
                                        "Multiple source purposes; see imported Contact details")),
                        List.of(contact(9)));
        assertThat(retained.nativeRepresentable()).isTrue();
        assertThat(retained.contacts().getFirst().purposes())
                .allMatch(CdsExportPersonalContactDto.Purpose::coded);
        assertThat(
                        CdsExportDemographicSupportingMapper.carries(
                                retained.fields().entrySet().stream()
                                        .filter(field -> field.getKey().column().equals("role"))
                                        .findFirst()
                                        .orElseThrow()
                                        .getValue()))
                .isTrue();
    }

    @Test
    void
            shouldVerifyUnknownPurposeFlagsConflictingAssertionsConsentAndResponsibleProviderContextAreNotDiscarded_forPersonalContactMapperContract()
                    throws Exception {
        // Updated for D5: consent "1" is the UI and model default (see CdsExportContactConsent) and
        // is covered by shouldAcceptTheDefaultContactConsent_andCarryAnExplicitRefusal, which also
        // carries an explicit refusal ("0"). A spelling no screen writes, a set
        // responsible-provider flag and "false" beside a relationship naming the same purpose
        // were holds; each is now carried exactly beside the exported contact.
        for (var pair :
                List.of(
                        Map.entry("ec", "yes"),
                        Map.entry("sdm", "1"),
                        Map.entry("consentToContact", "true"),
                        Map.entry("mrp", "1"))) {
            var link = changed(association(-1, 9), pair.getKey(), pair.getValue());
            var result = map(List.of(link), List.of(contact(9)));
            assertThat(result.nativeRepresentable()).as(pair.getKey()).isTrue();
            assertThat(result.contacts()).hasSize(1);
            assertThat(
                            CdsExportDemographicSupportingMapper.carries(
                                    result.fields()
                                            .get(
                                                    new CdsExportNativeFieldKeyDto(
                                                            "DemographicContact",
                                                            link.recordKey(),
                                                            pair.getKey()))))
                    .as(pair.getKey())
                    .isTrue();
        }
        var conflicting =
                changed(changed(association(-1, 9), "role", "Emergency Contact"), "ec", "false");
        var result = map(List.of(conflicting), List.of(contact(9)));
        assertThat(result.nativeRepresentable()).isTrue();
        // The relationship the chart shows is the purpose; the stored "false" is carried.
        assertThat(result.contacts().getFirst().purposes())
                .contains(new CdsExportPersonalContactDto.Purpose(true, "EC"));
        assertThat(
                        CdsExportDemographicSupportingMapper.carries(
                                result.fields()
                                        .get(
                                                new CdsExportNativeFieldKeyDto(
                                                        "DemographicContact",
                                                        conflicting.recordKey(),
                                                        "ec"))))
                .isTrue();
    }

    @Test
    void shouldVerifyGuideUnicodeNameNoteAndExtensionBoundariesAreChecked_withoutTruncation()
            throws Exception {
        var good =
                changed(
                        changed(contact(9), "firstName", "😀".repeat(50)),
                        "note",
                        "😀".repeat(200));
        var result = map(List.of(association(-1, 9)), List.of(good));
        assertThat(result.nativeRepresentable()).isTrue();
        parse(result, HASH);
        // A note, extension or telephone the element cannot hold travels in the native record.
        for (var pair :
                List.of(
                        Map.entry("note", "😀".repeat(201)),
                        Map.entry("workPhoneExtension", "123456"),
                        Map.entry("residencePhone", "1".repeat(26))))
            assertThat(
                            map(
                                            List.of(association(-1, 9)),
                                            List.of(
                                                    changed(
                                                            contact(9),
                                                            pair.getKey(),
                                                            pair.getValue())))
                                    .nativeRepresentable())
                    .isTrue();
        // A name part longer than the CDS name part is left out of the element and travels whole
        // in the native record; one holding a character XML cannot carry is written with U+FFFD
        // and carried exactly. Neither holds the chart.
        for (String name : List.of("😀".repeat(51), "Bad\u0001name")) {
            var named =
                    map(
                            List.of(association(-1, 9)),
                            List.of(changed(contact(9), "firstName", name)));
            assertThat(named.nativeRepresentable()).isTrue();
            assertThat(
                            named.fields().entrySet().stream()
                                    .filter(entry -> entry.getKey().column().equals("firstName"))
                                    .map(entry -> entry.getValue().destination())
                                    .findFirst()
                                    .orElseThrow())
                    .contains(CdsExportDemographicSupportingMapper.DESTINATION);
            parse(named, HASH);
        }
    }

    @Test
    void
            shouldVerifyNativeHomeTelephoneTextIsNotReverseParsedAndOrphanWorkExtensionsRequireReview_forPersonalContactMapperContract()
                    throws Exception {
        var result =
                map(
                        List.of(association(-1, 9)),
                        List.of(changed(contact(9), "residencePhone", "613-555-0101 ext 5")));
        assertThat(result.nativeRepresentable()).isTrue();
        assertThat(result.contacts().getFirst().phones().getFirst().number())
                .isEqualTo("613-555-0101 ext 5");
        // An extension left without its work number travels in the native record.
        assertThat(
                        map(
                                        List.of(association(-1, 9)),
                                        List.of(changed(contact(9), "workPhone", null)))
                                .nativeRepresentable())
                .isTrue();
    }

    @Test
    void
            shouldVerifyForeignOwnershipDuplicateRowsAndIncompleteColumnIdentitiesAreRejected_forPersonalContactMapperContract()
                    throws Exception {
        assertThatThrownBy(
                        () ->
                                map(
                                        List.of(changed(association(-1, 9), "demographicNo", "28")),
                                        List.of(contact(9))))
                .hasMessage("CDS_EXPORT_CONTACT_SOURCE_SCOPE_INVALID");
        assertThatThrownBy(() -> map(List.of(association(-1, 9)), List.of(contact(10))))
                .hasMessage("CDS_EXPORT_CONTACT_SOURCE_SCOPE_INVALID");
        assertThatThrownBy(
                        () ->
                                map(
                                        List.of(association(-1, 9), association(-1, 9)),
                                        List.of(contact(9))))
                .hasMessage("CDS_EXPORT_CONTACT_SOURCE_SCOPE_INVALID");
        assertThatThrownBy(() -> map(List.of(association(-1, 9)), List.of(contact(9), contact(9))))
                .hasMessage("CDS_EXPORT_CONTACT_SOURCE_SCOPE_INVALID");
        var fields = new TreeMap<>(contact(9).fields());
        fields.remove("systemId");
        assertThatThrownBy(
                        () ->
                                map(
                                        List.of(association(-1, 9)),
                                        List.of(
                                                new CdsExportCapturedTextRowDto(
                                                        "Contact",
                                                        contact(9).recordKey(),
                                                        fields))))
                .hasMessage("CDS_EXPORT_CONTACT_SOURCE_SCOPE_INVALID");
    }

    @Test
    void
            shouldMapContacts_pastTheFormerBoundsButNeverWithSwappedFieldIdentities()
                    throws Exception {
        assertThat(mapper.map( 27, List.of(association(-1, 9)), List.of(contact(9)), 1, 1L << 20, () -> {})).isNotNull();
        assertThat(mapper.map( 27, List.of(association(-1, 9)), List.of(contact(9)), 100, 64, () -> {})).isNotNull();
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        List.of(association(-1, 9)),
                                        List.of(contact(9)),
                                        100,
                                        1L << 20,
                                        () -> {
                                            throw new IllegalStateException("CANCELLED");
                                        }))
                .hasMessage("CANCELLED");
        var result = map(List.of(association(-1, 9)), List.of(contact(9)));
        var fields = new HashMap<>(result.fields());
        var key = new CdsExportNativeFieldKeyDto("Contact", contact(9).recordKey(), "note");
        var original = fields.remove(key);
        fields.put(
                new CdsExportNativeFieldKeyDto("Contact", contact(10).recordKey(), "note"),
                original);
        assertThatThrownBy(
                        () ->
                                new CdsExportPersonalContactMapper.Result(
                                        27, 1, 1, result.contacts(), fields))
                .hasMessage("CDS_EXPORT_CONTACT_MAPPING_INVALID");
    }

    @Test
    void
            shouldVerifyAnotherGenerationCannotBeCombinedAndDebugStringsProtectClinicalValues_forPersonalContactMapperContract()
                    throws Exception {
        var result = map(List.of(association(-1, 9)), List.of(contact(9)));
        assertThatThrownBy(() -> parse(result, "b".repeat(64)))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_RELATIONS_REQUIRES_REVIEW");
        assertThat(
                        result.toString()
                                + result.contacts().getFirst()
                                + result.contacts().getFirst().purposes().getFirst())
                .doesNotContain("Élodie", "Contact Fixture", "0012");
    }

    static CdsExportCapturedTextRowDto association(long id, long reference) {
        var fields = columns(CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CONTACT);
        fields.putAll(
                Map.of(
                        "id",
                        Long.toString(id),
                        "demographicNo",
                        "27",
                        "contactId",
                        Long.toString(reference),
                        "type",
                        "2",
                        "category",
                        "personal",
                        "deleted",
                        "0",
                        "active",
                        "1",
                        "role",
                        "Next of Kin",
                        "ec",
                        "true",
                        "sdm",
                        "true"));
        fields.put("mrp", "0");
        return new CdsExportCapturedTextRowDto(
                "DemographicContact", CdsExportPatientTableSourceReader.key(id), fields);
    }

    static CdsExportCapturedTextRowDto contact(long id) {
        var fields = columns(CdsExportPatientTableReadSupport.Plan.CONTACT);
        fields.putAll(
                Map.of(
                        "id",
                        Long.toString(id),
                        "type",
                        "Contact",
                        "deleted",
                        "0",
                        "firstName",
                        "Élodie😀",
                        "lastName",
                        "Contact Fixture",
                        "note",
                        "  Exact contact note\r\n😀  ",
                        "residencePhone",
                        "613-555-0101",
                        "cellPhone",
                        "613-555-0102",
                        "workPhone",
                        "613-555-0103",
                        "workPhoneExtension",
                        "0012"));
        fields.put("email", "contact@example.test");
        return new CdsExportCapturedTextRowDto(
                "Contact", CdsExportPatientTableSourceReader.key(id), fields);
    }

    static CdsExportCapturedTextRowDto changed(
            CdsExportCapturedTextRowDto row, String column, String value) {
        var fields = new TreeMap<>(row.fields());
        fields.put(column, value);
        return new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields);
    }

    private static Map<String, String> columns(CdsExportPatientTableReadSupport.Plan plan) {
        var fields = new TreeMap<String, String>();
        plan.columns().forEach(column -> fields.put(column, null));
        return fields;
    }

    private CdsExportPersonalContactMapper.Result map(
            List<CdsExportCapturedTextRowDto> links, List<CdsExportCapturedTextRowDto> contacts)
            throws Exception {
        return mapper.map(27, links, contacts, 100, 1L << 20, () -> {});
    }

    private CdsRecordDto parse(CdsExportPersonalContactMapper.Result contacts, String sourceHash)
            throws Exception {
        var row = CdsExportEnrolmentHistoryUnitTest.current(null, null, null, null, null);
        var history = CdsExportEnrolmentHistoryUnitTest.history();
        history.accept(row, null);
        var demographic =
                CdsExportEnrolmentHistoryUnitTest.projection(row, history.finish(0), null);
        var pharmacy =
                new CdsExportPharmacyMapper()
                        .map(
                                27,
                                List.of(CdsExportPharmacyMapperUnitTest.link(-1, 9)),
                                List.of(CdsExportPharmacyMapperUnitTest.pharmacy(9)),
                                100,
                                1L << 20,
                                () -> {});
        var section =
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(HASH, CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        xml ->
                                new CdsExportDemographicXmlWriter()
                                        .writeWithRelated(
                                                demographic,
                                                new CdsExportPharmacyProjectionService.Result(
                                                        HASH, pharmacy),
                                                new CdsExportPersonalContactProjectionService
                                                        .Result(sourceHash, contacts),
                                                xml));
        var output = new ByteArrayOutputStream();
        var selected = Set.of(CdsCategory.DEMOGRAPHICS);
        new CdsExportXmlComposer()
                .compose(
                        output,
                        HASH,
                        HASH,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        List.of(section).iterator(),
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
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        Files.write(
                                directory.resolve(UUID.randomUUID() + ".xml"),
                                output.toByteArray()),
                        directory.resolve(UUID.randomUUID().toString()),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return records.getFirst();
    }
}
