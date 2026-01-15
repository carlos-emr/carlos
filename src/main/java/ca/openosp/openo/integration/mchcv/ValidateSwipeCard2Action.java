//CHECKSTYLE:OFF
/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package ca.openosp.openo.integration.mchcv;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2 action for validating health card magnetic stripe data.
 * <p>
 * This action processes magnetic stripe data from a health card reader,
 * validates the health card number and version, and returns the validation
 * result. Used for verifying patient health card information via card swipe.
 * <p>
 * The magnetic stripe property is automatically populated by Struts 2 from
 * the request parameter.
 *
 * @since 2006-04-20
 */
public class ValidateSwipeCard2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Validates health card magnetic stripe data.
     * <p>
     * This method:
     * <ul>
     * <li>Parses the magnetic stripe data to extract health number and card version</li>
     * <li>Validates the health card information using the appropriate HC validator</li>
     * <li>Sets validation results and parsed data as request attributes</li>
     * </ul>
     * <p>
     * The magneticStripe property is populated automatically by Struts 2 from
     * the request parameter before this method is called.
     *
     * @return String "success" to forward to the result page
     * @throws Exception if magnetic stripe parsing or validation fails
     */
    @Override
    public String execute() throws Exception {
        String magneticStripe = this.getMagneticStripe();
        HCMagneticStripe hcMagneticStripe = new HCMagneticStripe(magneticStripe);

        HCValidator validator = HCValidationFactory.getHCValidator();
        HCValidationResult validationResult = validator.validate(hcMagneticStripe.getHealthNumber(), hcMagneticStripe.getCardVersion());

        request.setAttribute("hcMagneticStripe", hcMagneticStripe);
        request.setAttribute("validationResult", validationResult);
        return SUCCESS;
    }

    /**
     * Raw magnetic stripe data read from the health card.
     * <p>
     * This property is automatically set by Struts 2 from the magneticStripe
     * request parameter before the execute() method is called.
     */
    private String magneticStripe;

    /**
     * Gets the raw magnetic stripe data from the health card.
     *
     * @return String raw magnetic stripe data
     */
    public String getMagneticStripe() {
        return magneticStripe;
    }

    /**
     * Sets the raw magnetic stripe data from the health card.
     * <p>
     * This setter is called automatically by Struts 2 when the magneticStripe
     * parameter is present in the request.
     *
     * @param magneticStripe String raw magnetic stripe data
     */
    public void setMagneticStripe(String magneticStripe) {
        this.magneticStripe = magneticStripe;
    }
}