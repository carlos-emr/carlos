/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import org.apache.struts2.ActionInvocation;
import org.apache.struts2.interceptor.AbstractInterceptor;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/** Refuses every mapped BC-only endpoint before action/JSP access to provincial tables. */
public final class BcBillingProvinceInterceptor extends AbstractInterceptor {
    private static final long serialVersionUID = 1L;
    @Override
    public String intercept(ActionInvocation invocation) throws Exception {
        if (invocation.getProxy().getActionName().startsWith("billing/CA/BC/")
                && !BcBillingRequestGuards.requireBcProvince(ServletActionContext.getResponse())) {
            return ActionSupport.NONE;
        }
        return invocation.invoke();
    }
}
