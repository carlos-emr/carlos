//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 */
package ca.openosp.openo.commn.dao;

import java.util.List;

import ca.openosp.openo.commn.model.AbstractCodeSystemModel;

public interface AbstractCodeSystemDao<T extends AbstractCodeSystemModel<?>> extends AbstractDao<T> {

    //public static enum codingSystem {icd9,icd10,ichppccode,msp,SnomedCore}

    // public static String getDaoName(codingSystem codeSystem);

    public List<T> searchCode(String term);

    public T findByCode(String code);

    public AbstractCodeSystemModel<?> findByCodingSystem(String codingSystem);

    public static enum codingSystem {icd9, icd10, ichppccode, msp, SnomedCore}

    public static Class<?> getDaoName(codingSystem codeSystem) {
        Class<?> object;
        switch(codeSystem) {
            case SnomedCore:
                object = SnomedCoreDao.class;
                break;
            case icd10:
                object = Icd10Dao.class;
                break;
            case icd9:
                object = Icd9Dao.class;
                break;
            case ichppccode:
                object = IchppccodeDao.class;
                break;
            case msp:
                object = DiagnosticCodeDao.class;
                break;
            default:
                throw new IllegalArgumentException("Unsupported code system: " + codeSystem + ". Please use one of icd9, ichppccode, snomedcore");
        }
        return object;
    }
}
