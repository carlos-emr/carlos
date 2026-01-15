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


/*
 * Util.java
 *
 * Created on March 7, 2009, 7:51 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ca.openosp.openo.demographic.pageUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;

import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.XmlCalendar;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.model.CaseManagementNoteExt;
import ca.openosp.openo.commn.dao.PartialDateDao;
import ca.openosp.openo.commn.model.PartialDate;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.prevention.PreventionDisplayConfig;
import ca.openosp.openo.providers.data.ProviderData;
import ca.openosp.openo.util.StringUtils;
import ca.openosp.openo.util.UtilDateUtilities;
import ca.openosp.OscarProperties;

/**
 * @author Ronnie
 */
public class Util {
    static private final Logger logger = MiscUtils.getLogger();
    static private final PartialDateDao partialDateDao = (PartialDateDao) SpringUtils.getBean(PartialDateDao.class);

    static public String addLine(String baseStr, String addStr) {
        return addLine(baseStr, "", addStr);
    }

    static public String addLine(String baseStr, String label, String addStr) {
        String newStr = StringUtils.noNull(baseStr);
        if (StringUtils.filled(newStr)) {
            newStr += StringUtils.filled(addStr) ? "\n" + StringUtils.noNull(label) + addStr : "";
        } else {
            newStr += StringUtils.filled(addStr) ? StringUtils.noNull(label) + addStr : "";
        }
        return newStr;
    }

    static public String addSummary(String label, String item) {
        return addSummary("", label, item);
    }

    static public String addSummary(String summary, String label, String item) {
        if (summary == null) summary = addSummary(label, item);
        if (StringUtils.empty(item)) return summary;

        if (StringUtils.filled(summary)) summary += ",";
        summary += "[" + label + "]:" + item;

        return summary;
    }

    static public XmlCalendar calDate(Date inDate) {
        return calDate(inDate, false);
    }

    static public XmlCalendar calDateTZD(Date inDate) {
        return calDate(inDate, true);
    }

    static public XmlCalendar calDate(Date inDate, Boolean includeTZD) {
        String date = UtilDateUtilities.DateToString(inDate, "yyyy-MM-dd");
        String time = UtilDateUtilities.DateToString(inDate, "HH:mm:ss");
        String tzd;
        if (includeTZD) {
            tzd = UtilDateUtilities.DateToString(inDate, "Z");
            tzd = new StringBuffer(tzd).insert(tzd.length() - 2, ":").toString();
        } else {
            tzd = "";
        }
        try {
            XmlCalendar x = new XmlCalendar(date + "T" + time + tzd);
            return x;
        } catch (Exception ex) {
            XmlCalendar x = new XmlCalendar("0001-01-01T00:00:00");
            return x;
        }
    }

    static public XmlCalendar calDate(String inDate) {
        Date dateTime = UtilDateUtilities.StringToDate(inDate, "yyyy-MM-dd HH:mm:ss");
        if (dateTime != null) {
            return calDate(dateTime, true); // add timezone
        }
        if (dateTime == null) {
            dateTime = UtilDateUtilities.StringToDate(inDate, "yyyy-MM-dd");
        }
        if (dateTime == null) { //inDate may contain time only
            try {
                XmlCalendar x = new XmlCalendar(inDate);
                return x;
            } catch (Exception ex) { //inDate format is wrong
                XmlCalendar x = new XmlCalendar("0001-01-01T00:00:00");
                return x;
            }
        } else {
            return calDate(dateTime, false);
        }
    }

    static public boolean checkDir(String dirName) throws Exception {
        dirName = fixDirName(dirName);
        try {
            Runtime rt = Runtime.getRuntime();
            String[] env = {""};
            File dir = new File(dirName);
            Process proc = rt.exec("touch null.tmp", env, dir);
            int ecode = proc.waitFor();
            if (ecode == 0) {
                cleanFile("null.tmp", dirName);
                return true;
            } else {
                return false;
            }
        } catch (IOException ex) {
            logger.error("Error", ex);
        }
        logger.error("Error! Cannot write to directory [" + dirName + "]");
        return false;
    }

