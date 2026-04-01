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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;


public class ImageIoUtils {
    private static Logger logger = MiscUtils.getLogger();
    public static final float GENERAL_GOOD_COMPRESSION = 0.92F;

    public ImageIoUtils() {
    }

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

    public static void scaleJpgSmallerProportionally(InputStream inputStream, OutputStream outputStream, int maxWidth, int maxHeight, float quality) throws IOException {
        BufferedImage image = ImageIO.read(inputStream);
        image = scaleJpgSmallerProportionally(image, maxWidth, maxHeight);
        writeJpg(outputStream, quality, image);
    }

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

    /**
     * Strips EXIF and other embedded metadata from image bytes by re-encoding
     * through ImageIO (decode to pixel data, then re-encode clean).
     *
     * <p>Supports JPEG (re-encoded at {@link #GENERAL_GOOD_COMPRESSION} quality
     * — this is lossy but at high quality), PNG (lossless re-encode), and GIF.
     * If ImageIO cannot decode the input, the original bytes are returned unchanged
     * with a warning logged.</p>
     *
     * @param imageBytes raw image bytes that may contain EXIF/metadata
     * @param imageType  the format hint: extension ("jpg", "png", "gif") or
     *                   MIME type ("image/jpeg", "image/png", "image/gif")
     * @return image bytes with metadata stripped, or the original bytes if
     *         stripping is not possible
     */
    public static byte[] stripExifMetadata(byte[] imageBytes, String imageType) {
        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (bufferedImage == null) {
                logger.warn("Could not strip EXIF metadata: ImageIO could not decode image (type={})", imageType);
                return imageBytes;
            }
            String format = normalizeImageFormatName(imageType);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if ("jpeg".equals(format)) {
                writeJpg(baos, GENERAL_GOOD_COMPRESSION, bufferedImage);
            } else {
                if (!ImageIO.write(bufferedImage, format, baos)) {
                    logger.warn("Could not strip EXIF metadata: no ImageIO writer for format '{}'", format);
                    return imageBytes;
                }
            }
            logger.debug("Stripped EXIF metadata from image bytes (type={})", imageType);
            return baos.toByteArray();
        } catch (IOException e) {
            logger.error("Error stripping EXIF metadata from image bytes", e);
            return imageBytes;
        }
    }

    /**
     * Strips EXIF and other embedded metadata from an image file by re-encoding it
     * through ImageIO and writing the result to {@code destinationFile}.
     *
     * <p>Supports JPEG (re-encoded at {@link #GENERAL_GOOD_COMPRESSION} quality
     * — this is lossy but at high quality), PNG (lossless re-encode), and GIF.
     * If ImageIO cannot decode the source file (e.g. unsupported format), the
     * source is copied to the destination unchanged and a warning is logged.</p>
     *
     * <p>If the destination file already exists it will be overwritten.</p>
     *
     * @param sourceFile      the source image file to read
     * @param imageType       the format hint: extension ("jpg", "png", "gif") or
     *                        MIME type ("image/jpeg", "image/png", "image/gif")
     * @param destinationFile the destination file to write the clean image to;
     *                        overwritten if it already exists
     * @throws IOException if an I/O error occurs reading the source or writing
     *                     the destination
     */
    public static void stripExifToFile(File sourceFile, String imageType, File destinationFile) throws IOException {
        BufferedImage bufferedImage;
        try (InputStream fis = Files.newInputStream(sourceFile.toPath())) {
            bufferedImage = ImageIO.read(fis);
        }
        if (bufferedImage == null) {
            logger.warn("Could not strip EXIF metadata: ImageIO could not decode image (type={}, file={})",
                    imageType, sourceFile.getName());
            Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        String format = normalizeImageFormatName(imageType);
        if ("jpeg".equals(format)) {
            try (OutputStream fos = Files.newOutputStream(destinationFile.toPath())) {
                writeJpg(fos, GENERAL_GOOD_COMPRESSION, bufferedImage);
            }
        } else {
            if (!ImageIO.write(bufferedImage, format, destinationFile)) {
                logger.warn("Could not strip EXIF metadata: no ImageIO writer for format '{}', falling back to raw copy", format);
                Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        }
        logger.debug("Stripped EXIF metadata from uploaded image: {}", destinationFile.getName());
    }

    /**
     * Normalises an image type hint to an ImageIO-compatible format name.
     *
     * <p>Accepts file extensions ("jpg", "jpeg", "png", "gif") and MIME types
     * ("image/jpeg", "image/png", "image/gif"). For strings that look like filenames
     * the extension is extracted before mapping (e.g. "photo.jpg" → "jpeg").
     * Unknown or {@code null} values default to {@code "png"}
     * (e.g. "unknown.bmp" → "png").</p>
     *
     * @param imageType a file extension, MIME type, or filename string (case-insensitive)
     * @return lowercase ImageIO format name: {@code "jpeg"}, {@code "png"}, or {@code "gif"}
     */
    static String normalizeImageFormatName(String imageType) {
        if (imageType == null) {
            return "png";
        }
        String type = imageType.toLowerCase().trim();
        switch (type) {
            case "jpg":
            case "jpeg":
            case "image/jpeg":
            case "image/jpg":
                return "jpeg";
            case "png":
            case "image/png":
                return "png";
            case "gif":
            case "image/gif":
                return "gif";
            default:
                int dot = type.lastIndexOf('.');
                if (dot >= 0 && dot < type.length() - 1) {
                    return normalizeImageFormatName(type.substring(dot + 1));
                }
                return "png";
        }
    }

    static {
        ImageIO.setUseCache(false);
    }
}
