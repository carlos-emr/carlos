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
package io.github.carlos_emr.carlos.commn.dao.paging;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Two-part {@code @EmbeddedId} of {@link PagedTraversalCompositeRecord}.
 *
 * <p>The fields are declared {@code zone} then {@code seq} on purpose: the documented default
 * {@code findAll} order sorts key components by attribute name, so the pages must come back by
 * {@code seq} first, then {@code zone}, which is not the declaration order.</p>
 *
 * @since 2026-10-29
 */
@Embeddable
public class PagedTraversalCompositeKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "zone_code", nullable = false, length = 8)
    private String zone;

    @Column(name = "seq_no", nullable = false)
    private Integer seq;

    public PagedTraversalCompositeKey() {
    }

    public PagedTraversalCompositeKey(String zone, Integer seq) {
        this.zone = zone;
        this.seq = seq;
    }

    public String getZone() {
        return zone;
    }

    public Integer getSeq() {
        return seq;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PagedTraversalCompositeKey key)) {
            return false;
        }
        return Objects.equals(zone, key.zone) && Objects.equals(seq, key.seq);
    }

    @Override
    public int hashCode() {
        return Objects.hash(zone, seq);
    }

    @Override
    public String toString() {
        return zone + "/" + seq;
    }
}
