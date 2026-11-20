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

    /**
     * The public product DrugRef holds for a DIN, or null when DrugRef has no usable product for
     * it: an unknown DIN (a product no longer in the installed dictionary), or an answer the
     * reference cannot describe (another DIN, no product name, no ingredient list, an ingredient
     * without its name, unit or strength, a strength that is negative or not a finite number,
     * text longer than the reference holds). Either way the prescription is exported as an
     * unqualified catalogue selection, with no DIN or Strength element and its stored values
     * exactly in the medication residual ({@code CdsExportNativeMedicationMapper}); DrugRef's
     * answer is reference data, not the chart, so it never holds the patient (it did, as
     * {@code CDS_EXPORT_DRUG_REFERENCE_REQUIRES_REVIEW}). A transport failure is an I/O failure.
     */
    public CdsExportDrugReferenceDto read(String din) throws IOException {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException(
                    "CDS_EXPORT_REFERENCE_LOOKUP_INSIDE_TRANSACTION_REFUSED");
        // The source reader asks only for an eight-digit DIN (CdsExportDrugReferenceSourceReader).
        if (din == null || !din.matches("[0-9]{8}"))
            throw new IllegalArgumentException("CDS_EXPORT_DRUG_REFERENCE_DIN_INVALID");
        try {
            // false avoids the native client's HTML detail enrichment. No patient or prescription
            // identifier, name, dose, author or chart content enters the request.
            var product = reference.getDrugByDIN(din, Boolean.FALSE);
            // RxDrugRef.getDrugByDIN reports "no product for this DIN" as null (#5006).
            if (product == null) return null;
            // A product has as many active ingredients as the DPD lists (its own count,
            // number_of_ais, is not bounded); every one is read. Only the first is written, as
            // Strength (Guide 10.5).
            if (!din.equals(product.get("regional_identifier"))
                    || !(product.get("product") instanceof String)
                    || !(product.get("components") instanceof Vector<?>)) return null;
            var result = new ArrayList<CdsExportDrugReferenceDto.Component>();
            for (var item : (Vector<?>) product.get("components")) {
                if (!(item instanceof Hashtable<?, ?> component)
                        || !(component.get("name") instanceof String)
                        || !(component.get("unit") instanceof String)
                        || !(component.get("strength") instanceof String
                                || component.get("strength") instanceof Number)) return null;
                String strength = strength(component.get("strength"));
                if (strength == null) return null;
                result.add(
                        new CdsExportDrugReferenceDto.Component(
                                (String) component.get("name"),
                                strength,
                                (String) component.get("unit")));
            }
            return new CdsExportDrugReferenceDto(
                    1,
                    CdsExportDrugReferenceDto.AUTHORITY,
                    din,
                    (String) product.get("product"),
                    result);
        } catch (IllegalArgumentException unusable) {
            // Text longer than the reference holds (CdsExportDrugReferenceDto): not usable.
            return null;
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

    /**
     * DrugRef serves numeric strengths as doubles (DrugLookUpManager); Double.toString would
     * publish "500.0" or "1.0E7". The exact plain decimal without a trailing zero is written
     * instead (a trailing zero is on ISMP Canada's do-not-use list). Text strengths stay as
     * authored.
     *
     * @return the strength's text, or null for a negative or non-finite number, which no
     *     ingredient has: the product is then not a usable reference
     */
    static String strength(Object value) {
        if (value instanceof String text) return text;
        try {
            var decimal = new java.math.BigDecimal(value.toString()).stripTrailingZeros();
            if (decimal.signum() < 0) return null;
            return (decimal.signum() == 0 ? java.math.BigDecimal.ZERO : decimal).toPlainString();
        } catch (NumberFormatException notFinite) {
            return null;
        }
    }
}
