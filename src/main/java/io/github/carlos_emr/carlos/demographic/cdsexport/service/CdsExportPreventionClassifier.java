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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/**
 * Exact category classification from captured configuration/catalogue evidence. This does not
 * select a clinical code, establish row disclosure authority or qualify native immunization
 * export. Callers must authenticate both the XML bytes and catalogue rows in the bound source
 * generation before constructing this immutable classifier. Unknown and conflicting types
 * require review; they never silently become immunizations or out-of-scope screening facts.
 */
public final class CdsExportPreventionClassifier {
    public enum Kind {
        IMMUNIZATION,
        NON_IMMUNIZATION,
        REQUIRES_REVIEW
    }

    public enum Rule {
        CONFIGURED_IMMUNIZATION,
        CVC_IMMUNIZATION,
        CONFIGURED_NON_IMMUNIZATION,
        UNKNOWN_TYPE,
        CATEGORY_NOT_PROVEN,
        UNSUPPORTED_LAYOUT,
        CONFLICTING_CATEGORY,
        UNRESOLVED_CVC_MAPPING,
        UNRESOLVED_NON_IMMUNIZATION_CODE
    }

    public record Definition(String nativeName, String healthCanadaType) {
        @Override
        public String toString() {
            return "CdsExportPreventionClassifier.Definition[protected]";
        }
    }

    public record Decision(Kind kind, Rule rule) {
        public Decision {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(rule);
        }
    }

    private static final int MAX_XML = 1_048_576,
            MAX_ROWS = 10_000,
            MAX_CATALOGUE_BYTES = 4_194_304;
    private static final Set<String> VACCINE_COLUMNS =
            Set.of(
                    "displayName",
                    "generic",
                    "id",
                    "ispa",
                    "parentConceptId",
                    "picklistName",
                    "prevalence",
                    "snomedConceptId",
                    "versionId");
    private static final Set<String> MAPPING_COLUMNS =
            Set.of("cvcSnomedId", "id", "oscarName", "preferCVC");
    private final String sourceManifestSha256, configurationSha256, catalogueSha256;
    private final Map<String, Definition> definitions;
    private final Map<String, Kind> configured;
    private final Map<String, Rule> configuredReviews;
    private final Set<String> vaccineNames, vaccineCodes, unresolvedMappings;

    public static CdsExportPreventionClassifier fromCaptured(
            String sourceManifestSha256,
            byte[] configuration,
            Iterable<CdsExportCapturedTextRowDto> catalogue)
            throws IOException {
        if (sourceManifestSha256 == null || !sourceManifestSha256.matches("[0-9a-f]{64}"))
            invalid();
        return new CdsExportPreventionClassifier(sourceManifestSha256, configuration, catalogue);
    }

    static CdsExportPreventionClassifier forSelection(
            byte[] configuration, Iterable<CdsExportCapturedTextRowDto> catalogue)
            throws IOException {
        return new CdsExportPreventionClassifier(null, configuration, catalogue);
    }

