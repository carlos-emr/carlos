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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class CdsPreparationSealUnitTest {
    @Test void shouldKeepTheExistingUtf8PreparedPayloadSeal_withoutJsonRewriting() {
        String payload = "{\"description\":\"café 😀\"}";
        assertThat(CdsPreparationSeal.digest("synthetic.Native", "a".repeat(64), "b".repeat(64), "c".repeat(64), payload)).isEqualTo("e58f28c34fd5f67581b9ffea631d84889ab20e61cede2c928aaa5b597d01b0e7");
        assertThat(CdsPreparationSeal.digest("synthetic.Native", "a".repeat(64), "b".repeat(64), "c".repeat(64), payload + " ")).isNotEqualTo("e58f28c34fd5f67581b9ffea631d84889ab20e61cede2c928aaa5b597d01b0e7");
    }
}