    static public boolean cleanFile(String filename, String dirname) {
        // Validate path to prevent directory traversal attacks
        dirname = fixDirName(dirname);

        // Extract just the base filename, removing any directory components
        String safeFileName = FilenameUtils.getName(filename);
        if (!StringUtils.filled(safeFileName)) {
            logger.error("Error! Invalid filename after sanitization");
            return false;
        }

        // Create the base directory
        File baseDir = new File(dirname);

        // Create the target file
        File f = new File(baseDir, safeFileName);

        // Validate that the resolved path is within the allowed directory
        try {
            PathValidationUtils.validateExistingPath(f, baseDir);
        } catch (SecurityException e) {
            logger.error("Error! Attempted path traversal attack detected for file: " + filename);
            return false;
        }

        return cleanFile(f);
    }

    static public boolean cleanFile(String filename) {
        File f = new File(filename);
        OscarProperties props = OscarProperties.getInstance();

        // Try configured directories first (strict validation, no temp fallback)
        String[] allowedDirProperties = {"TMP_DIR", "DOCUMENT_DIR"};
        for (String propName : allowedDirProperties) {
            String dirPath = props.getProperty(propName);
            if (dirPath != null && !dirPath.trim().isEmpty()) {
                try {
                    File validatedFile = PathValidationUtils.validateExistingPath(f, new File(dirPath));
                    return cleanFile(validatedFile);
                } catch (SecurityException e) {
                    // File not in this directory, try next configured directory
                    logger.debug("File validation failed for {}: {}", propName, e.getMessage());
                }
            }
        }

        // Fall back to system temp directories for app-created temp files
        if (PathValidationUtils.isInAllowedTempDirectory(f)) {
            return cleanFile(f);
        }

        logger.error("File validation failed - file is outside all allowed directories: {}", filename);
        return false;
    }

    static public boolean cleanFile(File file) {
        if (!file.delete()) {
            logger.error("Error! Cannot delete file [" + file.getPath() + "]");
            return false;
        }
        return true;
    }

    static public void cleanFiles(ArrayList<File> files) {
        for (File file : files) {
            cleanFile(file);
        }
    }

    static public boolean convert10toboolean(String s) {
        Boolean ret = false;
        if (s != null && s.trim().equals("1")) {
            ret = true;
        }
        return ret;
    }

