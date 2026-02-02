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


package ca.openosp.openo.utility;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.logging.log4j.Logger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class QrCodeUtils {

    private static final Logger logger = MiscUtils.getLogger();

    public static final int MAX_QR_CODE_DATA_LENGTH = 2953;
    public static final float DEFAULT_QR_CODE_GAP = .20f;

    public enum QrCodesOrientation {
        VERTICAL, HORIZONTAL
    }

    public static byte[] toMultipleQrCodePngs(String s, ErrorCorrectionLevel ec, QrCodesOrientation qrCodesOrientation, int scaleFactor) throws IOException, WriterException {
        return (toMultipleQrCodePngs(s, ec, qrCodesOrientation, null, MAX_QR_CODE_DATA_LENGTH, scaleFactor));
    }

    /**
     * This method will break the longInputString into maxQrCodeDataSize size
     * segments and create separate qr codes for each segment. There is no
     * interpretation of the data; therefore, the individual qr codes should not be
     * assumed to make sense individually but only collectively after all qr codes
     * are interpreted and the resulting data concatenated.
     *
     * @param qrCodeImageGap the number of pixels between the QR code images, if this value is null it will calculate it at DEFAULT_QR_CODE_GAP % of the size of the first (and presumably full size) qr code image
     * @param scaleFactor    this scales the resulting image by the provided factor, this value must be an int or it'll ruin the structure of the qr image
     */
    public static byte[] toMultipleQrCodePngs(String s, ErrorCorrectionLevel ec, QrCodesOrientation qrCodesOrientation, Integer qrCodeImageGap, int maxQrCodeDataSize, int scaleFactor) throws IOException, WriterException {
        ArrayList<BufferedImage> results = new ArrayList<BufferedImage>();

        int startIndex = 0;
        int endIndex = 0;
        while (true) {
            endIndex = Math.min(startIndex + maxQrCodeDataSize, s.length());

            String stringChunk = s.substring(startIndex, endIndex);
            logger.debug("Encoding chunk : " + stringChunk);

            results.add(toSingleQrCodeBufferedImage(stringChunk, ec, scaleFactor));

            if (endIndex == s.length()) break;
            else startIndex = endIndex;
        }

        if (qrCodeImageGap == null) qrCodeImageGap = (int) (results.get(0).getWidth() * DEFAULT_QR_CODE_GAP);

        byte[] mergedResults = mergeImages(results, qrCodesOrientation, qrCodeImageGap);

        return (mergedResults);
    }

    public static BufferedImage toSingleQrCodeBufferedImage(String s, ErrorCorrectionLevel ec, int scaleFactor) throws WriterException {
        // Use QRCodeWriter with hints for error correction level
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ec);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        // Use a base size that will be scaled
        int baseSize = 100;
        BitMatrix bitMatrix = qrCodeWriter.encode(s, BarcodeFormat.QR_CODE, baseSize, baseSize, hints);

        BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

        if (scaleFactor != 1) {
            int newWidth = bufferedImage.getWidth() * scaleFactor;
            int newHeight = bufferedImage.getHeight() * scaleFactor;
            Image image = bufferedImage.getScaledInstance(newWidth, newHeight, Image.SCALE_FAST);
            bufferedImage = ImageIoUtils.toBufferedImage(image);
        }

        return (bufferedImage);
    }

    public static byte[] toSingleQrCodePng(String s, ErrorCorrectionLevel ec, int scaleFactor) throws IOException, WriterException {
        return (toPng(toSingleQrCodeBufferedImage(s, ec, scaleFactor)));
    }

    private static byte[] toPng(BufferedImage bufferedImage) throws IOException {
        ImageWriter imageWriter = getPngImageWriter();
        try {
            // set quality
            ImageWriteParam imageWriteParam = imageWriter.getDefaultWriteParam();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(byteArrayOutputStream);

            imageWriter.setOutput(imageOutputStream);
            IIOImage iioImage = new IIOImage(bufferedImage, null, null);
            imageWriter.write(null, iioImage, imageWriteParam);

            return (byteArrayOutputStream.toByteArray());
        } finally {
            imageWriter.dispose();
        }
    }

    private static byte[] mergeImages(ArrayList<BufferedImage> results, QrCodesOrientation qrCodesOrientation, int qrCodeImageGap) throws IOException {

        int requiredWidth = getRequiredWidth(results, qrCodesOrientation, qrCodeImageGap);
        int requiredHeight = getRequiredHeight(results, qrCodesOrientation, qrCodeImageGap);

        BufferedImage mergedImage = new BufferedImage(requiredWidth, requiredHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D doubleBuffer = (Graphics2D) mergedImage.getGraphics();
        int x = 0;
        int y = 0;
        for (BufferedImage bi : results) {
            doubleBuffer.drawImage(bi, x, y, null);

            if (qrCodesOrientation == QrCodesOrientation.VERTICAL) y = y + bi.getHeight() + qrCodeImageGap;
            else x = x + bi.getWidth() + qrCodeImageGap;
        }

        return (toPng(mergedImage));
    }

    private static int getRequiredHeight(ArrayList<BufferedImage> results, QrCodesOrientation qrCodesOrientation, int qrCodeImageGap) {
        if (qrCodesOrientation == QrCodesOrientation.VERTICAL)
            return (sumHeight(results) + (qrCodeImageGap * (results.size() - 1)));
        else return (maxHeight(results));
    }

    private static int maxHeight(ArrayList<BufferedImage> results) {
        int max = 0;
        for (BufferedImage bi : results) max = Math.max(max, bi.getHeight());
        return (max);
    }

    private static int sumHeight(ArrayList<BufferedImage> results) {
        int total = 0;
        for (BufferedImage bi : results) total = total + bi.getHeight();
        return (total);
    }

    private static int getRequiredWidth(ArrayList<BufferedImage> results, QrCodesOrientation qrCodesOrientation, int qrCodeImageGap) {
        if (qrCodesOrientation == QrCodesOrientation.HORIZONTAL)
            return (sumWidth(results) + (qrCodeImageGap * (results.size() - 1)));
        else return (maxWidth(results));
    }

    private static int maxWidth(ArrayList<BufferedImage> results) {
        int max = 0;
        for (BufferedImage bi : results) max = Math.max(max, bi.getWidth());
        return (max);
    }

    private static int sumWidth(ArrayList<BufferedImage> results) {
        int total = 0;
        for (BufferedImage bi : results) total = total + bi.getWidth();
        return (total);
    }

    /**
     * Remember to dispose of the ImageWriter when you're finished with it.
     */
    private static ImageWriter getPngImageWriter() {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix("png");
        if (writers.hasNext()) {
            return (writers.next());
        } else {
            throw (new IllegalStateException("Missing png Image Writer"));
        }
    }

	/**
	 * Adds a logo to a QR code image.
	 * This method takes a QR code image as a byte array and a logo image as an InputStream.
	 * It resizes the logo if it's too large, then overlays it onto the center of the QR code.
	 * A transparent hole is created in the QR code for the logo, and a radial gradient is applied
	 * around the hole to enhance the visual effect.
	 *
	 * @param qrCodeBytes     The byte array representing the QR code image.
	 * @param logoInputStream The InputStream for the logo image.
	 * @return A byte array representing the QR code image with the logo embedded.
	 * @throws IOException If there is an error reading or writing the image data.
	 * @throws IllegalArgumentException if qrCodeBytes or logoInputStream is null
	 */
	public static byte[] addLogoToQRCode(byte[] qrCodeBytes, InputStream logoInputStream) throws IOException {
		BufferedImage qrImage = inputStreamToBufferedImage(new ByteArrayInputStream(qrCodeBytes));
		BufferedImage logo = inputStreamToBufferedImage(logoInputStream);

		int maxLogoSize = qrImage.getWidth() / 5;
		if (logo.getWidth() > maxLogoSize || logo.getHeight() > maxLogoSize) {
			logo = resizeImage(logo, maxLogoSize, maxLogoSize);
		}

		int qrWidth = qrImage.getWidth();
		int qrHeight = qrImage.getHeight();
		int x = (qrWidth - logo.getWidth()) / 2;
		int y = (qrHeight - logo.getHeight()) / 2;

		// Create a new BufferedImage with transparency for the logo area
		BufferedImage logoWithTransparency = new BufferedImage(qrWidth, qrHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = logoWithTransparency.createGraphics();
		g2d.drawImage(qrImage, 0, 0, null); // Draw the original QR code

		g2d.setComposite(AlphaComposite.Clear); // Set alpha composite to clear logo area
		g2d.fillOval(x - 2, y - 2, logo.getWidth() + 4, logo.getHeight() + 4); // Make a hole in the QR code for the logo

		g2d.setComposite(AlphaComposite.Src); // Reset alpha composite to overwrite the hole with white color

		// Define the gradient colors
		Paint gradientPaint = getshadowGradientPaint(x, y, logo);

		// Set the gradient as the paint and fill the hole
		g2d.setPaint(gradientPaint);
		g2d.fillOval(x - 2, y - 2, logo.getWidth() + 4, logo.getHeight() + 4);


		g2d.setComposite(AlphaComposite.SrcOver); // Reset alpha composite to draw logo
		g2d.drawImage(logo, x, y, null); // Draw the logo in the center
		g2d.dispose();

		return imageToByteArray(logoWithTransparency, "png");
	}

	private static Paint getshadowGradientPaint(int x, int y, BufferedImage logo) {
		Color color1 = new Color(213, 213, 213);
		Color color2 = new Color(255, 255, 255);
		Color color3 = new Color(180, 200, 220);

		float[] fractions = {0.0f, 0.8f, 1.0f};
		Point2D center = new Point2D.Float(x + (float) logo.getWidth() / 2, y + (float) logo.getHeight() / 2); // Center of the hole
		float radius = (float) Math.max(logo.getWidth(), logo.getHeight()) / 2 + 10; // Radius of the gradient
        return new RadialGradientPaint(center, radius, fractions, new Color[]{color3, color2, color1});
	}

	/**
	 * Resizes a BufferedImage to the specified width and height.
	 *
	 * @param originalImage The original BufferedImage to resize.
	 * @param width         The desired width of the resized image.
	 * @param height        The desired height of the resized image.
	 * @return A new BufferedImage that is a resized version of the original.
	 * @throws IllegalArgumentException if originalImage is null
	 */
	public static BufferedImage resizeImage(BufferedImage originalImage, int width, int height) {
		Image tmp = originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
		BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = resizedImage.createGraphics();
		g2d.drawImage(tmp, 0, 0, null);
		g2d.dispose();
		return resizedImage;
	}

	/**
	 * Converts a BufferedImage to a byte array.
	 *
	 * @param image  The BufferedImage to convert.
	 * @param format The format of the image (e.g., "png", "jpg").
	 * @return A byte array representing the image.
	 * @throws IOException If there is an error writing the image data.
	 * @throws IllegalArgumentException if image or format is null
	 */
	private static byte[] imageToByteArray(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(image, format, baos);
		return baos.toByteArray();
	}

	/**
	 * Converts an InputStream to a BufferedImage.
	 *
	 * @param inputStream The InputStream to read from.
	 * @return The BufferedImage read from the InputStream.
	 * @throws IOException If there is an error reading from the InputStream or if the InputStream is null.
	 * @throws IllegalArgumentException if inputStream is null
	 */
	private static BufferedImage inputStreamToBufferedImage(InputStream inputStream) throws IOException {
		if (inputStream == null) {
			throw new IOException("LogoStream is null");
		}
		return ImageIO.read(inputStream);
	}

	public static void main(String... argv) throws Exception
	{
        byte[] b = toSingleQrCodePng("this is a test of some text", ErrorCorrectionLevel.H, 1);

        FileOutputStream fos = new FileOutputStream("/tmp/test_h.png");
        fos.write(b);
        fos.flush();
        fos.close();

        //------
        {
            byte[] b1 = toMultipleQrCodePngs("1234567890abcdefghijklmnopqrstuvwxyz", ErrorCorrectionLevel.H, QrCodesOrientation.HORIZONTAL, null, 5, 1);

            FileOutputStream fos1 = new FileOutputStream("/tmp/test_h1.png");
            fos1.write(b1);
            fos1.flush();
            fos1.close();
        }
        //------
        {
            byte[] b1 = toMultipleQrCodePngs("1234567890abcdefghijklmnopqrstuvwxyz", ErrorCorrectionLevel.H, QrCodesOrientation.HORIZONTAL, null, 5, 3);

            FileOutputStream fos1 = new FileOutputStream("/tmp/test_h1_x3.png");
            fos1.write(b1);
            fos1.flush();
            fos1.close();
        }
    }
}
