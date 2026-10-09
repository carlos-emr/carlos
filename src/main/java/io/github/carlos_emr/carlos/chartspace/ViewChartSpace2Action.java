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
package io.github.carlos_emr.carlos.chartspace;

import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Gate action for the read-only ChartSpace page ({@code encounter/chartspace}).
 *
 * <p>Protects the page with the {@code _eChart} read privilege checked against
 * the requested patient, after validating the {@code demographicNo} parameter.
 * Check order is the contract: HTTP method, logged-in session, parameter
 * validity (400), privilege, then request attribute and view result.</p>
 *
 * <p>GET/HEAD only: the page is a pure view and must never mutate state, so
 * every other method is rejected with 405 before any other work.</p>
 *
 * <p>Deliberately does not touch {@code EctSessionBean} or any other
 * per-session "current chart" state: ChartSpace reads the id from the request
 * only, so it can be open alongside an already-open eChart for a different
 * patient without disturbing it.</p>
 *
 * @since 2026-10-08
 */
@Component(ViewChartSpace2Action.SPRING_BEAN_NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ViewChartSpace2Action extends ActionSupport {

    public static final String SPRING_BEAN_NAME = "viewChartSpace2Action";

    private final transient ChartSpaceRequestValidator validator;

    /**
     * Creates the action for Struts-managed instantiation paths, resolving
     * collaborators from the Spring context.
     *
     * @throws org.springframework.beans.BeansException if a required bean is unavailable
     */
    public ViewChartSpace2Action() {
        this(SpringUtils.getBean(ChartSpaceRequestValidator.class));
    }

    /**
     * Creates the action with explicit collaborators for Spring injection and tests.
     *
     * @param validator shared ChartSpace request checks
     */
    @Autowired
    public ViewChartSpace2Action(ChartSpaceRequestValidator validator) {
        this.validator = validator;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        Optional<ChartSpaceRequestValidator.Validated> validated = validator.validate(request, response);
        if (validated.isEmpty()) {
            return NONE;
        }

        request.setAttribute("demographicNo", validated.get().demographicNo());
        return SUCCESS;
    }
}