    static public void downloadFile(String fileName, String dirName, HttpServletResponse rsp) {
        try {
            dirName = fixDirName(dirName);
            if (rsp == null) return;

            // Validate filename to prevent path traversal attacks
            // Extract just the base filename, removing any directory components
            String safeFileName = FilenameUtils.getName(fileName);
            
            // If the filename is empty or null after sanitization, reject the request
            if (safeFileName == null || safeFileName.trim().isEmpty()) {
                logger.error("Invalid filename provided: " + fileName);
                return;
            }
            
            // Create the file object with the sanitized filename
            File documentDir = new File(dirName);
            File requestedFile = new File(documentDir, safeFileName);

            // Validate the file path using PathValidationUtils
            try {
                PathValidationUtils.validateExistingPath(requestedFile, documentDir);
            } catch (SecurityException e) {
                logger.error("Path traversal attempt detected for file: " + fileName);
                return;
            }

            // Verify the file exists and is readable
            if (!requestedFile.exists() || !requestedFile.isFile() || !requestedFile.canRead()) {
                logger.error("Error during file download: file does not exist or is not accessible - {}", fileName);
                rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            rsp.setContentType("application/octet-stream");
            rsp.setHeader("Content-Disposition", "attachment; filename=\"" + sanitizeHeaderValue(safeFileName) + "\"");
            rsp.setContentLengthLong(requestedFile.length());
            
            try (InputStream in = new FileInputStream(requestedFile);
                OutputStream out = rsp.getOutputStream()) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        } catch (IOException ex) {
            logger.error("Error during file download: {}", fileName, ex);
        }
    }
    
    /**
     * Sanitizes a value to be safely used in HTTP headers.
     * Removes all control characters including CR and LF to prevent HTTP response splitting attacks.
     * This prevents attackers from injecting additional headers or splitting the HTTP response.
     * 
     * @param value The header value to sanitize
     * @return The sanitized header value safe for use in HTTP headers
     */
    private static String sanitizeHeaderValue(String value) {
        if (value == null) {
            return "";
        }
        
        // Remove all control characters including CR (\r) and LF (\n)
        // This prevents HTTP response splitting attacks
        // Also remove other control characters that could cause issues
        String sanitized = value.replaceAll("[\r\n\u0000-\u001F\u007F-\u009F]", "");
        
        // Ensure the filename is not empty after sanitization
        if (sanitized.trim().isEmpty()) {
            return "file";
        }
        
        return sanitized;
    }

    static public String fixDirName(String dirName) {
        if (StringUtils.filled(dirName)) {
            if (dirName.charAt(dirName.length() - 1) != '/') dirName = dirName + '/';
        }
        return StringUtils.noNull(dirName);
    }

    static public String mimeToExt(String mimeType) {
        String ret = "";
        if (!StringUtils.filled(mimeType)) return ret;
        if (mimeType.charAt(0) == '.') return mimeType;

        String type_ext = "application/envoy=evy|application/fractals=fif|application/futuresplash=spl|application/hta=hta|application/internet-property-stream=acx|application/mac-binhex40=hqx|application/msword=doc|application/octet-stream=bin|application/oda=oda|application/olescript=axs|application/pdf=pdf|application/pics-rules=prf|application/pkcs10=p10|application/pkix-crl=crl|application/postscript=ai|application/postscript=eps|application/postscript=ps|application/rtf=rtf|application/set-payment-initiation=setpay|application/set-registration-initiation=setreg|application/vnd.ms-excel=xls|application/vnd.ms-outlook=msg|application/vnd.ms-pkicertstore=sst|application/vnd.ms-pkiseccat=cat|application/vnd.ms-pkistl=stl|application/vnd.ms-powerpoint=ppt|application/vnd.ms-project=mpp|application/vnd.ms-works=wcm|application/vnd.ms-works=wdb|application/vnd.ms-works=wks|application/vnd.ms-works=wps|application/winhlp=hlp|application/x-bcpio=bcpio|application/x-cdf=cdf|application/x-compress=z|application/x-compressed=tgz|application/x-cpio=cpio|application/x-csh=csh|application/x-director=dcr|application/x-director=dir|application/x-director=dxr|application/x-dvi=dvi|application/x-gtar=gtar|application/x-gzip=gz|application/x-hdf=hdf|application/x-internet-signup=ins|application/x-internet-signup=isp|application/x-iphone=iii|application/x-javascript=js|application/x-latex=latex|application/x-msaccess=mdb|application/x-mscardfile=crd|application/x-msclip=clp|application/x-msdownload=dll|application/x-msmediaview=m13|application/x-msmediaview=m14|application/x-msmediaview=mvb|application/x-msmetafile=wmf|application/x-msmoney=mny|application/x-mspublisher=pub|application/x-msschedule=scd|application/x-msterminal=trm|application/x-mswrite=wri|application/x-netcdf=cdf|application/x-netcdf=nc|application/x-perfmon=pma|application/x-perfmon=pmc|application/x-perfmon=pml|application/x-perfmon=pmr|application/x-perfmon=pmw|application/x-pkcs12=p12|application/x-pkcs12=pfx|application/x-pkcs7-certificates=p7b|application/x-pkcs7-certificates=spc|application/x-pkcs7-certreqresp=p7r|application/x-pkcs7-mime=p7c|application/x-pkcs7-mime=p7m|application/x-pkcs7-signature=p7s|application/x-sh=sh|application/x-shar=shar|application/x-shockwave-flash=swf|application/x-stuffit=sit|application/x-sv4cpio=sv4cpio|application/x-sv4crc=sv4crc|application/x-tar=tar|application/x-tcl=tcl|application/x-tex=tex|application/x-texinfo=texi|application/x-texinfo=texinfo|application/x-troff=roff|application/x-troff=t|application/x-troff=tr|application/x-troff-man=man|application/x-troff-me=me|application/x-troff-ms=ms|application/x-ustar=ustar|application/x-wais-source=src|application/x-x509-ca-cert=cer|application/x-x509-ca-cert=crt|application/x-x509-ca-cert=der|application/ynd.ms-pkipko=pko|application/zip=zip|audio/basic=au|audio/basic=snd|audio/mid=mid|audio/mid=rmi|audio/mpeg=mp3|audio/x-aiff=aif|audio/x-aiff=aifc|audio/x-aiff=aiff|audio/x-mpegurl=m3u|audio/x-pn-realaudio=ra|audio/x-pn-realaudio=ram|audio/x-wav=wav|image/png=png|image/bmp=bmp|image/cis-cod=cod|image/gif=gif|image/ief=ief|image/jpeg=jpg|image/pipeg=jfif|image/svg+xml=svg|image/tiff=tif|image/tiff=tiff|image/x-cmu-raster=ras|image/x-cmx=cmx|image/x-icon=ico|image/x-portable-anymap=pnm|image/x-portable-bitmap=pbm|image/x-portable-graymap=pgm|image/x-portable-pixmap=ppm|image/x-rgb=rgb|image/x-xbitmap=xbm|image/x-xpixmap=xpm|image/x-xwindowdump=xwd|message/rfc822=mht|message/rfc822=mhtml|message/rfc822=nws|text/css=css|text/h323=323|text/html=htm|text/html=html|text/html=stm|text/iuls=uls|text/plain=bas|text/plain=c|text/plain=h|text/plain=txt|text/richtext=rtx|text/scriptlet=sct|text/tab-separated-values=tsv|text/webviewhtml=htt|text/x-component=htc|text/x-setext=etx|text/x-vcard=vcf|video/mpeg=mp2|video/mpeg=mpa|video/mpeg=mpe|video/mpeg=mpeg|video/mpeg=mpg|video/mpeg=mpv2|video/quicktime=mov|video/quicktime=qt|video/x-la-asf=lsf|video/x-la-asf=lsx|video/x-ms-asf=asf|video/x-ms-asf=asr|video/x-ms-asf=asx|video/x-msvideo=avi|video/x-sgi-movie=movie|x-world/x-vrml=flr|x-world/x-vrml=vrml|x-world/x-vrml=wrl|x-world/x-vrml=wrz|x-world/x-vrml=xaf|x-world/x-vrml=xof|";
        mimeType = mimeType.toLowerCase();
        type_ext = type_ext.toLowerCase();
        int pos = type_ext.indexOf(mimeType);
        if (pos > -1) {
            pos = pos + mimeType.length() + 1;
            int end_pos = type_ext.indexOf('|', pos);
            ret = "." + type_ext.substring(pos, end_pos);
        }
        return ret;
    }

    static public cdsDt.HealthCardProvinceCode.Enum setProvinceCode(String provinceCode) {
        provinceCode = setCountrySubDivCode(provinceCode);
        if (provinceCode.equals("US")) return cdsDt.HealthCardProvinceCode.X_50; //Not available, temporarily
        if (provinceCode.equals("non-Canada/US")) return cdsDt.HealthCardProvinceCode.X_90; //Not applicable
        return cdsDt.HealthCardProvinceCode.Enum.forString(provinceCode);
    }

    static public String setCountrySubDivCode(String countrySubDivCode) {
        if (countrySubDivCode != null) {
            countrySubDivCode = countrySubDivCode.trim();
            if (StringUtils.filled(countrySubDivCode)) {
                if (countrySubDivCode.equals("OT")) return "Other";
                if (countrySubDivCode.indexOf("-") == -1) return "CA-" + countrySubDivCode;
                return countrySubDivCode;
            }
        }
        return "";
    }

    static public String onlyNum(String s) {
        if (s == null) return null;

        for (int i = 0; i < s.length(); i++) {
            if (!"0123456789".contains(s.substring(i, i + 1))) {
                s = s.substring(0, i) + s.substring(i + 1, s.length());
                i--;
            }
        }
        return s;
    }

    static public String leadingNum(String s) {
        if (s == null) return "";
        s = s.trim();
        for (int i = 0; i < s.length(); i++) {
            if (!".0123456789".contains(s.substring(i, i + 1))) {
                s = s.substring(0, i);
                break;
            }
        }
        return s;
    }

    /**
     * Extracts the leading numeric portion from a string, including fractions.
     * Unlike leadingNum(), this preserves '/' characters to support fractional
     * dosage formats like "125/5" in medication strength expressions.
     *
     * @param s The input string to parse
     * @return The leading numeric portion including fractions (e.g., "125/5" from "125/5 mg/ml")
     * @since 2025-01-08
     */
    static public String leadingNumWithFraction(String s) {
        if (s == null) return "";
        s = s.trim();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            // Allowed characters for the amount (digits, decimal point, slash)
            if (Character.isDigit(c) || c == '.' || c == '/') {
                sb.append(c);
            } else {
                break; // stop at anything else (space, letter, %, etc.)
            }
        }

        return sb.toString();
    }

