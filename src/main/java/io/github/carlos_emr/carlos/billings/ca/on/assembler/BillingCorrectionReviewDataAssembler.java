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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingCorrectionReviewDraft;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingCorrectionReviewItemDraft;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingCorrectionReviewViewModel;

/**
 * Assembles {@link BillingCorrectionReviewViewModel} for the ON correction
 * review JSP from a typed draft prepared by the service layer.
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingCorrectionReviewDataAssembler {

    public BillingCorrectionReviewViewModel assemble(BillingCorrectionReviewDraft draft) {
        BillingCorrectionReviewViewModel.Builder b = BillingCorrectionReviewViewModel.builder();
        if (draft == null || !draft.dataLoaded()) {
            return b.build();
        }

        b.dataLoaded(true)
                .demoName(draft.demoName())
                .demoAddress(draft.demoAddress())
                .demoCity(draft.demoCity())
                .demoProvince(draft.demoProvince())
                .demoPostal(draft.demoPostal())
                .demoSex(draft.demoSex())
                .demoDob(draft.dob())
                .hin(draft.hin())
                .billingNo(draft.billingNo())
                .billingType(draft.status())
                .billingDate(draft.billingDate())
                .visitLocation(draft.clinicRefCode())
                .billingPhysicianNo(draft.providerNo())
                .visitType(draft.visitType())
                .visitDate(draft.visitDate())
                .updateDate(draft.updateDate())
                .referralDoctor(draft.referralDoctor())
                .referralDoctorOhip(draft.referralDoctorOhip())
                .hcType(draft.hcType())
                .rosterStatus(draft.rosterStatus())
                .manualReviewLabel(draft.manualReviewLabel())
                .referralCheckedLabel(draft.referralCheckedLabel())
                .content(draft.content())
                .storedTotal(draft.total());

        List<BillingCorrectionReviewViewModel.Item> items = new ArrayList<>();
        for (BillingCorrectionReviewItemDraft item : draft.items()) {
            items.add(new BillingCorrectionReviewViewModel.Item(
                    item.serviceCode(),
                    item.description(),
                    item.quantity(),
                    formatCents(item.storedFee()),
                    item.storedFee(),
                    item.percentage(),
                    item.diagCode()));
        }
        b.billingItems(items).diagCode(draft.diagCode());

        b.formattedTotal(formatCents(draft.total()));

        return b.build();
    }

    /**
     * Format a "no-decimal cents" amount like {@code "2375"} as
     * {@code "23.75"}. Returns the input unchanged when too short to split
     * (matching the lenient legacy substring math at render time, but
     * without throwing on short inputs).
     */
    static String formatCents(String stored) {
        if (stored == null || stored.length() < 3) {
            return nullToEmpty(stored);
        }
        return stored.substring(0, stored.length() - 2) + "." + stored.substring(stored.length() - 2);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
