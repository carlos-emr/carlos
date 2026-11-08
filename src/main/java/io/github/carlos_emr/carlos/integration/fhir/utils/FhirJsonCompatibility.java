/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.fhir.utils;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hl7.fhir.dstu3.model.BaseResource;

import java.util.ArrayList;

/** Preserves CARLOS's pre-8.8 HAPI JSON contract for whitespace-only primitives. */
public final class FhirJsonCompatibility {
    // A blank field elsewhere in a bundle must never round a clinical quantity or
    // discard its declared decimal precision when the encoded tree is rewritten.
    // This reader only consumes our own already-materialized HAPI output. Its default
    // 20M-character string cap would newly reject existing PDF attachments after Base64
    // encoding. Relax that cap here only; inbound FHIR parsers keep their own limits.
    private static final ObjectMapper JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxStringLength(Integer.MAX_VALUE).build())
                    // Match HAPI's plain decimal spelling, including tiny quantities.
                    .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN).build())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();
    private static final DefaultPrettyPrinter HAPI_LAYOUT = new DefaultPrettyPrinter()
            .withSeparators(Separators.createDefaultInstance()
                    .withObjectFieldValueSpacing(Separators.Spacing.AFTER));

    static {
        HAPI_LAYOUT.indentObjectsWith(new DefaultIndenter("  ", "\n"));
    }

    private FhirJsonCompatibility() { }

    /**
     * HAPI 8.8 began emitting whitespace-only primitive values. CARLOS previously omitted
     * them. Adapt the encoded tree, rather than changing clinical model values or XML.
     * Primitive extensions/ids live in parallel underscore properties; preserve those
     * and the null placeholders HAPI used when an array value carried metadata.
     * Remove this adapter only with a deliberate review of the outbound JSON contract.
     *
     * @throws IllegalStateException if the encoded JSON cannot be read or rewritten,
     *         including when its nesting depth or number length exceeds Jackson's limits;
     *         callers must refuse the export rather than send an incomplete message
     */
    public static String encode(FhirContext context, BaseResource resource) {
        String encoded = context.newJsonParser().setPrettyPrint(true).encodeResourceToString(resource);
        try {
            JsonNode tree = JSON.readTree(encoded);
            return omitBlankPrimitives(tree) ? JSON.writer(HAPI_LAYOUT).writeValueAsString(tree) : encoded;
        } catch (JsonProcessingException exception) {
            // Refuse the export; never return an empty or partially rewritten clinical message.
            throw new IllegalStateException("Unable to encode the FHIR JSON compatibility contract", exception);
        }
    }

    private static boolean omitBlankPrimitives(JsonNode node) {
        boolean changed = false;
        if (node instanceof ObjectNode object) {
            // Snapshot names: removing fields while traversing an object's iterator is unsafe.
            var names = new ArrayList<String>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode value = object.get(name);
                if (isBlankText(value)) {
                    object.remove(name);
                    changed = true;
                } else if (value instanceof ArrayNode array) {
                    changed |= omitBlankArrayValues(object, name, array);
                } else {
                    changed |= omitBlankPrimitives(value);
                }
            }
        }
        return changed;
    }

    private static boolean omitBlankArrayValues(ObjectNode parent, String name, ArrayNode array) {
        boolean changed = false;
        JsonNode metadataNode = parent.get("_" + name);
        ArrayNode metadata = metadataNode instanceof ArrayNode arrayNode ? arrayNode : null;
        // Remove corresponding slots backwards so remaining values keep their own metadata.
        for (int index = array.size() - 1; index >= 0; index--) {
            JsonNode item = array.get(index);
            if (isBlankText(item)) {
                JsonNode slot = metadata == null ? null : metadata.get(index);
                // Only this value's id/extension justifies retaining a null placeholder.
                // A null/null pair would decode as an extra empty FHIR repetition.
                if (slot != null && !slot.isNull()) {
                    array.set(index, JSON.nullNode());
                } else {
                    array.remove(index);
                    if (metadata != null) {
                        metadata.remove(index);
                    }
                }
                changed = true;
            } else {
                changed |= omitBlankPrimitives(item);
            }
        }
        if (array.isEmpty()) {
            parent.remove(name);
        }
        if (metadata != null && allNull(metadata)) {
            parent.remove("_" + name);
            changed = true;
        }
        return changed;
    }

    private static boolean allNull(ArrayNode array) {
        for (JsonNode node : array) {
            if (!node.isNull()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlankText(JsonNode node) {
        return node != null && node.isTextual() && !node.textValue().isEmpty() && node.textValue().isBlank();
    }
}
