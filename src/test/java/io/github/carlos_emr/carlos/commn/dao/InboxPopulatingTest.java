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
package io.github.carlos_emr.carlos.commn.dao;

import org.junit.Test;
import io.github.carlos_emr.carlos.commn.dao.utils.DataUtils;
import io.github.carlos_emr.carlos.commn.dao.utils.SchemaUtils;
import org.junit.Before;
import org.junit.After;

import java.io.File;
import java.nio.file.*;
import java.util.Comparator;

public class InboxPopulatingTest extends DaoTestFixtures {

	@Before
	public void before() throws Exception {
		String tempDir = System.getProperty("java.io.tmpdir") + "/OscarDocumentTest";
        System.setProperty("DOCUMENT_DIR", tempDir);

        Files.createDirectories(Paths.get(tempDir));

		this.beforeForInnoDB();
		SchemaUtils.restoreAllTables();
	}

	@After
    public void cleanup() throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir") + "/OscarDocumentTest";
        Files.walk(Paths.get(tempDir))
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
        
        File dir = new File(tempDir);
        if (dir.exists()) {
            System.err.println("Directory not deleted: " + tempDir);
        }
    }

	@Test
	public void test()  {
		DataUtils.populateDemographicsAndProviders();
		DataUtils.populateProviders();
		DataUtils.populateLabs();
		DataUtils.populateDocs();
	}
	
}
