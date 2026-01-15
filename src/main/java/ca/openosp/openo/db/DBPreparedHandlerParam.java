//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 */

package ca.openosp.openo.db;

import java.sql.Date;
import java.sql.Timestamp;


/**
 * Parameter wrapper for legacy database prepared statement handling.
 * 
 * <p>This class wraps different parameter types (String, Date, int, Timestamp)
 * for use with the deprecated {@link DBPreparedHandler} class. It maintains both
 * the value and the parameter type information.</p>
 * 
 * <p><strong>DEPRECATED:</strong> Use JPA (Java Persistence API) or standard
 * JDBC PreparedStatement instead. No new code should be written against this class.</p>
 * 
 * @deprecated Use JPA or standard JDBC PreparedStatement with proper parameter binding
 */
@Deprecated
public final class DBPreparedHandlerParam {
    private Date dateValue;
    private String stringValue;
    private int intValue;
    private String paramType;
    private Timestamp timestampValue;

    /** Parameter type constant for String values */
    public static String PARAM_STRING = "String";
    
    /** Parameter type constant for Date values */
    public static String PARAM_DATE = "Date";
    
    /** Parameter type constant for integer values */
    public static String PARAM_INT = "Int";
    
    /** Parameter type constant for Timestamp values */
    public static String PARAM_TIMESTAMP = "Timestamp";

    /**
     * Constructs a parameter wrapper for a String value.
     * 
     * @param stringValue the string value to wrap
     */
    public DBPreparedHandlerParam(String stringValue) {
        this.intValue = 0;
        this.stringValue = stringValue;
        this.dateValue = null;
        this.timestampValue = null;
        this.paramType = PARAM_STRING;
    }

    /**
     * Constructs a parameter wrapper for a Date value.
     * 
     * @param dateValue the SQL date value to wrap
     */
    public DBPreparedHandlerParam(Date dateValue) {
        this.intValue = 0;
        this.stringValue = null;
        this.dateValue = dateValue;
        this.timestampValue = null;
        this.paramType = PARAM_DATE;
    }

    /**
     * Constructs a parameter wrapper for a Timestamp value.
     * 
     * @param dateValue the timestamp value to wrap
     */
    public DBPreparedHandlerParam(Timestamp dateValue) {
        this.intValue = 0;
        this.stringValue = null;
        this.dateValue = null;
        this.timestampValue = dateValue;
        this.paramType = PARAM_TIMESTAMP;
    }


    /**
     * Constructs a parameter wrapper for an integer value.
     * 
     * @param intValue the integer value to wrap
     */
    public DBPreparedHandlerParam(int intValue) {
        this.intValue = intValue;
        this.stringValue = "";
        this.dateValue = null;
        this.timestampValue = null;
        this.paramType = PARAM_INT;
    }

    /**
     * Gets the Date value if this parameter contains a date.
     * 
     * @return the Date value, or null if this parameter is not a date type
     */
    public Date getDateValue() {

        return dateValue;
    }


    /**
     * Gets the Timestamp value if this parameter contains a timestamp.
     * 
     * @return the Timestamp value, or null if this parameter is not a timestamp type
     */
    public Timestamp getTimestampValue() {

        return this.timestampValue;
    }


    /**
     * Gets the integer value if this parameter contains an int.
     * 
     * @return the integer value (0 if this parameter is not an int type)
     */
    public int getIntValue() {
        return intValue;
    }

//   public void setDateValue(Date dateValue) {
//	  this.dateValue = dateValue;
//   }

    /**
     * Gets the parameter type identifier.
     * 
     * @return one of PARAM_STRING, PARAM_DATE, PARAM_INT, or PARAM_TIMESTAMP
     */
    public String getParamType() {
        return paramType;
    }

//   public void setParamType(String paramType) {
//	  this.paramType = paramType;
//   }

    /**
     * Gets the String value if this parameter contains a string.
     * 
     * @return the String value (empty string if this parameter is not a string type)
     */
    public String getStringValue() {
        return stringValue;
    }

//   public void setStringValue(String stringValue) {
//	  this.stringValue = stringValue;
//   }

}
