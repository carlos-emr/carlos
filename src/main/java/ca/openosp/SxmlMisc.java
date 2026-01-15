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

import java.util.Enumeration;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;

import ca.openosp.openo.caisi_integrator.util.MiscUtils;

/**
 * Simple XML manipulation utility class for working with XML-like data structures.
 * 
 * <p>This class provides helper methods for:</p>
 * <ul>
 *   <li>Creating XML strings from HTTP request parameters</li>
 *   <li>Extracting content between XML tags</li>
 *   <li>Replacing or adding XML tag content</li>
 *   <li>HTML entity encoding for XML safety</li>
 *   <li>String replacement operations</li>
 * </ul>
 * 
 * <p><strong>Note:</strong> This is a legacy utility class. For robust XML processing,
 * consider using standard Java XML APIs like DOM, SAX, or StAX, or libraries like
 * JDOM or XOM.</p>
 */
public class SxmlMisc extends Properties {
    private static final Logger log = MiscUtils.getLogger();

    /**
     * Creates an XML string from HTTP request parameters that start with a specified prefix.
     * Parameter names must be alphanumeric with underscores or hyphens only.
     * Empty parameter values are skipped.
     * 
     * <p>Example: If request has parameters "field_name=John" and "field_age=25",
     * calling with prefix "field_" produces:</p>
     * <pre>{@code <field_name>John</field_name><field_age>25</field_age>}</pre>
     * 
     * @param req the HTTP servlet request containing parameters
     * @param strPrefix the prefix to filter parameters by
     * @return XML string containing the filtered parameters, with HTML content escaped
     */
    public static String createXmlDataString(HttpServletRequest req, String strPrefix) {
        String temp = null, content = "";//default is not null
        StringBuilder sbContent = new StringBuilder("");
        for (Enumeration e = req.getParameterNames(); e.hasMoreElements(); ) {
            temp = e.nextElement().toString();
            if (!temp.startsWith(strPrefix) || req.getParameter(temp).equals("")) continue;

            // Validate element name - only allow alphanumeric, underscore, hyphen
            if (!temp.matches("^[a-zA-Z0-9_-]+$")) {
                log.error("Invalid XML element name: " + temp);
                continue; // Skip invalid parameter names
            }

            sbContent = sbContent.append("<").append(temp).append(">").append(SxmlMisc.replaceHTMLContent(req.getParameter(temp))).append("</").append(temp).append(">");    //Content+="<" +temp+ ">" +request.getParameter(temp)+ "</" +temp+ ">";
        }
        content = sbContent.toString();
        return content;
    }

    /**
     * Creates a data string from indexed HTTP request parameters.
     * Parameters must have the format: prefix + numeric_index (e.g., "data0", "data1").
     * The result is a string where each character corresponds to the parameter at that index.
     * 
     * @param req the HTTP servlet request containing indexed parameters
     * @param strPrefix the prefix for parameter names (e.g., "data" for "data0", "data1", etc.)
     * @param defaultValue the default character to use for empty or space-only parameters
     * @param maxsize the maximum size of the result array
     * @return a string constructed from the indexed parameter values
     */
    public static String createDataString(HttpServletRequest req, String strPrefix, String defaultValue, int maxsize) {
        String temp = null;//default is not null
        //StringBuilder sbContent=new StringBuilder("");
        byte[] abyte = new byte[maxsize];
        int i = 0, n = 0;
        for (Enumeration e = req.getParameterNames(); e.hasMoreElements(); ) {
            temp = e.nextElement().toString();
            if (temp.startsWith(strPrefix)) {
                i = Integer.parseInt(temp.substring(strPrefix.length()));
                abyte[i] = (byte) ((req.getParameter(temp).equals("") || req.getParameter(temp).equals(" ")) ? defaultValue.charAt(0) : req.getParameter(temp).charAt(0));
                n++;
                //sbContent = sbContent.append(req.getParameter(temp).equals("")?defaultValue:req.getParameter(temp));
            }
        }
        //String content=new String(abyte,0,n);
        //content=sbContent.toString();
        return new String(abyte, 0, n);
    }

    /**
     * Parses an XML string and stores the tag-value pairs as properties.
     * 
     * <p><strong>Note:</strong> This method appears to be incomplete in the current implementation.</p>
     * 
     * @param strXml the XML string to parse
     */
    public void setXmlStringProp(String strXml) {
        //parse strXml
        String name = "", val = null;
        setProperty(name.toUpperCase(), val);
    }

