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

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.DefaultModelClassFactory;
import ca.uhn.hl7v2.parser.FixFieldDataType;
import ca.uhn.hl7v2.parser.ModelClassFactory;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.validation.impl.ValidationContextImpl;

/**
 * The one place CARLOS creates HAPI HL7 v2 pipe parsers (issue #5414).
 *
 * <p>Every parser it returns keeps the behaviour CARLOS had with HAPI 1.0.1, so the 2.x upgrade
 * changes no lab value, prescription QR code or ADT export:</p>
 * <ul>
 *   <li>{@link #newPipeParser()} reads received HL7, such as lab results: no format checks, FT/ST/TX
 *       left-trimmed ({@link LegacyHl7NoValidation}), {@link LegacyHl7Escaping}, and 1.0.1's names
 *       for the segments of a message HAPI parses generically ({@link LegacyHl7PipeParser}).</li>
 *   <li>{@link #forBuilding(Message)} and {@link #newEncodingPipeParser()} are for messages CARLOS
 *       builds: values are stored exactly as set, with no trimming and no format checks, then
 *       encoded with {@link LegacyHl7Escaping}. HAPI 2.x would otherwise check numeric fields while
 *       a message is built, and a prescription typed as "1-2" tablets would fail its QR code.</li>
 * </ul>
 *
 * <p>Loading this class also keeps HAPI 1.0.1's handling of a subcomponent separator inside a
 * single-value OBX-5 (see the static initialiser).</p>
 *
 * <p>XML is never parsed here: use {@link UntrustedHl7MessageParser} or {@link HardenedHl7XmlParser}.
 * The parsers are not thread-safe; create one per message or per thread.</p>
 *
 * @since 2026-10-29
 */
public final class Hl7Parsers {

    /*
     * HAPI 2.x keeps an unescaped subcomponent separator inside a single-value OBX-5 as text
     * ("Group & screen" stays whole), where 1.0.1 kept only the text before it ("Group"). HAPI
     * reads this JVM-wide system property on every parse, and its ParserConfiguration flag can only
     * switch the behaviour on, so the property is the one control. It is set to "false" to keep
     * the 1.0.1 results CARLOS displays and stores, unless an operator has already chosen a value
     * (-Dca.uhn.hl7v2.model.varies.escape_subcomponent_delim_in_primitive=true opts in to keeping
     * the whole text). This class is the only way CARLOS creates HL7 pipe parsers, so the property
     * is in place before any lab message is parsed.
     */
    static {
        if (System.getProperty(FixFieldDataType.ESCAPE_SUBCOMPONENT_DELIM_IN_PRIMITIVE) == null) {
            System.setProperty(FixFieldDataType.ESCAPE_SUBCOMPONENT_DELIM_IN_PRIMITIVE, "false");
        }
    }

    private Hl7Parsers() {
    }

    /** A context for parsing received HL7 with the model classes HAPI ships. */
    public static HapiContext newParsingContext() {
        return newParsingContext(new DefaultModelClassFactory());
    }

    /**
     * A context for parsing received HL7 with custom model classes, such as the Spire message
     * extension.
     */
    public static HapiContext newParsingContext(ModelClassFactory modelClassFactory) {
        HapiContext context = new DefaultHapiContext(modelClassFactory);
        context.setValidationContext(new LegacyHl7NoValidation());
        context.getParserConfiguration().setEscaping(new LegacyHl7Escaping());
        return context;
    }

    /**
     * A pipe parser for received HL7. A message of a type HAPI does not define keeps HAPI 1.0.1's
     * segment names ({@link LegacyHl7PipeParser}).
     */
    public static PipeParser newPipeParser() {
        return new LegacyHl7PipeParser(newParsingContext());
    }

    /** A pipe parser for received HL7 that maps segments onto custom model classes. */
    public static PipeParser newPipeParser(ModelClassFactory modelClassFactory) {
        return new LegacyHl7PipeParser(newParsingContext(modelClassFactory));
    }

    /**
     * A pipe parser that encodes messages CARLOS builds. It applies no validation rules at all,
     * so encoding never alters or rejects a value.
     */
    public static PipeParser newEncodingPipeParser() {
        HapiContext context = new DefaultHapiContext();
        context.setValidationContext(new ValidationContextImpl());
        context.getParserConfiguration().setEscaping(new LegacyHl7Escaping());
        return new PipeParser(context);
    }

    /**
     * Prepares a newly created message for filling in: values set on it afterwards are kept
     * exactly as given, as they were under HAPI 1.0.1. Call before setting any field.
     *
     * @param message a new, empty message
     * @return the same message
     */
    public static <M extends Message> M forBuilding(M message) {
        message.setParser(newEncodingPipeParser());
        return message;
    }
}
