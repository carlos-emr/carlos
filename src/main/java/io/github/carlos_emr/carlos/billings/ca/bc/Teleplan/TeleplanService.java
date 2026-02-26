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


package io.github.carlos_emr.carlos.billings.ca.bc.Teleplan;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * @author jay
 */
public class TeleplanService {
    static Logger log = MiscUtils.getLogger();

    /**
     * Creates a new instance of TeleplanService
     */
    public TeleplanService() {
    }


    ////////
//        //TETA-022 SEQ NUMBER ERROR. EXPECTED: 0262113 LAST COMMITTED:0262112


    public TeleplanAPI getTeleplanAPI(String username, String password) throws Exception {
        TeleplanAPI tAPI = new TeleplanAPI(); //

        TeleplanResponse tr = tAPI.login(username, password);


        if (tr != null && tr.getResult().equals("SUCCESS")) {
            return tAPI;
        }
        //TODO: ALSO RESULT COULD BE   EXPIRED.PASSWORD   need some kind of trigger that will propmt user to change password

        throw new Exception(tr.getMsgs());
    }


    //////
    public static int findExpectedSequenceNumber(String errormsg) throws Exception {
        //TETA-022 SEQ NUMBER ERROR. EXPECTED: 0262113 LAST COMMITTED:0262112
        log.debug("WORKING FROM ERROR MSG " + errormsg);
        int i = errormsg.lastIndexOf("COMMITTED:");
        if (i == -1) {
            throw new Exception("Unexpected message " + errormsg);
        }
        String numStr = errormsg.substring(i + 10);
        return Integer.parseInt(numStr);
    }


    //ATTEMPT to get the latest sequence number from teleplan.	
    //Creates a one-line (just the header )submission file with the last possible sequence number in it. 
    //If it submits successfully the next sequence # is 1 (return 0 so that the incrementing program will roll to one)
    //More than likely though this will be a failure and it will parse out the last committed number 
    public int getSequenceNumber(TeleplanAPI tAPI, String datacenter) throws Exception {

        String e = "VS1" + datacenter + "9999999V6242OSCAR_MCMASTER           V1.1      20030930OSCAR MCMASTER                          (905) 575-1300                                                                                   ";

        File getSequenceFile = File.createTempFile("oscarseq", "fil");
        BufferedWriter out = new BufferedWriter(new FileWriter(getSequenceFile));
        out.write(e);
        out.close();

        TeleplanResponse tr = tAPI.putMSPFile(getSequenceFile);
        getSequenceFile.delete();
        log.debug(tr.toString());

        if (tr.isSuccess()) {
            return 0;  // what are the chances!
        }
        return findExpectedSequenceNumber(tr.getMsgs());
    }

    //////
//    
//        TeleplanAPI tAPI = new TeleplanAPI(); //
//        
//
//        
//        File getSequenceFile =  File.createTempFile("ddd","eee");
//        
//        
//        getSequenceFile.delete();

    public void changePassword(TeleplanAPI tAPI, String oldpassword, String password) {
        TeleplanResponse chgPasswordResp = tAPI.changePassword("ttuv6242", "prkjg07", "jgprk07", "jgprk07");

        log.debug("RESULT " + chgPasswordResp.getResult());
        log.debug(chgPasswordResp.toString());
        tAPI.logoff();
    }


    ////////


}
