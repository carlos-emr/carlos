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

import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsContactDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsContactNormalizationService;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.Reason;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Reconciles one imported external personal contact against its original native projection.
 * Unchanged dependencies retain the complete original subtree, including middle names,
 * purpose choices and telephone components. A dependency edited in CARLOS takes the current
 * value by the rule of a contact added in CARLOS ({@link CdsExportPersonalContactMapper}), what
 * its element cannot say travels in the native demographic record, and the imported element it
 * replaces is listed as replaced; nothing about an edit holds the chart. An association removed
 * or made inactive in CARLOS removes the imported element, as a native removed or inactive
 * association is not a Contact. Changes use the parent's original source coordinates and need
 * full demographic coverage, active authenticated values and final XML validation before
 * publication.
 *
 * <p>What an edit replaces, by the importer's own projection
 * ({@code CdsContactNormalizationService}):
 *
 * <ul>
 *   <li>the first-name column holds the source's first and middle names together, so an edited
 *       first name replaces FirstName and removes MiddleName;
 *   <li>a relationship, emergency-contact or decision-maker change replaces only the source
 *       purposes that column was projected from: the several purposes behind the importer's
 *       marker stay while the relationship column still holds the marker;
 *   <li>an edited phone replaces the source phones projected into its column (equal repeats of
 *       one type); source phones the import kept as source only (differing repeats, a number
 *       no column could hold) stay, and a number typed since is a further PhoneNumber;
 *   <li>notes that now differ between the contact and its association leave no Note element
 *       and both travel in the native demographic record.
 * </ul>
 */
public final class CdsExportContactFieldReconciler {
    private static final Pattern PHONE =
            Pattern.compile("PhoneNumber\\[([1-9][0-9]{0,17})\\]/@phoneNumberType");
    private static final Pattern PURPOSE =
            Pattern.compile("ContactPurpose\\[[1-9][0-9]{0,17}\\](?:/.*)?");

    private enum Group {
        GIVEN("Name/FirstName", "firstName"),
        FAMILY("Name/LastName", "lastName"),
        PURPOSES("ContactPurpose", "role", "ec", "sdm"),
        RESIDENCE("PhoneNumber[R]", "residencePhone"),
        CELL("PhoneNumber[C]", "cellPhone"),
        WORK("PhoneNumber[W]", "workPhone", "workPhoneExtension"),
        EMAIL("EmailAddress", "email"),
        NOTE("Note", "note");
        final String path;
        final Set<String> columns;

        Group(String path, String... columns) {
            this.path = path;
            this.columns = Set.of(columns);
        }

        String table() {
            return this == PURPOSES ? "DemographicContact" : "Contact";
        }
    }

    /** Rule of the deleted flag of an imported association removed in Manage Contacts. */
    static final String REMOVED_IMPORTED_CONTACT =
            "REMOVED_IMPORTED_CONTACT_ASSOCIATION_NOT_IN_CURRENT_CHART";

    /**
     * @param superseded the imported association was removed or made inactive in CARLOS: its
     *     Contact element is removed (the only change) and the native rows are decided as for
     *     any removed or inactive association, so a later association may name the contact again
     * @param current the note of the contact and of its association now differ and the element
     *     holds nothing CARLOS keeps only in the import's retained source: the element is
     *     removed and the contact is decided from its current rows, as any contact added in
     *     CARLOS. An element that does hold such content is changed in place instead
     */
    public record Result(
            CdsExportRetainedContactBinding.Result binding,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Set<String> retainedPaths,
            Set<String> currentPaths,
            List<CdsExportRetainedSectionWriter.Change> changes,
            boolean superseded,
            boolean current) {
        public Result {
            fields = Map.copyOf(fields);
            retainedPaths = Set.copyOf(retainedPaths);
            currentPaths = Set.copyOf(currentPaths);
            changes = List.copyOf(changes);
        }

        public boolean representable() {
            return fields.values().stream()
                    .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        }

        @Override
        public String toString() {
            return "CdsExportContactFieldReconciler.Result[protected]";
        }
    }

    public Result reconcile(
            int patient,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            CdsExportCapturedTextRowDto contact,
            CdsExportNativeRowReceiptDto contactWitness,
            CdsExportCapturedTextRowDto association,
            CdsExportNativeRowReceiptDto associationWitness,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        return reconcile(
                patient,
                address,
                index,
                checked,
                allFields,
                contact,
                contactWitness,
                association,
                associationWitness,
                List.of(),
                values,
                checkpoint);
    }

