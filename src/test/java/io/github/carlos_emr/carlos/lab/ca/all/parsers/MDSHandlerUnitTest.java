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
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.NoValidation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MDSHandler")
@Tag("unit")
class MDSHandlerUnitTest {

    @Test
    void shouldKeepCommentsRaw_whenCommentContainsMarkupCharacters() throws Exception {
        String hl7 = String.join("\r",
                "MSH|^~\\&|MDS|LAB|CARLOS|CLINIC|202606121200||ORU^R01|MSGID1|P|2.3",
                "PID|1||12345^^^MDS||Doe^Jane||19800101|F",
                "OBR|1||ACC123|TEST^Test panel",
                "OBX|1|ST|1234^Result||5.0|mmol/L|||||F",
                "NTE|1|L|^result <5 and rising",
                "");
        PipeParser parser = new PipeParser();
        parser.setValidationContext(new NoValidation());
        Message message = parser.parse(hl7);
        MDSHandler handler = new MDSHandler();
        handler.msg = message;
        handler.terser = new Terser(message);
        handler.rawHL7Body = hl7;
        handler.obrGroups = new ArrayList();
        ArrayList<String> obxPaths = new ArrayList<>();
        obxPaths.add("/RESPONSE/ORDER_OBSERVATION(0)/OBSERVATION(0)/OBX");
        handler.obrGroups.add(obxPaths);

        String comment = handler.getOBXComment(0, 0, 0);

        assertThat(comment).isEqualTo("result <5 and rising");
        assertThat(comment).doesNotContain("&lt;");
    }
}
