/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.model;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class GroupMembersUnitTest {
    @Test
    void shouldReturnLocalLocation_whenHydratingLegacyNull() throws Exception {
        GroupMembers member = new GroupMembers();
        // Hibernate uses field access, bypassing the primitive public setter.
        // This throws IllegalArgumentException with the old primitive field,
        // reproducing the fresh-demo Messenger administration HTTP 500.
        Field location = GroupMembers.class.getDeclaredField("clinicLocationNo");
        location.setAccessible(true);
        location.set(member, null);

        assertThat(member.getClinicLocationNo()).isZero();
        assertThat(location.get(member)).isNull();
    }

    @Test
    void shouldPreserveRemoteLocation_whenSetOnNewMember() {
        GroupMembers member = new GroupMembers();
        assertThat(member.getClinicLocationNo()).isZero();
        member.setClinicLocationNo(27);
        assertThat(member.getClinicLocationNo()).isEqualTo(27);
        member.setClinicLocationNo(0);
        assertThat(member.getClinicLocationNo()).isZero();
    }
}
