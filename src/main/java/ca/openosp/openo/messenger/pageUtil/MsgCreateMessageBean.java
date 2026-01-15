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


package ca.openosp.openo.messenger.pageUtil;

/**
 * Legacy JavaBean for message creation functionality.
 * 
 * <p>This class appears to be a template or example JavaBean that was created
 * during early development but is no longer actively used in the messaging system.
 * It contains only a single "sample" property with basic getter/setter methods.</p>
 * 
 * <p>The bean follows the JavaBean specification with a no-argument constructor
 * (implicit) and property access methods. However, its current implementation
 * suggests it may have been intended as a placeholder or example that was never
 * fully developed.</p>
 * 
 * <p>Historical context: This bean was likely created when the messaging system
 * was first being developed using Struts 1.x ActionForms, but has been superseded
 * by more comprehensive session beans like MsgSessionBean.</p>
 * 
 * @deprecated This class appears to be unused and should be considered for removal
 * @version 1.0
 * @since 2003
 * @see MsgSessionBean
 */
@Deprecated
public class MsgCreateMessageBean {
    /**
     * Sample property with default initialization value.
     * Purpose unclear - likely a placeholder from initial development.
     */
    private String sample = "Start value";

    /**
     * Gets the value of the sample property.
     * 
     * @return the current value of the sample property
     */
    public String getSample() {
        return sample;
    }

    /**
     * Sets the value of the sample property.
     * 
     * <p>This method performs null-checking to prevent the property
     * from being set to null, maintaining the invariant that sample
     * always has a non-null value.</p>
     * 
     * @param newValue the new value for the sample property, ignored if null
     */
    public void setSample(String newValue) {
        if (newValue != null) {
            sample = newValue;
        }
    }
}
