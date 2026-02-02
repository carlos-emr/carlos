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
package io.github.carlos_emr.carlos.utility;

import java.io.StringWriter;

import org.apache.logging.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.tools.generic.DateTool;
import org.apache.velocity.tools.generic.EscapeTool;
import org.apache.velocity.tools.generic.NumberTool;

public class VelocityUtils {
    private static Logger logger = MiscUtils.getLogger();
    public static final VelocityEngine velocityEngine = getInitialisedVelocityEngine();
    public static final EscapeTool escapeTool = new EscapeTool();
    public static final NumberTool numberTool = new NumberTool();
    public static final DateTool dateTool = new DateTool();

    public VelocityUtils() {
    }

    private static VelocityEngine getInitialisedVelocityEngine() {
        try {
            VelocityEngine velocityEngine = new VelocityEngine();
            velocityEngine.setProperty("parser.pool.size", 10);
            velocityEngine.init();
            return velocityEngine;
        } catch (Exception var1) {
            logger.error("Error", var1);
            return null;
        }
    }

    public static VelocityContext createVelocityContextWithTools() {
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("escapeTool", escapeTool);
        velocityContext.put("numberTool", numberTool);
        velocityContext.put("dateTool", dateTool);
        return velocityContext;
    }

    public static String velocityEvaluate(VelocityContext velocityContext, String template) throws ResourceNotFoundException {
        if (template == null) {
            return null;
        } else {
            StringWriter stringWriter = new StringWriter();
            velocityEngine.evaluate(velocityContext, stringWriter, "template", template);
            return stringWriter.toString();
        }
    }
}