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


package io.github.carlos_emr.carlos.messenger.data;

import java.util.List;

import javax.servlet.jsp.JspWriter;

import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.OscarCommLocations;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Address book management class for the OpenO EMR messaging system.
 * 
 * <p>This class handles the retrieval and display of hierarchical address books containing
 * healthcare providers organized in groups. It manages both local and remote address books
 * from connected healthcare facilities, providing HTML rendering capabilities for the
 * messaging interface.</p>
 * 
 * <p>Key responsibilities:
 * <ul>
 *   <li>Retrieving XML address book data from the database</li>
 *   <li>Managing multiple location address books (local and remote)</li>
 *   <li>Rendering hierarchical tree structure in HTML</li>
 *   <li>Handling provider selection with checkboxes</li>
 *   <li>Supporting expandable/collapsible group navigation</li>
 * </ul>
 * </p>
 * 
 * @version 1.0
 * @deprecated This class is deprecated in favor of newer messaging implementations
 *             that provide better separation of concerns and modern UI frameworks
 * @since 2002
 * @see MsgAddressBookMaker
 * @see MsgProviderData
 */
@Deprecated
public class MsgAddressBook {
    /**
     * Static counter used for generating unique IDs in HTML output.
     */
    static int num;
    
    /**
     * Vector containing provider data from the address book.
     */
    public java.util.Vector providerList;
    
    /**
     * Vector containing remote address book XML data.
     */
    public java.util.Vector remoteaddrBook;
    
    /**
     * Vector containing descriptions of remote locations.
     */
    public java.util.Vector remoteLocationDesc;
    
    /**
     * Vector containing IDs of remote locations.
     */
    public java.util.Vector remoteLocationId;
    
    /**
     * Name of the current location/facility.
     */
    public String CurrentLocationName;

    /**
     * Constructor initializing the address book data structures.
     * 
     * <p>Creates empty vectors for storing provider lists, remote location
     * descriptions, and remote location IDs.</p>
     */
    public MsgAddressBook() {
        providerList = new java.util.Vector();
        remoteLocationDesc = new java.util.Vector();
        remoteLocationId = new java.util.Vector();
    }

    /**
     * Retrieves the local address book XML from the database.
     * 
     * <p>This method fetches the XML address book for the current location
     * (where current=1 in the database) and sets the CurrentLocationName
     * field with the location description.</p>
     *
     * @return The XML string containing the address book data for this location,
     *         or empty string if no current location is found
     */
    public String myAddressBook() {
        CurrentLocationName = "";

        OscarCommLocationsDao dao = SpringUtils.getBean(OscarCommLocationsDao.class);
        List<OscarCommLocations> comms = dao.findByCurrent1(1);

        for (OscarCommLocations comm : comms) {
            CurrentLocationName = comm.getLocationDesc();
            return comm.getAddressBook();
        }

        return "";
    }

