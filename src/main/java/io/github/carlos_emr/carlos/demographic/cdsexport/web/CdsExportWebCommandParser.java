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
package io.github.carlos_emr.carlos.demographic.cdsexport.web;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Strict bounded form decoding; duplicate/empty/unknown selections are rejected without shrinking intent. */
@Service
public class CdsExportWebCommandParser {
    public CdsExportCommand parse(
            String key,
            String provider,
            String facility,
            String profile,
            String selection,
            String physicians,
            String patients,
            String categories,
            String recipient,
            String audit) {
        var selected = CdsExportCommand.Selection.valueOf(required(selection));
        List<String> roster = tokens(physicians, 100);
        List<String> ids = tokens(patients, 10_000);
        List<Integer> patientsSelected =
                ids.stream().map(CdsExportWebCommandParser::positive).toList();
        var domainNames = tokens(categories, CdsCategory.values().length);
        Set<CdsCategory> domains =
                domainNames.stream()
                        .map(CdsCategory::fromWireName)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new CdsExportCommand(
                key,
                provider,
                facility == null || facility.isEmpty() ? null : positive(facility),
                CdsLogicalProfile.fromIdentifier(profile),
                selected,
                roster,
                patientsSelected,
                domains,
                recipient,
                audit);
    }

    private static List<String> tokens(String text, int max) {
        if (text == null || text.isEmpty()) return List.of();
        if (text.length() > 120_000)
            throw new IllegalArgumentException("CDS_EXPORT_SELECTION_INVALID");
        var list = Arrays.stream(text.split(",", -1)).map(String::strip).toList();
        if (list.size() > max
                || list.stream().anyMatch(String::isEmpty)
                || Set.copyOf(list).size() != list.size()) {
            throw new IllegalArgumentException("CDS_EXPORT_SELECTION_INVALID");
        }
        return list;
    }

    private static String required(String value) {
        if (value == null || value.isEmpty())
            throw new IllegalArgumentException("CDS_EXPORT_SELECTION_INVALID");
        return value;
    }

    private static int positive(String value) {
        if (!value.matches("[1-9][0-9]{0,9}"))
            throw new IllegalArgumentException("CDS_EXPORT_SELECTION_INVALID");
        return Integer.parseInt(value);
    }
}