    /**
     * @param further the other current associations of this chart that name the same contact
     *     row (the person picked again from the contact search with another relationship),
     *     each proven against its capture witness by the caller. When the imported element
     *     holds content CARLOS keeps only in the import's retained source, they are decided
     *     here with the imported association, as the native mapper decides the associations
     *     of one contact, and their purposes join the element; the result's fields then
     *     include their columns. Otherwise they are left to the caller, which decides the
     *     contact from its current rows.
     */
    public Result reconcile(
            int patient,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            CdsExportCapturedTextRowDto contact,
            CdsExportNativeRowReceiptDto contactWitness,
            CdsExportCapturedTextRowDto association,
            CdsExportNativeRowReceiptDto associationWitness,
            List<CdsExportCapturedTextRowDto> further,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(further);
        Objects.requireNonNull(values);
        Objects.requireNonNull(checkpoint);
        values.requireActive();
        checkpoint.run();
        var binding =
                new CdsExportRetainedContactBinding()
                        .bind(patient, address, index, checked, allFields, checkpoint);
        var source = new CdsExportRetainedPreparationReader().contact(index, address).value();
        var refs = checked.units().get(address);
        if (refs == null
                || !refs.stream()
                        .map(CdsExportRetainedReceiptVerificationDto.CheckedReference::expected)
                        .toList()
                        .equals(index.references().get(address))
                || refs.size() != 2
                || refs.stream().filter(ref -> ref.expected().table().equals("Contact")).count()
                        != 1
                || refs.stream()
                                .filter(ref -> ref.expected().table().equals("DemographicContact"))
                                .count()
                        != 1) scope();
        var unit =
                index.tables()
                        .get("cds_import_unit")
                        .get(CdsImportKeys.hash(address.substring(0, 36), address.substring(37)));
        CdsNativeBaselineCodec.Document baseline = null;
        if (unit.fields().get("native_baseline") != null) {
            try {
                baseline =
                        CdsNativeBaselineCodec.decode(
                                unit.fields().get("native_baseline"),
                                unit.fields().get("target_references"),
                                unit.fields().get("target_digest"));
            } catch (IllegalArgumentException malformed) {
                scope();
            }
        }
        var oldContact =
                qualify(
                        address,
                        index,
                        refs,
                        baseline,
                        contact,
                        contactWitness,
                        "Contact",
                        checkpoint);
        var oldAssociation =
                qualify(
                        address,
                        index,
                        refs,
                        baseline,
                        association,
                        associationWitness,
                        "DemographicContact",
                        checkpoint);
        if (!matches(oldContact, "id", contactWitness.id())
                || !matches(oldAssociation, "id", associationWitness.id())
                || !matches(oldAssociation, "demographicNo", Integer.toString(patient))
                || !matches(oldAssociation, "contactId", contactWitness.id())
                || !matches(oldAssociation, "type", "2")
                || !matches(oldAssociation, "category", "personal")
                || !matches(oldAssociation, "active", "1")
                || !matches(oldAssociation, "deleted", "0")
                || !matches(oldContact, "type", "Contact")
                || !matches(oldContact, "deleted", "0")) scope();
        if (!Integer.toString(patient).equals(association.fields().get("demographicNo"))
                || !contactWitness.id().equals(association.fields().get("contactId"))) scope();
        var relative = new TreeMap<String, CdsExportRetainedFieldDto>();
        for (var field : binding.fields()) {
            checkpoint.run();
            String path = field.sourcePath().substring(binding.sourceRoot().length());
            if (!path.startsWith("/") || relative.putIfAbsent(path.substring(1), field) != null)
                scope();
        }
        var phones = new HashMap<String, List<String>>();
        var purposes =
                new TreeSet<String>(
                        Comparator.comparingLong(CdsExportContactFieldReconciler::ordinal));
        for (var field : relative.entrySet()) {
            checkpoint.run();
            var phone = PHONE.matcher(field.getKey());
            if (phone.matches()) {
                String type = read(values, field.getValue(), 1, checkpoint);
                if (!Set.of("R", "C", "W").contains(type)) scope();
                phones.computeIfAbsent(type, ignored -> new ArrayList<>())
                        .add("PhoneNumber[" + phone.group(1) + "]");
            }
            if (PURPOSE.matcher(field.getKey()).matches())
                purposes.add(field.getKey().split("/", 2)[0]);
        }
        String prefix = "Contact[" + binding.ordinal() + "]";
        // What the imported element states that no CARLOS column holds: a separate middle
        // name, a second phone of one type, several purposes behind the importer's marker.
        // Deciding such a contact from its current rows alone would lose it.
        var middleName = relative.get("Name[1]/MiddleName[1]");
        boolean sourceOnly =
                middleName != null && !read(values, middleName, 50, checkpoint).isBlank()
                        || source.roles().contains(CdsContactNormalizationService.MULTIPLE_PURPOSES);
        for (String phoneType : List.of("R", "C", "W")) {
            int retainedPhones = phones.getOrDefault(phoneType, List.of()).size();
            sourceOnly |=
                    retainedPhones > 1
                            || retainedPhones == 1 && source.phones().get(phoneType) == null;
        }
        String deleted = association.fields().get("deleted"),
                activeState = association.fields().get("active");
        boolean removedOrInactive =
                "1".equals(deleted) || "0".equals(deleted) && "0".equals(activeState);
        // The contact's other current associations are decided here only when the imported
        // element must stay (it holds source-only content); see the parameter's contract.
        var links = new ArrayList<CdsExportCapturedTextRowDto>();
        links.add(association);
        if (sourceOnly && !removedOrInactive)
            for (var link : further) {
                checkpoint.run();
                if (link == null
                        || !"DemographicContact".equals(link.table())
                        || link.recordKey().equals(association.recordKey())
                        || !Integer.toString(patient).equals(link.fields().get("demographicNo"))
                        || !"2".equals(link.fields().get("type"))
                        || !contactWitness.id().equals(link.fields().get("contactId"))
                        || !"0".equals(link.fields().get("deleted"))
                        || !"1".equals(link.fields().get("active"))) scope();
                links.add(link);
            }
        var candidate =
                new CdsExportPersonalContactMapper()
                        .map(patient, links, List.of(contact), 2, 4L << 20, checkpoint);
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        rows.add(contact);
        rows.addAll(links);
        var decisions = new LinkedHashMap<>(candidate.fields());
        var retained = new LinkedHashSet<String>();
        var current = new LinkedHashSet<String>();
        String sourceContact = "Contact[" + binding.ordinal() + "]";
        decisions.replaceAll(
                (key, field) ->
                        new CdsExportFieldAssessmentDto(
                                field.presence(),
                                field.outcome(),
                                field.destination().startsWith("Contact[1]")
                                        ? sourceContact
                                                + field.destination()
                                                        .substring("Contact[1]".length())
                                        : field.destination(),
                                field.rule()));
        // Manage Contacts "Delete" (deleted=1) or the editor's "Inactive" (active=0) on an imported
        // association: the chart no longer lists the person as a current contact. As for a native
        // association, a removed one is not exported and an inactive one travels in the native
        // demographic record, both decided by the personal-contact mapper above. The imported
        // Contact element is removed; its source fields stay in the import's retained source and
        // the receipt lists them as replaced.
        if (removedOrInactive) {
            if (!candidate.contacts().isEmpty()) scope();
            if ("1".equals(deleted))
                decisions.put(
                        key(association, "deleted"),
                        new CdsExportFieldAssessmentDto(
                                CdsExportFieldAssessmentDto.Presence.VALUE,
                                Outcome.OPERATIONAL,
                                "",
                                REMOVED_IMPORTED_CONTACT));
            var ids = new HashSet<String>();
            for (var field : binding.fields()) if (!ids.add(field.id())) scope();
            new CdsExportNativeFieldAssessmentValidator()
                    .validate(List.of(contact, association), decisions, 1000, checkpoint);
            values.requireActive();
            checkpoint.run();
            return new Result(
                    binding,
                    decisions,
                    Set.of(),
                    Set.of(),
                    List.of(new CdsExportRetainedSectionWriter.Change(sourceContact, ids, null)),
                    true,
                    false);
        }
        // Any other association state (an unknown flag no CARLOS screen writes) stays an
        // explicit review, never a carry beside the still-retained imported element.
        for (String column : List.of("deleted", "active"))
            if (!(column.equals("deleted") ? "0" : "1")
                    .equals(association.fields().get(column))) {
                String value = association.fields().get(column);
                decisions.put(
                        new CdsExportNativeFieldKeyDto(
                                association.table(), association.recordKey(), column),
                        new CdsExportFieldAssessmentDto(
                                value == null
                                        ? CdsExportFieldAssessmentDto.Presence.SQL_NULL
                                        : value.isEmpty()
                                                ? CdsExportFieldAssessmentDto.Presence.EMPTY
                                                : CdsExportFieldAssessmentDto.Presence.VALUE,
                                Outcome.REQUIRES_REVIEW,
                                sourceContact,
                                "EDITED_IMPORTED_CONTACT_ASSOCIATION_STATE_REQUIRES_REVIEW"));
            }
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        var expected = projection(source);
        var nativeRows = Map.of("Contact", contact, "DemographicContact", association);
        var previous = Map.of("Contact", oldContact, "DemographicContact", oldAssociation);
        var differences =
                Map.of(
                        "Contact",
                        CdsNativeRowComparison.compare(oldContact, contactWitness.state()),
                        "DemographicContact",
                        CdsNativeRowComparison.compare(oldAssociation, associationWitness.state()));
        boolean decideAsCurrent = false;
        var output = candidate.contacts().isEmpty() ? null : candidate.contacts().getFirst();
        boolean marker = source.roles().contains(CdsContactNormalizationService.MULTIPLE_PURPOSES);
        long lastPhone =
                phones.values().stream()
                        .flatMap(List::stream)
                        .mapToLong(CdsExportContactFieldReconciler::ordinal)
                        .max()
                        .orElse(0);
        for (var group : Group.values()) {
            checkpoint.run();
            boolean proved = true;
            for (String column : group.columns)
                proved &=
                        matches(
                                previous.get(group.table()),
                                column,
                                expected.get(group.table()).get(column));
            if (group == Group.NOTE) proved &= matches(oldAssociation, "note", source.note());
            if (!proved) {
                mark(
                        nativeRows,
                        decisions,
                        group,
                        prefix,
                        Outcome.REQUIRES_REVIEW,
                        "PREPARED_NATIVE_CONTACT_PROJECTION_NOT_PROVEN");
                continue;
            }
            var changed = differences.get(group.table()).changedColumns();
            if (group == Group.PURPOSES && output != null && (marker || links.size() > 1)) {
                // Several source purposes behind the importer's marker, or further current
                // associations of the same person: each purpose column replaces only the
                // source purposes it was projected from. Both were holds
                // (EDITED_COMPOUND_OR_UNPROJECTED_CONTACT_VALUE_REQUIRES_REVIEW,
                // RETAINED_CONTACT_PURPOSE_RECONCILIATION_REQUIRED).
                var kept = new HashSet<CdsExportPersonalContactDto.Purpose>();
                boolean replaced = false;
                long lastPurpose = 0;
                for (String path : purposes) {
                    checkpoint.run();
                    lastPurpose = Math.max(lastPurpose, ordinal(path));
                    var coded = relative.get(path + "/PurposeAsEnum[1]");
                    var plain = relative.get(path + "/PurposeAsPlainText[1]");
                    String stated =
                            coded != null
                                    ? read(values, coded, 50, checkpoint)
                                    : plain != null ? read(values, plain, 50, checkpoint) : null;
                    // The importer's rule: EC and SDM set the two flags, anything else is
                    // the relationship.
                    String column =
                            coded != null && "EC".equals(stated)
                                    ? "ec"
                                    : coded != null && "SDM".equals(stated) ? "sdm" : "role";
                    if (changed.contains(column)) {
                        replace(relative, prefix, path, null, changes);
                        replaced = true;
                        continue;
                    }
                    if (stated == null) continue;
                    try {
                        kept.add(
                                new CdsExportPersonalContactDto.Purpose(
                                        coded != null, coded != null ? stated : stated.strip()));
                    } catch (IllegalArgumentException unusual) {
                        // A source purpose this export would not write itself stays as it is;
                        // it equals no current purpose.
                    }
                }
                var wanted = new LinkedHashSet<CdsExportPersonalContactDto.Purpose>();
                for (var link : links) {
                    boolean imported = link == association;
                    if (!imported || changed.contains("role")) {
                        var purpose =
                                CdsExportPersonalContactMapper.rolePurpose(
                                        link.fields().get("role"));
                        if (purpose != null) wanted.add(purpose);
                    }
                    for (String flag : List.of("ec", "sdm"))
                        if ((!imported || changed.contains(flag))
                                && "true".equals(link.fields().get(flag)))
                            wanted.add(
                                    new CdsExportPersonalContactDto.Purpose(
                                            true, flag.equals("ec") ? "EC" : "SDM"));
                }
                wanted.removeAll(kept);
                for (var purpose : wanted) {
                    changes.add(
                            new CdsExportRetainedSectionWriter.Change(
                                    prefix + "/ContactPurpose[" + ++lastPurpose + "]",
                                    Set.of(),
                                    xml ->
                                            xml.element(
                                                    "cds_dt",
                                                    purpose.coded()
                                                            ? "PurposeAsEnum"
                                                            : "PurposeAsPlainText",
                                                    purpose.value())));
                    replaced = true;
                }
                // An unchanged column keeps the import's proven projection; a changed one, and
                // every column of a further association, keeps the native mapper's decision.
                for (String column : group.columns)
                    if (!changed.contains(column)) {
                        var key = key(association, column);
                        decisions.put(
                                key,
                                new CdsExportFieldAssessmentDto(
                                        decisions.get(key).presence(),
                                        Outcome.MAPPED,
                                        prefix + "/" + group.path,
                                        "VERIFIED_UNCHANGED_CONTACT_SOURCE_PROJECTION"));
                    }
                if (!changed.containsAll(group.columns)) retained.add(group.path);
                if (replaced || !Collections.disjoint(group.columns, changed))
                    current.add(group.path);
                continue;
            }
            boolean unchanged = Collections.disjoint(group.columns, changed);
            if (group == Group.NOTE) {
                unchanged &=
                        !differences.get("DemographicContact").changedColumns().contains("note");
                // A further association's own note joins the one Note by the native rule,
                // unless it is the note the element already states.
                for (var link : links)
                    unchanged &=
                            link == association
                                    || link.fields().get("note") == null
                                    || link.fields().get("note").isEmpty()
                                    || link.fields().get("note").equals(source.note());
            }
            if (unchanged) {
                retained.add(group.path);
                mark(
                        nativeRows,
                        decisions,
                        group,
                        prefix,
                        Outcome.MAPPED,
                        "VERIFIED_UNCHANGED_CONTACT_SOURCE_PROJECTION");
                continue;
            }
            if (group == Group.NOTE
                    && !sourceOnly
                    && !Objects.equals(
                            contact.fields().get("note"), association.fields().get("note"))) {
                // Manage Contacts saves the note on the association and the contact editor on
                // the contact: after either, the two differ and the one Note element cannot say
                // which is meant. The contact is decided from its current rows (both notes
                // travel exactly, as for any contact); it was a hold. An element holding
                // source-only content is changed in place below instead, by the same rule.
                decideAsCurrent = true;
                continue;
            }
            String type =
                    group == Group.RESIDENCE
                            ? "R"
                            : group == Group.CELL ? "C" : group == Group.WORK ? "W" : null;
            if (group.columns.stream()
                            .anyMatch(
                                    column ->
                                            decisions
                                                            .get(
                                                                    key(
                                                                            nativeRows.get(
                                                                                    group.table()),
                                                                            column))
                                                            .outcome()
                                                    == Outcome.REQUIRES_REVIEW)
                    || group == Group.NOTE
                            && decisions.get(key(association, "note")).outcome()
                                    == Outcome.REQUIRES_REVIEW) continue;
            if ((group == Group.PURPOSES || type != null) && output == null) {
                mark(
                        nativeRows,
                        decisions,
                        group,
                        prefix,
                        Outcome.REQUIRES_REVIEW,
                        "CURRENT_CONTACT_COMPOUND_MAPPING_REQUIRES_REVIEW");
                continue;
            }
            if (group == Group.PURPOSES) {
                var contents = new ArrayList<CdsExportXmlComposer.Content>();
                for (var purpose : output.purposes())
                    contents.add(
                            xml ->
                                    xml.element(
                                            "cds_dt",
                                            purpose.coded()
                                                    ? "PurposeAsEnum"
                                                    : "PurposeAsPlainText",
                                            purpose.value()));
                repeated(
                        relative,
                        prefix,
                        "ContactPurpose",
                        new ArrayList<>(purposes),
                        contents,
                        changes);
            } else if (type != null) {
                var phone =
                        output.phones().stream()
                                .filter(number -> number.type().equals(type))
                                .findFirst()
                                .orElse(null);
                CdsExportXmlComposer.Content content =
                        phone == null
                                ? null
                                : xml -> {
                                    xml.attribute("", "phoneNumberType", type);
                                    xml.element("cds_dt", "phoneNumber", phone.number());
                                    if (phone.extension() != null)
                                        xml.element("cds_dt", "extension", phone.extension());
                                };
                var old = phones.getOrDefault(type, List.of());
                if (source.phones().get(type) == null || old.isEmpty()) {
                    // Nothing of the source was projected into this column: there was no
                    // phone of the type, or the import kept its phones of the type as source
                    // only (differing repeats, a number no column could hold). Those stay, and
                    // the number typed since is a further PhoneNumber at a new
                    // original-coordinate ordinal across all phone types. An edit beside
                    // source-only phones was a hold.
                    if (content != null)
                        changes.add(
                                new CdsExportRetainedSectionWriter.Change(
                                        prefix + "/PhoneNumber[" + ++lastPhone + "]",
                                        Set.of(),
                                        content));
                } else {
                    // The source phones projected into this column (equal repeats of the type;
                    // a home or cell extension was folded into the column's text) are replaced
                    // by the column's current value, by the native rule. An edit of a column
                    // holding a folded extension or equal repeats was a hold.
                    replace(relative, prefix, old.getFirst(), content, changes);
                    for (String repeat : old.subList(1, old.size()))
                        replace(relative, prefix, repeat, null, changes);
                }
            } else {
                String path =
                        group == Group.GIVEN
                                ? "Name[1]/FirstName[1]"
                                : group == Group.FAMILY
                                        ? "Name[1]/LastName[1]"
                                        : group == Group.EMAIL ? "EmailAddress[1]" : "Note[1]";
                // The native mapper's element text: a value the element cannot hold gives none
                // and travels in the native demographic record (its decision stands above).
                String value;
                if (output != null)
                    value =
                            group == Group.GIVEN
                                    ? output.firstName()
                                    : group == Group.FAMILY
                                            ? output.lastName()
                                            : group == Group.EMAIL ? output.email() : output.note();
                else {
                    value = contact.fields().get(group.columns.iterator().next());
                    if (value != null && value.isEmpty()) value = null;
                }
                final String actual = value;
                replace(
                        relative,
                        prefix,
                        path,
                        actual == null ? null : xml -> xml.text(actual),
                        changes);
                // The first-name column held the source's first and middle names together
                // (the importer joins them), so its current value replaces both. Editing it
                // was a hold.
                if (group == Group.GIVEN)
                    replace(relative, prefix, "Name[1]/MiddleName[1]", null, changes);
            }
            current.add(group.path);
        }
        new CdsExportNativeFieldAssessmentValidator()
                .validate(rows, decisions, 1_000_000, checkpoint);
        values.requireActive();
        checkpoint.run();
        return new Result(binding, decisions, retained, current, changes, false, decideAsCurrent);
    }

