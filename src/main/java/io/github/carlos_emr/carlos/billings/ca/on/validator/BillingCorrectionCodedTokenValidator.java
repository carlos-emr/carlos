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
package io.github.carlos_emr.carlos.billings.ca.on.validator;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates the regex-constrained coded MOH tokens ({@code rdohip},
 * {@code hctype}, {@code demosex}) that are stored raw — never
 * entity-escaped — in the persisted billing correction content blob,
 * because the fixed-width OHIP claim extractor reads them back without
 * unescaping.
 *
 * <p>The same allowlists must hold at both write paths: review
 * preparation (form fields) and final submission (the {@code content}
 * hidden field that round-trips through the browser and can be tampered
 * with independently of the reviewed form).</p>
 *
 * @since 2026-06-12
 */
public final class BillingCorrectionCodedTokenValidator {

    private static final Logger LOGGER = MiscUtils.getLogger();

    private record CodedToken(Pattern allowedValue, String validationMessage) {
    }

    private static final Map<String, CodedToken> CODED_TOKENS = Map.of(
            "rdohip", new CodedToken(Pattern.compile("^[A-Za-z0-9]{0,6}$"),
                    "Referral doctor OHIP number contains unsupported characters."),
            "hctype", new CodedToken(Pattern.compile("^[A-Za-z]{0,2}$"),
                    "Health card type contains unsupported characters."),
            "demosex", new CodedToken(Pattern.compile("^[A-Za-z0-9]{0,2}$"),
                    "Demographic sex code contains unsupported characters."));

    private BillingCorrectionCodedTokenValidator() {
    }

    /**
     * Validates one coded token value against its element's allowlist.
     *
     * @param elementName one of the coded element names ({@code rdohip}, {@code hctype}, {@code demosex})
     * @param value       the raw submitted value; {@code null} is treated as empty
     * @return the value with {@code null} coalesced to empty, safe to embed raw in the content blob
     * @throws BillingValidationException if the value fails its allowlist, with an operator-facing message
     * @throws IllegalArgumentException   if {@code elementName} is not a known coded element (caller bug)
     */
    public static String validate(String elementName, String value) {
        CodedToken token = CODED_TOKENS.get(elementName);
        if (token == null) {
            throw new IllegalArgumentException("Unknown billing correction coded element: " + elementName);
        }
        String safeValue = value == null ? "" : value;
        if (!token.allowedValue().matcher(safeValue).matches()) {
            LOGGER.warn("Rejecting invalid billing correction coded XML value for {}", LogSafe.sanitize(elementName));
            throw new BillingValidationException(token.validationMessage());
        }
        return safeValue;
    }

    /**
     * Re-validates the coded tokens inside an assembled content blob. Used at
     * final submission, where the blob arrives from a browser hidden field and
     * must be checked exactly as the OHIP extractor will later read it.
     *
     * @param content the correction content blob; {@code null} validates trivially (nothing stored)
     * @throws BillingValidationException if any coded token fails its allowlist
     */
    public static void validateStoredContent(String content) {
        if (content == null) {
            return;
        }
        for (String elementName : CODED_TOKENS.keySet()) {
            // getXmlContent mirrors the extractor's read: raw substring between
            // the tags, empty when the element is absent. Absent elements pass
            // because every allowlist accepts empty.
            validate(elementName, SxmlMisc.getXmlContent(content, elementName));
        }
    }
}
