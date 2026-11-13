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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Whole occurrence/row/field accounting across retained and subsequently created native contacts. */
@Tag("unit")
@Tag("cds-export")
class CdsExportContactRetainedAssemblerUnitTest {
    @TempDir Path directory;
    private CdsExportContactFieldReconcilerUnitTest.Graph graph;
    private final List<CdsExportCapturedTextRowDto> contacts = new ArrayList<>(),
            associations = new ArrayList<>();
    private final List<CdsExportNativeRowReceiptDto> witnesses = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        var fixture = new CdsExportContactFieldReconcilerUnitTest();
        fixture.directory = directory;
        fixture.setup();
        graph = fixture.snapshot();
        contacts.add(graph.contact());
        associations.add(graph.association());
        witnesses.addAll(graph.witnesses());
    }

    private String parent() {
        return graph.index().nativeParents().values().iterator().next();
    }

    @Test
    void shouldVerifyEveryImportedOccurrenceAndEveryNativeFieldIsClaimedExactlyOnce_forContactRetainedAssemblerContract() throws Exception {
        var result = prepare();
        assertThat(result.sourceContacts()).isEqualTo(1);
        assertThat(result.currentContacts()).isEqualTo(1);
        assertThat(result.changes()).isEmpty();
        assertThat(result.fields()).hasSize(40);
        assertThat(result.sourceContactFields())
                .isEqualTo(
                        graph.fields().stream()
                                .filter(f -> f.sourcePath().contains("/Contact[1]/"))
                                .map(CdsExportRetainedFieldDto::id)
                                .collect(java.util.stream.Collectors.toSet()));
        assertThat(render(result)).contains("Middle😀");
        assertThat(result.toString()).doesNotContain("Middle", parent());
    }

    @Test
    void shouldVerifyASubsequentlyAddedNativeContactIsAppended_withoutDuplicatingOrFlatteningTheImportedContact()
            throws Exception {
        addContact();
        var result = prepare();
        assertThat(result.sourceContacts()).isEqualTo(1);
        assertThat(result.currentContacts()).isEqualTo(2);
        assertThat(result.fields()).hasSize(80);
        assertThat(result.changes())
                .extracting(CdsExportRetainedSectionWriter.Change::relativePath)
                .containsExactly("Contact[2]");
        String xml = render(result);
        assertThat(xml).contains("Middle😀", "Grace", "Added family", "new@example.test");
        assertThat(xml.split("<Contact>", -1)).hasSize(3);
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "Contact",
                                                CdsExportPatientTableSourceReader.key(8),
                                                "firstName"))
                                .destination())
                .isEqualTo("Contact[2]/Name/FirstName");
    }

    @Test
    void shouldAppendASpecialistAddedAfterImport_asAContactWithItsRoleAsPurpose()
            throws Exception {
        // A specialist added to an imported chart's health-care team (procontact.jsp, an
        // external professional contact) is a Demographics/Contact appended after the imported
        // contacts, its role the plain-text ContactPurpose.
        var contact =
                copy(
                        graph.contact(),
                        8,
                        Map.of(
                                "type",
                                "ProfessionalContact",
                                "firstName",
                                "Grace",
                                "lastName",
                                "Specialist family"));
        var link =
                copy(
                        graph.association(),
                        10,
                        Map.of(
                                "contactId",
                                "8",
                                "role",
                                "Specialist",
                                "category",
                                "professional"));
        contacts.add(contact);
        associations.add(link);
        witnesses.add(CdsExportContactFieldReconcilerUnitTest.capturedWitness(contact));
        witnesses.add(CdsExportContactFieldReconcilerUnitTest.capturedWitness(link));
        var result = prepare();
        assertThat(result.currentContacts()).isEqualTo(2);
        assertThat(result.changes())
                .extracting(CdsExportRetainedSectionWriter.Change::relativePath)
                .containsExactly("Contact[2]");
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "DemographicContact",
                                                CdsExportPatientTableSourceReader.key(10),
                                                "role"))
                                .rule())
                .isEqualTo("HEALTH_CARE_TEAM_ROLE_AS_PLAIN_TEXT_PURPOSE");
        String xml = render(result);
        assertThat(xml)
                .contains(
                        "<cdsd:PurposeAsPlainText>Specialist</cdsd:PurposeAsPlainText>",
                        "Specialist family");
    }

    @Test
    void shouldVerifyAMissingNativeChildCannotLeaveItsOriginalSourceContactUnaccounted_forContactRetainedAssemblerContract() {
        removeChildren();
        assertThatThrownBy(this::prepare)
                .hasMessage("CDS_EXPORT_CONTACT_SOURCE_OCCURRENCE_MISSING");
    }

    @Test
    void shouldVerifyANewAssociation_toAnImportedContactRequiresReview() {
        var extra = copy(graph.association(), 10, Map.of("role", "Care Giver"));
        associations.add(extra);
        witnesses.add(CdsExportContactFieldReconcilerUnitTest.capturedWitness(extra));
        assertThatThrownBy(this::prepare)
                .hasMessage("CDS_EXPORT_CONTACT_NEW_ASSOCIATION_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyAForeignPatientRelationshipCannotSubstitute_forTheSelectedPatientsRow() {
        var foreign = copy(graph.association(), 9, Map.of("demographicNo", "28"));
        associations.set(0, foreign);
        witnesses.set(1, CdsExportContactFieldReconcilerUnitTest.capturedWitness(foreign));
        assertThatThrownBy(this::prepare).hasMessage("CDS_EXPORT_CONTACT_SOURCE_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyMissingAndDuplicateNativeWitnessesCannotHideBehindTableCounts_forContactRetainedAssemblerContract() {
        witnesses.removeLast();
        assertThatThrownBy(this::prepare).hasMessage("CDS_EXPORT_CONTACT_ASSEMBLY_SCOPE_INVALID");
        witnesses.add(witnesses.getFirst());
        assertThatThrownBy(this::prepare).hasMessage("CDS_EXPORT_CONTACT_ASSEMBLY_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyANewContactCannotUseValuesDifferent_fromItsFrozenNativeWitness() {
        addContact();
        var original = contacts.getLast();
        contacts.set(1, copy(original, 8, Map.of("email", "substituted@example.test")));
        assertThatThrownBy(this::prepare)
                .hasMessage("CDS_EXPORT_CONTACT_RECONCILIATION_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyInactiveOrConsentedNativeStateDoesNotDisappearBehindRetainedSource_forContactRetainedAssemblerContract() {
        var changed = copy(graph.association(), 9, Map.of("active", "0"));
        associations.set(0, changed);
        witnesses.set(1, CdsExportContactFieldReconcilerUnitTest.capturedWitness(changed));
        assertThatThrownBy(this::prepare)
                .hasMessage("CDS_EXPORT_CONTACT_RECONCILIATION_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyBoundsAndCancellationAreEnforced_beforeReturningChanges() {
        assertThatThrownBy(
                        () ->
                                new CdsExportContactRetainedAssembler()
                                        .prepare(
                                                27,
                                                parent(),
                                                graph.index(),
                                                graph.checked(),
                                                graph.fields(),
                                                associations,
                                                contacts,
                                                witnesses,
                                                1,
                                                4L << 20,
                                                values(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_CONTACT_RESOURCE_LIMIT");
        assertThatThrownBy(
                        () ->
                                new CdsExportContactRetainedAssembler()
                                        .prepare(
                                                27,
                                                parent(),
                                                graph.index(),
                                                graph.checked(),
                                                graph.fields(),
                                                associations,
                                                contacts,
                                                witnesses,
                                                8192,
                                                4L << 20,
                                                values(),
                                                () -> {
                                                    throw new IllegalStateException("CANCELLED");
                                                }))
                .hasMessage("CANCELLED");
    }

    @Test
    void shouldVerifyAWholeSourceFreeContactSetCanBeMapped_fromCurrentRowsWithoutInventingOriginalContactFields()
            throws Exception {
        removeChildren();
        var tables = new LinkedHashMap<>(graph.index().tables());
        var fieldRows = new LinkedHashMap<>(tables.get("cds_import_field"));
        var originalFields =
                graph.fields().stream()
                        .filter(field -> !field.sourcePath().contains("/Contact[1]/"))
                        .toList();
        var ids =
                originalFields.stream()
                        .map(CdsExportRetainedFieldDto::id)
                        .collect(java.util.stream.Collectors.toSet());
        fieldRows.keySet().retainAll(ids);
        tables.put("cds_import_field", fieldRows);
        var parents = new LinkedHashMap<>(graph.index().nativeParents());
        parents.keySet().retainAll(ids);
        var refs = new LinkedHashMap<>(graph.index().references());
        refs.replaceAll(
                (address, rs) ->
                        rs.stream()
                                .filter(
                                        ref ->
                                                !ref.table().equals("cds_import_field")
                                                        || ids.contains(ref.id()))
                                .toList());
        var slots = new LinkedHashMap<>(graph.checked().units());
        slots.replaceAll(
                (address, rs) ->
                        rs.stream()
                                .filter(
                                        ref ->
                                                !ref.expected().table().equals("cds_import_field")
                                                        || ids.contains(ref.expected().id()))
                                .toList());
        graph =
                new CdsExportContactFieldReconcilerUnitTest.Graph(
                        new CdsExportRetainedIndexDto(
                                graph.index().sourceManifestSha256(),
                                graph.index().metadataRows(),
                                graph.index().metadataBytes(),
                                tables,
                                refs.keySet(),
                                refs,
                                parents),
                        new CdsExportRetainedReceiptVerificationDto(
                                graph.checked().sourceManifestSha256(),
                                slots,
                                graph.checked().unchangedUnits(),
                                graph.checked().reviewUnits()),
                        originalFields,
                        graph.contact(),
                        graph.association(),
                        graph.witnesses());
        // Supply a native role instead of a source-only placeholder, which needs retained evidence.
        var first = copy(graph.contact(), 7, Map.of("firstName", "Current native"));
        var link = copy(graph.association(), 9, Map.of("role", "Next of Kin"));
        contacts.set(0, first);
        associations.set(0, link);
        witnesses.clear();
        witnesses.add(CdsExportContactFieldReconcilerUnitTest.capturedWitness(first));
        witnesses.add(CdsExportContactFieldReconcilerUnitTest.capturedWitness(link));
        var result = prepare();
        assertThat(result.sourceContacts()).isZero();
        assertThat(result.currentContacts()).isEqualTo(1);
        assertThat(result.sourceContactFields()).isEmpty();
        assertThat(render(result)).contains("Current native");
    }

    private CdsExportContactRetainedAssembler.Result prepare() throws Exception {
        return new CdsExportContactRetainedAssembler()
                .prepare(
                        27,
                        parent(),
                        graph.index(),
                        graph.checked(),
                        graph.fields(),
                        associations,
                        contacts,
                        witnesses,
                        8192,
                        4L << 20,
                        values(),
                        () -> {});
    }

    private void addContact() {
        var contact =
                copy(
                        graph.contact(),
                        8,
                        Map.of(
                                "firstName",
                                "Grace",
                                "lastName",
                                "Added family",
                                "email",
                                "new@example.test"));
        var link = copy(graph.association(), 10, Map.of("contactId", "8", "role", "Care Giver"));
        contacts.add(contact);
        associations.add(link);
        witnesses.add(CdsExportContactFieldReconcilerUnitTest.capturedWitness(contact));
        witnesses.add(CdsExportContactFieldReconcilerUnitTest.capturedWitness(link));
    }

    private static CdsExportCapturedTextRowDto copy(
            CdsExportCapturedTextRowDto original, int id, Map<String, String> changes) {
        var fields = new LinkedHashMap<>(original.fields());
        fields.put("id", Integer.toString(id));
        fields.putAll(changes);
        return new CdsExportCapturedTextRowDto(
                original.table(), CdsExportPatientTableSourceReader.key(id), fields);
    }

    private void removeChildren() {
        var tables = new LinkedHashMap<>(graph.index().tables());
        var units = new LinkedHashMap<>(tables.get("cds_import_unit"));
        var removed = new HashSet<String>();
        units.values()
                .removeIf(
                        row -> {
                            if (!"Demographics_CONTACT".equals(row.fields().get("category")))
                                return false;
                            removed.add(
                                    row.fields().get("run_id")
                                            + ":"
                                            + row.fields().get("unit_key"));
                            return true;
                        });
        tables.put("cds_import_unit", units);
        var refs = new LinkedHashMap<>(graph.index().references());
        refs.keySet().removeAll(removed);
        var slots = new LinkedHashMap<>(graph.checked().units());
        slots.keySet().removeAll(removed);
        var unchanged = new HashSet<>(graph.checked().unchangedUnits());
        unchanged.removeAll(removed);
        var review = new HashSet<>(graph.checked().reviewUnits());
        review.removeAll(removed);
        graph =
                new CdsExportContactFieldReconcilerUnitTest.Graph(
                        new CdsExportRetainedIndexDto(
                                graph.index().sourceManifestSha256(),
                                graph.index().metadataRows(),
                                graph.index().metadataBytes(),
                                tables,
                                refs.keySet(),
                                refs,
                                graph.index().nativeParents()),
                        new CdsExportRetainedReceiptVerificationDto(
                                graph.checked().sourceManifestSha256(), slots, unchanged, review),
                        graph.fields(),
                        graph.contact(),
                        graph.association(),
                        graph.witnesses());
    }

    private static CdsExportRetainedSectionWriter.Values values() {
        return new CdsExportRetainedSectionWriter.Values() {
            public Reader text(CdsExportRetainedFieldDto field) {
                return new StringReader(
                        field.logicalInline() == null
                                ? field.originalInline()
                                : field.logicalInline());
            }

            public InputStream binary(CdsExportRetainedFieldDto field) {
                throw new AssertionError("No binary value");
            }
        };
    }

    private String render(CdsExportContactRetainedAssembler.Result result) throws Exception {
        var prepared =
                new CdsExportRetainedSectionWriter()
                        .prepareChanges(graph.fields(), values(), result.changes(), () -> {});
        var output = new ByteArrayOutputStream();
        String key = "a".repeat(64);
        var selected = Set.of(CdsCategory.DEMOGRAPHICS);
        new CdsExportXmlComposer()
                .compose(
                        output,
                        key,
                        key,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        List.of(
                                        new CdsExportXmlComposer.Section(
                                                new CdsExportIdentityDto(
                                                        key, CdsCategory.DEMOGRAPHICS, key),
                                                prepared.content()))
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
        return output.toString(StandardCharsets.UTF_8);
    }
}