    /**
     * The imported Contact element of an association that now names another contact row than
     * the one imported with it (the server accepts a changed contact on an existing association;
     * no form offers it). The association is proven exactly as in {@link #reconcile}: its unit's
     * references, its authenticated baseline row, which named the imported contact for this
     * patient, and its capture witness. Nothing of the imported person is asserted from it any
     * more, so the caller removes the returned element and decides the association with the
     * contact it names, like any association added in CARLOS.
     */
    CdsExportRetainedContactBinding.Result repointed(
            int patient,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            String importedContactId,
            CdsExportCapturedTextRowDto association,
            CdsExportNativeRowReceiptDto associationWitness,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(values);
        Objects.requireNonNull(checkpoint);
        values.requireActive();
        checkpoint.run();
        var binding =
                new CdsExportRetainedContactBinding()
                        .bind(patient, address, index, checked, allFields, checkpoint);
        var refs = checked.units().get(address);
        if (refs == null
                || !refs.stream()
                        .map(CdsExportRetainedReceiptVerificationDto.CheckedReference::expected)
                        .toList()
                        .equals(index.references().get(address))
                || refs.size() != 2
                || refs.stream()
                                .filter(
                                        ref ->
                                                ref.expected().table().equals("Contact")
                                                        && ref.expected()
                                                                .id()
                                                                .equals(importedContactId))
                                .count()
                        != 1
                || refs.stream()
                                .filter(ref -> ref.expected().table().equals("DemographicContact"))
                                .count()
                        != 1) scope();
        var unit =
                index.tables()
                        .get("cds_import_unit")
                        .get(CdsImportKeys.hash(address.substring(0, 36), address.substring(37)));
        CdsNativeBaselineCodec.Document baseline = null;
        if (unit.fields().get("native_baseline") != null) {
            try {
                baseline =
                        CdsNativeBaselineCodec.decode(
                                unit.fields().get("native_baseline"),
                                unit.fields().get("target_references"),
                                unit.fields().get("target_digest"));
            } catch (IllegalArgumentException malformed) {
                scope();
            }
        }
        var oldAssociation =
                qualify(
                        address,
                        index,
                        refs,
                        baseline,
                        association,
                        associationWitness,
                        "DemographicContact",
                        checkpoint);
        if (!matches(oldAssociation, "id", associationWitness.id())
                || !matches(oldAssociation, "demographicNo", Integer.toString(patient))
                || !matches(oldAssociation, "contactId", importedContactId)
                || !matches(oldAssociation, "type", "2")
                || !Integer.toString(patient).equals(association.fields().get("demographicNo"))
                || importedContactId.equals(association.fields().get("contactId"))) scope();
        var ids = new HashSet<String>();
        for (var field : binding.fields()) if (!ids.add(field.id())) scope();
        values.requireActive();
        checkpoint.run();
        return binding;
    }