    static public float leadingNumF(String s) {
        s = leadingNum(s);
        try {
            float f = Float.parseFloat(s);
            return f;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static public String trailingTxt(String s) {
        s = s.trim();
        for (int i = 0; i < s.length(); i++) {
            if (!".0123456789".contains(s.substring(i, i + 1))) {
                s = s.substring(i);
                break;
            }
        }
        return s.trim();
    }


    static public void writeNameSimple(cdsDt.PersonNameSimpleWithMiddleName personName, String firstName, String lastName) {
        if (!StringUtils.filled(firstName)) firstName = "";
        if (!StringUtils.filled(lastName)) lastName = "";
        personName.setFirstName(firstName);
        personName.setLastName(lastName);
    }

    static public void writeNameSimple(cdsDt.PersonNameSimple personName, String firstName, String lastName) {
        if (!StringUtils.filled(firstName)) firstName = "";
        if (!StringUtils.filled(lastName)) lastName = "";
        personName.setFirstName(firstName);
        personName.setLastName(lastName);
    }

    static public void writeNameSimple(cdsDt.PersonNameSimple personName, String name) {
        if (!StringUtils.filled(name)) name = "";
        if (name.contains(",")) {
            String[] names = name.split(",");
            String firstName = "";
            for (int i = 1; i < names.length; i++)
                firstName += StringUtils.filled(firstName) ? "," + names[i] : names[i];
            personName.setFirstName(firstName);
            personName.setLastName(names[0]);
        } else if (name.contains(" ")) {
            String[] names = name.split(" ");
            String firstName = "";
            for (int i = 0; i < names.length - 1; i++)
                firstName += StringUtils.filled(firstName) ? " " + names[i] : names[i];
            personName.setFirstName(firstName);
            personName.setLastName(names[names.length - 1]);
        } else {
            personName.setFirstName(name);
            personName.setLastName("");
        }
    }

    static public cdsDt.YnIndicatorsimple.Enum yn(String YorN) {
        YorN = YorN.trim().toLowerCase();
        if (YorN.equals("yes") || YorN.equals("y")) return cdsDt.YnIndicatorsimple.Y;
        else return cdsDt.YnIndicatorsimple.N;
    }

    static public boolean zipFiles(ArrayList<File> files, String zipFileName, String dirName) throws Exception {
        try {
            if (files == null) {
                logger.error("Error! No source file for zipping");
                return false;
            }
            if (!StringUtils.filled(zipFileName)) {
                logger.error("Error! Zip filename not given");
                return false;
            }
            if (!checkDir(dirName)) {
                return false;
            }
            
            // Sanitize zipFileName to prevent path traversal
            zipFileName = sanitizeFileName(zipFileName);
            if (!StringUtils.filled(zipFileName)) {
                logger.error("Error! Invalid zip filename after sanitization");
                return false;
            }
            
            dirName = fixDirName(dirName);
            
            // Create the output file with proper path validation
            File outputDir = new File(dirName).getCanonicalFile();
            File outputFile = new File(outputDir, zipFileName).getCanonicalFile();
            
            // Verify the output file is within the intended directory
            if (!outputFile.getParentFile().equals(outputDir)) {
                logger.error("Error! Zip file path traversal attempt detected: " + zipFileName);
                return false;
            }
            
            byte[] buf = new byte[1024];
            ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(outputFile));
            for (File f : files) {
                if (f == null) continue;

                FileInputStream fin = new FileInputStream(f.getAbsolutePath());

                // Add ZIP entry to output stream
                zout.putNextEntry(new ZipEntry(f.getName()));

                // Transfer bytes from the input files to the ZIP file
                int len;
                while ((len = fin.read(buf)) > 0) {
                    zout.write(buf, 0, len);
                }

                // Complete the entry
                zout.closeEntry();
                fin.close();
            }
            // Complete the ZIP file
            zout.close();
            return true;

        } catch (IOException ex) {
            logger.error("Error", ex);
        }
        return false;
    }

    static public boolean zipFiles(ArrayList<File> files, ArrayList<String> dirs, String zipFileName, String dirName) throws Exception {
        try {
            if (files == null) {
                logger.error("Error! No source file for zipping");
                return false;
            }
            if (!StringUtils.filled(zipFileName)) {
                logger.error("Error! Zip filename not given");
                return false;
            }
            if (!checkDir(dirName)) {
                return false;
            }
            
            // Sanitize zipFileName to prevent path traversal
            zipFileName = sanitizeFileName(zipFileName);
            if (!StringUtils.filled(zipFileName)) {
                logger.error("Error! Invalid zip filename after sanitization");
                return false;
            }
            
            dirName = fixDirName(dirName);
            
            // Create the output file with proper path validation
            File outputDir = new File(dirName).getCanonicalFile();
            File outputFile = new File(outputDir, zipFileName).getCanonicalFile();
            
            // Verify the output file is within the intended directory
            if (!outputFile.getParentFile().equals(outputDir)) {
                logger.error("Error! Zip file path traversal attempt detected: " + zipFileName);
                return false;
            }
            
            byte[] buf = new byte[1024];
            ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(outputFile));
            Map<String, Boolean> dirMap = new HashMap<String, Boolean>();
            for (String dir : dirs) {
                dirMap.put(dir, true);
            }
            for (String dir : dirMap.keySet()) {
                if (!dir.isEmpty()) {
                    zout.putNextEntry(new ZipEntry(dir + "/"));
                }
            }

            for (int x = 0; x < files.size(); x++) {
                File f = files.get(x);
                if (f == null) continue;

                // Validate the file path
                if (!isPathWithinDirectory(f, dirName)) {
                    logger.error("Error! File path is outside the allowed directory: " + f.getAbsolutePath());
                    return false;
                }

                FileInputStream fin = new FileInputStream(f.getAbsolutePath());

                String dir = dirs.get(x);

                if (dir.isEmpty()) {

                    // Add ZIP entry to output stream
                    zout.putNextEntry(new ZipEntry(f.getName()));

                } else {
                    zout.putNextEntry(new ZipEntry(dir + "/" + f.getName()));

                }
                // Transfer bytes from the input files to the ZIP file
                int len;
                while ((len = fin.read(buf)) > 0) {
                    zout.write(buf, 0, len);
                }

                // Complete the entry
                zout.closeEntry();
                fin.close();
            }
            // Complete the ZIP file
            zout.close();
            return true;

        } catch (IOException ex) {
            logger.error("Error", ex);
        }
        return false;
    }

