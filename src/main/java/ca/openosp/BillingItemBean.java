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

import java.io.Serializable;

/**
 * Data bean representing an individual billing item in the system.
 * 
 * <p>This class holds detailed information about a single billable service or item,
 * including:</p>
 * <ul>
 *   <li>Service code identifying the type of service</li>
 *   <li>Description of the service</li>
 *   <li>Service value and pricing</li>
 *   <li>Percentage (for billing calculations)</li>
 *   <li>Diagnosis code associated with the service</li>
 *   <li>Quantity and total cost</li>
 * </ul>
 * 
 * <p>This bean is thread-safe through synchronized setter methods.</p>
 */
public class BillingItemBean extends Object implements Serializable {
    private String service_code;
    private String desc;
    private String service_value;
    private String percentage;
    private String diag_code;
    private float total;
    private float service_price;
    private String quantity;

    /**
     * Constructs a new BillingItemBean with default values.
     * Total and service price are initialized to 0.
     */
    public BillingItemBean() {

        total = 0;
        service_price = 0;
    }

    /**
     * Gets the service code for this billing item.
     * 
     * @return the service code
     */
    public String getService_code() {
        return service_code;
    }

    /**
     * Sets the service code for this billing item.
     * This method is synchronized to ensure thread safety.
     * 
     * @param value the service code to set
     */
    public synchronized void setService_code(String value) {
        service_code = value;
    }

    /**
     * Gets the description of this billing item.
     * 
     * @return the description
     */
    public String getDesc() {
        return desc;
    }

    /**
     * Sets the description for this billing item.
     * This method is synchronized to ensure thread safety.
     * 
     * @param value the description to set
     */
    public synchronized void setDesc(String value) {
        desc = value;
    }

    /**
     * Gets the service value for this billing item.
     * 
     * @return the service value
     */
    public String getService_value() {
        return service_value;
    }

    /**
     * Sets the service value for this billing item.
     * This method is synchronized to ensure thread safety.
     * 
     * @param value the service value to set
     */
    public synchronized void setService_value(String value) {
        service_value = value;
    }

    /**
     * Gets the percentage value used in billing calculations.
     * 
     * @return the percentage value
     */
    public String getPercentage() {
        return percentage;
    }

    /**
     * Sets the diagnosis code for this billing item.
     * This method is synchronized to ensure thread safety.
     * 
     * @param value the diagnosis code to set
     */
    public synchronized void setDiag_code(String value) {
        diag_code = value;
    }

    /**
     * Gets the diagnosis code for this billing item.
     * 
     * @return the diagnosis code
     */
    public String getDiag_code() {
        return diag_code;
    }

    /**
     * Sets the percentage value for billing calculations.
     * This method is synchronized to ensure thread safety.
     * 
     * @param value the percentage value to set
     */
    public synchronized void setPercentage(String value) {
        percentage = value;
    }


    /**
     * Gets the service price for this billing item.
     * 
     * @return the service price as a float
     */
    public float getService_price() {
        return service_price;
    }

    /**
     * Sets the service price for this billing item.
     * This method is synchronized to ensure thread safety.
     * 
     * @param newPrice the service price to set
     */
    public synchronized void setService_price(float newPrice) {
        service_price = newPrice;
    }

    /**
     * Gets the quantity for this billing item.
     * 
     * @return the quantity as a String
     */
    public String getQuantity() {
        return quantity;
    }

    /**
     * Sets the quantity for this billing item.
     * This method is synchronized to ensure thread safety.
     * 
     * @param newQuantity the quantity to set
     */
    public synchronized void setQuantity(String newQuantity) {
        quantity = newQuantity;
    }

    /**
     * Gets the total cost for this billing item.
     * 
     * @return the total cost as a float
     */
    public float getTotal() {
        return total;
    }

    /**
     * Sets the total cost for this billing item.
     * This method is synchronized to ensure thread safety.
     * 
     * @param newTotal the total cost to set
     */
    public synchronized void setTotal(float newTotal) {
        total = newTotal;
    }
}
