/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.webserv.transfer_objects;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.test.builders.ProgramTestBuilder;

/**
 * Unit tests for {@link ProgramTransfer}.
 *
 * <p>Tests static conversion from {@link Program} domain model
 * to {@link ProgramTransfer} transfer object, including single
 * and bulk conversions.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("ProgramTransfer")
class ProgramTransferUnitTest {

    @Nested
    @DisplayName("toTransfer")
    class ToTransfer {

        @Test
        @DisplayName("should convert Program to ProgramTransfer with all core fields")
        void shouldConvertProgram_withAllCoreFields() {
            Program program = ProgramTestBuilder.aProgram()
                    .withName("Mental Health Outreach")
                    .withType("Service")
                    .withFacilityId(3345)
                    .withProgramStatus("active")
                    .build();
            program.setId(12345);
            program.setHic(false);

            ProgramTransfer transfer = ProgramTransfer.toTransfer(program);

            assertThat(transfer).isNotNull();
            assertThat(transfer.getId()).isEqualTo(12345);
            assertThat(transfer.getName()).isEqualTo("Mental Health Outreach");
            assertThat(transfer.getFacilityId()).isEqualTo(3345);
            assertThat(transfer.isHic()).isFalse();
        }

        @Test
        @DisplayName("should preserve program type and status")
        void shouldPreserve_programTypeAndStatus() {
            Program program = ProgramTestBuilder.aProgram()
                    .withType("community")
                    .withProgramStatus("inactive")
                    .build();
            program.setId(1);

            ProgramTransfer transfer = ProgramTransfer.toTransfer(program);

            assertThat(transfer.getType()).isEqualTo("community");
            assertThat(transfer.getProgramStatus()).isEqualTo("inactive");
        }

        @Test
        @DisplayName("should handle program with hic true")
        void shouldHandleProgram_withHicTrue() {
            Program program = ProgramTestBuilder.aProgram().build();
            program.setId(1);
            program.setHic(true);

            ProgramTransfer transfer = ProgramTransfer.toTransfer(program);

            assertThat(transfer.isHic()).isTrue();
        }

        @Test
        @DisplayName("should handle program with null name")
        void shouldHandleProgram_withNullName() {
            Program program = ProgramTestBuilder.aProgram()
                    .withName(null)
                    .build();
            program.setId(1);

            ProgramTransfer transfer = ProgramTransfer.toTransfer(program);

            assertThat(transfer).isNotNull();
            assertThat(transfer.getName()).isNull();
        }
    }

    @Nested
    @DisplayName("toTransfers")
    class ToTransfers {

        @Test
        @DisplayName("should convert list of programs to array of transfers")
        void shouldConvertList_toArray() {
            Program p1 = ProgramTestBuilder.aProgram().withName("Program A").build();
            p1.setId(1);
            Program p2 = ProgramTestBuilder.aProgram().withName("Program B").build();
            p2.setId(2);

            ProgramTransfer[] transfers = ProgramTransfer.toTransfers(Arrays.asList(p1, p2));

            assertThat(transfers).hasSize(2);
            assertThat(transfers[0].getName()).isEqualTo("Program A");
            assertThat(transfers[1].getName()).isEqualTo("Program B");
        }

        @Test
        @DisplayName("should return empty array for empty list")
        void shouldReturnEmptyArray_forEmptyList() {
            ProgramTransfer[] transfers = ProgramTransfer.toTransfers(Collections.emptyList());

            assertThat(transfers).isEmpty();
        }
    }
}
