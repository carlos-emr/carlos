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
package io.github.carlos_emr.carlos.email.core;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import io.github.carlos_emr.carlos.commn.dao.EmailFooterLogoDao;
import io.github.carlos_emr.carlos.commn.model.EmailFooterLogo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The clinic's email footer logo (issue #3981; added by maintainer decision, 8 Oct 2026): one logo
 * for the clinic, shown above the footer in the formatted version of patient emails.
 *
 * <p>An upload must be a PNG or JPEG of at most {@link #MAX_BYTES} bytes and
 * {@link #MAX_WIDTH} x {@link #MAX_HEIGHT} pixels. Its size is read from the file's header before
 * the picture is decoded, so a small file that would expand into a huge picture is refused unread,
 * and a JPEG with more than {@link #MAX_JPEG_SCANS} scans is refused before it is decoded.
 * CARLOS then re-saves the picture in the same format, which drops anything else the file carried
 * (camera data, comments, embedded thumbnails), and stores that copy, never the upload. The copy
 * must fit {@link #MAX_BYTES} too.</p>
 *
 * <p>The logo travels inside each email ({@link #inlineLogo()}), never as a link to a web address:
 * there is nothing to host, it shows without a network, and opening the email reveals nothing.</p>
 *
 * @since 2026-10-08
 */
@Service
public class EmailFooterLogoService {

    /** Largest logo accepted, before and after CARLOS re-saves it: 100 KB. */
    public static final int MAX_BYTES = 100 * 1024;
    /** Widest logo accepted, in pixels. */
    public static final int MAX_WIDTH = 600;
    /** Tallest logo accepted, in pixels. */
    public static final int MAX_HEIGHT = 200;
    /**
     * Most scans a JPEG logo may have. Each scan is another pass over the whole picture when it is
     * read; an ordinary JPEG has one and a progressive one about ten.
     */
    static final int MAX_JPEG_SCANS = 64;

    /** Why an upload was refused; nothing is saved. */
    public enum Rejection {
        /** No file, or an empty one. */
        EMPTY,
        /** Over {@link #MAX_BYTES}. */
        TOO_BIG,
        /** Not a PNG or JPEG that Java can read. */
        NOT_AN_IMAGE,
        /** Over {@link #MAX_WIDTH} x {@link #MAX_HEIGHT} pixels. */
        TOO_LARGE,
        /** The upload fits, but the copy CARLOS saves of it would be over {@link #MAX_BYTES}. */
        COPY_TOO_BIG,
        /** The server could not read or save the upload. */
        UPLOAD_FAILED
    }

    /** Thrown when an upload is refused; nothing is saved. */
    public static final class LogoRejectedException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private final Rejection reason;

        LogoRejectedException(Rejection reason) {
            super("Email footer logo refused: " + reason);
            this.reason = reason;
        }

        /** @return why the upload was refused */
        public Rejection reason() {
            return reason;
        }
    }

    /** A picture ready to store: re-saved, measured and hashed. */
    record PreparedLogo(String contentType, byte[] bytes, int width, int height, String sha256) {

        // Compared and printed by the picture's content, not the array's identity; never printed whole.
        @Override
        public boolean equals(Object o) {
            return o instanceof PreparedLogo other && contentType.equals(other.contentType) && width == other.width
                    && height == other.height && sha256.equals(other.sha256) && Arrays.equals(bytes, other.bytes);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(contentType, width, height, sha256) + Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return "PreparedLogo[contentType=" + contentType + ", " + width + "x" + height + ", bytes=" + bytes.length
                    + ", sha256=" + sha256 + "]";
        }
    }

    private static final Logger logger = MiscUtils.getLogger();

    private final EmailFooterLogoDao logoDao;

    @Autowired
    public EmailFooterLogoService(EmailFooterLogoDao logoDao) {
        this.logoDao = logoDao;
    }

    /** @return the logo in use, or null when the clinic has none */
    public EmailFooterLogo currentLogo() {
        return logoDao.findCurrent();
    }

    /**
     * The logo as an email carries it. Its Content-ID names the picture by its hash, so one email
     * can only ever refer to the bytes it carries.
     *
     * @return the logo to carry inline, or null when the clinic has none
     */
    public EmailInlineImage inlineLogo() {
        EmailFooterLogo logo = logoDao.findCurrent();
        if (logo == null) {
            return null;
        }
        return new EmailInlineImage("clinic-logo-" + logo.getSha256().substring(0, 16) + "@carlos-emr",
                logo.getContentType(), logo.getImageData());
    }

    /**
     * Replaces the clinic logo. The previous one is marked removed, not deleted.
     *
     * @param upload the uploaded file
     * @param providerNo the administrator saving it
     * @return the stored logo
     * @throws LogoRejectedException when the upload is refused; nothing is saved
     */
    @Transactional
    public EmailFooterLogo replace(byte[] upload, String providerNo) {
        // Checked and re-saved before any lock is taken: a refusal touches nothing.
        PreparedLogo prepared = prepare(upload);
        Date now = new Date();
        markRemoved(logoDao.lockCurrent(), providerNo, now);
        EmailFooterLogo logo = new EmailFooterLogo();
        logo.setContentType(prepared.contentType());
        logo.setImageData(prepared.bytes());
        logo.setWidth(prepared.width());
        logo.setHeight(prepared.height());
        logo.setSha256(prepared.sha256());
        logo.setUploadedBy(providerNo);
        logo.setUploadedAt(now);
        logoDao.persist(logo);
        return logo;
    }

    /**
     * Removes the clinic logo; emails sent from now on have none.
     *
     * @param providerNo the administrator removing it
     * @return false when there was no logo to remove
     */
    @Transactional
    public boolean remove(String providerNo) {
        List<EmailFooterLogo> current = logoDao.lockCurrent();
        markRemoved(current, providerNo, new Date());
        return !current.isEmpty();
    }

    private void markRemoved(List<EmailFooterLogo> logos, String providerNo, Date when) {
        for (EmailFooterLogo old : logos) {
            old.setRemovedAt(when);
            old.setRemovedBy(providerNo);
            logoDao.merge(old);
        }
    }

    /**
     * Checks an upload and re-saves it.
     *
     * @param upload the uploaded bytes
     * @return the re-saved picture
     * @throws LogoRejectedException when the upload is refused
     */
    static PreparedLogo prepare(byte[] upload) {
        return prepare(upload, MAX_BYTES);
    }

    // The byte limit is a parameter only so a test can show the copy's own check with a small
    // picture; everything else uses MAX_BYTES.
    // FindSecBugs IMPROPER_UNICODE: the case fold is of an image format name the JDK's own reader reports, compared with fixed names; not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case fold of a JDK image reader's format name, compared with fixed names; not a security or authorization decision")
    static PreparedLogo prepare(byte[] upload, int maxBytes) {
        if (upload == null || upload.length == 0) {
            throw new LogoRejectedException(Rejection.EMPTY);
        }
        if (upload.length > maxBytes) {
            throw new LogoRejectedException(Rejection.TOO_BIG);
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw new LogoRejectedException(Rejection.NOT_AN_IMAGE);
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean png = "png".equals(format);
                if (!png && !"jpeg".equals(format) && !"jpg".equals(format)) {
                    throw new LogoRejectedException(Rejection.NOT_AN_IMAGE);
                }
                // Metadata is not read at all: the copy CARLOS stores never carries it.
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_WIDTH || height > MAX_HEIGHT) {
                    throw new LogoRejectedException(Rejection.TOO_LARGE);
                }
                if (!png && jpegScanCount(upload) > MAX_JPEG_SCANS) {
                    throw new LogoRejectedException(Rejection.NOT_AN_IMAGE);
                }
                BufferedImage decoded = reader.read(0);
                byte[] bytes;
                try {
                    bytes = resave(decoded, png);
                } catch (IOException | RuntimeException e) {
                    logger.warn("Clinic email logo could not be saved; cause={}", e.getClass().getSimpleName());
                    throw new LogoRejectedException(Rejection.UPLOAD_FAILED);
                }
                if (bytes.length > maxBytes) {
                    throw new LogoRejectedException(Rejection.COPY_TOO_BIG);
                }
                return new PreparedLogo(png ? "image/png" : "image/jpeg", bytes, width, height, DigestUtils.sha256Hex(bytes));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof LogoRejectedException rejected) {
                throw rejected;
            }
            // A damaged file, or one Java's readers cannot decode (a CMYK JPEG, say). Logged by type
            // only, so a server-side fault (a failed re-save) can still be told apart from a bad file.
            logger.warn("Clinic email logo refused as not a readable picture; cause={}", e.getClass().getSimpleName());
            throw new LogoRejectedException(Rejection.NOT_AN_IMAGE);
        }
    }

    /**
     * Counts the start-of-scan markers (0xFF 0xDA) in a JPEG's bytes. Inside the compressed data a
     * 0xFF byte is followed only by 0x00 or a restart marker (0xD0 to 0xD7), never by 0xDA, so any
     * 0xFF 0xDA found there is a real marker. One inside a metadata segment is counted too, which
     * only errs towards refusing.
     */
    static int jpegScanCount(byte[] jpeg) {
        int scans = 0;
        for (int i = 0; i + 1 < jpeg.length; i++) {
            if (jpeg[i] == (byte) 0xFF && jpeg[i + 1] == (byte) 0xDA) {
                scans++;
            }
        }
        return scans;
    }

    private static byte[] resave(BufferedImage image, boolean png) throws IOException {
        BufferedImage toWrite = image;
        if (!png && image.getColorModel().hasAlpha()) {
            // The JPEG writer refuses a picture with transparency; flatten it onto white.
            toWrite = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = toWrite.createGraphics();
            try {
                graphics.setColor(java.awt.Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(png ? "png" : "jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No writer for the logo's format");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                // PNG: quality 0 is the strongest (still lossless) compression, so the copy stays
                // near the upload's size. JPEG: 0.9 keeps a logo's lettering sharp; the default
                // 0.75 blurs it.
                param.setCompressionQuality(png ? 0.0f : 0.9f);
            }
            // No metadata is passed: the copy carries the picture and nothing else.
            writer.write(null, new IIOImage(toWrite, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
