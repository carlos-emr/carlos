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


package io.github.carlos_emr.carlos.prevention;

//import java.io.ByteArrayInputStream;

import org.springframework.stereotype.Component;

/**
 * @author Jay Gallagher
 */
@Component
public interface PreventionDS {


    public void reloadRuleBase();


    public Prevention getMessages(Prevention p) throws Exception;


    ///
    //         URL url = Prevs.class.getResource( "prevention.drl" );
    //      log.debug(url.getFile());
    //
    //      //RuleBase ruleBase = RuleBaseBuilder.buildFromUrl( url );
    //      RuleBase ruleBase = RuleBaseLoader.loadFromUrl( url );
    //
    //
    //
    //      Prevention[] p = new Prevention[4];
    //
    //      p[0] = new Prevention("BOB","M",new GregorianCalendar(1998, Calendar.DECEMBER, 12).getTime()); //name, num imm, sex, age in months
    //      p[1] = new Prevention("RITA","F",new GregorianCalendar(2005, Calendar.JANUARY, 1).getTime());
    //      p[2] = new Prevention("GEORGE","M",new GregorianCalendar(2004, Calendar.APRIL, 25).getTime());
    //      p[3] = new Prevention("Tyler","M",new GregorianCalendar(2005, Calendar.MARCH, 23).getTime());
    //
    //
    //      PreventionItem pi = new PreventionItem("DTaP IPV",new GregorianCalendar(2005, Calendar.MARCH, 1).getTime());
    //      p[1].addPreventionItem(pi, "DTaP IPV");
    //      log.debug("should be 1 "+p[1].getNumberOfPreventionType("DTaP IPV"));
    //
    //      log.debug("should be ?? "+p[1].getHowManyMonthsSinceLast("DTaP IPV"));
    //
    //      for ( int k = 0; k < p.length; k++){
    //         long start = System.currentTimeMillis();
    //         WorkingMemory workingMemory = ruleBase.newWorkingMemory();
    //
    //         workingMemory.assertObject(p[k]);
    //
    //         workingMemory.fireAllRules();
    //         long end = System.currentTimeMillis();
    //
    //         ArrayList alist = p[k].getWarnings();
    //         log.debug(p[k].getName()+" "+"size:"+alist.size()+ " at months : "+p[k].getAgeInMonths() + " time :"+(end-start));
    //
    //         for (int i = 0; i < alist.size(); i++){
    //            String s = (String) alist.get(i);
    //            log.debug(s);
    //         }
    //
    //      }


    ////

}
 