    /**
     * Retrieves address books for all active remote locations.
     * 
     * <p>This method populates three parallel vectors:
     * <ul>
     *   <li>Address book XML data</li>
     *   <li>Location descriptions</li>
     *   <li>Location IDs</li>
     * </ul>
     * These vectors maintain corresponding indices for related data.</p>
     *
     * @return Vector containing XML address book strings for all active locations
     */
    public java.util.Vector remoteAddressBooks() {
        java.util.Vector vector = new java.util.Vector();
        remoteLocationDesc = new java.util.Vector();
        remoteLocationId = new java.util.Vector();

        OscarCommLocationsDao dao = SpringUtils.getBean(OscarCommLocationsDao.class);
        List<OscarCommLocations> comms = dao.findByCurrent1(1);

        for (OscarCommLocations comm : comms) {
            vector.add(comm.getAddressBook());
            remoteLocationDesc.add(comm.getLocationDesc());
            remoteLocationId.add("" + comm.getId());
        }
        return vector;
    }
    /**
     * Recursively renders XML address book nodes as HTML tree structure.
     * 
     * <p>This method generates HTML tables with expandable/collapsible groups
     * and selectable provider checkboxes. It creates a hierarchical display
     * with proper indentation based on depth level.</p>
     * 
     * <p>The generated HTML includes:
     * <ul>
     *   <li>Expandable group nodes with +/- icons</li>
     *   <li>Checkboxes for individual providers</li>
     *   <li>Proper CSS classes for styling</li>
     *   <li>JavaScript onclick handlers for tree expansion</li>
     * </ul>
     * </p>
     *
     * @param node The current XML node to render
     * @param out The JspWriter for HTML output
     * @param depth Current depth in the tree hierarchy (for indentation)
     * @param thePros Array of pre-selected provider IDs to check
     */
    public void displayNodes(Node node, JspWriter out, int depth, String[] thePros) {
        depth++;

        Element element = (Element) node;

        try {
            if (depth > 2) {
                if ((element.getTagName()).equals("group")) {
                    out.print("<table id=\"tblDFR" + depth + "\" style=\"display:none\" class=\"groupIndent\" cellpadding=\"0\" border=0 >\n");
                } else {
                    if (node.getPreviousSibling() == null) {
                        out.print("<table id=\"tblDFR" + depth + "\" style=\"display:none\" class=\"groupIndent\" cellpadding=\"0\" cellspacing=0  >\n");
                    }
                }
            } else {
                if (depth == 1) {
                    out.print("<table id=\"tblDFR" + depth + "\" cellpadding=\"0\" border=0>\n");
                } else {
                    out.print("<table id=\"tblDFR" + depth + "\" class=\"groupIndent\" cellpadding=\"0\" border=0>\n");
                }
            }
            out.print("   <tr> \n");
            out.print("      <td> \n");

            if ((element.getTagName()).equals("group")) {
                out.print("<span class=\"treeNode\" onclick=\"javascript:showTbl('tblDFR" + (depth + 1) + "');\">");

                if (depth < 2) {
                    out.print("<img class=\"treeNode\" src=\"img/minusblue.gif\" border=\"0\" />");
                } else {
                    out.print("<img class=\"treeNode\" src=\"img/plusblue.gif\" border=\"0\" />");
                }
                out.print("</span>");
                if (depth == 1) {
                    out.print("<input type=\"checkbox\" name=tblDFR" + depth + " onclick=\"javascript:checkGroup('tblDFR" + (depth + 1) + "');\"><font color=#0c7bd6><b>" + CurrentLocationName + "</b></font><br>");
                } else {
                    out.print("<input type=\"checkbox\" name=tblDFR" + depth + " onclick=\"javascript:checkGroup('tblDFR" + (depth + 1) + "');\"><font color=#0c7bd6><b>" + element.getAttribute("desc") + "</b></font><br>");
                }

            } else {
                if (java.util.Arrays.binarySearch(thePros, element.getAttribute("id")) < 0) {
                    out.print("<input type=\"checkbox\" name=providerNos value=" + element.getAttribute("id") + "  > <font color=#0e8ef7>" + element.getAttribute("desc") + "</font>\n");
                } else {
                    out.print("<input type=\"checkbox\" name=providerNos value=" + element.getAttribute("id") + " checked > " + element.getAttribute("desc") + "\n");
                }
            }
            if (node.hasChildNodes()) {
                NodeList nlst = node.getChildNodes();
                for (int i = 0; i < nlst.getLength(); i++) {
                    displayNodes(nlst.item(i), out, depth, thePros);
                }
            }
            out.print("</td>\n");
            out.print("</tr>\n");
            if ((element.getTagName()).equals("group") && !node.hasChildNodes()) {
                out.print("</table id=" + depth + ">\n");
            } else {
                if (node.getNextSibling() == null) {
                    out.print("</table id=" + depth + ">\n");
                    if (depth == 2)
                        out.print("<img id=\"tblDFR" + depth + "\" class=\"collapse\"   onclick=\"javascript:showTbl('tblDFR" + (depth) + "');\" src=\"img/collapse.gif\" border=\"0\" />");
                    else
                        out.print("<img id=\"tblDFR" + depth + "\" class=\"collapse\"  style=\"display:none\" onclick=\"javascript:showTbl('tblDFR" + (depth) + "');\" src=\"img/collapse.gif\" border=\"0\" />");

                }
            }

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }
//------------------------------------------------------------------------------------------------------------

    ////////////////////////////////////////////////////////////////////////////////
    public void displayRemoteNodes(Node node, JspWriter out, int depth, String[] thePros, int remoId, java.util.Vector locationVector) {
        depth++;

        Element element = (Element) node;

        try {
            if (depth > 2) {
                if ((element.getTagName()).equals("group")) {
                    out.print("<table id=\"tblDFR" + depth + "\" style=\"display:none\" class=\"groupIndent\" cellpadding=\"0\" border=0 >\n");
                } else {
                    if (node.getPreviousSibling() == null) {
                        out.print("<table id=\"tblDFR" + depth + "\" style=\"display:none\" class=\"groupIndent\" cellpadding=\"0\" cellspacing=0  >\n");
                    }
                }
            } else {
                out.print("<table id=\"tblDFR" + depth + "\"  cellpadding=\"0\" border=0>\n");
            }
            out.print("   <tr> \n");
            out.print("      <td> \n");

            if ((element.getTagName()).equals("group")) {
                out.print("<span class=\"treeNode\" onclick=\"javascript:showTbl('tblDFR" + (depth + 1) + "');\">");

                if (depth < 2) {
                    out.print("<img class=\"treeNode\" src=\"img/minusblue.gif\" border=\"0\" />");
                } else {
                    out.print("<img class=\"treeNode\" src=\"img/plusblue.gif\" border=\"0\" />");
                }
                out.print("</span>");
                if (depth == 1) {
                    out.print("<input type=\"checkbox\" name=tblDFR" + depth + " onclick=\"javascript:checkGroup('tblDFR" + (depth + 1) + "');\"><font color=#ff9452><b>" + ((String) remoteLocationDesc.elementAt(remoId)) + "</b></font><br>");
                } else {
                    out.print("<input type=\"checkbox\" name=tblDFR" + depth + " onclick=\"javascript:checkGroup('tblDFR" + (depth + 1) + "');\"><font color=#ff9452><b>" + element.getAttribute("desc") + "</b></font><br>");
                }

            } else {
                MiscUtils.getLogger().debug("Im here");
                int binSearch = java.util.Arrays.binarySearch(thePros, element.getAttribute("id"));
                MiscUtils.getLogger().debug("the binsearch returned " + binSearch + " there are " + locationVector.size() + " in the locationVector ");
                if ((binSearch > 0) && (((String) locationVector.elementAt(binSearch)).equals(remoteLocationId.elementAt(remoId)))) {
                    MiscUtils.getLogger().debug("i found it at = " + locationVector.elementAt(binSearch));
                    out.print("<input type=\"checkbox\" name=providers value=" + element.getAttribute("id") + "@" + ((String) remoteLocationId.elementAt(remoId)) + " checked > " + element.getAttribute("desc") + "\n");
                } else {
                    out.print("<input type=\"checkbox\" name=providers value=" + element.getAttribute("id") + "@" + ((String) remoteLocationId.elementAt(remoId)) + "  ><font color=#ff5900>" + element.getAttribute("desc") + "</font>\n");
                }
            }
            if (node.hasChildNodes()) {
                NodeList nlst = node.getChildNodes();
                for (int i = 0; i < nlst.getLength(); i++) {
                    displayRemoteNodes(nlst.item(i), out, depth, thePros, remoId, locationVector);
                }
            }
            out.print("</td>\n");
            out.print("</tr>\n");
            if ((element.getTagName()).equals("group") && !node.hasChildNodes()) {
                out.print("</table id=" + depth + ">\n");
            } else {
                if (node.getNextSibling() == null) {
                    out.print("</table id=" + depth + ">\n");
                    if (depth == 2)
                        out.print("<img id=\"tblDFR" + depth + "\" class=\"collapse\"   onclick=\"javascript:showTbl('tblDFR" + (depth) + "');\" src=\"img/collapse.gif\" border=\"0\" />");
                    else
                        out.print("<img id=\"tblDFR" + depth + "\" class=\"collapse\"  style=\"display:none\" onclick=\"javascript:showTbl('tblDFR" + (depth) + "');\" src=\"img/collapse.gif\" border=\"0\" />");

                }
            }

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }
//------------------------------------------------------------------------------------------------------------

    ////////////////////////////////////////////////////////////////////////////////
    public void displayRemoteNodes2(Node node, JspWriter out, int depth, MsgReplyMessageData reData, int remoId, java.util.Vector locationVector) {
        depth++;

        Element element = (Element) node;

        try {
            if (depth > 2) {
                if ((element.getTagName()).equals("group")) {
                    out.print("<table id=\"tblDFR" + depth + "\" style=\"display:none\" class=\"groupIndent\" cellpadding=\"0\" border=0 >\n");
                } else {
                    if (node.getPreviousSibling() == null) {
                        out.print("<table id=\"tblDFR" + depth + "\" style=\"display:none\" class=\"groupIndent\" cellpadding=\"0\" cellspacing=0  >\n");
                    }
                }
            } else {
                out.print("<table id=\"tblDFR" + depth + "\"  cellpadding=\"0\" border=0>\n");
            }
            out.print("   <tr> \n");
            out.print("      <td> \n");

            if ((element.getTagName()).equals("group")) {
                out.print("<span class=\"treeNode\" onclick=\"javascript:showTbl('tblDFR" + (depth + 1) + "',event);\">");

                if (depth < 2) {
                    out.print("<img class=\"treeNode\" src=\"img/minusblue.gif\" border=\"0\" />");
                } else {
                    out.print("<img class=\"treeNode\" src=\"img/plusblue.gif\" border=\"0\" />");
                }
                out.print("</span>");
                if (depth == 1) {
                    out.print("<input type=\"checkbox\" name=tblDFR" + depth + " onclick=\"javascript:checkGroup('tblDFR" + (depth + 1) + "',event);\"><font color=#ff9452><b>" + ((String) remoteLocationDesc.elementAt(remoId)) + "</b></font><br>");
                } else {
                    out.print("<input type=\"checkbox\" name=tblDFR" + depth + " onclick=\"javascript:checkGroup('tblDFR" + (depth + 1) + "',event);\"><font color=#ff9452><b>" + element.getAttribute("desc") + "</b></font><br>");
                }

            } else {

                //int binSearch = java.util.Arrays.binarySearch(thePros,element.getAttribute("id"));

                //if ( ( binSearch > 0 ) && ( ( (String) locationVector.elementAt(binSearch) ).equals( (String) remoteLocationId.elementAt(remoId)  ) )){

                if (reData.remoContains(element.getAttribute("id"), (String) remoteLocationId.elementAt(remoId))) {
                    out.print("<input type=\"checkbox\" name=providers value=" + element.getAttribute("id") + "@" + ((String) remoteLocationId.elementAt(remoId)) + " checked > " + element.getAttribute("desc") + "\n");
                } else {
                    out.print("<input type=\"checkbox\" name=providers value=" + element.getAttribute("id") + "@" + ((String) remoteLocationId.elementAt(remoId)) + "  ><font color=#ff5900>" + element.getAttribute("desc") + "</font>\n");
                }
            }
            if (node.hasChildNodes()) {
                NodeList nlst = node.getChildNodes();
                for (int i = 0; i < nlst.getLength(); i++) {
                    displayRemoteNodes2(nlst.item(i), out, depth, reData, remoId, locationVector);
                }
            }
            out.print("</td>\n");
            out.print("</tr>\n");
            if ((element.getTagName()).equals("group") && !node.hasChildNodes()) {
                out.print("</table id=" + depth + ">\n");
            } else {
                if (node.getNextSibling() == null) {
                    out.print("</table id=" + depth + ">\n");
                    if (depth == 2)
                        out.print("<img id=\"tblDFR" + depth + "\" class=\"collapse\"   onclick=\"javascript:showTbl('tblDFR" + (depth) + "',event);\" src=\"img/collapse.gif\" border=\"0\" />");
                    else
                        out.print("<img id=\"tblDFR" + depth + "\" class=\"collapse\"  style=\"display:none\" onclick=\"javascript:showTbl('tblDFR" + (depth) + "',event);\" src=\"img/collapse.gif\" border=\"0\" />");

                }
            }

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }
//------------------------------------------------------------------------------------------------------------


}
