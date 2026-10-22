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

package io.github.carlos_emr.carlos.prescript.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.SAXBuilder;
import io.github.carlos_emr.carlos.commn.dao.ResourceStorageDao;
import io.github.carlos_emr.carlos.commn.model.ResourceStorage;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;

import io.github.carlos_emr.CarlosProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;


/**
 * Parses the ODB formulary XML, caching the limited-use (LU) notes keyed by DIN.
 * <p>
 * Formulary data is resolved from three sources in order of precedence:
 * <ol>
 *   <li>File path configured in the {@code odb_formulary_file} CarlosProperties key</li>
 *   <li>Active {@link ResourceStorage} record of type {@link ResourceStorage#LU_CODES}</li>
 *   <li>Bundled classpath resource {@code oscar/oscarRx/data_extract_20250730.xml}</li>
 * </ol>
 * The lookup table is populated lazily on first use via double-checked locking over the
 * {@code volatile loaded} flag, mirroring {@link DrugPriceLookup}.
 *
 * @author jay
 */
public class LimitedUseLookup {

    private static Logger log = MiscUtils.getLogger();

    private static final String BUNDLED_FORMULARY_RESOURCE = "oscar/oscarRx/data_extract_20250730.xml";

    static Hashtable<String, ArrayList<LimitedUseCode>> luLookup = new Hashtable<String, ArrayList<LimitedUseCode>>();
    /**
     * Volatile so the double-checked lock in {@link #loadLULookupInformation()} publishes a fully
     * populated {@link #luLookup} to readers that never enter the synchronized block.
     */
    static volatile boolean loaded = false;

    /**
     * Creates a new instance of RenalDosingFactory
     */
    protected LimitedUseLookup() {
    }

    static public ArrayList<LimitedUseCode> getLUInfoForDin(String din) {
        loadLULookupInformation();
        if (din == null) {
            return null;
        }
        return luLookup.get(din);
    }

    static public LimitedUseCode makeLUNote(Element e) {
        LimitedUseCode lu = new LimitedUseCode();
        lu.setSeq(getVal(e, "seq"));
        lu.setUseId(getVal(e, "reasonForUseId"));
        lu.setType(getVal(e, "type"));
        lu.setTxt(e.getText());
        return lu;
    }

    static public String getVal(Element e, String name) {
        if (e.getAttribute(name) != null) {
            return e.getAttribute(name).getValue();
        }
        return "";
    }

    /**
     * Clears the cached limited-use notes and reloads them from the configured source.
     * <p>
     * Synchronized on the same monitor as the lazy loader so a reload cannot race a concurrent
     * first-use load and leave the table half populated.
     */
    public static synchronized void reLoadLookupInformation() {
        loaded = false;
        luLookup.clear();
        loadLULookupInformation();
    }

    /**
     * Resolves the formulary {@link InputStream} from the first available source.
     * <p>
     * The configured {@code odb_formulary_file} is canonicalized and required to be an existing
     * file by {@link PathValidationUtils#validateConfiguredFile(String, String)}. That helper
     * resolves plain relative filenames against the working directory, so deployments that keep
     * the formulary beside the server process keep working; a rejected path is logged and the
     * remaining sources are tried rather than failing the load outright.
     *
     * @param resourceStorageDao DAO used to look up the database-stored formulary
     * @return an open stream over the formulary XML, or {@code null} when no source is available
     * @throws IOException if the configured file cannot be opened
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    private static InputStream resolveOdbInputStream(ResourceStorageDao resourceStorageDao) throws IOException {
        String fileName = CarlosProperties.getInstance().getProperty("odb_formulary_file");
        if (fileName != null && !fileName.isEmpty()) {
            try {
                File formularyFile = PathValidationUtils.validateConfiguredFile(fileName, "odb_formulary_file");
                log.info("loading odb file from property {}", () -> LogSafe.sanitize(fileName, 1024));
                return new BufferedInputStream(new FileInputStream(formularyFile));
            } catch (SecurityException e) {
                log.error("Formulary file path validation failed, skipping property source: {}",
                        LogSafe.sanitize(fileName, 1024), e);
            }
        }

        ResourceStorage resourceStorage = resourceStorageDao.findActive(ResourceStorage.LU_CODES);
        if (resourceStorage != null) {
            byte[] contents = resourceStorage.getFileContents();
            if (contents != null) {
                log.info("loading odb file from resource storage id {}", resourceStorage.getId());
                return new ByteArrayInputStream(contents);
            }
            log.warn("Active LU_CODES resource {} has no file contents; falling back to bundled formulary",
                    resourceStorage.getId());
        }

        log.info("loading odb file from internal resource {}", BUNDLED_FORMULARY_RESOURCE);
        return LimitedUseLookup.class.getClassLoader().getResourceAsStream(BUNDLED_FORMULARY_RESOURCE);
    }

    static private void loadLULookupInformation() {
        log.debug("current LU lookup size " + luLookup.size());
        if (!loaded) {
            synchronized (LimitedUseLookup.class) {
                if (!loaded) {
                    ResourceStorageDao resourceStorageDao = SpringUtils.getBean(ResourceStorageDao.class);
                    try (InputStream is = resolveOdbInputStream(resourceStorageDao)) {

                        if (is == null) {
                            log.error("Limited use formulary resource could not be resolved; "
                                    + "limited use codes will be unavailable");
                            return;
                        }

                        SAXBuilder parser = XmlUtils.createSecureSAXBuilder();
                        Document doc = parser.build(is);
                        Element root = doc.getRootElement();
                        Element formulary = root.getChild("formulary");
                        if (formulary == null) {
                            log.error("Limited use XML is missing <formulary> element; "
                                    + "limited use codes will be unavailable");
                            return;
                        }
                        Iterator<Element> items = formulary.getDescendants(new ElementFilter("pcgGroup"));

                        while (items.hasNext()) {
                            Element pcgGroup = items.next();
                            List<Element> lccNoteList = pcgGroup.getChildren("lccNote");

                            if (lccNoteList.size() > 0) {
                                ArrayList<LimitedUseCode> luList = new ArrayList<LimitedUseCode>();
                                for (Element lccNo : lccNoteList) {
                                    luList.add(makeLUNote(lccNo));
                                }

                                Iterator<Element> drugs = pcgGroup.getDescendants(new ElementFilter("drug"));
                                while (drugs.hasNext()) {
                                    Element drug = drugs.next();
                                    if (drug.getAttribute("id") == null) {
                                        continue;
                                    }
                                    String din = drug.getAttribute("id").getValue();
                                    luLookup.put(din, luList);
                                }
                            }
                        }

                        loaded = true;
                    } catch (Exception e) {
                        MiscUtils.getLogger().error("Error", e);
                    }
                }
            }
        }

    }
}
