/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.htmlpdf;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test helper: small image files of every kind the image step meets, built on the fly so no binary
 * fixture has to be committed. Pixel content is a gradient that compresses well.
 */
public final class TestImages {

    private TestImages() {
    }

    /** A picture of the given size: a gradient, so it compresses and is not flat. */
    public static BufferedImage picture(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = (x * 255) / Math.max(1, width - 1);
                int green = (y * 255) / Math.max(1, height - 1);
                image.setRGB(x, y, (red << 16) | (green << 8) | ((x + y) & 0xFF));
            }
        }
        return image;
    }

    public static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("no writer for " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] png(int width, int height) {
        return encode(picture(width, height, BufferedImage.TYPE_INT_RGB), "png");
    }

    public static byte[] gif(int width, int height) {
        return encode(picture(width, height, BufferedImage.TYPE_INT_RGB), "gif");
    }

    public static byte[] bmp(int width, int height) {
        return encode(picture(width, height, BufferedImage.TYPE_INT_RGB), "bmp");
    }

    public static byte[] tiff(int width, int height) {
        return encode(picture(width, height, BufferedImage.TYPE_INT_RGB), "tiff");
    }

    /** A one-bit WBMP, a format ImageIO reads and OpenPDF cannot embed. */
    public static byte[] wbmp(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, ((x + y) % 2 == 0) ? 0xFFFFFF : 0x000000);
            }
        }
        return encode(image, "wbmp");
    }

    public static byte[] jpeg(int width, int height, boolean progressive) {
        BufferedImage image = picture(width, height, BufferedImage.TYPE_INT_RGB);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setProgressiveMode(progressive ? ImageWriteParam.MODE_DEFAULT : ImageWriteParam.MODE_DISABLED);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /**
     * A picture whose four quarters are four different colours, red and green above and blue and yellow below,
     * so that any turning or mirroring of it can be told from the picture itself.
     */
    public static BufferedImage quarters(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, width / 2, height / 2);
            graphics.setColor(new Color(0, 160, 0));
            graphics.fillRect(width / 2, 0, width - width / 2, height / 2);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, height / 2, width / 2, height - height / 2);
            graphics.setColor(Color.YELLOW);
            graphics.fillRect(width / 2, height / 2, width - width / 2, height - height / 2);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * A scene that has an obvious way up, drawn with shapes only so that no font is needed: a sky that is
     * light blue at the top, green ground below it, a yellow sun in the upper right and a red house with a dark roof
     * and a white door in the middle.
     */
    public static BufferedImage scene(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            int horizon = (int) (height * 0.62);
            graphics.setPaint(new GradientPaint(0, 0, new Color(110, 180, 245), 0, (float) horizon, new Color(205, 230, 255)));
            graphics.fillRect(0, 0, width, horizon);
            graphics.setColor(new Color(60, 150, 60));
            graphics.fillRect(0, horizon, width, height - horizon);
            graphics.setColor(new Color(255, 215, 0));
            int sun = (int) (width * 0.2);
            graphics.fillOval((int) (width * 0.68), (int) (height * 0.06), sun, sun);
            graphics.setColor(new Color(200, 60, 50));
            graphics.fillRect((int) (width * 0.28), horizon, (int) (width * 0.44), (int) (height * 0.26));
            graphics.setColor(new Color(110, 30, 30));
            graphics.fillPolygon(new Polygon(
                    new int[]{(int) (width * 0.22), (int) (width * 0.5), (int) (width * 0.78)},
                    new int[]{horizon, (int) (height * 0.42), horizon}, 3));
            graphics.setColor(Color.WHITE);
            graphics.fillRect((int) (width * 0.45), (int) (height * 0.74), (int) (width * 0.1), (int) (height * 0.14));
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * A portrait photograph as a phone stores it: the {@link #scene} turned a quarter anticlockwise into a
     * landscape picture, with EXIF orientation 6 recording that a viewer must turn it clockwise. Shown correctly, the
     * sun is in the upper right and the ground is below.
     *
     * @return a 120 x 90 JPEG, which shows as 90 x 120
     */
    public static byte[] phonePhoto() {
        BufferedImage upright = scene(90, 120);
        int width = upright.getHeight();
        int height = upright.getWidth();
        BufferedImage stored = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Orientation 6 puts the stored pixel (x, y) at (height - 1 - y, x) of the picture a viewer shows.
                stored.setRGB(x, y, upright.getRGB(height - 1 - y, x));
            }
        }
        return withOrientation(encode(stored, "jpeg"), 6);
    }

    /** The JPEG with an EXIF segment recording an orientation, inserted right after the start-of-image marker. */
    public static byte[] withOrientation(byte[] jpeg, int orientation) {
        ByteBuffer tiff = ByteBuffer.allocate(26).order(ByteOrder.BIG_ENDIAN);
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        tiff.putShort((short) 1);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1);
        tiff.putShort((short) orientation).putShort((short) 0);
        tiff.putInt(0);
        byte[] exif = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
        int length = 2 + exif.length + tiff.capacity();
        ByteBuffer segment = ByteBuffer.allocate(2 + length);
        segment.put((byte) 0xFF).put((byte) 0xE1).putShort((short) length).put(exif).put(tiff.array());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(segment.array(), 0, segment.capacity());
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** The start of a HEIC file: a format no vetted reader knows. */
    public static byte[] heic() {
        byte[] bytes = new byte[256];
        byte[] header = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'};
        System.arraycopy(header, 0, bytes, 0, header.length);
        return bytes;
    }

    public static byte[] pdf() {
        return "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n"
                .getBytes(StandardCharsets.ISO_8859_1);
    }

    /** An SVG that points at the outside world as well as drawing a rectangle. */
    public static byte[] svgWithOutsideReference() {
        return ("<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" "
                + "xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"40\" height=\"20\">"
                + "<rect width=\"40\" height=\"20\" fill=\"#cc0000\"/>"
                + "<image xlink:href=\"http://127.0.0.1:1/probe.png\" width=\"10\" height=\"10\"/></svg>")
                .getBytes(StandardCharsets.UTF_8);
    }

    /** Writes a fixture and returns its path. */
    public static Path write(Path directory, String name, byte[] bytes) {
        try {
            Path file = directory.resolve(name);
            Files.createDirectories(file.getParent());
            return Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
