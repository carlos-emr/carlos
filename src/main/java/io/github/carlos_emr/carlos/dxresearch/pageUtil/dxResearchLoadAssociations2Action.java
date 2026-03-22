/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.dxresearch.pageUtil;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.DxDao;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.model.DxAssociation;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts2 action for managing diagnosis code associations between issue lists
 * and the disease registry.
 *
 * <p>Provides CRUD operations for {@link DxAssociation} records via method-based
 * routing. Supports retrieving all associations, adding/clearing associations,
 * CSV import/export, and auto-populating associations from case management issues.
 * Requires {@code _dxresearch} privilege with appropriate access level per operation.</p>
 *
 * @since 2026-03-17
 */
public class dxResearchLoadAssociations2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private DxDao dxDao = (DxDao) SpringUtils.getBean(DxDao.class);
    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private static final String PRIVILEGE_READ = "r";
    private static final String PRIVILEGE_UPDATE = "u";
    private static final String PRIVILEGE_WRITE = "w";

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Routes the request to the appropriate handler method based on the {@code method}
     * request parameter.
     *
     * @return String "success" after method delegation
     * @throws Exception if an error occurs during processing
     */
    public String execute() throws Exception {
        String method = request.getParameter("method");

        if ("getAllAssociations".equals(method)) {
            getAllAssociations();
        } else if ("clearAssociations".equals(method)) {
            clearAssociations();
        } else if ("addAssociation".equals(method)) {
            addAssociation();
        } else if ("export".equals(method)) {
            export();
        } else if ("uploadFile".equals(method)) {
            uploadFile();
        } else if ("autoPopulateAssociations".equals(method)) {
            autoPopulateAssociations();
        }

        return SUCCESS;
     }

    /**
     * Retrieves all diagnosis code associations with their descriptions and writes
     * them as a JSON array to the response.
     *
     * @return String {@code null} (response written directly)
     * @throws IOException if an I/O error occurs writing the response
     */
    public String getAllAssociations() throws IOException {
        checkPrivilege(request, PRIVILEGE_READ);

        //load associations
        List<DxAssociation> associations = dxDao.findAllAssociations();

        for (DxAssociation assoc : associations) {
            assoc.setDxDescription(getDescription(assoc.getDxCodeType(), assoc.getDxCode()));
            assoc.setDescription(getDescription(assoc.getCodeType(), assoc.getCode()));
        }

        //serialize and return
        ArrayNode jsonArray = objectMapper.valueToTree(associations);
        response.getWriter().print(jsonArray);
        return null;
    }

    private String getDescription(String dxCodeType, String dxCode) {
        for (Object[] o : dxDao.findCodingSystemDescription(dxCodeType, dxCode)) {
            return String.valueOf(o[1]);
        }
        return null;
    }

    /**
     * Removes all diagnosis code associations and returns the count of records updated.
     *
     * @return String {@code null} (response written directly)
     * @throws IOException if an I/O error occurs writing the response
     */
    public String clearAssociations() throws IOException {
        checkPrivilege(request, PRIVILEGE_UPDATE);

        int recordsUpdated = dxDao.removeAssociations();

        Map<String, Integer> map = new HashMap<String, Integer>();
        map.put("recordsUpdated", recordsUpdated);
        response.getWriter().print(objectMapper.valueToTree(map));
        return null;
    }

    /**
     * Adds a new diagnosis code association from request parameters.
     *
     * @return String {@code null} (response written directly)
     * @throws IOException if an I/O error occurs writing the response
     */
    public String addAssociation() throws IOException {
        checkPrivilege(request, PRIVILEGE_WRITE);

        DxAssociation dxa = new DxAssociation();
        dxa.setCodeType(request.getParameter("codeType"));
        dxa.setCode(request.getParameter("code"));
        dxa.setDxCodeType(request.getParameter("dxCodeType"));
        dxa.setDxCode(request.getParameter("dxCode"));

        dxDao.persist(dxa);

        Map<String, String> map = new HashMap<String, String>();
        map.put("result", "success");
        response.getWriter().print(objectMapper.valueToTree(map));
        return null;
    }

    /**
     * Exports all diagnosis code associations as a CSV file download.
     *
     * @return String {@code null} (response written directly as CSV download)
     * @throws IOException if an I/O error occurs writing the response
     */
    public String export() throws IOException {
        checkPrivilege(request, PRIVILEGE_READ);

        List<DxAssociation> associations = dxDao.findAllAssociations();

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"dx_associations.csv\"");

        CSVPrinter printer = new CSVPrinter(response.getWriter(), CSVFormat.EXCEL);

        printer.printRecord("Issue List Code Type", "Issue List Code", "Disease Registry Code Type", "Disease Registry Code");
        for (DxAssociation dxa : associations) {
            printer.printRecord(dxa.getCodeType(), dxa.getCode(), dxa.getDxCodeType(), dxa.getDxCode());
        }

        printer.flush();
        printer.close();

        return null;
    }

    /**
     * Imports diagnosis code associations from an uploaded CSV file. Optionally
     * replaces all existing associations before importing.
     *
     * @return String "success" or "error" if the file is invalid
     * @throws IOException if an I/O error occurs during file processing
     */
    public String uploadFile() throws IOException {
        checkPrivilege(request, PRIVILEGE_WRITE);

        if (file == null) {
            addActionError("File not uploaded.");
            return ERROR;
        }

        // Validate that the file is a valid uploaded file and prevent path traversal
        if (!isValidUploadedFile(file)) {
            MiscUtils.getLogger().error("SECURITY WARNING: Invalid file path detected for file upload");
            addActionError("Invalid file upload.");
            return ERROR;
        }

        // Re-validate at point of use for static analysis visibility
        File validatedFile = PathValidationUtils.validateUpload(file);
        // Parse CSV using Apache Commons CSV with proper resource management
        String[][] data;
        try (FileReader reader = new FileReader(validatedFile);
             CSVParser parser = new CSVParser(reader, CSVFormat.EXCEL)) {
            List<CSVRecord> records = parser.getRecords();
            data = new String[records.size()][];
            for (int i = 0; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                data[i] = new String[record.size()];
                for (int j = 0; j < record.size(); j++) {
                    data[i][j] = record.get(j);
                }
            }
        }

        int rowsInserted = 0;

        if (this.isReplace()) {
            dxDao.removeAssociations();
        }

        for (int x = 1; x < data.length; x++) {
            if (data[x].length != 4) {
                continue;
            }
            DxAssociation assoc = new DxAssociation();
            assoc.setCodeType(data[x][0]);
            assoc.setCode(data[x][1]);
            assoc.setDxCodeType(data[x][2]);
            assoc.setDxCode(data[x][3]);

            dxDao.persist(assoc);
            rowsInserted++;
        }

        Map<String, Integer> map = new HashMap<String, Integer>();
        map.put("recordsAdded", rowsInserted);
        response.getWriter().print(objectMapper.valueToTree(map));

        return SUCCESS;
    }

    /**
     * Auto-populates the disease registry by matching case management issues
     * with existing associations and saving the corresponding diagnosis entries.
     *
     * @return String {@code null} (response written directly)
     * @throws IOException if an I/O error occurs writing the response
     */
    public String autoPopulateAssociations() throws IOException {
        checkPrivilege(request, PRIVILEGE_WRITE);

        int recordsAdded = 0;
        CaseManagementIssueDAO cmiDao = (CaseManagementIssueDAO) SpringUtils.getBean(CaseManagementIssueDAO.class);
        CaseManagementManager cmMgr = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);
        IssueDAO issueDao = (IssueDAO) SpringUtils.getBean(IssueDAO.class);
        DxresearchDAO dxrDao = (DxresearchDAO) SpringUtils.getBean(DxresearchDAO.class);

        //clear existing entries
        dxrDao.removeAllAssociationEntries();

        //get all certain issues
        List<CaseManagementIssue> certainIssues = cmiDao.getAllCertainIssues();
        MiscUtils.getLogger().debug("certain issues found=" + certainIssues.size());
        for (CaseManagementIssue issue : certainIssues) {
            Issue iss = issueDao.getIssue(issue.getIssue().getId());
            MiscUtils.getLogger().debug("checking " + iss.getType() + "," + iss.getCode());
            DxAssociation assoc = dxDao.findAssociation(iss.getType(), iss.getCode());
            if (assoc != null) {
                MiscUtils.getLogger().debug("match");
                //we now have a certain issue which matches an association.
                cmMgr.saveToDx(LoggedInInfo.getLoggedInInfoFromSession(request), issue.getDemographic_no().toString(), assoc.getDxCode(), assoc.getDxCodeType(), true);
                recordsAdded++;
            }
        }

        Map<String, Integer> map = new HashMap<String, Integer>();
        map.put("recordsAdded", recordsAdded);
        response.getWriter().print(objectMapper.valueToTree(map));

        return null;
    }


    private void checkPrivilege(HttpServletRequest request, String privilege) {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_dxresearch", privilege, null)) {
            throw new RuntimeException("missing required sec object (_dxresearch)");
        }
    }

    /**
     * Validates that the uploaded file is within the expected temporary directory
     * and prevents path traversal attacks.
     *
     * @param uploadedFile the file to validate
     * @return true if the file is valid, false otherwise
     */
    private boolean isValidUploadedFile(File uploadedFile) {
        if (uploadedFile == null) {
            return false;
        }

        // Use PathValidationUtils for temp directory validation
        // This checks system temp dir and Tomcat work directories
        if (!PathValidationUtils.isInAllowedTempDirectory(uploadedFile)) {
            return false;
        }

        // Additionally verify the file exists and is a regular file
        return uploadedFile.exists() && uploadedFile.isFile();
    }

    private File file; // Uploaded file
    private boolean replace = true; // Flag for replacement

    /**
     * Returns the uploaded CSV file.
     *
     * @return File the uploaded file, or {@code null} if no file was uploaded
     */
    public File getFile() {
        return file;
    }

    @StrutsParameter
    public void setFile(File file) {
        this.file = file;
    }

    /**
     * Returns whether existing associations should be replaced on import.
     *
     * @return boolean {@code true} to clear existing associations before import
     */
    public boolean isReplace() {
        return replace;
    }

    @StrutsParameter
    public void setReplace(boolean replace) {
        this.replace = replace;
    }
}