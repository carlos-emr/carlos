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
package ca.openosp.openo.documentManager;


// Flying Saucer's ITextFSImage requires the old lowagie Image class
import com.lowagie.text.BadElementException;
import com.lowagie.text.Image;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.MiscUtils;
import org.w3c.dom.Element;
import org.xhtmlrenderer.extend.FSImage;
import org.xhtmlrenderer.extend.ReplacedElement;
import org.xhtmlrenderer.extend.ReplacedElementFactory;
import org.xhtmlrenderer.extend.UserAgentCallback;
import org.xhtmlrenderer.layout.LayoutContext;

import org.xhtmlrenderer.pdf.*;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.simple.extend.FormSubmissionListener;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An override implementation of the Flying Saucer HTMLtoPDF ReplacedElementFactory class
 * that handles scaling of embedded images more accurately.
 */
public class ReplacedElementFactoryImpl implements ReplacedElementFactory {

    private static Logger logger = MiscUtils.getLogger();

    @Override
    public ReplacedElement createReplacedElement(LayoutContext layoutContext, BlockBox blockBox, UserAgentCallback userAgentCallback, int width, int height) {
        Element e = blockBox.getElement();
        if (e == null) {
            return null;
        }
        String nodeName = e.getNodeName();
        if (nodeName.equals("img")) {
            String attribute = e.getAttribute("src");
            FSImage fsImage;
            try {
                fsImage = imageForPDF(attribute, userAgentCallback);
            } catch (BadElementException e1) {
                fsImage = null;
                logger.debug("Could not create image element: " + e1.getMessage());
            } catch (IOException e1) {
                fsImage = null;
                logger.debug("Could not load image: " + e1.getMessage());
            }
            if (fsImage != null) {
                if (width != -1 || height != -1) {
                    fsImage.scale(width, height);
                }
                return new ITextImageElement(fsImage);
            }
        }
        if (nodeName.equals("input")) {
            return new TextFormField(layoutContext, blockBox, width, height);
        }

        return blockBox.getReplacedElement();
    }

    protected final FSImage imageForPDF(String attribute, UserAgentCallback uac) throws IOException, BadElementException {
        FSImage fsImage;
        try (InputStream input = new FileInputStream(attribute)) {
            byte[] bytes = IOUtils.toByteArray(input);
            Image image = Image.getInstance(bytes);
            fsImage = new ITextFSImage(image);
        }

        return fsImage;
    }

    @Override
    public void reset() {
        // override
    }

    @Override
    public void remove(Element element) {
        // override
    }

    @Override
    public void setFormSubmissionListener(FormSubmissionListener formSubmissionListener) {
        // override
    }
}
