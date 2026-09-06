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
package io.github.carlos_emr.carlos.documentManager.annotation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the annotation viewer's JSON payload into validated
 * {@link DocumentAnnotationDto} instances.
 *
 * <p>This class is the trust boundary for annotation input. The viewer is a browser
 * page and its payload is attacker-controllable, so every bound below is enforced
 * here rather than in the composer, and the composer is entitled to assume the model
 * it receives is already in range.
 *
 * <table>
 *   <caption>Enforced limits</caption>
 *   <tr><th>Rule</th><th>Limit</th></tr>
 *   <tr><td>Annotations per document</td><td>{@value #MAX_ANNOTATIONS}</td></tr>
 *   <tr><td>Ink points per stroke</td><td>{@value #MAX_POINTS_PER_STROKE}</td></tr>
 *   <tr><td>Text length</td><td>{@value #MAX_TEXT_LENGTH} characters</td></tr>
 *   <tr><td>Font size</td><td>{@value #MIN_FONT_SIZE} to {@value #MAX_FONT_SIZE} points</td></tr>
 *   <tr><td>Stroke width</td><td>{@value #MIN_STROKE_WIDTH} to {@value #MAX_STROKE_WIDTH} points</td></tr>
 *   <tr><td>Geometry</td><td>0 &le; x, y &le; 1; x + w &le; 1; y + h &le; 1</td></tr>
 *   <tr><td>Page</td><td>1 to the document's page count</td></tr>
 *   <tr><td>Colour</td><td>allowlist: {@code yellow green blue pink red black}</td></tr>
 * </table>
 *
 * <p>Rejection messages name the rule that failed and never echo the offending value,
 * because annotation text is PHI and the message reaches a browser.
 *
 * @since 2026-09
 */
public class DocumentAnnotationParser {

    public static final int MAX_ANNOTATIONS = 500;
    public static final int MAX_POINTS_PER_STROKE = 5_000;
    public static final int MAX_TEXT_LENGTH = 2_000;
    public static final double MIN_FONT_SIZE = 6d;
    public static final double MAX_FONT_SIZE = 36d;
    public static final double MIN_STROKE_WIDTH = 0.5d;
    public static final double MAX_STROKE_WIDTH = 8d;

    /**
     * Colour names the composer knows how to map to RGB. Kept as an allowlist rather
     * than accepting a hex value so no caller-supplied string ever reaches a drawing
     * or styling context.
     */
    public static final Set<String> ALLOWED_COLORS =
            Set.of("yellow", "green", "blue", "pink", "red", "black");

    private static final double DEFAULT_FONT_SIZE = 11d;
    private static final double DEFAULT_STROKE_WIDTH = 2d;
    private static final String DEFAULT_COLOR = "black";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses and validates the viewer payload.
     *
     * @param json      the raw request body
     * @param pageCount the source document's page count, used as the upper page bound
     * @return the validated annotations, in payload order; never {@code null}, possibly empty
     * @throws IllegalArgumentException if the payload is malformed or violates any documented
     *                                  limit. The message names the rule and is safe to show a
     *                                  user; it never contains annotation content.
     */
    public List<DocumentAnnotationDto> parse(String json, int pageCount) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("No annotation data was received.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // The parse failure detail can quote the payload, which may hold PHI.
            throw new IllegalArgumentException("The annotation data was not valid JSON.");
        }

        JsonNode array = root.get("annotations");
        if (array == null || !array.isArray()) {
            throw new IllegalArgumentException("The annotation data has no annotations list.");
        }
        if (array.size() > MAX_ANNOTATIONS) {
            throw new IllegalArgumentException(
                    "A document cannot carry more than " + MAX_ANNOTATIONS + " annotations.");
        }

        List<DocumentAnnotationDto> result = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            result.add(parseOne(node, pageCount));
        }
        return result;
    }

    private DocumentAnnotationDto parseOne(JsonNode node, int pageCount) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Each annotation must be an object.");
        }

        DocumentAnnotationDto.Type type = parseType(node.path("type").asText(null));

        int page = node.path("page").asInt(0);
        if (page < 1 || page > pageCount) {
            throw new IllegalArgumentException(
                    "An annotation names page " + page + ", outside the document's 1 to " + pageCount + ".");
        }

        String color = node.hasNonNull("color")
                ? node.get("color").asText("").trim().toLowerCase(Locale.ROOT)
                : DEFAULT_COLOR;
        if (!ALLOWED_COLORS.contains(color)) {
            throw new IllegalArgumentException("An annotation uses a colour that is not permitted.");
        }

        if (type == DocumentAnnotationDto.Type.INK) {
            return new DocumentAnnotationDto(type, page, 0, 0, 0, 0,
                    parsePoints(node.get("points")), null, color,
                    bounded(node, "strokeWidth", DEFAULT_STROKE_WIDTH,
                            MIN_STROKE_WIDTH, MAX_STROKE_WIDTH, "Stroke width"),
                    DEFAULT_FONT_SIZE);
        }

        double x = coordinate(node, "x");
        double y = coordinate(node, "y");
        double w = coordinate(node, "w");
        double h = coordinate(node, "h");
        if (x + w > 1d || y + h > 1d) {
            throw new IllegalArgumentException("An annotation extends past the edge of its page.");
        }
        if (w <= 0d || h <= 0d) {
            throw new IllegalArgumentException("An annotation has no width or height.");
        }

        String text = null;
        if (type == DocumentAnnotationDto.Type.TEXT || type == DocumentAnnotationDto.Type.DATE) {
            text = node.path("text").asText("");
            if (text.isBlank()) {
                throw new IllegalArgumentException("A text annotation has no content.");
            }
            if (text.length() > MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException(
                        "A text annotation is longer than " + MAX_TEXT_LENGTH + " characters.");
            }
        }

        return new DocumentAnnotationDto(type, page, x, y, w, h, List.of(), text, color,
                DEFAULT_STROKE_WIDTH,
                bounded(node, "fontSize", DEFAULT_FONT_SIZE, MIN_FONT_SIZE, MAX_FONT_SIZE, "Font size"));
    }

    private static DocumentAnnotationDto.Type parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("An annotation has no type.");
        }
        try {
            return DocumentAnnotationDto.Type.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // Deliberately does not echo the value: it came from the request.
            throw new IllegalArgumentException("An annotation has an unrecognised type.");
        }
    }

    private static double coordinate(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException("An annotation is missing its " + field + " coordinate.");
        }
        double d = value.asDouble();
        if (!Double.isFinite(d) || d < 0d || d > 1d) {
            throw new IllegalArgumentException(
                    "An annotation's " + field + " coordinate is outside the page.");
        }
        return d;
    }

    private static double bounded(JsonNode node, String field, double fallback,
                                  double min, double max, String label) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            return fallback;
        }
        double d = value.asDouble();
        if (!Double.isFinite(d) || d < min || d > max) {
            throw new IllegalArgumentException(
                    label + " must be between " + min + " and " + max + ".");
        }
        return d;
    }

    private static List<double[]> parsePoints(JsonNode points) {
        if (points == null || !points.isArray() || points.isEmpty()) {
            throw new IllegalArgumentException("A freehand annotation has no points.");
        }
        if (points.size() > MAX_POINTS_PER_STROKE) {
            throw new IllegalArgumentException(
                    "A freehand annotation has more than " + MAX_POINTS_PER_STROKE + " points.");
        }
        List<double[]> parsed = new ArrayList<>(points.size());
        for (JsonNode point : points) {
            if (!point.isArray() || point.size() != 2
                    || !point.get(0).isNumber() || !point.get(1).isNumber()) {
                throw new IllegalArgumentException("A freehand point is not an [x, y] pair.");
            }
            double px = point.get(0).asDouble();
            double py = point.get(1).asDouble();
            if (!Double.isFinite(px) || !Double.isFinite(py)
                    || px < 0d || px > 1d || py < 0d || py > 1d) {
                throw new IllegalArgumentException("A freehand point lies outside the page.");
            }
            parsed.add(new double[]{px, py});
        }
        return parsed;
    }
}
