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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDrugReferenceDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.prescript.util.RxDrugRef;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Uses the same configured DrugRef protocol as native prescribing, with public DINs only. */
@Service
public class CdsExportDrugReferenceLookupService {
    private final RxDrugRef reference;

    @Autowired
    public CdsExportDrugReferenceLookupService(
            @Qualifier("cdsExportProperties") Properties settings) {
        this(new RxDrugRef(settings.getProperty("drugref_url")));
    }

    CdsExportDrugReferenceLookupService(RxDrugRef reference) {
        this.reference = Objects.requireNonNull(reference);
    }

    public CdsExportDrugReferenceDto read(String din) throws IOException {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException(
                    "CDS_EXPORT_REFERENCE_LOOKUP_INSIDE_TRANSACTION_REFUSED");
        if (din == null || !din.matches("[0-9]{8}")) review();
        try {
            // false avoids the native client's HTML detail enrichment. No patient or prescription
            // identifier, name, dose, author or chart content enters the request.
            var product = reference.getDrugByDIN(din, Boolean.FALSE);
            if (product == null
                    || !din.equals(product.get("regional_identifier"))
                    || !(product.get("product") instanceof String name)
                    || !(product.get("components") instanceof Vector<?> components)
                    || components.size() > 64) review();
            var result = new ArrayList<CdsExportDrugReferenceDto.Component>();
            for (var item : (Vector<?>) product.get("components")) {
                if (!(item instanceof Hashtable<?, ?> component)
                        || !(component.get("name") instanceof String)
                        || !(component.get("unit") instanceof String)
                        || !(component.get("strength") instanceof String
                                || component.get("strength") instanceof Number)) review();
                var component = (Hashtable<?, ?>) item;
                result.add(
                        new CdsExportDrugReferenceDto.Component(
                                (String) component.get("name"),
                                component.get("strength").toString(),
                                (String) component.get("unit")));
            }
            return new CdsExportDrugReferenceDto(
                    1,
                    CdsExportDrugReferenceDto.AUTHORITY,
                    din,
                    (String) product.get("product"),
                    result);
        } catch (CdsExportMappingReviewException invalid) {
            throw invalid;
        } catch (IllegalArgumentException invalid) {
            throw new CdsExportMappingReviewException("CDS_EXPORT_DRUG_REFERENCE_REQUIRES_REVIEW");
        } catch (Exception failure) {
            if (Thread.currentThread().isInterrupted() || failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CancellationException(
                        "CDS_EXPORT_REFERENCE_LOOKUP_INTERRUPTED");
            }
            // A transport/protocol outage stays distinct from clinical reconciliation. Neither
            // outcome may become a successful DIN-free export of a catalogue-selected medication.
            throw new IOException("CDS_EXPORT_DRUG_REFERENCE_UNAVAILABLE");
        }
    }

    private static void review() throws IOException {
        throw new CdsExportMappingReviewException("CDS_EXPORT_DRUG_REFERENCE_REQUIRES_REVIEW");
    }
}
