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

import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates the persisted billing correction content blob grammar and
 * the regex-constrained coded MOH tokens ({@code rdohip}, {@code hctype},
 * {@code demosex}) that are stored raw — never entity-escaped — because
 * the fixed-width OHIP claim extractor reads them back without unescaping.
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
    private static final Pattern STORED_CONTENT_ELEMENT =
            Pattern.compile("\\G<([A-Za-z_][A-Za-z0-9_.-]*)>([^<>]*)</\\1>");

    private record CodedToken(Pattern allowedValue, String validationMessage) {
    }

    private static final Map<String, CodedToken> CODED_TOKENS = Map.of(
            "rdohip", new CodedToken(Pattern.compile("^[A-Za-z0-9]{0,6}$"),
                    "Referral doctor OHIP number contains unsupported characters."),
            "hctype", new CodedToken(Pattern.compile("^[A-Za-z]{0,2}$"),
                    "Health card type contains unsupported characters."),
            "demosex", new CodedToken(Pattern.compile("^[A-Za-z0-9]{0,2}$"),
                    "Demographic sex code contains unsupported characters."));
    // Keep this fixed element list in sync with BillingCorrectionReviewPreparationService.buildContent().
    private static final Set<String> FIXED_CONTENT_ELEMENTS = Set.of(
            "rdohip",
            "rd",
            "xml_referral",
            "mreview",
            "hctype",
            "demosex",
            "specialty",
            "xml_roster");

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
     * Re-validates the assembled content blob. Used at final submission, where
     * the blob arrives from a browser hidden field and must be checked exactly
     * as the OHIP extractor will later read it.
     *
     * @param content the correction content blob; {@code null} validates trivially (nothing stored)
     * @throws BillingValidationException if the blob structure is unsupported or any coded token fails its allowlist
     */
    public static void validateStoredContent(String content) {
        if (content == null) {
            return;
        }
        Matcher matcher = STORED_CONTENT_ELEMENT.matcher(content);
        int position = 0;
        while (position < content.length()) {
            matcher.region(position, content.length());
            if (!matcher.lookingAt()) {
                LOGGER.warn("Rejecting billing correction content with unsupported structure at offset {}", position);
                throw new BillingValidationException("Billing correction content contains unsupported structure.");
            }
            String elementName = matcher.group(1);
            if (!isAllowedStoredElement(elementName)) {
                LOGGER.warn("Rejecting unsupported billing correction XML element name {}", LogSafe.sanitize(elementName));
                throw new BillingValidationException("Billing correction content contains an unsupported element.");
            }
            if (CODED_TOKENS.containsKey(elementName)) {
                validate(elementName, matcher.group(2));
            }
            position = matcher.end();
        }
    }

    private static boolean isAllowedStoredElement(String elementName) {
        return FIXED_CONTENT_ELEMENTS.contains(elementName) || elementName.startsWith("xml_");
    }
}
