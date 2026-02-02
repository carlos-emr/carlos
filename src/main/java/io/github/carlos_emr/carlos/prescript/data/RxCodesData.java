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


package io.github.carlos_emr.carlos.prescript.data;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.CtlFrequencyDao;
import io.github.carlos_emr.carlos.commn.dao.CtlSpecialInstructionsDao;
import io.github.carlos_emr.carlos.commn.model.CtlFrequency;
import io.github.carlos_emr.carlos.commn.model.CtlSpecialInstructions;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class RxCodesData {

    private CtlFrequencyDao ctlFrequencyDao = (CtlFrequencyDao) SpringUtils.getBean(CtlFrequencyDao.class);
    private CtlSpecialInstructionsDao ctlSpecialInstructionsDao = (CtlSpecialInstructionsDao) SpringUtils.getBean(CtlSpecialInstructionsDao.class);

    public FrequencyCode[] getFrequencyCodes() {
        FrequencyCode[] arr = {};
        ArrayList<FrequencyCode> lst = new ArrayList<FrequencyCode>();

        List<CtlFrequency> ctlFrequencies = ctlFrequencyDao.findAll();
        for (CtlFrequency ctlFrequency : ctlFrequencies) {
            lst.add(new FrequencyCode(ctlFrequency.getId(), ctlFrequency.getFreqCode(), ctlFrequency.getDailyMin(), ctlFrequency.getDailyMax()));
        }
        arr = lst.toArray(arr);

        return arr;
    }

    public String[] getSpecialInstructions() {
        List<String> resultList = new ArrayList<String>();
        List<CtlSpecialInstructions> ctlSpecialInstructionsList = ctlSpecialInstructionsDao.findAll();
        for (CtlSpecialInstructions ctlSpecialInstructions : ctlSpecialInstructionsList) {
            resultList.add(ctlSpecialInstructions.getDescription());
        }
        String[] arr = {};
        arr = resultList.toArray(arr);

        return arr;
    }


    public class FrequencyCode {
        int freqId;
        String freqCode;
        String dailyMin;
        String dailyMax;

        public FrequencyCode(int freqId, String freqCode, int dailyMin, int dailyMax) {
            this.freqId = freqId;
            this.freqCode = freqCode;
            this.dailyMin = Integer.toString(dailyMin);
            this.dailyMax = Integer.toString(dailyMax);
        }

        public FrequencyCode(int freqId, String freqCode, String dailyMin, String dailyMax) {
            this.freqId = freqId;
            this.freqCode = freqCode;
            this.dailyMin = dailyMin;
            this.dailyMax = dailyMax;
        }

        public int getFreqId() {
            return this.freqId;
        }

        public String getFreqCode() {
            return this.freqCode;
        }

        public String getDailyMin() {
            return this.dailyMin;
        }

        public String getDailyMax() {
            return this.dailyMax;
        }
    }
}
