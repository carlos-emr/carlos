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

package io.github.carlos_emr.carlos.utility;

import org.apache.logging.log4j.Logger;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;


/**
 * Utility class for image manipulation operations including scaling, cropping,
 * and format conversion.
 *
 * <p>Provides methods for proportional scaling, center cropping, and writing images
 * in JPEG and PNG formats with configurable compression quality. Used primarily
 * for patient photo management and document image processing.
 *
 * <p>Image I/O caching is disabled via {@link ImageIO#setUseCache(boolean)} in the
 * static initializer to avoid temporary file creation on the server.
 *
 * @since 2026-03-17
 */
public class ImageIoUtils {
    private static Logger logger = MiscUtils.getLogger();
    public static final float GENERAL_GOOD_COMPRESSION = 0.92F;

    /**
     * Creates a new ImageIoUtils instance. Prefer using static methods directly.
     */
    public ImageIoUtils() {
    }

    /**
     * Crops the image to a centered square, then scales it proportionally to fit
     * within the specified maximum dimensions.
     *
     * @param inputImage byte[] the raw image data
     * @param maxWidth   int the maximum width in pixels
     * @param maxHeight  int the maximum height in pixels
     * @return BufferedImage the cropped and scaled image
     * @throws IOException if the image cannot be read
     */
    public static BufferedImage cropSquareThenScaleSmallerProportionally(byte[] inputImage, int maxWidth, int maxHeight) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(inputImage);
        BufferedImage image = ImageIO.read(bais);
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int desiredDimension = Math.min(imageWidth, imageHeight);
        image = cropCentre(image, desiredDimension, desiredDimension);
        image = scaleJpgSmallerProportionally(image, maxWidth, maxHeight);
        return image;
    }

    /**
     * Crops the image to a centered square, scales it proportionally, and encodes as JPEG.
     *
     * @param inputImage byte[] the raw image data
     * @param maxWidth   int the maximum width in pixels
     * @param maxHeight  int the maximum height in pixels
     * @param quality    float the JPEG compression quality (0.0 to 1.0)
     * @return byte[] the JPEG-encoded image data, or {@code null} on error
     */
    public static byte[] cropSquareThenScaleJpgSmallerProportionally(byte[] inputImage, int maxWidth, int maxHeight, float quality) {
        try {
            BufferedImage image = cropSquareThenScaleSmallerProportionally(inputImage, maxWidth, maxHeight);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeJpg(baos, quality, image);
            return baos.toByteArray();
        } catch (Exception var6) {
            logger.error("Error scaling image.", var6);
            return null;
        }
    }

    /**
     * Crops the image from the center to the specified dimensions. If the image is
     * already smaller than the desired dimensions, it is returned unchanged.
     *
     * @param image         BufferedImage the source image
     * @param desiredWidth  int the desired width in pixels
     * @param desiredHeight int the desired height in pixels
     * @return BufferedImage the center-cropped image
     */
    public static BufferedImage cropCentre(BufferedImage image, int desiredWidth, int desiredHeight) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        if (desiredWidth < imageWidth || desiredHeight < imageHeight) {
            int newWidth = Math.min(imageWidth, desiredWidth);
            int newHeight = Math.min(imageHeight, desiredHeight);
            int newX = (imageWidth - newWidth) / 2;
            int newY = (imageHeight - newHeight) / 2;
            image = image.getSubimage(newX, newY, newWidth, newHeight);
        }

        return image;
    }

    /**
     * Scales the image proportionally to fit within the maximum dimensions and encodes as JPEG.
     *
     * @param inputImage byte[] the raw image data
     * @param maxWidth   int the maximum width in pixels
     * @param maxHeight  int the maximum height in pixels
     * @param quality    float the JPEG compression quality (0.0 to 1.0)
     * @return byte[] the scaled JPEG-encoded image data, or {@code null} on error
     */
    public static byte[] scaleJpgSmallerProportionally(byte[] inputImage, int maxWidth, int maxHeight, float quality) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(inputImage);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaleJpgSmallerProportionally(bais, baos, maxWidth, maxHeight, quality);
            return baos.toByteArray();
        } catch (Exception var6) {
            logger.error("Error scaling image.", var6);
            return null;
        }
    }

    /**
     * Reads an image from the input stream, scales it proportionally, and writes JPEG output.
     *
     * @param inputStream  InputStream the source image stream
     * @param outputStream OutputStream the destination for the scaled JPEG
     * @param maxWidth     int the maximum width in pixels
     * @param maxHeight    int the maximum height in pixels
     * @param quality      float the JPEG compression quality (0.0 to 1.0)
     * @throws IOException if reading or writing the image fails
     */
    public static void scaleJpgSmallerProportionally(InputStream inputStream, OutputStream outputStream, int maxWidth, int maxHeight, float quality) throws IOException {
        BufferedImage image = ImageIO.read(inputStream);
        image = scaleJpgSmallerProportionally(image, maxWidth, maxHeight);
        writeJpg(outputStream, quality, image);
    }

    /**
     * Scales a buffered image proportionally to fit within the specified maximum dimensions.
     * Images already within bounds are returned unchanged.
     *
     * @param image     BufferedImage the source image
     * @param maxWidth  int the maximum width in pixels
     * @param maxHeight int the maximum height in pixels
     * @return BufferedImage the scaled image
     */
    public static BufferedImage scaleJpgSmallerProportionally(BufferedImage image, int maxWidth, int maxHeight) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        if (maxWidth < imageWidth || maxHeight < imageHeight) {
            float shrinkRatio = Math.min((float) maxHeight / (float) imageHeight, (float) maxWidth / (float) imageWidth);
            int newWidth = (int) ((float) imageWidth * shrinkRatio);
            int newHeight = (int) ((float) imageHeight * shrinkRatio);
            image = toBufferedImage(image.getScaledInstance(newWidth, newHeight, 4));
        }

        return image;
    }

    public static void writeJpg(OutputStream outputStream, float quality, BufferedImage image) throws IOException {
        ImageWriter jpgImageWriter = getJpgImageWriter();

        try {
            ImageWriteParam imageWriteParam = jpgImageWriter.getDefaultWriteParam();
            imageWriteParam.setCompressionMode(2);
            imageWriteParam.setCompressionQuality(quality);
            ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream);

            try {
                jpgImageWriter.setOutput(imageOutputStream);
                IIOImage iioImage = new IIOImage(image, (List) null, (IIOMetadata) null);
                jpgImageWriter.write((IIOMetadata) null, iioImage, imageWriteParam);
            } finally {
                imageOutputStream.close();
            }
        } finally {
            jpgImageWriter.dispose();
        }

    }

    public static ImageWriter getJpgImageWriter() {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix("jpg");
        if (writers.hasNext()) {
            return (ImageWriter) writers.next();
        } else {
            throw new IllegalStateException("Missing jpg Image Writer");
        }
    }

    public static void writePng(OutputStream outputStream, BufferedImage image) throws IOException {
        ImageWriter pngImageWriter = getPngImageWriter();

        try {
            ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream);

            try {
                pngImageWriter.setOutput(imageOutputStream);
                IIOImage iioImage = new IIOImage(image, (List) null, (IIOMetadata) null);
                pngImageWriter.write((IIOMetadata) null, iioImage, (ImageWriteParam) null);
            } finally {
                imageOutputStream.close();
            }
        } finally {
            pngImageWriter.dispose();
        }

    }

    public static ImageWriter getPngImageWriter() {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix("png");
        if (writers.hasNext()) {
            return (ImageWriter) writers.next();
        } else {
            throw new IllegalStateException("Missing png Image Writer");
        }
    }

    public static BufferedImage toBufferedImage(Image image) {
        BufferedImage bufferedImage = new BufferedImage(image.getWidth((ImageObserver) null), image.getHeight((ImageObserver) null), 1);
        Graphics2D g2d = bufferedImage.createGraphics();

        try {
            g2d.drawImage(image, 0, 0, (ImageObserver) null);
        } finally {
            g2d.dispose();
        }

        return bufferedImage;
    }

    static {
        ImageIO.setUseCache(false);
    }
}
