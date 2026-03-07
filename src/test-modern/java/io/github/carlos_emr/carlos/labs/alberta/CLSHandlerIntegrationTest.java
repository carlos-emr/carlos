/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.labs.alberta;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for CLS (Calgary Lab Services) handler.
 *
 * <p>Migrated from legacy JUnit 4 {@code CLSHandlerIntegrationTest}. The entire
 * legacy test class was commented out with the note "Ignored and Commented as
 * skipping CLS module as of now." This modern version preserves the same
 * disabled status.</p>
 *
 * <p>The original test verified uploading HL7 CLS lab messages to a local OSCAR
 * instance using RSA key pairs for authentication. It required system-specific
 * configuration (OSCAR and client keys, running local server, and initialized
 * database schema).</p>
 *
 * @since 2012-01-01
 */
@Tag("integration")
@Tag("lab")
@Tag("upload")
@DisplayName("CLS Handler Integration Tests")
class CLSHandlerIntegrationTest {

    /** Public OSCAR key (empty - requires system-specific configuration) */
    private static final String OSCAR_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDxvzlAHc8QOI30bT+OtUs7GiebFbT0Fp+Cn2kj 5lYqp9c8ejOXzGFBY6A80CA+a6DGLQoqCPkmCKZ8OFH01xGgkAQIzGuLF90iAZV348RHUvwu7qg1 f3zi8k7O+1uq6/cWidSb2jUnAiYpa21uBSMNfSwtxIp2LzmuRQF+xEKhTQIDAQAB";

    /** Public client key (empty - requires system-specific configuration) */
    private static final String CLIENT_KEY = "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAMbA/Di5Xtk+4qqzAw1KkF5W8GEm k9uYvAY8H6A+6NcFV+vblPzg1WNvp5OSeqoMggvDb0r+AdAkcefkcXherQ0Fv3YCtCJt+DAGMG+0 OHmDMdKSIfNoG7gh/fV2btzyw7AZ5B/fP/cOXvjv3R2TCEHxmepnQGCRCbNFkchJwSbZAgMBAAEC gYBNOX7Gq3/m+UApAxUUfPxLK4yKsuqQUG/+HC5NnSPrJ/BZfCAPgWxoDmIWPLvchq+g0nbTtI4P yZlYeLJ+CotB7KlbAIF7xm+xE8iqkSojclJ2myU8lOpQ39GiiO+KDkfA2cHA4ftIwkDqudmnnRGs lIAWabYG1P8sgjSD2MHAAQJBAOoJ5XTPoIO9QDXV63q4GhlUIqkmZ0U51l0VjrEDW4Wu8puOw38a uO10ht+lkCbniiGEoqMZyJSD9H6U2waan9kCQQDZZ3khl6gJzaQYzQ+/XZTu6ecbc8n2k/hZB5Vk pSRQMH8RFkeB4g7xSTggvbC+JpKX5eEfeLRYyuXFSepQXV8BAkEAuWzl00q9TiMfQIggbbZ3VyIF 5CZ9I6fTYyS1TSHv3Vbi+MR/t7CgW+I7Ce7O60P/eNbxVHAVLzXs/G1Lq0vO4QJAGZ9kW10wZNdj u7iPXpJ89xuCLW4cI3+VCYknRlFgUkMk9rKVgu1NrYpfnxw8NGz/Yf+p5LepKb3gDryDbS1UAQJA cHIjxjgUrJUtRqA5fHmcu/jgt65vMjXhdG8DLs3BV9l6VYus9G+jmsRYutaF5CSYoOndMNfWL2FQ 5oNTJ6TWxw==";

    @Test
    @Disabled("CLS module skipped - entire legacy test was commented out. "
        + "Requires system-specific configuration: running local server, RSA keys, and database schema.")
    @DisplayName("should upload CLS HL7 lab messages with RSA authentication")
    void shouldUploadClsHl7LabMessages_withRsaAuthentication() throws Exception {
        // Original test loaded HL7-CLS/MillenniumUpgrade2010_Clinic_Validation_Current.hl7
        // and sent it to a local OSCAR instance using RSA key-based authentication via SendingUtils.
        // It verified a 200 status code response.
        //
        // This test requires:
        // 1. A running local OSCAR/CARLOS instance at http://localhost:8080
        // 2. Valid OSCAR and client RSA key pairs
        // 3. Full database schema initialization
    }
}