    /**
     * Extracts the content between specified start and end tags from a string.
     * Returns empty string if either tag is not found.
     * 
     * @param str the string to search in
     * @param sTag the start tag (including angle brackets, e.g., "&lt;name&gt;")
     * @param eTag the end tag (including angle brackets, e.g., "&lt;/name&gt;")
     * @return the content between the tags, or empty string if tags not found or str is null
     */
    public static String getXmlContent(String str, String sTag, String eTag) {
        if (str == null) return null;

        int s = str.indexOf(sTag);
        int e = str.indexOf(eTag);
        String val = "";
        if (s == -1 || e == -1) return val;
        val = str.substring(s + sTag.length(), e);

        return val;
    }

    /**
     * Extracts the content for a specified XML tag name.
     * Convenience method that automatically adds angle brackets to the tag name.
     * 
     * @param str the string to search in
     * @param sTagValue the tag name without angle brackets (e.g., "name")
     * @return the content between the tags, or empty string if not found
     */
    public static String getXmlContent(String str, String sTagValue) {
        return (getXmlContent(str, "<" + sTagValue + ">", "</" + sTagValue + ">"));
    }

    /**
     * Converts a potentially null string to an empty string for safe display.
     * 
     * @param str the string to check
     * @return empty string if str is null, otherwise the original string
     */
    public static String getReadableString(String str) {
        String val = str == null ? "" : str;
        return val;
    }

    /**
     * Replaces the content between specified XML tags with a new value.
     * If either tag is not found, returns the original string unchanged.
     * 
     * @param str the original string
     * @param sTag the start tag (including angle brackets)
     * @param eTag the end tag (including angle brackets)
     * @param newVal the new value to insert between the tags
     * @return the string with replaced content, or original string if tags not found
     */
    public static String replaceXmlContent(String str, String sTag, String eTag, String newVal) {
        int s = str.indexOf(sTag);
        int e = str.indexOf(eTag);
        String newStr = str;
        if (s != -1 && e != -1)
            newStr = str.substring(0, s + sTag.length()) + newVal + str.substring(e);

        return newStr;
    }

    /**
     * Replaces the content between XML tags, or adds the tags with content if they don't exist.
     * 
     * @param str the original string
     * @param sTag the start tag (including angle brackets)
     * @param eTag the end tag (including angle brackets)
     * @param newVal the value to insert between the tags
     * @return the string with replaced/added content
     */
    public static String replaceOrAddXmlContent(String str, String sTag, String eTag, String newVal) {
        int s = str.indexOf(sTag);
        int e = str.indexOf(eTag);
        String newStr = str;
        if (s != -1 && e != -1)
            newStr = str.substring(0, s + sTag.length()) + newVal + str.substring(e);
        else
            newStr = str + sTag + newVal + eTag;

        return newStr;
    }

    /**
     * Replaces all occurrences of a substring with a new substring.
     * 
     * @param str the original string
     * @param oldstr the substring to replace
     * @param newstr the replacement substring
     * @return the string with all replacements applied
     */
    public static String replaceString(String str, String oldstr, String newstr) {
        int s = str.indexOf(oldstr);
        int stemp = 0;
        while (s >= 0) {
            s = stemp + s;
            str = str.substring(0, s) + newstr + str.substring(s + oldstr.length());
            stemp = s + newstr.length();

            s = str.substring(stemp).indexOf(oldstr);

        }
        return str;
    }

    /**
     * Escapes HTML special characters for safe inclusion in XML content.
     * Replaces &amp;, &gt;, and &lt; with their corresponding HTML entities.
     * 
     * <p><strong>Note:</strong> For better security, consider using
     * {@link org.apache.commons.text.StringEscapeUtils#escapeXml11(String)} instead.</p>
     * 
     * @param str the string to escape
     * @return the escaped string safe for XML content
     */
    public static String replaceHTMLContent(String str) {
        str = SxmlMisc.replaceString(str, "&", "&amp;");
        str = SxmlMisc.replaceString(str, ">", "&gt;");
        str = SxmlMisc.replaceString(str, "<", "&lt;");
//  	str = SxmlMisc.replaceString(str,"'","&apos;");
//  	str = SxmlMisc.replaceString(str,"\"","&quot;");
        return str;
    }

}