    private static boolean isPathWithinDirectory(File file, String dirName) throws IOException {
        try {
            PathValidationUtils.validateExistingPath(file, new File(dirName));
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    static public void putPartialDate(cdsDt.DateFullOrPartial dfp, CaseManagementNoteExt cme) {
        putPartialDate(dfp, cme.getDateValue(), cme.getValue());
    }

    static public void putPartialDate(cdsDt.DateFullOrPartial dfp, Date dateValue, Integer tableName, Integer tableId, Integer fieldName) {
        PartialDate pd = partialDateDao.getPartialDate(tableName, tableId, fieldName);
        putPartialDate(dfp, dateValue, pd.getFormat());
    }

    static public void putPartialDate(cdsDt.DateFullOrPartial dfp, Date dateValue, String format) {
        if (dateValue != null) {
            if (PartialDate.YEARONLY.equals(format)) dfp.setYearOnly(calDate(dateValue));
            else if (PartialDate.YEARMONTH.equals(format)) dfp.setYearMonth(calDate(dateValue));
            else dfp.setFullDate(calDate(dateValue));
        }
    }

    static public void putPartialDate(cdsDt.DateTimeFullOrPartial dfp, Date dateValue, String format) {
        if (dateValue != null) {
            if (PartialDate.YEARONLY.equals(format)) dfp.setYearOnly(calDate(dateValue));
            else if (PartialDate.YEARMONTH.equals(format)) dfp.setYearMonth(calDate(dateValue));
            else dfp.setFullDate(calDate(dateValue));
        }
    }

    static public void putPartialDate(cdsDtCihi.DateFullOrPartial dfp, CaseManagementNoteExt cme) {
        if (cme != null) putPartialDate(dfp, cme.getDateValue(), cme.getValue());
    }

    static public void putPartialDate(cdsDtCihi.DateFullOrPartial dfp, Date dateValue, Integer tableName, Integer tableId, Integer fieldName) {
        String format = null;
        PartialDate pd = partialDateDao.getPartialDate(tableName, tableId, fieldName);
        if (pd != null) format = pd.getFormat();
        putPartialDate(dfp, dateValue, format);
    }

    static public void putPartialDate(cdsDtCihi.DateFullOrPartial dfp, Date dateValue, String format) {
        if (dateValue != null) {
            if (PartialDate.YEARONLY.equals(format)) dfp.setYearOnly(calDate(dateValue));
            else if (PartialDate.YEARMONTH.equals(format)) dfp.setYearMonth(calDate(dateValue));
            else dfp.setFullDate(calDate(dateValue));
        }
    }

    static public String readPartialDate(CaseManagementNoteExt cme) {
        String type = cme.getValue();
        String val = null;

        if (StringUtils.filled(type)) {
            if (type.equals(PartialDate.YEARONLY))
                val = UtilDateUtilities.DateToString(cme.getDateValue(), "yyyy");
            else if (type.equals(PartialDate.YEARMONTH))
                val = UtilDateUtilities.DateToString(cme.getDateValue(), "yyyy-MM");
            else val = UtilDateUtilities.DateToString(cme.getDateValue(), "yyyy-MM-dd");
        } else {
            val = UtilDateUtilities.DateToString(cme.getDateValue(), "yyyy-MM-dd");
        }
        return val;
    }

    static public String formatIcd9(String code) {
        if (StringUtils.empty(code)) return code;
        if (code.length() <= 3) return code;

        String codeFormatted = code.substring(0, 3);
        code = code.substring(3);
        while (code.length() > 3) {
            codeFormatted += "." + code.substring(0, 3);
            code = code.substring(3);
        }
        if (code.length() > 0) codeFormatted += "." + code;

        return codeFormatted;
    }

    static public SpokenLangProperties spokenLangProperties = SpokenLangProperties.getInstance();
    static public RosterTermReasonProperties rosterTermReasonProperties = RosterTermReasonProperties.getInstance();

    static public String convertLanguageToCode(String lang) {
        return spokenLangProperties.getCodeByLang(lang);
    }

    static public String convertCodeToLanguage(String code) {
        return spokenLangProperties.getLangByCode(code);
    }

    static private final String verified = "[Verified and Signed";

    static public boolean isVerified(CaseManagementNote cmn) {
        if (cmn == null) return false;

        String note = cmn.getNote();
        if (StringUtils.empty(note)) return false;
        if (!note.contains("\n")) return false;

        int marker = note.substring(0, note.lastIndexOf("\n")).lastIndexOf("\n");
        if (marker < 0) return false;

        String vtext = verified;
        if (cmn.getUpdate_date() != null)
            vtext += " on " + UtilDateUtilities.DateToString(cmn.getUpdate_date(), "dd-MMM-yyyy HH:mm");
        String providerName = ProviderData.getProviderName(cmn.getProviderNo());
        if (StringUtils.filled(providerName)) vtext += " by " + providerName;

        return note.substring(marker + 1).startsWith(vtext);
    }

    static public void writeVerified(CaseManagementNote cmn) {
        if (isVerified(cmn)) return;

        String note = cmn.getNote();
        if (StringUtils.empty(note)) return;

        String vtext = verified;
        if (cmn.getUpdate_date() != null)
            vtext += " on " + UtilDateUtilities.DateToString(cmn.getUpdate_date(), "dd-MMM-yyyy HH:mm");
        String providerName = ProviderData.getProviderName(cmn.getProviderNo());
        if (StringUtils.filled(providerName)) vtext += " by " + providerName;

        note = Util.addLine(note, "\n" + vtext + "]\n");
        cmn.setNote(note);
    }

    static private HashMap<String, String> preventionToImmunizationType = new HashMap<String, String>();
    static private HashMap<String, String> immunizationToPreventionType = new HashMap<String, String>();
    static private ArrayList<String> nonImmunizationPreventionType = new ArrayList<String>();

    static private void setPreventionTypes() {
        if (!preventionToImmunizationType.isEmpty() && !immunizationToPreventionType.isEmpty() && !nonImmunizationPreventionType.isEmpty())
            return;

        PreventionDisplayConfig pdc = PreventionDisplayConfig.getInstance();
        ArrayList<HashMap<String, String>> prevTypeList = pdc.getPreventions();

        for (HashMap<String, String> prevTypeHash : prevTypeList) {
            if (prevTypeHash != null && StringUtils.filled(prevTypeHash.get("layout"))) {
                if (prevTypeHash.get("layout").equals("injection")) {
                    preventionToImmunizationType.put(prevTypeHash.get("name"), prevTypeHash.get("healthCanadaType"));
                    immunizationToPreventionType.put(prevTypeHash.get("healthCanadaType"), prevTypeHash.get("name"));
                } else {
                    nonImmunizationPreventionType.add(prevTypeHash.get("name"));
                }
            }
        }
    }

    static public String getImmunizationType(String preventionType) {
        if (preventionToImmunizationType.isEmpty()) setPreventionTypes();
        return preventionToImmunizationType.get(preventionType);
    }

    static public String getPreventionType(String immunizationType) {
        if (immunizationToPreventionType.isEmpty()) setPreventionTypes();
        return immunizationToPreventionType.get(immunizationType);
    }

    static public boolean isNonImmunizationPrevention(String type) {
        if (nonImmunizationPreventionType.isEmpty()) setPreventionTypes();
        return nonImmunizationPreventionType.contains(type);
    }

    public static cdsDt.ResidualInformation fillResidualInfo(String name, String dataType, String content) {
        List<cdsDt.ResidualInformation.DataElement> dataElementArray = new ArrayList<cdsDt.ResidualInformation.DataElement>();
        cdsDt.ResidualInformation residualInformation = cdsDt.ResidualInformation.Factory.newInstance();
        
        cdsDt.ResidualInformation.DataElement data = residualInformation.addNewDataElement();
        data.setName(name);
        data.setDataType(dataType);
        data.setContent(content);
        dataElementArray.add(data);
        residualInformation.setDataElementArray((cdsDt.ResidualInformation.DataElement[]) dataElementArray.toArray(new cdsDt.ResidualInformation.DataElement[dataElementArray.size()]));
        return residualInformation;
    }

    public static cdsDt.ResidualInformation fillResidualInfoSummary(String content) {
        cdsDt.ResidualInformation residualInformation = null;

        if (StringUtils.filled(content)) {
            residualInformation = fillResidualInfo("Summary", "String", content);
        }

        return residualInformation;
    }

    static public String replaceTags(String s) {
        if (StringUtils.empty(s)) return s;

        String tag = "<br />";
        s = s.replace(tag, "\n");
        tag = "<br/>";
        s = s.replace(tag, "\n");
        tag = "<br>";
        s = s.replace(tag, "\n");
        tag = "&nbsp;";
        s = s.replace(tag, " ");

        return s;
    }
    
    /**
     * Sanitizes a filename to prevent path traversal attacks.
     * Removes any path separators and parent directory references.
     * 
     * @param fileName The filename to sanitize
     * @return The sanitized filename, or empty string if the input is invalid
     */
    private static String sanitizeFileName(String fileName) {
        if (!StringUtils.filled(fileName)) {
            return "";
        }
        
        // Remove any path separators and parent directory references
        String sanitized = fileName.replaceAll("[/\\\\]", "_")  // Replace forward and back slashes
                                   .replaceAll("\\.\\./", "_")     // Replace ../ sequences
                                   .replaceAll("\\.\\.", "_");      // Replace .. sequences
        
        // Remove any leading dots to prevent hidden files
        while (sanitized.startsWith(".")) {
            sanitized = sanitized.substring(1);
        }
        
        // Ensure the filename has a valid extension for zip files
        if (!sanitized.toLowerCase().endsWith(".zip")) {
            // If no .zip extension, add it
            if (!sanitized.contains(".")) {
                sanitized = sanitized + ".zip";
            }
        }
        
        // Final validation - ensure no path traversal characters remain
        if (sanitized.contains("..") || sanitized.contains("/") || sanitized.contains("\\")) {
            logger.error("Invalid filename after sanitization: " + fileName);
            return "";
        }
        
        return sanitized;
    }
}
