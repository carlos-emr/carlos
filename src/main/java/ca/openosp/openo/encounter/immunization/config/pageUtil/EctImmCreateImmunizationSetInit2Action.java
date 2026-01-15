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
package ca.openosp.openo.encounter.immunization.config.pageUtil;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;

import org.apache.struts2.ActionSupport;

public class EctImmCreateImmunizationSetInit2Action extends ActionSupport {
    private HttpServletRequest request = ServletActionContext.getRequest();
    private String setName;
    private String numRows;
    private String numCols;

    @Override
    public String execute() {
        if (numCols != null) numCols = numCols.trim();
        if (numRows != null) numRows = numRows.trim();

        addActionMessage("cols: " + numCols);
        addActionMessage("rows: " + numRows);
        addActionMessage("name: " + setName);

        request.setAttribute("cols", numCols);
        request.setAttribute("rows", numRows);
        request.setAttribute("name", setName);

        return SUCCESS;
    }

    // Validation annotations
    public void validate() {
        if (setName == null || setName.trim().isEmpty()) {
            addFieldError("setName", "Set Name is required.");
        }
        if (numRows == null || numRows.trim().isEmpty()) {
            addFieldError("numRows", "Number of Rows is required.");
        } else {
            try {
                int rows = Integer.parseInt(numRows);
                if (rows <= 0) {
                    addFieldError("numRows", "Number of Rows must be greater than zero.");
                }
            } catch (NumberFormatException e) {
                addFieldError("numRows", "Number of Rows must be numeric.");
            }
        }
        if (numCols == null || numCols.trim().isEmpty()) {
            addFieldError("numCols", "Number of Columns is required.");
        } else {
            try {
                int cols = Integer.parseInt(numCols);
                if (cols <= 0) {
                    addFieldError("numCols", "Number of Columns must be greater than zero.");
                }
            } catch (NumberFormatException e) {
                addFieldError("numCols", "Number of Columns must be numeric.");
            }
        }
    }

    // Getters and Setters
    public String getSetName() {
        return setName;
    }

    public void setSetName(String setName) {
        this.setName = setName;
    }

    public String getNumRows() {
        return numRows;
    }

    public void setNumRows(String numRows) {
        this.numRows = numRows;
    }

    public String getNumCols() {
        return numCols;
    }

    public void setNumCols(String numCols) {
        this.numCols = numCols;
    }
}