    private CdsExportPreventionClassifier(
            String source, byte[] configuration, Iterable<CdsExportCapturedTextRowDto> catalogue)
            throws IOException {
        if (source != null && !source.matches("[0-9a-f]{64}")
                || configuration == null
                || configuration.length < 1
                || configuration.length > MAX_XML
                || catalogue == null) invalid();
        sourceManifestSha256 = source;
        byte[] xml = configuration.clone();
        configurationSha256 = sha256(xml);
        var extracted = new HashMap<String, Definition>();
        var types = new HashMap<String, Kind>();
        var reviewRules = new HashMap<String, Rule>();
        try {
            var reader =
                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsXmlReaders.reader(
                            128, 1024, 2, 64, 4);
            var handler =
                    new org.xml.sax.helpers.DefaultHandler() {
                        private int depth, items;
                        private boolean root;

                        @Override
                        public void startElement(
                                String uri,
                                String local,
                                String qualified,
                                org.xml.sax.Attributes attributes)
                                throws org.xml.sax.SAXException {
                            if (!uri.isEmpty() || ++depth > 2 || attributes.getLength() > 64)
                                reject();
                            for (int i = 0; i < attributes.getLength(); i++)
                                if (!attributes.getURI(i).isEmpty()
                                        || attributes.getValue(i).length() > 4096) reject();
                            if (depth == 1) {
                                if (root || !"preventions".equals(local)) reject();
                                root = true;
                                return;
                            }
                            String name = attributes.getValue("name"),
                                    layout = attributes.getValue("layout");
                            if (!"item".equals(local)
                                    || ++items > MAX_ROWS
                                    || name == null
                                    || name.isEmpty()
                                    || !CdsExportNativeFieldAssessments.legal(name, 255)) reject();
                            String healthType = attributes.getValue("healthCanadaType"),
                                    heading = attributes.getValue("headingName");
                            boolean vaccineLayout =
                                    "injection".equals(layout) || "h1n1".equals(layout);
                            boolean supported =
                                    vaccineLayout
                                            || "PAPMAM".equals(layout)
                                            || "history".equals(layout);
                            boolean immunizationDeclared =
                                    healthType != null && !healthType.isBlank();
                            Kind kind;
                            if (!supported) {
                                kind = Kind.REQUIRES_REVIEW;
                                reviewRules.put(name, Rule.UNSUPPORTED_LAYOUT);
                            } else if (immunizationDeclared) {
                                if (!CdsExportNativeFieldAssessments.legal(healthType, 255))
                                    reject();
                                if (!vaccineLayout || "Screenings".equals(heading)) {
                                    kind = Kind.REQUIRES_REVIEW;
                                    reviewRules.put(name, Rule.CONFLICTING_CATEGORY);
                                } else kind = Kind.IMMUNIZATION;
                            } else if ("Screenings".equals(heading)
                                    || "OtherB".equals(name)
                                            && "PAPMAM".equals(layout)
                                            && "Other".equals(heading))
                                kind = Kind.NON_IMMUNIZATION;
                            else {
                                kind = Kind.REQUIRES_REVIEW;
                                reviewRules.put(name, Rule.CATEGORY_NOT_PROVEN);
                            }
                            extracted.put(name, new Definition(name, healthType));
                            if (types.putIfAbsent(name, kind) != null) reject();
                        }

                        @Override
                        public void endElement(String uri, String local, String qualified) {
                            depth--;
                        }

                        @Override
                        public void characters(char[] text, int start, int length)
                                throws org.xml.sax.SAXException {
                            for (int i = start; i < start + length; i++)
                                if (!Character.isWhitespace(text[i])) reject();
                        }

                        @Override
                        public void error(org.xml.sax.SAXParseException failed)
                                throws org.xml.sax.SAXException {
                            reject();
                        }

                        @Override
                        public void fatalError(org.xml.sax.SAXParseException failed)
                                throws org.xml.sax.SAXException {
                            reject();
                        }

                        private void reject() throws org.xml.sax.SAXException {
                            throw new org.xml.sax.SAXException(
                                    "CDS_EXPORT_PREVENTION_CLASSIFICATION_INVALID");
                        }
                    };
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            reader.parse(new org.xml.sax.InputSource(new ByteArrayInputStream(xml)));
            if (types.isEmpty()) invalid();
        } catch (org.xml.sax.SAXException
                | javax.xml.parsers.ParserConfigurationException
                | RuntimeException failed) {
            throw new IOException("CDS_EXPORT_PREVENTION_CLASSIFICATION_INVALID");
        }
        definitions = Map.copyOf(extracted);
        configured = Map.copyOf(types);
        configuredReviews = Map.copyOf(reviewRules);
        var rows = new TreeMap<String, CdsExportCapturedTextRowDto>();
        long bytes = 0;
        for (var row : catalogue) {
            if (row == null || rows.size() >= MAX_ROWS) invalid();
            Set<String> columns =
                    switch (row.table()) {
                        case "CVCImmunization" -> VACCINE_COLUMNS;
                        case "CVCMapping" -> MAPPING_COLUMNS;
                        default -> Set.of();
                    };
            if (columns.isEmpty()
                    || !row.fields().keySet().equals(columns)
                    || !row.exactNumbers().isEmpty()) invalid();
            String id = row.fields().get("id");
            try {
                int number = Integer.parseInt(id);
                if (!Integer.toString(number).equals(id)
                        || !CdsExportPatientTableSourceReader.key(number).equals(row.recordKey()))
                    invalid();
            } catch (NumberFormatException failed) {
                invalid();
            }
            if (rows.putIfAbsent(row.table() + ":" + row.recordKey(), row) != null) invalid();
            for (String value : row.fields().values())
                if (value != null) {
                    if (!CdsExportNativeFieldAssessments.legal(value, 255)) invalid();
                    bytes = Math.addExact(bytes, value.getBytes(StandardCharsets.UTF_8).length);
                    if (bytes > MAX_CATALOGUE_BYTES) invalid();
                }
            for (String field :
                    row.table().equals("CVCMapping")
                            ? Set.of("preferCVC")
                            : Set.of("generic", "ispa")) {
                String value = row.fields().get(field);
                if (value != null && !Set.of("0", "1").contains(value)) invalid();
            }
        }
        var names = new HashSet<String>();
        var codes = new HashSet<String>();
        for (var row : rows.values())
            if (row.table().equals("CVCImmunization")) {
                add(names, row.fields().get("picklistName"));
                add(codes, row.fields().get("snomedConceptId"));
            }
        var unresolved = new HashSet<String>();
        var mappedNames = new HashSet<String>();
        for (var row : rows.values())
            if (row.table().equals("CVCMapping")) {
                String name = row.fields().get("oscarName"), code = row.fields().get("cvcSnomedId");
                if (name == null || name.isEmpty() || !mappedNames.add(name)) invalid();
                if (code != null && !code.isEmpty() && codes.contains(code)) names.add(name);
                else unresolved.add(name);
            }
        vaccineNames = Set.copyOf(names);
        vaccineCodes = Set.copyOf(codes);
        unresolvedMappings = Set.copyOf(unresolved);
        var digest = digest();
        try (var data =
                new DataOutputStream(
                        new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            frame(data, "CDS_EXPORT_PREVENTION_CATALOGUE_V1");
            for (var row : rows.values()) {
                frame(data, row.table());
                frame(data, row.recordKey());
                for (var entry : new TreeMap<>(row.fields()).entrySet()) {
                    frame(data, entry.getKey());
                    frame(data, entry.getValue());
                }
            }
        }
        catalogueSha256 = HexFormat.of().formatHex(digest.digest());
    }

    public String sourceManifestSha256() {
        if (sourceManifestSha256 == null)
            throw new IllegalStateException("CDS_EXPORT_PREVENTION_CLASSIFIER_UNBOUND");
        return sourceManifestSha256;
    }

    public String configurationSha256() {
        return configurationSha256;
    }

    public String catalogueSha256() {
        return catalogueSha256;
    }

    public java.util.Optional<Definition> definition(String nativeType) {
        return java.util.Optional.ofNullable(
                nativeType == null ? null : definitions.get(nativeType));
    }

    public Decision classify(String nativeType, String nativeSnomed) {
        Kind configuredKind = nativeType == null ? null : configured.get(nativeType);
        boolean byName = nativeType != null && vaccineNames.contains(nativeType),
                byCode = nativeSnomed != null && vaccineCodes.contains(nativeSnomed);
        Rule configuredReview = nativeType == null ? null : configuredReviews.get(nativeType);
        if (configuredReview == Rule.UNSUPPORTED_LAYOUT
                || configuredReview == Rule.CONFLICTING_CATEGORY)
            return new Decision(Kind.REQUIRES_REVIEW, configuredReview);
        if (nativeType != null && unresolvedMappings.contains(nativeType))
            return new Decision(Kind.REQUIRES_REVIEW, Rule.UNRESOLVED_CVC_MAPPING);
        if (configuredKind == Kind.NON_IMMUNIZATION && (byName || byCode))
            return new Decision(Kind.REQUIRES_REVIEW, Rule.CONFLICTING_CATEGORY);
        if (configuredKind == Kind.IMMUNIZATION)
            return new Decision(Kind.IMMUNIZATION, Rule.CONFIGURED_IMMUNIZATION);
        if (byName || byCode) return new Decision(Kind.IMMUNIZATION, Rule.CVC_IMMUNIZATION);
        if (configuredKind == Kind.NON_IMMUNIZATION)
            return nativeSnomed == null || nativeSnomed.isEmpty()
                    ? new Decision(Kind.NON_IMMUNIZATION, Rule.CONFIGURED_NON_IMMUNIZATION)
                    : new Decision(Kind.REQUIRES_REVIEW, Rule.UNRESOLVED_NON_IMMUNIZATION_CODE);
        return new Decision(
                Kind.REQUIRES_REVIEW,
                configuredReview == Rule.CATEGORY_NOT_PROVEN
                        ? Rule.CATEGORY_NOT_PROVEN
                        : Rule.UNKNOWN_TYPE);
    }

    private static void add(Set<String> target, String value) {
        if (value != null && !value.isEmpty()) target.add(value);
    }

    private static void frame(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failed) {
            throw new IllegalStateException(failed);
        }
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_PREVENTION_CLASSIFICATION_INVALID");
    }

    @Override
    public String toString() {
        return "CdsExportPreventionClassifier[protected]";
    }
}