    private static CdsNativeRowState qualify(
            String address,
            CdsExportRetainedIndexDto index,
            List<CdsExportRetainedReceiptVerificationDto.CheckedReference> references,
            CdsNativeBaselineCodec.Document baseline,
            CdsExportCapturedTextRowDto row,
            CdsExportNativeRowReceiptDto witness,
            String table,
            Runnable checkpoint)
            throws IOException {
        var ref =
                references.stream()
                        .filter(item -> item.expected().table().equals(table))
                        .findFirst()
                        .orElseThrow();
        if (ref.reason() == Reason.ROW_NOT_IN_CAPTURE)
            throw new IOException("CDS_EXPORT_CONTACT_DELETION_REQUIRES_REVIEW");
        requireCapturedWitness(row, witness, checkpoint);
        if (ref.currentDigest() == null)
            throw new IOException("CDS_EXPORT_CONTACT_NATIVE_WITNESS_REQUIRED");
        if (!table.equals(row.table())
                || !ref.expected().id().equals(witness.id())
                || !ref.currentDigest().equals(witness.digest())) scope();
        for (var unit : index.references().entrySet()) {
            checkpoint.run();
            if (!unit.getKey().equals(address)
                    && unit.getValue().stream()
                            .anyMatch(
                                    item ->
                                            item.table().equals(table)
                                                    && item.id().equals(witness.id())))
                throw new IOException("CDS_EXPORT_CONTACT_ALIAS_REQUIRES_REVIEW");
        }
        var old =
                baseline == null
                        ? (ref.reason() == Reason.UNCHANGED_ROW ? witness.state() : null)
                        : baseline.rows().stream()
                                .filter(
                                        item ->
                                                item.table().equals(table)
                                                        && item.id().equals(witness.id()))
                                .findFirst()
                                .orElse(null);
        if (old == null || !old.digest().equals(ref.expected().digest()))
            throw new IOException("CDS_EXPORT_CONTACT_FIELD_BASELINE_REQUIRED");
        var difference = CdsNativeRowComparison.compare(old, witness.state());
        var operational =
                table.equals("Contact") ? Set.of("updateDate") : Set.of("created", "updateDate");
        if (difference.schemaChanged() || !operational.containsAll(difference.unqualifiedColumns()))
            throw new IOException("CDS_EXPORT_CONTACT_FIELD_CODEC_REQUIRES_REVIEW");
        return old;
    }

