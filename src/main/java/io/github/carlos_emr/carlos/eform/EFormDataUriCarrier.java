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
package io.github.carlos_emr.carlos.eform;

import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Restores the signature and drawing images an eForm page carried in {@value #PARAMETER} back into
 * the clinic's own form fields when the eForm is saved.
 *
 * <p><b>Why the values travel separately (issue #3552).</b> A signature pad stores the drawn
 * signature as a data URI ({@code data:image/png;base64,...}, or jSignature's
 * {@code data:image/jsignature;base30,...}) in one of the form's own fields. Those field names are
 * chosen by whoever wrote the eForm, so the front-door WAF cannot name them, and the data URI matches
 * the CRS attribute-XSS rules 941130/941170. The WAF used to remove those two rules for the whole save
 * request, which also stopped them checking every other eForm field. The eForm page script
 * ({@code eform_floating_toolbar.js}) now moves each such value into the fixed {@value #PARAMETER}
 * argument while the browser builds the request, and leaves {@value #REFERENCE_PREFIX}{@code <n>}
 * in the field, where {@code n} is the value's position among the {@value #PARAMETER} values. The
 * WAF then excludes only {@value #PARAMETER}, and the save restores each field to what the page held.
 * The stored eForm and its values are the same as if the field had been posted directly.</p>
 *
 * <p><b>Security boundary.</b> {@value #PARAMETER} is the one eForm argument the WAF does not score
 * for XSS, SQL injection or command injection, so this class is what keeps it from carrying anything
 * else. A value is accepted only when it is entirely one of:</p>
 * <ul>
 *   <li>a raster image, {@code data:image/(png|jpeg|gif|webp);base64,<payload>}, whose payload is
 *       padded, strict Base64 that decodes to at most {@value #MAX_DECODED_BYTES} bytes starting
 *       with that format's file signature; or</li>
 *   <li>a jSignature stroke list, {@code data:image/jsignature;base30,<payload>}, whose payload uses
 *       only jSignature's base30 alphabet ({@code 0-9 a-z A-Z _}).</li>
 * </ul>
 * <p>Neither shape contains a quote, angle bracket, whitespace or a second scheme, so a restored
 * value cannot break out of the attribute or script context the eForm renders it into, and it can
 * only name an image. SVG ({@code image/svg+xml}), which can carry script, is never accepted here:
 * a field holding one is posted as it is and stays subject to the full XSS rules.</p>
 *
 * <p>Any invalid carried value, an over-long list, or a field that names a carried value that does
 * not exist rejects the whole submission rather than saving the eForm without its signature: the
 * page never produces those, so they mean a tampered or broken request. When the request carries no
 * {@value #PARAMETER} value at all, field values are left untouched, including one that happens to
 * read like a reference.</p>
 *
 * @since 2026-10-22
 */
public final class EFormDataUriCarrier {

    /** The request argument the eForm page moves field data URIs into. Must match the WAF rule 1040. */
    public static final String PARAMETER = "carlos-data-uri";

    /** The prefix of the value left in a field whose data URI was moved; followed by its index. */
    public static final String REFERENCE_PREFIX = "carlos-data-uri:";

    /**
     * The most carried values one submission may hold. A real eForm has a handful of signature pads
     * (and their mirrored "slave" pads); this only bounds work on a hostile request.
     */
    static final int MAX_VALUES = 256;

    /** The largest decoded raster image accepted, matching the signature-pad upload endpoint. */
    static final int MAX_DECODED_BYTES = 5 * 1024 * 1024;

    /**
     * The longest Base64 payload accepted: whole four-character groups whose decoded size cannot
     * exceed {@link #MAX_DECODED_BYTES} (6,990,504 characters). The page script uses the same number.
     */
    static final int MAX_BASE64_CHARS = (MAX_DECODED_BYTES / 3) * 4;

    /** jSignature strokes are far smaller than an image; the bound only limits a hostile value. */
    static final int MAX_BASE30_CHARS = MAX_BASE64_CHARS;

    // At most three digits (MAX_VALUES is 256; restore() checks the index); no leading zeros.
    private static final Pattern REFERENCE = Pattern.compile(Pattern.quote(REFERENCE_PREFIX) + "(0|[1-9][0-9]{0,2})");
    // Lower case only, exactly as canvas.toDataURL() and jSignature write them; the page script
    // matches the same shapes, so a value either side would not carry is never sent here.
    private static final Pattern RASTER_PREFIX = Pattern.compile("data:image/(png|jpeg|gif|webp);base64,");
    private static final String JSIGNATURE_PREFIX = "data:image/jsignature;base30,";
    private static final Pattern BASE30_PAYLOAD = Pattern.compile("[0-9A-Za-z_]*");

    private EFormDataUriCarrier() {
    }

    /**
     * Why a submission's carried values were refused. The message names only the rule that failed,
     * never the value, which is a patient's or provider's signature.
     */
    public static final class InvalidCarriedValueException extends Exception {
        private static final long serialVersionUID = 1L;

        InvalidCarriedValueException(String reason) {
            super(reason);
        }
    }

    /**
     * Replaces each field value that references a carried data URI with that data URI, in place.
     *
     * @param fieldValues the posted eForm field values, in posting order; each one that is exactly
     *                    {@value #REFERENCE_PREFIX}{@code <n>} is replaced by carried value {@code n}
     * @param carried     the {@value #PARAMETER} values in posting order, or {@code null} when the
     *                    request has none (then {@code fieldValues} is left unchanged)
     * @return the number of field values replaced
     * @throws InvalidCarriedValueException when a carried value is not an accepted image data URI,
     *                                      there are more than {@link #MAX_VALUES}, or a field
     *                                      references an index with no carried value
     */
    public static int restore(List<String> fieldValues, String[] carried) throws InvalidCarriedValueException {
        if (carried == null || carried.length == 0) {
            return 0;
        }
        if (carried.length > MAX_VALUES) {
            throw new InvalidCarriedValueException("too many carried values");
        }
        // Every carried value is checked, referenced or not: the page sends none it does not use, so
        // an invalid one is a tampered request, and refusing it keeps the rule simple to audit.
        for (String value : carried) {
            if (!isAcceptedDataUri(value)) {
                throw new InvalidCarriedValueException("carried value is not an accepted image data URI");
            }
        }
        int restored = 0;
        for (int i = 0; i < fieldValues.size(); i++) {
            String value = fieldValues.get(i);
            if (value == null || !value.startsWith(REFERENCE_PREFIX)) {
                continue;
            }
            Matcher reference = REFERENCE.matcher(value);
            if (!reference.matches()) {
                throw new InvalidCarriedValueException("malformed carried value reference");
            }
            int index = Integer.parseInt(reference.group(1));
            if (index >= carried.length) {
                throw new InvalidCarriedValueException("reference to a missing carried value");
            }
            fieldValues.set(i, carried[index]);
            restored++;
        }
        return restored;
    }

    /**
     * Whether a value is one of the two data URI shapes this class accepts (see the class comment).
     *
     * @param value the candidate value; {@code null} is not accepted
     * @return true for a valid raster image or jSignature base30 data URI
     */
    public static boolean isAcceptedDataUri(String value) {
        if (value == null) {
            return false;
        }
        Matcher raster = RASTER_PREFIX.matcher(value);
        if (raster.lookingAt()) {
            return isRasterPayload(raster.group(1), value.substring(raster.end()));
        }
        if (value.startsWith(JSIGNATURE_PREFIX)) {
            String payload = value.substring(JSIGNATURE_PREFIX.length());
            return payload.length() <= MAX_BASE30_CHARS && BASE30_PAYLOAD.matcher(payload).matches();
        }
        return false;
    }

    private static boolean isRasterPayload(String format, String payload) {
        // Padded Base64 only, as canvas.toDataURL() writes it: the page script checks the same.
        if (payload.isEmpty() || payload.length() > MAX_BASE64_CHARS || payload.length() % 4 != 0) {
            return false;
        }
        byte[] image;
        try {
            // The basic decoder rejects every character outside the Base64 alphabet, including
            // whitespace, quotes and angle brackets, and misplaced padding.
            image = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return image.length <= MAX_DECODED_BYTES && hasFileSignature(format, image);
    }

    // The decoded bytes must start the way the declared format does, so a value labelled as an image
    // is one: browsers render a data URI by its declared type, and this keeps the label honest.
    private static boolean hasFileSignature(String format, byte[] image) {
        switch (format) {
            case "png":
                return startsWith(image, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            case "jpeg":
                return startsWith(image, 0, 0xFF, 0xD8, 0xFF);
            case "gif":
                return startsWith(image, 0, 'G', 'I', 'F', '8');
            case "webp":
                return startsWith(image, 0, 'R', 'I', 'F', 'F') && startsWith(image, 8, 'W', 'E', 'B', 'P');
            default:
                return false;
        }
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
