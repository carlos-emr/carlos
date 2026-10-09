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

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Read-only JSON endpoint of the ChartSpace Allergies block
 * ({@code encounter/chartspace/allergies}).
 *
 * <p>Response shape: {@code {"status":"OK"|"EMPTY"|"NO_ACCESS","items":[...]}}
 * (see {@link AllergyBlockDto}). The shared {@link ChartSpaceRequestValidator}
 * runs first (405, session, 400, {@code _eChart} read for the patient); the
 * {@code _allergy} privilege and the data belong to {@link AllergyBlockLoader}.</p>
 *
 * <p>GET/HEAD only: the endpoint is a pure read, so it is not a mutator and
 * needs no CSRF token. The body carries PHI, hence {@code Cache-Control:
 * no-store}. Failures before the body is written use {@code sendError} (via the
 * validator) or a {@link SecurityException}; this action always ends with
 * {@code NONE} so Struts never appends an HTML result to the JSON.</p>
 *
 * @since 2026-10-09
 */
@Component(ChartSpaceAllergies2Action.SPRING_BEAN_NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ChartSpaceAllergies2Action extends ActionSupport {

    public static final String SPRING_BEAN_NAME = "chartSpaceAllergies2Action";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final transient ChartSpaceRequestValidator validator;
    private final transient AllergyBlockLoader loader;

    /**
     * Creates the action for Struts-managed instantiation paths, resolving
     * collaborators from the Spring context.
     *
     * @throws org.springframework.beans.BeansException if a required bean is unavailable
     */
    public ChartSpaceAllergies2Action() {
        this(SpringUtils.getBean(ChartSpaceRequestValidator.class),
                SpringUtils.getBean(AllergyBlockLoader.class));
    }

    /**
     * Creates the action with explicit collaborators for Spring injection and tests.
     *
     * @param validator shared ChartSpace request checks
     * @param loader source of the Allergies block data
     */
    @Autowired
    public ChartSpaceAllergies2Action(ChartSpaceRequestValidator validator, AllergyBlockLoader loader) {
        this.validator = validator;
        this.loader = loader;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        Optional<ChartSpaceRequestValidator.Validated> validated = validator.validate(request, response);
        if (validated.isEmpty()) {
            return NONE;
        }

        AllergyBlockDto dto = loader.load(validated.get().loggedInInfo(), validated.get().demographicNo());
        // Serialize before touching the response so a failure still yields a clean error, not half a body.
        byte[] body = JSON.writeValueAsBytes(dto);

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getOutputStream().write(body);
        return NONE;
    }
}
