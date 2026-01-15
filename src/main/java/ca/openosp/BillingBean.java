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
import java.util.ArrayList;
import java.util.GregorianCalendar;

/**
 * Data bean representing a billing transaction in the system.
 * 
 * <p>This class holds information about a billing event, including:</p>
 * <ul>
 *   <li>A list of individual billing items ({@link BillingItemBean})</li>
 *   <li>The date of the billing transaction</li>
 *   <li>A unique billing number identifier</li>
 * </ul>
 * 
 * <p>This bean is thread-safe for adding/removing billing items through
 * synchronized methods.</p>
 */
public class BillingBean extends Object implements Serializable {
    private ArrayList<BillingItemBean> billingItems;
    private GregorianCalendar billingDate;
    private int billingNo;

    /**
     * Constructs a new BillingBean with empty billing items list,
     * current date, and billing number of 0.
     */
    public BillingBean() {
        billingItems = new ArrayList<BillingItemBean>();
        billingDate = new GregorianCalendar();
        billingNo = 0;
    }

    /**
     * Gets the list of billing items in this billing transaction.
     * 
     * @return the ArrayList of BillingItemBean objects
     */
    public ArrayList<BillingItemBean> getBillingItems() {
        return billingItems;
    }

    /**
     * Sets the list of billing items for this billing transaction.
     * This method is synchronized to ensure thread safety.
     * 
     * @param newBillingItems the new ArrayList of BillingItemBean objects
     */
    public synchronized void setBillingItems(ArrayList<BillingItemBean> newBillingItems) {
        billingItems = newBillingItems;
    }

    /**
     * Gets the date of this billing transaction.
     * 
     * @return the GregorianCalendar representing the billing date
     */
    public GregorianCalendar getBillingDate() {
        return billingDate;
    }

    /**
     * Gets the unique billing number for this transaction.
     * 
     * @return the billing number
     */
    public int getBillingNo() {
        return billingNo;
    }

    /**
     * Sets the billing number for this transaction.
     * 
     * @param newBillingNo the new billing number
     */
    public void setBillingNo(int newBillingNo) {
        billingNo = newBillingNo;
    }

    /**
     * Adds a billing item to this billing transaction.
     * This method is synchronized to ensure thread safety.
     * 
     * @param newBillItem the BillingItemBean to add to the billing items list
     */
    public synchronized void addBillingItem(BillingItemBean newBillItem) {
        billingItems.add(newBillItem);
    }

    /**
     * Removes a billing item from this billing transaction by its position.
     * This method is synchronized to ensure thread safety.
     * 
     * @param itemNo the index of the billing item to remove (0-based)
     */
    public synchronized void removeBillingItem(int itemNo) {
        billingItems.remove(itemNo);
    }

    /**
     * Gets a specific billing item by its position in the list.
     * 
     * @param itemNo the index of the billing item to retrieve (0-based)
     * @return the BillingItemBean at the specified position
     */
    public BillingItemBean getBillingItem(int itemNo) {
        return billingItems.get(itemNo);
    }

}
