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
package io.github.carlos_emr.carlos.commn.dto;

/**
 * One active Merge Records link ({@code demographic_merged} with {@code deleted} 0), as the CDS
 * export groups selected charts by it: the row's identifier and the two charts it names.
 *
 * @since 2026-11-14
 */
public record CdsExportMergeLinkRowDto(int id, int demographicNo, int mergedTo) {
    @Override
    public String toString() {
        return "CdsExportMergeLinkRowDto[protected]";
    }
}
