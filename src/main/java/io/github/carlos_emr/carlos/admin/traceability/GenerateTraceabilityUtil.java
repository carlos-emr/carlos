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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.admin.traceability;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

import io.github.carlos_emr.carlos.util.OscarRoleObjectPrivilege;

/**
 * Utility class providing helper methods for the CARLOS EMR traceability system.
 *
 * <p>Provides functionality for building file-hash trace maps of the deployed application,
 * downloading generated trace files, and checking user privileges for traceability operations.
 *
 * @see GenerateTrace2Action
 * @see GenerateTraceabilityReport2Action
 * @since 2026-03-17
 */
public class GenerateTraceabilityUtil {
    private GenerateTraceabilityUtil() {
    }

    /**
     * Builds a map of relative file paths to their SHA-256 hashes for all files in the web application.
     *
     * @param request HttpServletRequest used to resolve the web application's real path
     * @return Map of String file paths to String SHA-256 hex digests
     * @throws Exception if file reading or hashing fails
     */
    public static Map<String, String> buildTraceMap(HttpServletRequest request) throws Exception {
        Map<String, String> traceMap = new HashMap<String, String>();
        HttpSession session = request.getSession();
        ServletContext servletContext = session.getServletContext();
        String realPath = servletContext.getRealPath("/");
        Iterator<File> iterator = FileUtils.iterateFiles(new File(realPath), null, true);
        while (iterator.hasNext()) {
            File f_ = iterator.next();
            FileInputStream fi_ = new FileInputStream(f_);
            String path = f_.getAbsolutePath();
            path = path.replace(realPath, "");
            traceMap.put(path, DigestUtils.sha256Hex(fi_));
            fi_.close();
        }

        return traceMap;
    }

    /**
     * Streams a file to the HTTP response as a downloadable attachment.
     *
     * @param response HttpServletResponse the response to write the file to
     * @param fileName String the path to the file to download (also used as the attachment filename)
     * @param contentType String the MIME content type for the response
     * @throws Exception if file reading or response writing fails
     */
    public static void download(HttpServletResponse response, String fileName, String contentType) throws Exception {
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        FileInputStream in = new FileInputStream(fileName);
        ServletOutputStream out = response.getOutputStream();
        long buf_size = new File(fileName).length();
        byte[] outputByte = new byte[(int) buf_size];
        while (in.read(outputByte) != -1) {
            out.write(outputByte, 0, (int) buf_size);
        }
        in.close();
        out.flush();
        out.close();
    }

    /**
     * Checks if the specified role has privilege for the given security object.
     *
     * @param objectName String the security object name(s) to check
     * @param roleName String the role name to check privileges for
     * @return boolean true if the role has the required privilege
     */
    @SuppressWarnings("unchecked")
    public static boolean hasPrivilege(String objectName, String roleName) {
        ArrayList<Object> v = OscarRoleObjectPrivilege.getPrivilegePropAsArrayList(objectName);
        return OscarRoleObjectPrivilege.checkPrivilege(roleName, (Properties) v.get(0), (ArrayList<String>) v.get(1));
    }
}
