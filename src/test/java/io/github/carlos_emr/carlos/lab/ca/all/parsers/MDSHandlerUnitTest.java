/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
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
