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
package io.github.carlos_emr.carlos.commn.hl7.v2;

import java.util.Collection;

import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.validation.EncodingRule;
import ca.uhn.hl7v2.validation.MessageRule;
import ca.uhn.hl7v2.validation.PrimitiveTypeRule;
import ca.uhn.hl7v2.validation.ValidationContext;
import ca.uhn.hl7v2.validation.builder.ValidationRuleBuilder;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;

/**
 * The HL7 validation context CARLOS uses in place of HAPI's {@code NoValidation}.
 *
 * <p>Like HAPI's own, it applies no format checks, so lab messages with non-numeric NM values,
 * free-form dates or unusual phone numbers still parse. It differs in one rule. HAPI HL7 v2 1.0.1,
 * which CARLOS used until issue #5414, removed leading whitespace from FT, ST and TX values. HAPI
 * 2.x removes trailing whitespace from TX instead. Lab results, the inbox, PDF exports and the
 * measurement import all read these values, so this context keeps the 1.0.1 behaviour: an upgrade
 * of the parsing library must not quietly re-indent or re-pad clinical text. Adopting HAPI's TX
 * handling would be a deliberate display change of its own.</p>
 *
 * <p>Thread-safe and immutable; one instance may be shared by any number of parsers.</p>
 *
 * @since 2026-10-29
 */
public final class LegacyHl7NoValidation implements ValidationContext {

    /** Left-trims FT, ST and TX for every HL7 version, as HAPI 1.0.1's {@code NoValidation} did. */
    private static final class LeftTrimTextRules extends ValidationRuleBuilder {
        private static final long serialVersionUID = 1L;

        @Override
        protected void configure() {
            forAllVersions().primitive("FT", "ST", "TX").leftTrim();
        }
    }

    private final ValidationContext rules = ValidationContextFactory.fromBuilder(new LeftTrimTextRules());

    @Override
    public Collection<PrimitiveTypeRule> getPrimitiveRules(String version, String typeName, Primitive type) {
        return rules.getPrimitiveRules(version, typeName, type);
    }

    @Override
    public Collection<MessageRule> getMessageRules(String version, String messageType, String triggerEvent) {
        return rules.getMessageRules(version, messageType, triggerEvent);
    }

    @Override
    public Collection<EncodingRule> getEncodingRules(String version, String encoding) {
        return rules.getEncodingRules(version, encoding);
    }
}
