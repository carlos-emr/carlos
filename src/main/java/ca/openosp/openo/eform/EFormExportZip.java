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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.openosp.openo.eform;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.eform.data.EForm;
import ca.openosp.openo.eform.upload.ImageUpload2Action;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Utility class for exporting eForms as ZIP archives.
 */
public class EFormExportZip {
    private static final Logger _log = MiscUtils.getLogger();

    public void exportForms(List<EForm> eForms, OutputStream os) throws IOException, Exception {
        ZipOutputStream zos = new ZipOutputStream(os);
        zos.setLevel(9);

        for (EForm eForm : eForms) {
            if (eForm.getFormName() == null || eForm.getFormName().equals("")) {
                _log.error("Eform must have a name to export.  FID: " + eForm.getFid());
                throw new Exception("EForm must have a name to export");
            }
            Properties properties = new Properties(); //put all form properties into here
            String fileName = eForm.getFormFileName();
            _log.debug("before:>" + fileName + "<");
            if (fileName == null || fileName.equals("")) {
                fileName = eForm.getFormName().replaceAll("\\s", "") + ".html"; //make fileName = formname with all spaces removed
            }
            _log.debug("after:>" + fileName + "<");

            String directoryName = eForm.getFormName().replaceAll("\\s", "") + "/"; //formName with all spaces removed
            String html = eForm.getFormHtml();
            properties.setProperty("form.htmlFilename", fileName);
            if (eForm.getFormName() != null && !eForm.getFormName().equals(""))
                properties.setProperty("form.name", eForm.getFormName());
            if (eForm.getFormSubject() != null && !eForm.getFormSubject().equals(""))
                properties.setProperty("form.details", eForm.getFormSubject());
            if (eForm.getFormCreator() != null && !eForm.getFormCreator().equals(""))
                properties.setProperty("form.creator", eForm.getFormCreator());
            if (eForm.getFormDate() != null && !eForm.getFormDate().equals(""))
                properties.setProperty("form.date", eForm.getFormDate());
            if (eForm.isShowLatestFormOnly()) properties.setProperty("form.showLatestFormOnly", "true");
            if (eForm.isPatientIndependent()) properties.setProperty("form.patientIndependent", "true");

            //write properties file
            ZipEntry propertiesZipEntry = new ZipEntry(directoryName + "eform.properties");
            zos.putNextEntry(propertiesZipEntry);
            properties.store(zos, "");
            zos.closeEntry();

            //write html
            String htmlFilename = "";
            htmlFilename = directoryName + fileName;
            _log.debug("html file name " + htmlFilename);
            ZipEntry htmlZipEntry = new ZipEntry(htmlFilename);
            zos.putNextEntry(htmlZipEntry);
            byte[] bytes = html.getBytes("UTF-8");
            InputStream is = new ByteArrayInputStream(bytes);
            outputToInput(zos, is);
            zos.closeEntry();

            //get Images, must do html search for image name
            Pattern eformImagePattern = Pattern.compile("\\$\\{oscar_image_path\\}.+?[\"|'|>|<]"); //searches for ${oscar_image_path}xxx...xxx" (terminated by ", ', or >)
            Matcher matcher = eformImagePattern.matcher(html);
            int start = 0;
            while (matcher.find(start)) {
                String match = matcher.group();
                MiscUtils.getLogger().debug(match);
                start = matcher.end();
                int length = "${oscar_image_path}".length();
                String imageFileName = match.substring(length, match.length() - 1);
                MiscUtils.getLogger().debug("Image Name: " + imageFileName);
                File imageFile = getImageFile(imageFileName);
                try {
                    FileInputStream fis = new FileInputStream(imageFile);  //should error out if image not found, in this case, skip the image
                    ZipEntry imageZipEntry = new ZipEntry(directoryName + imageFileName);
                    zos.putNextEntry(imageZipEntry);
                    outputToInput(zos, fis);
                    zos.closeEntry();
                } catch (FileNotFoundException fnfe) {
                    continue;
                }

            }
        }

        zos.close();
    }


