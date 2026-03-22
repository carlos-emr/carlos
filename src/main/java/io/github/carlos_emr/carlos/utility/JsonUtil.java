/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.utility;

import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Utility class for JSON serialization and deserialization using Jackson.
 *
 * <p>Provides convenience methods for converting between POJOs and JSON strings/nodes,
 * including collection handling and field filtering. Uses a shared {@link ObjectMapper}
 * instance for performance.
 *
 * @since 2026-03-17
 */
public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Converts a list of POJOs to a JSON array string.
     *
     * @param pojoList List&lt;?&gt; the list of objects to serialize
     * @return String the JSON array representation
     */
    public static final String pojoCollectionToJson(final List<?> pojoList) {
        return pojoCollectionToJson(pojoList, null);
    }

    /**
     * Converts a list of POJOs to a JSON array string, excluding specified fields.
     *
     * @param pojoList      List&lt;?&gt; the list of objects to serialize
     * @param ignoreMethods String[] field names to exclude from the output
     * @return String the JSON array representation
     */
    public static final String pojoCollectionToJson(final List<?> pojoList, String[] ignoreMethods) {
        try {
            ArrayNode arrayNode = objectMapper.createArrayNode();

            if (pojoList != null && !pojoList.isEmpty()) {
                Iterator<?> it = pojoList.iterator();
                while (it.hasNext()) {
                    ObjectNode objNode = pojoToJson(it.next(), ignoreMethods);
                    arrayNode.add(objNode);
                }
            }

            return objectMapper.writeValueAsString(arrayNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert POJO collection to JSON", e);
        }
    }

    /**
     * Converts a POJO to a Jackson {@link ObjectNode}.
     *
     * @param pojo Object the object to convert
     * @return ObjectNode the JSON object representation
     */
    public static final ObjectNode pojoToJson(final Object pojo) {
        return pojoToJson(pojo, null);
    }

    /**
     * Converts a POJO to a Jackson {@link ObjectNode}, excluding specified fields.
     *
     * @param pojo          Object the object to convert
     * @param ignoreMethods String[] field names to exclude
     * @return ObjectNode the JSON object representation
     */
    public static final ObjectNode pojoToJson(final Object pojo, final String[] ignoreMethods) {
        ObjectNode objectNode = objectMapper.valueToTree(pojo);

        // Remove ignored fields if specified
        if (ignoreMethods != null) {
            for (String field : ignoreMethods) {
                objectNode.remove(field);
            }
        }

        return objectNode;
    }

    public static final List<?> jsonToPojoList(final String json, final Class<?> clazz) {
        try {
            JsonNode jsonNode = objectMapper.readTree(json);
            return jsonToPojoList(jsonNode, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON to POJO list", e);
        }
    }

    public static final List<?> jsonToPojoList(final JsonNode jsonNode, final Class<?> clazz) {
        try {
            return objectMapper.convertValue(jsonNode,
                objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Failed to convert JSON node to POJO list", e);
        }
    }

    public static final Object jsonToPojo(final String json, final Class<?> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON to POJO", e);
        }
    }

    public static final Object jsonToPojo(final JsonNode jsonNode, final Class<?> clazz) {
        try {
            return objectMapper.treeToValue(jsonNode, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert JSON node to POJO", e);
        }
    }

}
