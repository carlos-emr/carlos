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


package ca.openosp.openo.eform.actions;

import com.opensymphony.xwork2.ActionSupport;

import org.apache.commons.io.IOUtils;
import org.apache.struts2.ServletActionContext;

import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.OscarProperties;

import javax.activation.MimetypesFileTypeMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
/**
 * eform_image
 *
 * @author jay
 * and Paul
 */
public class DisplayImage2Action extends ActionSupport {
    private HttpServletRequest request = ServletActionContext.getRequest();
    private HttpServletResponse response = ServletActionContext.getResponse();
    private record StreamData(InputStream stream, String contentType) {}

    /**
     * Creates a new instance of DisplayImage2Action
     */
    public DisplayImage2Action() {
    }

    public String execute() throws Exception {
        StreamData data = process();
        String contentType = data.contentType();
        InputStream stream = data.stream();

        try {
            response.setContentType(contentType);
            OutputStream outputStream = response.getOutputStream();
            IOUtils.copy(stream, outputStream);
            return NONE;
        } catch (Exception e) {
            return NONE;
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }

    public StreamData process() throws Exception {

        String fileName = request.getParameter("imagefile");
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename");
        }
        String home_dir = OscarProperties.getInstance().getEformImageDirectory();

        File file = null;
        try {
            File directory = new File(home_dir);
            if (!directory.exists()) {
                throw new Exception("Directory:  " + home_dir + " does not exist");
            }
            file = new File(directory, fileName);

            if (!directory.equals(file.getParentFile())) {
                MiscUtils.getLogger().debug("SECURITY WARNING: Illegal file path detected, client attempted to navigate away from the file directory");
                throw new Exception("Could not open file " + fileName + ".  Check the file path");
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            throw new Exception("Could not open file " + home_dir + fileName + " does " + home_dir + " exist ?", e);
        }
        // Gets content type from image extension
        String contentType = new MimetypesFileTypeMap().getContentType(file);
        
        /**
         * For encoding file types not included in the mimetypes file
         * You need to look at mimetypes file to check if the file type you are using is included
         */
        try {
            if (extension(file.getName()).equalsIgnoreCase("png")) { // for PNG
                contentType = "image/png";
            } else if (extension(file.getName()).equalsIgnoreCase("jpeg") ||
                    extension(file.getName()).equalsIgnoreCase("jpe") ||
                    extension(file.getName()).equalsIgnoreCase("jpg")) { //for JPEG,JPG,JPE
                contentType = "image/jpeg";
            } else if (extension(file.getName()).equalsIgnoreCase("bmp")) { // for BMP
                contentType = "image/bmp";
            } else if (extension(file.getName()).equalsIgnoreCase("cod")) { // for COD
                contentType = "image/cis-cod";
            } else if (extension(file.getName()).equalsIgnoreCase("ief")) { // for IEF
                contentType = "image/ief";
            } else if (extension(file.getName()).equalsIgnoreCase("jfif")) { // for JFIF
                contentType = "image/pipeg";
            } else if (extension(file.getName()).equalsIgnoreCase("svg")) { // for SVG
                contentType = "image/svg+xml";
            } else if (extension(file.getName()).equalsIgnoreCase("tiff") ||
                    extension(file.getName()).equalsIgnoreCase("tif")) { // for TIFF or TIF
                contentType = "image/tiff";
            } else if (extension(file.getName()).equalsIgnoreCase("pbm")) { // for PBM
                contentType = "image/x-portable-bitmap";
            } else if (extension(file.getName()).equalsIgnoreCase("pnm")) { // for PNM
                contentType = "image/x-portable-anymap";
            } else if (extension(file.getName()).equalsIgnoreCase("pgm")) { // for PGM
                contentType = "image/x-portable-greymap";
            } else if (extension(file.getName()).equalsIgnoreCase("ppm")) { // for PPM
                contentType = "image/x-portable-pixmap";
            } else if (extension(file.getName()).equalsIgnoreCase("xbm")) { // for XBM
                contentType = "image/x-xbitmap";
            } else if (extension(file.getName()).equalsIgnoreCase("xpm")) { // for XPM
                contentType = "image/x-xpixmap";
            } else if (extension(file.getName()).equalsIgnoreCase("xwd")) { // for XWD
                contentType = "image/x-xwindowdump";
            } else if (extension(file.getName()).equalsIgnoreCase("rgb")) { // for RGB
                contentType = "image/x-rgb";
            } else if (extension(file.getName()).equalsIgnoreCase("ico")) { // for ICO
                contentType = "image/x-icon";
            } else if (extension(file.getName()).equalsIgnoreCase("cmx")) { // for CMX
                contentType = "image/x-cmx";
            } else if (extension(file.getName()).equalsIgnoreCase("ras")) { // for RAS
                contentType = "image/x-cmu-raster";
            } else if (extension(file.getName()).equalsIgnoreCase("gif")) { // for GIF
                contentType = "image/gif";
            } else if (extension(file.getName()).equalsIgnoreCase("js")) { // for JS
                contentType = "text/javascript";
            } else if (extension(file.getName()).equalsIgnoreCase("css")) { // for CSS
                contentType = "text/css";
            } else if (extension(file.getName()).equalsIgnoreCase("rtl") || extension(file.getName()).equalsIgnoreCase("html") || extension(file.getName()).equalsIgnoreCase("htm")) { // for HTML
                contentType = "text/html";
            } else {
                throw new Exception("please check the file type or update mimetypes.default file to include the " + "." + extension(file.getName()));
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            throw new Exception("Could not open file " + file.getName() + " wrong file extension, ", e);
        }
        response.setContentType(contentType);
        response.setHeader("Content-disposition", "inline; filename=\"" + sanitizeHeaderValue(fileName) + "\"");

        InputStream fileStream = new FileInputStream(file);
        return new StreamData(fileStream, contentType);
    }

    /**
     * Sanitizes a header value to prevent HTTP response splitting attacks.
     * Removes all control characters including CR (\r) and LF (\n) that could
     * be used to inject additional headers or split the HTTP response.
     * 
     * @param value The header value to sanitize
     * @return The sanitized header value safe for use in HTTP headers
     */
    private String sanitizeHeaderValue(String value) {
        if (value == null) {
            return "";
        }
        
        // Remove all control characters including CR (\r) and LF (\n)
        // This prevents HTTP response splitting attacks
        // Also remove other control characters that could cause issues
        String sanitized = value
            .replaceAll("[\r\n\u0000-\u001F\u007F-\u009F]", "")  // Control chars
            .replaceAll("[\"';]", "");  // Quotes and semicolons

        // Ensure the filename is not empty after sanitization
        if (sanitized.trim().isEmpty()) {
            return "image";
        }
        
        return sanitized;
    }

    /**
     * Gets the file extension from a given filename.
     *
     * @param f the filename (e.g., example.jpeg)
     * @return the file extension
     */
    public String extension(String f) {
        int dot = f.lastIndexOf(".");
        return f.substring(dot + 1);
    }

    public static File getImageFile(String imageFileName) throws Exception {
        String home_dir = OscarProperties.getInstance().getEformImageDirectory();

        File file = null;
        try {
            File directory = new File(home_dir);
            if (!directory.exists()) {
                throw new Exception("Directory:  " + home_dir + " does not exist");
            }
            file = new File(directory, imageFileName);
            //String canonicalPath = file.getParentFile().getCanonicalPath(); //absolute path of the retrieved file

            if (!directory.equals(file.getParentFile())) {
                MiscUtils.getLogger().debug("SECURITY WARNING: Illegal file path detected, client attempted to navigate away from the file directory");
                throw new Exception("Could not open file " + imageFileName + ".  Check the file path");
            }
            return file;
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            throw new Exception("Could not open file " + home_dir + imageFileName + " does " + home_dir + " exist ?", e);
        }
    }

    /**
     * Process only files under dir
     * This method used to list images for eform generator
     */
    public String[] visitAllFiles(File dir) {
        String[] children = null;
        if (dir.isDirectory()) {
            children = dir.list();
            for (int i = 0; i < children.length; i++) {
                visitAllFiles(new File(dir, children[i]));
            }
        }
        return children;
    }

    public static String[] getRichTextLetterTemplates(File dir) {
        ArrayList<String> results = getFiles(dir, ".*(rtl)$", null);
        return results.toArray(new String[0]);
    }

    public static ArrayList<String> getFiles(File dir, String ext, ArrayList<String> files) {
        if (files == null) {
            files = new ArrayList<String>();
        }
        if (dir.isDirectory()) {
            for (String fileName : dir.list()) {
                if (fileName.toLowerCase().matches(ext)) {
                    files.add(fileName);
                }
            }
        }
        return files;
    }
}
