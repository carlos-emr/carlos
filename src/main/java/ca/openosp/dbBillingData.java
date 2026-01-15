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


package ca.openosp;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.BillingServiceDao;
import ca.openosp.openo.commn.model.BillingService;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.db.DBPreparedHandler;

/**
 * Database access class for billing service code information.
 * 
 * <p>This class provides methods to load billing service details from the database
 * based on service codes. It retrieves:</p>
 * <ul>
 *   <li>Service code</li>
 *   <li>Service description</li>
 *   <li>Service value/price</li>
 *   <li>Percentage information</li>
 * </ul>
 * 
 * <p><strong>Note:</strong> This class uses the deprecated {@link DBPreparedHandler}
 * alongside modern DAO access. Consider fully migrating to the DAO pattern.</p>
 * 
 * @see BillingServiceDao
 * @deprecated Consider using BillingServiceDao directly for new code
 */
@Deprecated
public class dbBillingData {
    private Logger logger = MiscUtils.getLogger();
    private BillingServiceDao billingServiceDao = SpringUtils.getBean(BillingServiceDao.class);


    DBPreparedHandler accessDB = null;
    private String db_service_code = null;
    private String service_code = null;
    private String description = null;
    private String value = null;
    private String percentage = null;

    /**
     * Constructs a new dbBillingData instance.
     */
    public dbBillingData() {
    }

    /**
     * Sets the service code to load.
     * 
     * @param value the billing service code
     */
    public void setService_code(String value) {
        service_code = value;
    }

    /**
     * Loads billing service information from the database.
     * 
     * <p>Queries the database for the billing service matching the configured
     * service code and returns the service details as a string array.</p>
     * 
     * @return String array containing [service_code, description, value, percentage],
     *         or null if service code not found or doesn't match
     */
    public String[] ejbLoad() {
        for (BillingService bs : billingServiceDao.findByServiceCode(service_code)) {
            db_service_code = bs.getServiceCode();
            description = bs.getDescription();
            value = bs.getValue();
            percentage = bs.getPercentage();
        }

        if (db_service_code == null) return null;

        if (service_code.equals(db_service_code)) {
            String[] strAuth = new String[4];
            strAuth[0] = db_service_code;
            strAuth[1] = description;
            strAuth[2] = value;
            strAuth[3] = percentage;
            return strAuth;
        } else {
            return null;
        }
    }
}
