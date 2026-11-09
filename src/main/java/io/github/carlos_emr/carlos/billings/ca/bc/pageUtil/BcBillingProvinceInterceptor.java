/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import org.apache.struts2.ActionInvocation;
import org.apache.struts2.interceptor.AbstractInterceptor;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Refuses mapped BC-only endpoints before action/JSP access to provincial tables.
 * @since 2026-11-09
 */
public final class BcBillingProvinceInterceptor extends AbstractInterceptor {
    private static final long serialVersionUID = 1L;
    @Override
    public String intercept(ActionInvocation invocation) throws Exception {
        String actionName = invocation.getProxy().getActionName();
        if ((actionName.startsWith("billing/CA/BC/") || "quickBillingBC".equals(actionName)
                || "saveQuickBillingBC".equals(actionName))
                && !BcBillingRequestGuards.requireBcProvince(ServletActionContext.getResponse())) {
            return ActionSupport.NONE;
        }
        return invocation.invoke();
    }
}
