/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONFormViewModel;
import io.github.carlos_emr.carlos.billings.ca.bc.decisionSupport.BillingGuidelines;
import io.github.carlos_emr.carlos.decisionSupport.model.DSConsequence;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * Composer for the Drools billing-guidelines block.
 * {@link BillingGuidelines#evaluateAndGetConsequences} returns a list of
 * decision-support consequences; this composer keeps only those at
 * warning strength, HTML-encodes each text, joins with {@code <br/>}, and
 * sets it on the builder.
 *
 * <p>Catches broad {@code Exception} because Drools rule compilation can
 * throw {@code RuntimeException}, {@code KieBaseException}, or
 * {@code OutOfMemoryError}-adjacent shapes; the form must still render if
 * the rule cache is corrupted, so we log + drop the recommendations.
 * The log line includes demoNo and userNo so ops can distinguish a one-off
 * failure from a global rule-cache outage.</p>
 *
 * @since 2026-04-25
 */
final class BillingONFormDroolsRecommender {

    void recommend(BillingONFormViewModel.Builder b,
                   LoggedInInfo loggedInInfo,
                   String demoNo,
                   String userNo) {
        StringBuilder recommendations = new StringBuilder();
        try {
            List<DSConsequence> consequences = BillingGuidelines.getInstance()
                    .evaluateAndGetConsequences(loggedInInfo, demoNo, userNo);
            for (DSConsequence dscon : consequences) {
                if (dscon.getConsequenceStrength() == DSConsequence.ConsequenceStrength.warning) {
                    recommendations.append(SafeEncode.forHtml(dscon.getText())).append("<br/>");
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error(
                    "Drools billing-guidelines evaluation failed for demo={} provider={}",
                    LogSanitizer.sanitize(demoNo), userNo, e);
        }
        b.billingRecommendations(recommendations.toString());
    }
}
