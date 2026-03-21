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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;


import org.apache.logging.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.SAXBuilder;
import io.github.carlos_emr.carlos.commn.dao.ResourceStorageDao;
import io.github.carlos_emr.carlos.commn.model.ResourceStorage;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;


/**
 * Parses xml file, storing an HashMap
 * @author phc
 */
public class DrugPriceLookup {

	private static Logger log = MiscUtils.getLogger();

	static HashMap<String, String> costLookup = new HashMap<String, String>();
	static boolean loaded = false;

	/** Creates a new instance  */
	protected DrugPriceLookup() {
	}

	static public String getPriceInfoForDin(String din) {
		loadCostLookupInformation();
		if (din == null) {
			log.info("din null returning null");
			return null;
		}
		log.debug("current lookup for din " + din + " yields " + costLookup.get(din));
		return costLookup.get(din);
	}

	static public String getVal(Element e, String name) {
		if (e.getAttribute(name) != null) {
			return e.getAttribute(name).getValue();
		}
		return "";
	}
	
	static public void reLoadLookupInformation(){
		loaded = false;
		loadCostLookupInformation();
	}

	static private void loadCostLookupInformation() {
		log.debug("current price lookup size " + costLookup.size());
		if (!loaded) {
			DrugPriceLookup rdf = new DrugPriceLookup();
			InputStream is = null;
			ResourceStorageDao resourceStorageDao = SpringUtils.getBean(ResourceStorageDao.class);
			try {

				String fileName = OscarProperties.getInstance().getProperty("odb_formulary_file");
				if (fileName != null && !fileName.isEmpty()) {
					is = new BufferedInputStream(new FileInputStream(fileName));
					log.info("loading odb file from property "+fileName);

				} else {
					ResourceStorage resourceStorage = resourceStorageDao.findActive(ResourceStorage.LU_CODES);
		        	if(resourceStorage != null){
		        		is = new ByteArrayInputStream(resourceStorage.getFileContents());
		        		log.info("loading odb file from resource storage id"+resourceStorage.getId());
		        	}else{
						String dosing = "oscar/oscarRx/data_extract_20250730.xml";
						log.info("loading odb file from internal resource "+dosing);
						is = rdf.getClass().getClassLoader().getResourceAsStream(dosing);
		        	}
				}

/**
 * Parses xml file.  
 * Simplified structure is
 * extract > formulary > pcg2 > pcg6 > genericName > pcgGroup > pcg9 > drug > individualPrice
 * we want the drug.id (its din) and link it to drug.individualPrice its formulary cost per unit
 * 
 */
				SAXBuilder parser = new SAXBuilder();
				Document doc = parser.build(is);
				Element root = doc.getRootElement();
				Element formulary = root.getChild("formulary");
				@SuppressWarnings("unchecked")
				Iterator<Element> drugs = formulary.getDescendants(new ElementFilter("drug"));

				while (drugs.hasNext()) {
					Element drug = drugs.next();
					String din = drug.getAttribute("id").getValue();
					String cost = drug.getChildText("individualPrice");
					costLookup.put(din, cost);
				}
						
				log.debug("Drug Prices loaded=true size:"+costLookup.size());
				loaded = true;
				
			} catch (Exception e) {
				MiscUtils.getLogger().error("Error", e);
			} finally {
				if(is != null) {
					try {
						is.close();
					}catch(IOException e) {
						MiscUtils.getLogger().error("Error", e);
					}
				}
			}
		}

	}
}

