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

/**
 * Data bean for document management in the system.
 * 
 * <p>This bean encapsulates document metadata and properties including:</p>
 * <ul>
 *   <li>File and folder information</li>
 *   <li>Document description and type</li>
 *   <li>Function and function ID for document categorization</li>
 *   <li>Creation metadata (date, creator)</li>
 *   <li>XML content and properties</li>
 *   <li>Document diversion settings</li>
 * </ul>
 * 
 * <p>This is a serializable JavaBean used for managing document information
 * across the application layers.</p>
 */
public class DocumentBean extends java.lang.Object implements java.io.Serializable {

    /** Document filename */
    protected String filename = "";
    /** Folder where document is stored */
    protected String foldername = "";
    /** Document description */
    protected String filedesc = "";
    /** Document function/category */
    protected String function = "";
    /** Function identifier */
    protected String function_id = "";
    /** Document creation date */
    protected String createdate = "";
    /** XML content of the document */
    protected String docxml = "";
    /** Document creator identifier */
    protected String doccreator = "";
    /** Document type */
    protected String doctype = "";
    /** Oscar properties for the document */
    protected String oscar_prop = "";
    /** Document diversion setting */
    protected String docDivert = "";

    /**
     * Sets the document diversion setting.
     * 
     * @param value the diversion setting
     */
    public void setDocDivert(String value) {
        docDivert = value;
    }

    /**
     * Gets the document diversion setting.
     * 
     * @return the diversion setting
     */
    public String getDocDivert() {
        return docDivert;
    }

    /**
     * Sets the document filename.
     * 
     * @param value the filename
     */
    public void setFilename(String value) {
        filename = value;
    }

    /**
     * Gets the document filename.
     * 
     * @return the filename
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Sets the Oscar properties for this document.
     * 
     * @param value the Oscar properties
     */
    public void setOscar_prop(String value) {
        oscar_prop = value;
    }

    /**
     * Gets the Oscar properties for this document.
     * 
     * @return the Oscar properties
     */
    public String getOscar_prop() {
        return oscar_prop;
    }

    /**
     * Sets the folder name where the document is stored.
     * 
     * @param value the folder name
     */
    public void setFoldername(String value) {
        foldername = value;
    }

    public String getFoldername() {
        return foldername;
    }

    public void setFileDesc(String value) {
        filedesc = value;
    }

    public String getFileDesc() {
        return filedesc;
    }

    public void setFunction(String value) {
        function = value;
    }

    public String getFunction() {
        return function;
    }

    public void setFunctionID(String value) {
        function_id = value;
    }

    public String getFunctionID() {
        return function_id;
    }

    public void setDocXML(String value) {
        docxml = value;
    }

    public String getDocXML() {
        return docxml;
    }

    public void setCreateDate(String value) {
        createdate = value;
    }

    public String getCreateDate() {
        return createdate;
    }

    public void setDocCreator(String value) {
        doccreator = value;
    }

    public String getDocCreator() {
        return doccreator;
    }

    public void setDocType(String value) {
        doctype = value;
    }

    public String getDocType() {
        return doctype;
    }
}
