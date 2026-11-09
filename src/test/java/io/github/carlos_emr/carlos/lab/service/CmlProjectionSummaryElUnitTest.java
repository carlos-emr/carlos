/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual EL 6 record resolution used by the installed repair JSP, beyond Jasper compilation.
 * @since 2026-11-09
 */
@Tag("unit") @Tag("lab")
class CmlProjectionSummaryElUnitTest {
    private Object evaluate(CmlProjectionService.Summary summary, String expression, Class<?> type) {
        var factory=ExpressionFactory.newInstance();var context=new StandardELContext(factory);
        context.getVariableMapper().setVariable("plan",factory.createValueExpression(summary,CmlProjectionService.Summary.class));
        return factory.createValueExpression(context,expression,type).getValue(context);
    }
    @Test void shouldResolveDerivedRecordProperties_whenRenderingARepairOrNoOp() {
        var repair=new CmlProjectionService.Summary("fingerprint",CmlProjectionService.Reason.READY,1,2,true);
        assertThat(evaluate(repair,"${plan.ready and plan.changeNeeded}",Boolean.class)).isEqualTo(true);
        assertThat(evaluate(repair,"${plan.archiveCount}",Integer.class)).isEqualTo(1);
        assertThat(evaluate(repair,"${plan.createCount}",Integer.class)).isEqualTo(2);
        assertThat(evaluate(repair,"${plan.spansReports}",Boolean.class)).isEqualTo(true);
        var noop=new CmlProjectionService.Summary("fingerprint",CmlProjectionService.Reason.READY,0,0,false);
        assertThat(evaluate(noop,"${plan.ready and plan.changeNeeded}",Boolean.class)).isEqualTo(false);
    }
    @Test void shouldResolveReviewAndReason_withoutOfferingAnAmbiguousApply() {
        var review=new CmlProjectionService.Summary("",CmlProjectionService.Reason.AMBIGUOUS_SOURCE,0,0,false);
        assertThat(evaluate(review,"${plan.ready}",Boolean.class)).isEqualTo(false);
        assertThat(evaluate(review,"${plan.reviewNeeded}",Boolean.class)).isEqualTo(true);
        assertThat(evaluate(review,"${plan.reason}",String.class)).isEqualTo("AMBIGUOUS_SOURCE");
    }
}
