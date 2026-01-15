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
package ca.openosp.openo.commn.dao;

import static org.junit.Assert.assertEquals;
import static ca.openosp.openo.commn.dao.AbstractCodeSystemDao.getDaoName;

import org.junit.Before;
import org.junit.Test;
import ca.openosp.openo.commn.dao.AbstractCodeSystemDao;

public class AbstractCodeSystemDaoTest extends DaoTestFixtures {

    @Before
    public void setUp() {
        // nothing to do right now
    }

	@Test
	public void testGetDaoName_Valid() {
		String codeSystem = "icd9";
		assertEquals(Icd9Dao.class, getDaoName(AbstractCodeSystemDao.codingSystem.valueOf(codeSystem)));
		
		codeSystem = "ichppccode";
		assertEquals(IchppccodeDao.class, getDaoName(AbstractCodeSystemDao.codingSystem.valueOf(codeSystem)));
		
		codeSystem = "SnomedCore";
		assertEquals(SnomedCoreDao.class, getDaoName(AbstractCodeSystemDao.codingSystem.valueOf(codeSystem)));
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testGetDaoName_Invalid() {
		String codeSystem = "FAIL";
		assertEquals(Object.class, getDaoName(AbstractCodeSystemDao.codingSystem.valueOf(codeSystem)));
	}
}
