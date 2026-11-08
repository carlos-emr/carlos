package io.github.carlos_emr.carlos.integration.fhir.utils;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.JsonProcessingException;
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
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    private FhirJsonCompatibility() { }

    /**
     * HAPI 8.8 began emitting whitespace-only primitive values. CARLOS previously omitted
     * them. Adapt the encoded tree, rather than changing clinical model values or XML.
     * Primitive extensions/ids live in parallel underscore properties; preserve those
     * and the null placeholders HAPI used when an array value carried metadata.
     * Remove this adapter only with a deliberate review of the outbound JSON contract.
     */
    public static String encode(FhirContext context, BaseResource resource) {
        String encoded = context.newJsonParser().setPrettyPrint(true).encodeResourceToString(resource);
        try {
            JsonNode tree = JSON.readTree(encoded);
            return omitBlankPrimitives(tree) ? JSON.writerWithDefaultPrettyPrinter().writeValueAsString(tree) : encoded;
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
        JsonNode metadata = parent.get("_" + name);
        // Iterate backwards so metadata indices still refer to the original values.
        for (int index = array.size() - 1; index >= 0; index--) {
            JsonNode item = array.get(index);
            if (isBlankText(item)) {
                // Any parallel metadata array makes every position significant,
                // including a blank value preceding another value's extension.
                if (metadata instanceof ArrayNode) {
                    array.set(index, JSON.nullNode());
                } else {
                    array.remove(index);
                }
                changed = true;
            } else {
                changed |= omitBlankPrimitives(item);
            }
        }
        if (array.isEmpty()) {
            parent.remove(name);
        }
        return changed;
    }

    private static boolean isBlankText(JsonNode node) {
        return node != null && node.isTextual() && !node.textValue().isEmpty() && node.textValue().isBlank();
    }
}
