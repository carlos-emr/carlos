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
 * The chart privilege row Merge Records writes for a merged chart ({@code secObjPrivilege}, role
 * {@code _all}, object {@code _eChart$} and the chart number), every column exactly as stored:
 * SQL NULL as null, {@code priority} as its decimal text.
 *
 * @since 2026-11-14
 */
public record CdsExportMergePrivilegeRowDto(
        String roleUserGroup,
        String objectName,
        String privilege,
        String priority,
        String providerNo) {
    @Override
    public String toString() {
        return "CdsExportMergePrivilegeRowDto[protected]";
    }
}
