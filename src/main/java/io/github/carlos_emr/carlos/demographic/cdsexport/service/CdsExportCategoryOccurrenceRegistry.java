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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Finite immutable dispatch; duplicate assemblers fail startup instead of silently shadowing one another. */
@Service
public class CdsExportCategoryOccurrenceRegistry {
    private final Map<CdsCategory, CdsExportCategoryOccurrenceAssembler> assemblers;
    @Autowired public CdsExportCategoryOccurrenceRegistry(ObjectProvider<CdsExportCategoryOccurrenceAssembler> installed) {
        this(installed.orderedStream().toList());
    }
    public CdsExportCategoryOccurrenceRegistry(List<CdsExportCategoryOccurrenceAssembler> installed) {
        var registered = new EnumMap<CdsCategory, CdsExportCategoryOccurrenceAssembler>(CdsCategory.class);
        for (var assembler : List.copyOf(installed)) {
            var category = Objects.requireNonNull(assembler).category();
            if (category == null || category == CdsCategory.DEMOGRAPHICS || CdsExportNoteSourceReader.CATEGORIES.contains(category)
                    || Set.of(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS, CdsCategory.MEDICATIONS_AND_TREATMENTS).contains(category)
                    || registered.putIfAbsent(category, assembler) != null) throw new IllegalStateException("CDS_EXPORT_CATEGORY_ASSEMBLER_AMBIGUOUS");
        }
        assemblers = Map.copyOf(registered);
    }
    /** Explicit constructor for the existing scoped component adapters; production discovery validates every category declaration. */
    CdsExportCategoryOccurrenceRegistry(Map<CdsCategory, CdsExportCategoryOccurrenceAssembler> supplied) { assemblers = Map.copyOf(supplied); }
    public CdsExportCategoryOccurrenceAssembler find(CdsCategory category) { return assemblers.get(Objects.requireNonNull(category)); }
}
