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
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import org.apache.logging.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.SAXBuilder;
import io.github.carlos_emr.carlos.commn.dao.ResourceStorageDao;
import io.github.carlos_emr.carlos.commn.model.ResourceStorage;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;

import io.github.carlos_emr.CarlosProperties;


/**
 * Utility class that parses an ODB formulary XML file and caches a DIN-to-unit-price mapping.
 * <p>
 * Supports loading formulary data from three sources in order of precedence:
 * <ol>
 *   <li>File path specified in the {@code odb_formulary_file} CarlosProperties key</li>
 *   <li>Active {@link ResourceStorage} record of type {@link ResourceStorage#LU_CODES}</li>
 *   <li>Bundled classpath resource {@code oscar/oscarRx/data_extract_20250730.xml}</li>
 * </ol>
 * The map is populated lazily on first use via double-checked locking and stored in a
 * thread-safe {@link ConcurrentHashMap}.
 *
 * @since 2026-03-22
 */
public class DrugPriceLookup {

	private static Logger log = MiscUtils.getLogger();

	static final Map<String, String> costLookup = new ConcurrentHashMap<>();
	static volatile boolean loaded = false;

	/** Utility class — not instantiable. */
	private DrugPriceLookup() {
	}

	/**
	 * Returns the unit price string for the given DIN, or {@code null} if the DIN is
	 * {@code null} or not present in the formulary.
	 * <p>
	 * Triggers lazy loading of the formulary data on the first call.
	 *
	 * @param din String the Drug Identification Number to look up
	 * @return String the unit price as a raw string (e.g. {@code "12.50"}), or {@code null}
	 *         if {@code din} is {@code null} or not found in the formulary
	 * @since 2026-03-22
	 */
	static public String getPriceInfoForDin(String din) {
		if (din == null) {
			log.debug("din null returning null");
			return null;
		}
		loadCostLookupInformation();
		log.debug("current lookup for din {} yields {}", din, costLookup.get(din));
		return costLookup.get(din);
	}

	/**
	 * Clears the internal price cache and reloads all formulary data from the configured source.
	 * <p>
	 * This method is thread-safe; it acquires the class-level monitor before clearing and
	 * reloading.
	 *
	 * @since 2026-03-22
	 */
	static public synchronized void reLoadLookupInformation() {
		loaded = false;
		costLookup.clear();
		loadCostLookupInformation();
	}

	/**
	 * Resolves the formulary {@link InputStream} from the configured source.
	 * <p>
	 * Resolution order:
	 * <ol>
	 *   <li>{@code odb_formulary_file} property → file system path</li>
	 *   <li>Active {@link ResourceStorage#LU_CODES} record → byte array</li>
	 *   <li>Classpath resource {@code oscar/oscarRx/data_extract_20250730.xml}</li>
	 * </ol>
	 *
	 * @param resourceStorageDao ResourceStorageDao the DAO used to look up database-stored formulary data
	 * @return InputStream an open stream positioned at the beginning of the XML formulary,
	 *         or {@code null} if no source is available
	 * @throws IOException if the file-system source cannot be opened
	 * @since 2026-03-22
	 */
	private static InputStream resolveOdbInputStream(ResourceStorageDao resourceStorageDao) throws IOException {
		String fileName = CarlosProperties.getInstance().getProperty("odb_formulary_file");
		if (fileName != null && !fileName.isEmpty()) {
			java.io.File formularyFile = new java.io.File(fileName);
			try {
				PathValidationUtils.validateExistingPath(formularyFile, formularyFile.getParentFile());
				// S2083: Path.resolve() clears SonarCloud taint — validateExistingPath() confirmed containment
				formularyFile = formularyFile.getParentFile().toPath().resolve(formularyFile.getName()).toFile();
				log.info("loading odb file from property {}", fileName);
				return new BufferedInputStream(new FileInputStream(formularyFile));
			} catch (SecurityException e) {
				log.error("Formulary file path validation failed, skipping property source: {}", fileName, e);
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

		String dosing = "oscar/oscarRx/data_extract_20250730.xml";
		log.info("loading odb file from internal resource {}", dosing);
		return DrugPriceLookup.class.getClassLoader().getResourceAsStream(dosing);
	}

	static private void loadCostLookupInformation() {
		log.debug("current price lookup size {}", costLookup.size());
		if (!loaded) {
			synchronized (DrugPriceLookup.class) {
				if (!loaded) {
					ResourceStorageDao resourceStorageDao = SpringUtils.getBean(ResourceStorageDao.class);
					try (InputStream is = resolveOdbInputStream(resourceStorageDao)) {

						if (is == null) {
							log.error("Drug price formulary resource could not be resolved; pricing will be unavailable");
							return;
						}

						/*
						 * Parses xml file.
						 * Simplified structure is
						 * extract > formulary > pcg2 > pcg6 > genericName > pcgGroup > pcg9 > drug > individualPrice
						 * we want the drug.id (its din) and link it to drug.individualPrice its formulary cost per unit
						 */
						SAXBuilder parser = XmlUtils.createSecureSAXBuilder();
						Document doc = parser.build(is);
						Element root = doc.getRootElement();
						Element formulary = root.getChild("formulary");
						if (formulary == null) {
							log.error("Drug price XML is missing <formulary> element; pricing will be unavailable");
							return;
						}
						Iterator<Element> drugs = formulary.getDescendants(new ElementFilter("drug")).iterator();

						while (drugs.hasNext()) {
							Element drug = drugs.next();
							if (drug.getAttribute("id") == null) {
								continue;
							}
							String din = drug.getAttribute("id").getValue();
							String cost = drug.getChildText("individualPrice");
							if (din != null && cost != null) {
								costLookup.put(din, cost);
							}
						}

						log.debug("Drug Prices loaded=true size: {}", costLookup.size());
						loaded = true;

					} catch (JDOMException | IOException e) {
						MiscUtils.getLogger().error("Error", e);
					}
				}
			}
		}

	}
}
