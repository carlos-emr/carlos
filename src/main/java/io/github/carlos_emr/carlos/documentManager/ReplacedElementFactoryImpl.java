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
package io.github.carlos_emr.carlos.documentManager;


// Flying Saucer 10.x ITextFSImage works with OpenPDF 3.x (org.openpdf.*)
import org.openpdf.text.BadElementException;
import org.openpdf.text.Image;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
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
 * Custom implementation of the Flying Saucer {@link ReplacedElementFactory} for the
 * CARLOS EMR HTML-to-PDF conversion pipeline.
 *
 * <p>This factory handles two replaced element types:
 * <ul>
 *   <li>{@code <img>} tags: Loads images from the local file system, converts them to
 *       OpenPDF {@link Image} objects wrapped in {@link ITextFSImage}, and applies
 *       width/height scaling when dimensions are specified in the HTML.</li>
 *   <li>{@code <input>} tags: Renders form input fields as text elements via
 *       {@link TextFormField}.</li>
 * </ul>
 *
 * <p>Used by {@link ConvertToEdoc#fallbackRender} as the replaced element factory
 * for the Flying Saucer ITextRenderer with OpenPDF backend.
 *
 * <p><strong>Security note:</strong> The {@link #imageForPDF} method opens files directly
 * from the HTML {@code src} attribute via {@link java.io.FileInputStream}. This is safe
 * only when the renderer is created through {@link LocalOnlyUserAgent#createRestrictedRenderer()},
 * which enforces path containment on all {@code file:} URIs. Do not use this factory with
 * an unrestricted {@code ITextRenderer} — doing so would allow local file disclosure attacks.
 *
 * @see ConvertToEdoc
 * @see LocalOnlyUserAgent
 * @see org.xhtmlrenderer.extend.ReplacedElementFactory
 * @since 2022-05-12
 */
public class ReplacedElementFactoryImpl implements ReplacedElementFactory {

    private static Logger logger = MiscUtils.getLogger();

    /**
     * Creates a replaced element for {@code <img>} and {@code <input>} HTML tags.
     * Images are loaded from the local file system and scaled to the specified
     * dimensions. Input elements are rendered as text form fields.
     *
     * @param layoutContext LayoutContext the current layout context
     * @param blockBox BlockBox the block box containing the element to replace
     * @param userAgentCallback UserAgentCallback the user agent for resource resolution
     * @param width int the target width in CSS pixels, or -1 if unspecified
     * @param height int the target height in CSS pixels, or -1 if unspecified
     * @return ReplacedElement the replacement element, or null if the element is not handled
     */
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
                logger.warn("Could not create image element: {}", e1.getMessage());
            } catch (IOException e1) {
                fsImage = null;
                logger.warn("Could not load image: {}", e1.getMessage());
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

    /**
     * Loads an image from the local file system and wraps it as an OpenPDF-backed FSImage
     * for use in the Flying Saucer PDF rendering pipeline.
     *
     * @param attribute String the file system path to the image (from the img src attribute)
     * @param uac UserAgentCallback the user agent callback (unused, retained for API compatibility)
     * @return FSImage the loaded image wrapped as an ITextFSImage
     * @throws IOException if the image file cannot be read
     * @throws BadElementException if the image data cannot be parsed by OpenPDF
     */
    protected final FSImage imageForPDF(String attribute, UserAgentCallback uac) throws IOException, BadElementException {
        FSImage fsImage;
        try (InputStream input = new FileInputStream(attribute)) {
            byte[] bytes = IOUtils.toByteArray(input);
            Image image = Image.getInstance(bytes);
            fsImage = new ITextFSImage(image);
        }

        return fsImage;
    }

    /** {@inheritDoc} No-op; this factory maintains no internal state to reset. */
    @Override
    public void reset() {
        // No internal state to reset
    }

    /** {@inheritDoc} No-op; element removal is not tracked by this factory. */
    @Override
    public void remove(Element element) {
        // Element removal not tracked
    }

    /** {@inheritDoc} No-op; form submission is not supported in PDF output. */
    @Override
    public void setFormSubmissionListener(FormSubmissionListener formSubmissionListener) {
        // Form submission not applicable in PDF rendering context
    }
}
