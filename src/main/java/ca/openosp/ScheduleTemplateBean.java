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


package ca.openosp;


/**
 * Bean representing a provider's schedule template configuration.
 * 
 * <p>This bean stores template information for provider scheduling including:</p>
 * <ul>
 *   <li>Provider identification</li>
 *   <li>Template name and summary description</li>
 *   <li>Time code defining schedule slots</li>
 *   <li>Calculated step size based on time code</li>
 * </ul>
 * 
 * <p>The timecode string defines the schedule availability pattern, with each
 * character representing a time slot. The step size is calculated as
 * (24 hours * 60 minutes) / timecode length.</p>
 */
public class ScheduleTemplateBean {

    private String providerNo = "";
    private String name = "";
    private String summary = "";
    private String timecode = "";
    private int step = 0;

    /**
     * Constructs a new empty ScheduleTemplateBean.
     */
    public ScheduleTemplateBean() {
    }

    /**
     * Sets all schedule template properties at once.
     * Also calculates the step size based on timecode length.
     * 
     * @param provider_no1 the provider number
     * @param name1 the template name
     * @param summary1 the template summary/description
     * @param timecode1 the time code defining schedule slots
     */
    public void setScheduleTemplateBean(String provider_no1, String name1, String summary1, String timecode1) {
        providerNo = provider_no1;
        name = name1;
        summary = summary1;
        timecode = timecode1;
        step = timecode1.length() > 0 ? 24 * 60 / timecode1.length() : 0;
    }

    /**
     * Sets the provider number.
     * 
     * @param provider_no1 the provider number
     */
    public void setProviderNo(String provider_no1) {
        providerNo = provider_no1;
    }

    /**
     * Sets the template name.
     * 
     * @param name1 the template name
     */
    public void setName(String name1) {
        name = name1;
    }

    /**
     * Sets the template summary/description.
     * 
     * @param summary1 the template summary
     */
    public void setSummary(String summary1) {
        summary = summary1;
    }

    /**
     * Sets the time code defining schedule slots.
     * 
     * @param timecode1 the time code
     */
    public void setTimecode(String timecode1) {
        timecode = timecode1;
    }

    /**
     * Gets the provider number.
     * 
     * @return the provider number
     */
    public String getProviderNo() {
        return (providerNo);
    }

    /**
     * Gets the template name.
     * 
     * @return the template name
     */
    public String getName() {
        return (name);
    }

    /**
     * Gets the template summary/description.
     * 
     * @return the template summary
     */
    public String getSummary() {
        return (summary);
    }

    /**
     * Gets the time code.
     * 
     * @return the time code
     */
    public String getTimecode() {
        return (timecode);
    }

    public int getStep() {
        return (step);
    }

    public char getTimecodeCharAt(int i) {
        return (timecode.charAt(i));
    }

}
