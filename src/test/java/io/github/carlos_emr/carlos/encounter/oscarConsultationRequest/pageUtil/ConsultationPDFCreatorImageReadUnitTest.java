/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpdf.text.Image;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPRow;
import org.openpdf.text.pdf.PdfPTable;

/**
 * {@code addImage} read fax-header images with one {@code read} into a fixed 256 KiB buffer, so a
 * larger image was cut short and never printed, and a smaller one carried a zero-filled tail
 * (#3027). The whole file must reach the PDF.
 */
@Tag("unit")
@DisplayName("ConsultationPDFCreator image file reads")
class ConsultationPDFCreatorImageReadUnitTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("should print an image file larger than the old 256 KiB buffer")
    void shouldAddWholeImage_whenFileExceedsOldBuffer() throws Exception {
        byte[] png = noisyPng(420, 420);
        assertThat(png.length).as("fixture must exceed the old 256 KiB buffer").isGreaterThan(256 * 1024);
        Path file = Files.write(dir.resolve("fax-logo.png"), png);

        PdfPTable table = new PdfPTable(1);
        creator().addImage(table, file.toString(), 200f, 60f);

        Image printed = onlyImage(table);
        assertThat(printed.getWidth()).isEqualTo(420f);
        assertThat(printed.getHeight()).isEqualTo(420f);
    }

    @Test
    @DisplayName("should print a small image read to its exact length")
    void shouldAddImage_whenFileIsSmall() throws Exception {
        Path file = Files.write(dir.resolve("small-logo.png"), noisyPng(40, 20));

        PdfPTable table = new PdfPTable(1);
        creator().addImage(table, file.toString(), 200f, 60f);

        Image printed = onlyImage(table);
        assertThat(printed.getWidth()).isEqualTo(40f);
        assertThat(printed.getHeight()).isEqualTo(20f);
    }

    private static Image onlyImage(PdfPTable table) {
        assertThat(table.getRows()).as("one image row").hasSize(1);
        List<Image> images = new ArrayList<>();
        collectImages(table, images);
        assertThat(images).as("exactly one image reached the table").hasSize(1);
        return images.get(0);
    }

    /** OpenPDF may wrap a cell's image in a nested table; walk cells, composites and nested tables. */
    private static void collectImages(PdfPTable table, List<Image> images) {
        for (PdfPRow row : table.getRows()) {
            if (row == null) {
                continue;
            }
            for (PdfPCell cell : row.getCells()) {
                if (cell == null) {
                    continue;
                }
                if (cell.getImage() != null) {
                    images.add(cell.getImage());
                }
                if (cell.getTable() != null) {
                    collectImages(cell.getTable(), images);
                }
                if (cell.getCompositeElements() != null) {
                    for (Object element : cell.getCompositeElements()) {
                        if (element instanceof Image image) {
                            images.add(image);
                        } else if (element instanceof PdfPTable nested) {
                            collectImages(nested, images);
                        }
                    }
                }
            }
        }
    }

    /** A request-free creator; addImage reads only the file it is given. */
    private static ConsultationPDFCreator creator() {
        ConsultationLetterDto letter = new ConsultationLetterDto(
                Locale.ENGLISH,
                new ConsultationLetterDto.Letterhead("Synthetic Clinic", "", "", "", "Synthetic Clinic",
                        "1 Test Street", "Testville", "ON", "A1A 1A1", "555-0100", "555-0101"),
                new ConsultationLetterDto.Request("0", "2026-01-15", "1", "Synthetic Service", "", true, false),
                new ConsultationLetterDto.Specialist("Specialist, Sam MD", "", "", ""),
                new ConsultationLetterDto.Patient("SYNTHETIC, PATIENT", "", "", "", "", "", "2001-02-03", "F",
                        "ON", "0000000000", "AB", "", "", "", "", ""),
                new ConsultationLetterDto.Clinical("", "", "", "", "", "", ""),
                new ConsultationLetterDto.Footer(false, "", "", false, "", ""),
                new ConsultationLetterDto.Images(false, null, false, null, null));
        return new ConsultationPDFCreator(letter, new ByteArrayOutputStream());
    }

    /** Random pixels do not compress, so the PNG is about width x height x 3 bytes. */
    private static byte[] noisyPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(3027);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