    public File getImageFile(String imageFileName) throws Exception {
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

    private void outputToInput(OutputStream os, InputStream is) throws IOException {
        byte[] buf = new byte[1024];
        int len;
        while ((len = is.read(buf)) > 0) {
            os.write(buf, 0, len);
        }
    }

    private void inputToOutput(InputStream inputStream, OutputStream outputStream) throws IOException {
        for (int c = inputStream.read(); c != -1; c = inputStream.read()) {
            outputStream.write(c);
        }
    }

    public List<String> importForm(InputStream importInputStream) throws IOException, Exception {
        ArrayList<String> errors = new ArrayList<String>();
        _log.info("Importing eforms");

        File imageDir = ImageUpload2Action.getImageFolder();
        File imageExtractDir = new File(imageDir, "extractFolder"); //do not delete this as two people may be importing at once
        //create if exists
        if (!imageExtractDir.exists() && !imageExtractDir.mkdir()) {
            errors.add("Error: Cannot create temporary folder for unzipping eform contents.  Check system logs");
            Exception e = new Exception("Error: Cannot create temporary folder for unzipping eform contents.  New folder: " + imageExtractDir.getAbsolutePath());
            _log.error("Could not unzip folder, cannot create temp folder.", e);
        }
        //create temp folder to extract files
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddkkmmssS"); //to ensure it does not repeat
        File imageTempFolderDir = new File(imageExtractDir, "extract" + format.format(new Date()));
        if (!imageTempFolderDir.exists() && !imageTempFolderDir.mkdir()) {
            errors.add("Error: Cannot create temporary folder for unzipping eform contents.  Check system logs");
            Exception e = new Exception("Error: Cannot create temporary folder for unzipping eform contents.  New folder: " + imageTempFolderDir.getAbsolutePath());
            _log.error("Could not unzip folder, cannot create temp folder.", e);
        }

        ZipInputStream zis = new ZipInputStream(importInputStream);
        ZipEntry ze = null;
        Hashtable<String, EForm> eformTable = new Hashtable<String, EForm>(); //stores eforms constructed from eform.properties, no HTML
        Hashtable<String, EForm> eformTableFailed = new Hashtable<String, EForm>();  //stores eforms that are constructed from eform.properties that alredy exist and do not need to be imported
        Hashtable<String, File> tempFiles = new Hashtable<String, File>(); //references extracted files in the temp folder
        //first runthrough, get the properties files, construct eforms, cache files
        while ((ze = zis.getNextEntry()) != null) {
            File file = new File(ze.getName());
            _log.info("Unzipping..." + file.getName());
            if (file.getName().equalsIgnoreCase("eform.properties")) {
                Properties properties = new Properties();
                properties.load(zis);
                EForm newEForm = this.createEFormFromProperties(properties);
                //check for errors or existing forms
                if (newEForm.getFormName() == null || newEForm.getFormName().equals("")) {
                    errors.add("Skipped form because it has no form name.");
                    _log.info("Skipped form because it has no form name.");
                    eformTableFailed.put(newEForm.getFormFileName(), newEForm);
                    continue;
                }
                if (newEForm.getFormFileName() == null | newEForm.getFormFileName().equals("")) {
                    errors.add("Skipped form titled '" + newEForm.getFormName() + "' because it has no form filename.");
                    _log.info("Skipped form titled '" + newEForm.getFormName() + "' because it has no form filename.");
                    continue;
                }
                if (EFormUtil.formExistsInDB(newEForm.getFormName())) {
                    errors.add("Skipped form '" + newEForm.getFormName() + "', form already exists");
                    _log.info("Skipped form '" + newEForm.getFormName() + "', form already exists");
                    eformTableFailed.put(newEForm.getFormFileName(), newEForm);
                    continue;
                }
                eformTable.put(newEForm.getFormFileName(), newEForm);  //store to add html and save to DB later
                _log.debug("going in eform table >" + newEForm.getFormFileName() + "<");
            } else {
                //store temp files on HD
                File tempFile = new File(imageTempFolderDir, file.getName());
                tempFiles.put(file.getName(), tempFile); //reference so we can find it later
                FileOutputStream fos = new FileOutputStream(tempFile);
                inputToOutput(zis, fos);
                fos.close();
                //store temp files in memory
                /*ByteArrayOutputStream baos = new ByteArrayOutputStream();
                inputToOutput(zis, baos);
                tempFiles.put(file.getName(), baos.toByteArray());
                baos.close();*/
            }
            zis.closeEntry();
        }
        zis.close();

        //loop through each file and decide -if html eform, put in DB, if supporting files (i.e. images) put on HD
        for (Entry<String, File> tempFile : tempFiles.entrySet()) {
            _log.info("looking at " + tempFile.getKey());
            if (eformTable.containsKey(tempFile.getKey())) {  //if file name matches eform
                FileInputStream fis = new FileInputStream(tempFile.getValue());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                inputToOutput(fis, baos);
                String html = new String(baos.toByteArray());
                _log.debug("THIS IS WHAT THE HTML is" + html);
                eformTable.get(tempFile.getKey()).setFormHtml(html);
                fis.close();
                baos.close();
            } else if (eformTableFailed.containsKey(tempFile.getKey())) {
                //do not save file if eform fails
            } else {
                FileInputStream fis = new FileInputStream(tempFile.getValue());
                File imageFile = new File(ImageUpload2Action.getImageFolder(), tempFile.getKey());
                if (imageFile.exists()) {
                    errors.add("Image '" + tempFile.getKey() + "' already exists, skipping image, but the form may still be uploaded.  Please resolve.");
                    _log.info("EForm Import: Image with name '" + tempFile.getKey() + "' already exists, skipping image, but the form may still be uploaded.  Please resolve.");
                }
                OutputStream os = new FileOutputStream(imageFile);
                inputToOutput(fis, os);
                _log.info("Loaded eform file: " + tempFile.getKey());
                fis.close();
                os.close();
            }
        }
        _log.info("Registering: " + eformTable.values().size() + " eforms");
        //write constructed eforms
        for (EForm eform : eformTable.values()) {
            _log.info("New eform: " + eform.getFormName());
            EFormUtil.saveEForm(eform);
        }
        deleteDirectory(imageTempFolderDir);
        return errors;
    }

    private void deleteDirectory(File directory) {
        for (File file : directory.listFiles()) {
            file.delete();
        }
        directory.delete();
    }

    public EForm createEFormFromProperties(Properties properties) throws Exception {
        EForm eForm = new EForm();
        eForm.setFormName(properties.getProperty("form.name"));
        if (eForm.getFormName() == null)
            throw new Exception("Error, form.name property cannot be found in eform.properties");
        eForm.setFormSubject(properties.getProperty("form.details"));
        eForm.setFormFileName(properties.getProperty("form.htmlFilename"));
        eForm.setFormCreator(properties.getProperty("form.creator"));
        eForm.setFormDate(properties.getProperty("form.date"));
        eForm.setShowLatestFormOnly(Boolean.valueOf(properties.getProperty("form.showLatestFormOnly")));
        eForm.setPatientIndependent(Boolean.valueOf(properties.getProperty("form.patientIndependent")));
        return eForm;
    }

}
