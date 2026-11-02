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
package io.github.carlos_emr.carlos.model;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementCPP;
import io.github.carlos_emr.carlos.casemgmt.model.ClientImage;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import java.util.HashSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Identifier equality must not depend on clinical fields or mutable associations. */
@Tag("unit")
class BaseObjectIdentityUnitTest {
    @Test
    void shouldComparePersistedKeys_withoutComparingPatientContent() {
        CaseManagementCPP left = new CaseManagementCPP();
        CaseManagementCPP right = new CaseManagementCPP();
        left.setId(10L);
        right.setId(10L);
        left.setSocialHistory("FAKE-private-history");
        assertThat(left).isEqualTo(right).hasSameHashCodeAs(right);
        right.setId(11L);
        assertThat(left).isNotEqualTo(right).isNotEqualTo(null).isNotEqualTo(new ClientImage());
    }

    @Test
    void shouldRetainHashSetMembership_whenUnsavedEntityReceivesKey() {
        ClientImage image = new ClientImage();
        ClientImage other = new ClientImage();
        assertThat(image).isEqualTo(image).isNotEqualTo(other);
        HashSet<ClientImage> images = new HashSet<>();
        images.add(image);
        image.setId(20L);
        other.setId(20L);
        assertThat(images).contains(image, other);
    }

    @Test
    void shouldCompareIssueKeys_withoutFormatterOrDescriptionState() {
        Issue left = new Issue();
        Issue right = new Issue();
        left.setId(30L);
        right.setId(30L);
        left.setDescription("FAKE-diagnosis");
        assertThat(left).isEqualTo(right).hasSameHashCodeAs(right);
    }

    @Test
    void shouldCompareAllCompositeKeyParts_withNullSafety() {
        CtlDocumentPK key = new CtlDocumentPK("demographic", 1, 2);
        assertThat(key).isEqualTo(new CtlDocumentPK("demographic", 1, 2))
                .hasSameHashCodeAs(new CtlDocumentPK("demographic", 1, 2))
                .isNotEqualTo(new CtlDocumentPK("program", 1, 2))
                .isNotEqualTo(new CtlDocumentPK("demographic", 9, 2))
                .isNotEqualTo(new CtlDocumentPK("demographic", 1, 9))
                .isNotEqualTo(null).isNotEqualTo("other");
        assertThat(new CtlDocumentPK()).isEqualTo(new CtlDocumentPK())
                .hasSameHashCodeAs(new CtlDocumentPK());
    }
}
