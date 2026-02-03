package io.github.carlos_emr.carlos.caisi_integrator.util;

import java.awt.Graphics2D;
import java.util.Iterator;
import java.io.IOException;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.Image;
import java.awt.image.BufferedImage;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import org.apache.log4j.Logger;

public class ImageIoUtils
{
    private static final Logger logger;
    
    public static byte[] scaleJpgSmallerProportionally(final byte[] inputImage, final int maxWidth, final int maxHeight, final float quality) {
        try {
            final ByteArrayInputStream bais = new ByteArrayInputStream(inputImage);
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaleJpgSmallerProportionally(bais, baos, maxWidth, maxHeight, quality);
            return baos.toByteArray();
        }
        catch (final Exception e) {
            ImageIoUtils.logger.error((Object) "Error scaling image.", (Throwable) e);
            return null;
        }
    }
    
    public static void scaleJpgSmallerProportionally(final InputStream inputStream, final OutputStream outputStream, final int maxWidth, final int maxHeight, final float quality) throws IOException {
        BufferedImage image = ImageIO.read(inputStream);
        final int imageWidth = image.getWidth();
        final int imageHeight = image.getHeight();
        if (maxWidth < imageWidth || maxHeight < imageHeight) {
            final float shrinkRatio = Math.min(maxHeight / (float) imageHeight, maxWidth / (float) imageWidth);
            final int newWidth = (int) (imageWidth * shrinkRatio);
            final int newHeight = (int) (imageHeight * shrinkRatio);
            final Image scaledImage = image.getScaledInstance(newWidth, newHeight, 4);
            image = toBufferedImage(scaledImage);
        }
        final ImageWriter jpgImageWriter = getJpgImageWriter();
        try {
            final ImageWriteParam imageWriteParam = jpgImageWriter.getDefaultWriteParam();
            imageWriteParam.setCompressionMode(2);
            imageWriteParam.setCompressionQuality(quality);
            final ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream);
            try {
                jpgImageWriter.setOutput(imageOutputStream);
                final IIOImage iioImage = new IIOImage(image, null, null);
                jpgImageWriter.write(null, iioImage, imageWriteParam);
            }
            finally {
                imageOutputStream.close();
            }
        }
        finally {
            jpgImageWriter.dispose();
        }
    }
    
    public static ImageWriter getJpgImageWriter() {
        final Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix("jpg");
        if (writers.hasNext()) {
            return writers.next();
        }
        throw new IllegalStateException("Missing jpg Image Writer");
    }
    
    public static BufferedImage toBufferedImage(final Image image) {
        final BufferedImage bufferedImage = new BufferedImage(image.getWidth(null), image.getHeight(null), 1);
        final Graphics2D g2d = bufferedImage.createGraphics();
        try {
            g2d.drawImage(image, 0, 0, null);
        }
        finally {
            g2d.dispose();
        }
        return bufferedImage;
    }
    
    static {
        logger = MiscUtils.getLogger();
        ImageIO.setUseCache(false);
    }
}