    /** Same frozen row proof for imported and subsequently added native personal contacts. */
    static void requireCapturedWitness(
            CdsExportCapturedTextRowDto row,
            CdsExportNativeRowReceiptDto witness,
            Runnable checkpoint)
            throws IOException {
        if (row == null || witness == null || witness.state() == null || witness.partial())
            throw new IOException("CDS_EXPORT_CONTACT_NATIVE_WITNESS_REQUIRED");
        if (!Set.of("Contact", "DemographicContact").contains(row.table())
                || !row.table().equals(witness.table())
                || !row.recordKey().equals(witness.recordKey())
                || !witness.id().equals(row.fields().get("id"))
                || !row.exactNumbers().isEmpty()
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        witness.state().columns().stream()
                                                .map(CdsNativeRowDigest.Column::name)
                                                .toList()))) scope();
        for (var column : witness.state().columns()) {
            checkpoint.run();
            if (!matches(witness.state(), column.name(), row.fields().get(column.name()))) scope();
        }
    }

    private static Map<String, Map<String, String>> projection(CdsContactDto source) {
        var contact = new HashMap<String, String>();
        contact.put("firstName", source.firstName());
        contact.put("lastName", source.lastName());
        contact.put("email", source.email());
        contact.put("note", source.note());
        for (var entry :
                Map.of("R", "residencePhone", "C", "cellPhone", "W", "workPhone").entrySet()) {
            var phone = source.phones().get(entry.getKey());
            String number = phone == null ? null : phone.number();
            if (phone != null
                    && !entry.getKey().equals("W")
                    && phone.extension() != null
                    && !phone.extension().isEmpty()) number += " ext " + phone.extension();
            contact.put(entry.getValue(), number);
        }
        var work = source.phones().get("W");
        contact.put("workPhoneExtension", work == null ? null : work.extension());
        var link = new HashMap<String, String>();
        link.put("role", source.roles().getFirst());
        link.put("ec", Boolean.TRUE.equals(source.emergencyContact()) ? "true" : null);
        link.put("sdm", Boolean.TRUE.equals(source.substituteDecisionMaker()) ? "true" : null);
        link.put("note", source.note());
        return Map.of("Contact", contact, "DemographicContact", link);
    }

    private static boolean matches(CdsNativeRowState state, String column, String expected)
            throws IOException {
        int index = -1;
        for (int i = 0; i < state.columns().size(); i++)
            if (state.columns().get(i).name().equals(column)) index = i;
        if (index < 0) scope();
        var cell = state.cells().get(index);
        if (expected == null) return cell.sqlNull();
        byte[] bytes = expected.getBytes(StandardCharsets.UTF_8);
        return !cell.sqlNull()
                && cell.bytes() == bytes.length
                && cell.sha256()
                        .equals(HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes)));
    }

    private static CdsExportNativeFieldKeyDto key(CdsExportCapturedTextRowDto row, String column) {
        return new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column);
    }

    private static void mark(
            Map<String, CdsExportCapturedTextRowDto> rows,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Group group,
            String prefix,
            Outcome outcome,
            String rule) {
        for (String column : group.columns) {
            var key = key(rows.get(group.table()), column);
            fields.put(
                    key,
                    new CdsExportFieldAssessmentDto(
                            fields.get(key).presence(), outcome, prefix + "/" + group.path, rule));
        }
        if (group == Group.NOTE) {
            var key = key(rows.get("DemographicContact"), "note");
            fields.put(
                    key,
                    new CdsExportFieldAssessmentDto(
                            fields.get(key).presence(), outcome, prefix + "/Note", rule));
        }
    }

    private static void replace(
            Map<String, CdsExportRetainedFieldDto> fields,
            String prefix,
            String path,
            CdsExportXmlComposer.Content content,
            List<CdsExportRetainedSectionWriter.Change> changes) {
        var ids = ids(fields, path);
        if (!ids.isEmpty() || content != null)
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(prefix + "/" + path, ids, content));
    }

    private static void repeated(
            Map<String, CdsExportRetainedFieldDto> fields,
            String prefix,
            String element,
            List<String> old,
            List<CdsExportXmlComposer.Content> contents,
            List<CdsExportRetainedSectionWriter.Change> changes) {
        for (int i = 0; i < Math.max(old.size(), contents.size()); i++)
            replace(
                    fields,
                    prefix,
                    i < old.size() ? old.get(i) : element + "[" + (i + 1) + "]",
                    i < contents.size() ? contents.get(i) : null,
                    changes);
    }

    private static Set<String> ids(Map<String, CdsExportRetainedFieldDto> fields, String path) {
        var ids = new HashSet<String>();
        fields.forEach(
                (key, field) -> {
                    if (key.equals(path) || key.startsWith(path + "/")) ids.add(field.id());
                });
        return ids;
    }

    private static long ordinal(String path) {
        return Long.parseLong(path.substring(path.indexOf('[') + 1, path.indexOf(']')));
    }

    private static String read(
            CdsExportRetainedSectionWriter.Values values,
            CdsExportRetainedFieldDto field,
            int maximum,
            Runnable checkpoint)
            throws IOException {
        var result = new StringBuilder();
        char[] buffer = new char[128];
        try (Reader input = values.text(field)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkpoint.run();
                if (count == 0 || result.length() + count > maximum * 2)
                    throw new IOException("CDS_EXPORT_CONTACT_SOURCE_VALUE_REQUIRES_REVIEW");
                result.append(buffer, 0, count);
            }
        } finally {
            Arrays.fill(buffer, '\0');
        }
        if (result.codePointCount(0, result.length()) > maximum)
            throw new IOException("CDS_EXPORT_CONTACT_SOURCE_VALUE_REQUIRES_REVIEW");
        return result.toString();
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_CONTACT_RECONCILIATION_SCOPE_INVALID");
    }
}
