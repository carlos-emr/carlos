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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsSchemaContentOrder;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsSchemaModel;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsWireSchema;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedFieldDto;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Rebuilds one retained section from its scalar paths using the pinned element grammar.
 * This is a serializer, not a reconciliation decision: callers must first qualify source
 * ownership, effective values, native changes, attachment references and vendor identity.
 * Values must come from authenticated export-owned capture, never mutable import staging.
 * Metadata is bounded independently of streamed text/binary sizes. No value is truncated.
 */
public final class CdsExportRetainedSectionWriter {
    private static final Pattern ELEMENT =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_.-]*)\\[([1-9][0-9]{0,17})\\]");
    private static final Pattern ATTRIBUTE = Pattern.compile("@([A-Za-z_][A-Za-z0-9_.-]*)");
    private static final int MAX_FIELDS = 65_536;
    private static final int MAX_NODES = 131_072;
    private static final long MAX_PATH_CHARACTERS = 16L << 20;

    /** Each returned stream belongs to this writer and must authenticate its complete value at EOF/close. */
    public interface Values {
        /** Authenticated providers override this to enforce their callback lifetime and current authority. */
        default void requireActive() throws IOException {}

        Reader text(CdsExportRetainedFieldDto field) throws IOException;

        InputStream binary(CdsExportRetainedFieldDto field) throws IOException;
    }

    /** Exact replacement/delete of one element in original source coordinates; null content means deletion. */
    public record Change(
            String relativePath,
            java.util.Set<String> expectedPriorFields,
            CdsExportXmlComposer.Content content) {
        public Change {
            expectedPriorFields = java.util.Set.copyOf(expectedPriorFields);
        }

        @Override
        public String toString() {
            return "CdsExportRetainedSectionWriter.Change[protected]";
        }
    }

    public record Prepared(
            CdsExportXmlComposer.Content content,
            java.util.Set<String> retainedFields,
            java.util.Set<String> replacedFields) {
        public Prepared {
            retainedFields = java.util.Set.copyOf(retainedFields);
            replacedFields = java.util.Set.copyOf(replacedFields);
        }

        @Override
        public String toString() {
            return "CdsExportRetainedSectionWriter.Prepared[protected]";
        }
    }

    /**
     * Checks the complete bounded structure before returning a scoped composer callback.
     * Its root is already opened by the composer. All fields must belong to the same
     * patient, import run, category and category occurrence; page-unit keys may differ.
     */
    public CdsExportXmlComposer.Content prepare(
            List<CdsExportRetainedFieldDto> supplied, Values values, Runnable checkpoint)
            throws IOException {
        return prepareChanges(supplied, values, List.of(), checkpoint).content();
    }

    /** Unknown source subtrees remain intact. The caller must separately qualify every clinical change and final field coverage. */
    public Prepared prepareChanges(
            List<CdsExportRetainedFieldDto> supplied,
            Values values,
            List<Change> changes,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(values);
        Objects.requireNonNull(checkpoint);
        Objects.requireNonNull(changes);
        changes = List.copyOf(changes);
        if (changes.size() > 4096) invalid();
        if (supplied == null || supplied.isEmpty() || supplied.size() > MAX_FIELDS) invalid();
        var fields = List.copyOf(supplied);
        var first = fields.getFirst();
        String rootPath = rootPath(first);
        var model = CdsWireSchema.schemaModel();
        var type =
                model.element("cds", "OmdCds")
                        .child("cds", "PatientRecord")
                        .child("cds", first.category().wireName());
        var root =
                new Node(new CdsSchemaModel.ExpandedName("cds", first.category().wireName()), type);
        var ids = new HashSet<String>();
        int nodes = 1;
        long paths = 0;
        for (var field : fields) {
            checkpoint.run();
            paths = Math.addExact(paths, field.sourcePath().length());
            if (paths > MAX_PATH_CHARACTERS
                    || !ids.add(field.id())
                    || !first.runId().equals(field.runId())
                    || !first.sourcePatientKey().equals(field.sourcePatientKey())
                    || first.category() != field.category()
                    || !rootPath.equals(rootPath(field))) invalid();
            String relative = field.sourcePath().substring(rootPath.length());
            if (!relative.isEmpty() && !relative.startsWith("/")) invalid();
            String[] parts =
                    relative.isEmpty() ? new String[0] : relative.substring(1).split("/", -1);
            if (parts.length > 128) invalid();
            Node current = root;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                var attribute = ATTRIBUTE.matcher(part);
                if (attribute.matches()) {
                    if (i != parts.length - 1) invalid();
                    var name =
                            new CdsSchemaModel.ExpandedName(field.namespace(), attribute.group(1));
                    if (name.namespace().equals("http://www.w3.org/2001/XMLSchema-instance"))
                        throw new IOException(
                                "CDS_EXPORT_RETAINED_STRUCTURAL_ATTRIBUTE_REQUIRES_REVIEW");
                    var attributeType = current.type.attribute(name.namespace(), name.local());
                    verifyType(attributeType, field);
                    if (current.attributes.putIfAbsent(name, field) != null) invalid();
                    current = null;
                    break;
                }
                var element = ELEMENT.matcher(part);
                if (!element.matches()) invalid();
                String local = element.group(1);
                var matches =
                        current.type.childNames().stream()
                                .filter(name -> name.local().equals(local))
                                .toList();
                if (matches.size() != 1) invalid();
                var name = matches.getFirst();
                long ordinal = Long.parseLong(element.group(2));
                var key = new CdsSchemaContentOrder.Occurrence(name, ordinal);
                Node child = current.children.get(key);
                if (child == null) {
                    if (++nodes > MAX_NODES) invalid();
                    child = new Node(name, current.type.child(name.namespace(), name.local()));
                    current.children.put(key, child);
                }
                current = child;
            }
            if (current != null) {
                if (!current.name.namespace().equals(field.namespace()) || current.value != null)
                    invalid();
                verifyType(current.type, field);
                current.value = field;
            }
        }
        qualify(root, checkpoint);
        var replaced = new HashSet<String>();
        var changedPaths = new HashSet<String>();
        var changeOrder = new Changes();
        for (var change : changes) {
            checkpoint.run();
            String path = change.relativePath();
            if (path == null
                    || path.isEmpty()
                    || path.length() > 2048
                    || path.startsWith("/")
                    || !changedPaths.add(path)) invalid();
            for (String other : changedPaths)
                if (!other.equals(path)
                        && (path.startsWith(other + "/") || other.startsWith(path + "/")))
                    invalid();
            apply(root, change, replaced, changeOrder, checkpoint);
        }
        if (!changes.isEmpty()) {
            changeOrder.finish();
            normalize(root, new int[1], checkpoint);
            qualify(root, checkpoint);
        }
        var retained = new HashSet<>(ids);
        retained.removeAll(replaced);
        CdsExportXmlComposer.Content content =
                xml ->
                        xml.content(
                                scoped -> {
                                    values.requireActive();
                                    emit(root, values, scoped, checkpoint, false);
                                    values.requireActive();
                                });
        return new Prepared(content, retained, replaced);
    }

    private static void apply(
            Node root,
            Change change,
            java.util.Set<String> replaced,
            Changes changeOrder,
            Runnable checkpoint)
            throws IOException {
        String[] parts = change.relativePath().split("/", -1);
        if (parts.length > 128) invalid();
        Node parent = root;
        for (int i = 0; i < parts.length; i++) {
            checkpoint.run();
            var part = ELEMENT.matcher(parts[i]);
            if (!part.matches()) invalid();
            var names =
                    parent.type.childNames().stream()
                            .filter(name -> name.local().equals(part.group(1)))
                            .toList();
            if (names.size() != 1) invalid();
            var name = names.getFirst();
            long ordinal = Long.parseLong(part.group(2));
            var key = new CdsSchemaContentOrder.Occurrence(name, ordinal);
            Node prior = parent.children.get(key);
            if (i < parts.length - 1) {
                if (prior == null || prior.replacement != null) invalid();
                parent = prior;
                continue;
            }
            changeOrder.declare(parent);
            var priorIds = new HashSet<String>();
            if (prior != null) collect(prior, priorIds, checkpoint);
            if (!priorIds.equals(change.expectedPriorFields())
                    || !java.util.Collections.disjoint(priorIds, replaced)) invalid();
            if (change.content() == null) {
                if (prior == null) invalid();
                parent.children.remove(key);
                parent.generatedEmpty = true;
            } else {
                if (parent.value != null) invalid();
                if (prior == null) {
                    changeOrder.add(parent, name, ordinal);
                }
                var node = new Node(name, parent.type.child(name.namespace(), name.local()));
                node.replacement = change.content();
                parent.children.put(key, node);
            }
            replaced.addAll(priorIds);
        }
    }

    /** Additions use original coordinates, independently of deletion order or the caller's list order. */
    private static final class Changes {
        private final Map<Node, Map<CdsSchemaModel.ExpandedName, Long>> maxima =
                new java.util.IdentityHashMap<>();
        private final Map<Node, Map<CdsSchemaModel.ExpandedName, java.util.Set<Long>>> added =
                new java.util.IdentityHashMap<>();

        void declare(Node parent) {
            maxima.computeIfAbsent(
                    parent,
                    ignored -> {
                        var result = new HashMap<CdsSchemaModel.ExpandedName, Long>();
                        for (var key : parent.children.keySet())
                            result.merge(key.name(), key.ordinal(), Math::max);
                        return result;
                    });
        }

        void add(Node parent, CdsSchemaModel.ExpandedName name, long ordinal) throws IOException {
            if (ordinal <= maxima.get(parent).getOrDefault(name, 0L)) invalid();
            if (!added.computeIfAbsent(parent, ignored -> new HashMap<>())
                    .computeIfAbsent(name, ignored -> new HashSet<>())
                    .add(ordinal)) invalid();
        }

        void finish() throws IOException {
            for (var parent : added.entrySet())
                for (var entry : parent.getValue().entrySet()) {
                    long expected = maxima.get(parent.getKey()).getOrDefault(entry.getKey(), 0L);
                    for (long ordinal : entry.getValue().stream().sorted().toList())
                        if (ordinal != ++expected) invalid();
                }
        }
    }

    private static void collect(Node node, java.util.Set<String> ids, Runnable checkpoint)
            throws IOException {
        checkpoint.run();
        if (node.value != null && !ids.add(node.value.id())) invalid();
        for (var attribute : node.attributes.values()) if (!ids.add(attribute.id())) invalid();
        for (var child : node.children.values()) collect(child, ids, checkpoint);
    }

    /** Deletion changes wire ordinals while retaining each remaining field's original captured address. */
    private static void normalize(Node node, int[] count, Runnable checkpoint) throws IOException {
        checkpoint.run();
        if (++count[0] > MAX_NODES) invalid();
        var children =
                node.children.entrySet().stream()
                        .sorted(
                                java.util.Comparator.comparing(
                                                (Map.Entry<CdsSchemaContentOrder.Occurrence, Node>
                                                                entry) ->
                                                        entry.getKey().name().namespace())
                                        .thenComparing(entry -> entry.getKey().name().local())
                                        .thenComparingLong(entry -> entry.getKey().ordinal()))
                        .toList();
        node.children.clear();
        CdsSchemaModel.ExpandedName previous = null;
        long ordinal = 0;
        for (var child : children) {
            if (!child.getKey().name().equals(previous)) {
                previous = child.getKey().name();
                ordinal = 0;
            }
            node.children.put(
                    new CdsSchemaContentOrder.Occurrence(previous, ++ordinal), child.getValue());
            normalize(child.getValue(), count, checkpoint);
        }
    }

    private static String rootPath(CdsExportRetainedFieldDto field) throws IOException {
        try {
            return CdsExportRetainedOccurrencePath.root(field, field.category());
        } catch (IOException invalid) {
            invalid();
            return null;
        }
    }

    private static void verifyType(CdsSchemaModel.Type expected, CdsExportRetainedFieldDto field)
            throws IOException {
        if (expected == null
                || !field.valueType()
                        .equals("{" + expected.getTypeNamespace() + "}" + expected.getTypeName()))
            throw new IOException("CDS_EXPORT_RETAINED_SCHEMA_TYPE_REQUIRES_REVIEW");
    }

    private static void qualify(Node node, Runnable checkpoint) throws IOException {
        checkpoint.run();
        if (node.replacement != null) return;
        if (node.type.simple()) {
            if (node.value == null || !node.children.isEmpty()) invalid();
        } else if (node.children.isEmpty()) {
            if (node.value == null && !node.generatedEmpty) invalid();
        } else if (node.value != null) invalid();
        node.order =
                new CdsSchemaContentOrder()
                        .order(node.type, new ArrayList<>(node.children.keySet()));
        for (var key : node.order) qualify(node.children.get(key), checkpoint);
    }

    private static void emit(
            Node node,
            Values values,
            CdsExportXmlComposer.ContentWriter xml,
            Runnable checkpoint,
            boolean open)
            throws IOException {
        checkpoint.run();
        if (open) xml.start(node.name.namespace(), node.name.local());
        if (node.replacement != null) {
            xml.fragment(node.replacement);
            if (open) xml.end();
            return;
        }
        // Attribute order has no XML meaning, but a stable order makes receipts reproducible.
        var attributes =
                node.attributes.entrySet().stream()
                        .sorted(
                                Map.Entry.comparingByKey(
                                        java.util.Comparator.comparing(
                                                        CdsSchemaModel.ExpandedName::namespace)
                                                .thenComparing(CdsSchemaModel.ExpandedName::local)))
                        .toList();
        for (var attribute : attributes) {
            String text;
            try (Reader input = Objects.requireNonNull(values.text(attribute.getValue()))) {
                text = attribute(input, checkpoint);
            }
            xml.attribute(attribute.getKey().namespace(), attribute.getKey().local(), text);
        }
        if (node.value != null) {
            if (node.type.builtIn().equals("base64Binary")) {
                try (InputStream input = Objects.requireNonNull(values.binary(node.value))) {
                    xml.binary(input);
                }
            } else {
                try (Reader input = Objects.requireNonNull(values.text(node.value))) {
                    if (node.type.simple()) xml.text(input);
                    else if (input.read() != -1)
                        throw new IOException("CDS_EXPORT_RETAINED_COMPLEX_VALUE_REQUIRES_REVIEW");
                }
            }
        }
        for (var key : node.order) emit(node.children.get(key), values, xml, checkpoint, true);
        if (open) xml.end();
    }

    private static String attribute(Reader input, Runnable checkpoint) throws IOException {
        var result = new StringBuilder();
        char[] buffer = new char[1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            checkpoint.run();
            if (count == 0 || result.length() + count > 65_536)
                throw new IOException("CDS_EXPORT_RETAINED_ATTRIBUTE_LIMIT");
            result.append(buffer, 0, count);
        }
        return result.toString();
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_RETAINED_STRUCTURE_REQUIRES_REVIEW");
    }

    private static final class Node {
        private final CdsSchemaModel.ExpandedName name;
        private final CdsSchemaModel.Type type;
        private final Map<CdsSchemaContentOrder.Occurrence, Node> children = new HashMap<>();
        private final Map<CdsSchemaModel.ExpandedName, CdsExportRetainedFieldDto> attributes =
                new HashMap<>();
        private CdsExportRetainedFieldDto value;
        private CdsExportXmlComposer.Content replacement;
        private boolean generatedEmpty;
        private List<CdsSchemaContentOrder.Occurrence> order = List.of();

        private Node(CdsSchemaModel.ExpandedName name, CdsSchemaModel.Type type) {
            this.name = name;
            this.type = Objects.requireNonNull(type);
        }
    }
}
