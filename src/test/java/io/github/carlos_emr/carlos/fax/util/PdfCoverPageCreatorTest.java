/**
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
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
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 */

package io.github.carlos_emr.carlos.fax.util;

import io.github.carlos_emr.carlos.fax.util.PdfCoverPageCreator;
import org.junit.Test;
import io.github.carlos_emr.carlos.fax.core.FaxAccount;
import io.github.carlos_emr.carlos.fax.core.FaxRecipient;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class PdfCoverPageCreatorTest extends PdfCoverPageCreator {

    public PdfCoverPageCreatorTest() {
        super("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer laoreet nisi ante, vel dictum lacus egestas nec. Phasellus viverra tempor nisi, vel pretium dui bibendum quis. Cras nec porttitor sem, vel aliquam magna. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; Aenean suscipit orci et urna gravida dignissim. Nunc volutpat nisi augue. Ut sit amet placerat ligula. Nullam accumsan nibh aliquam odio luctus venenatis. Aenean eu interdum ex, sed ornare dolor. Sed nisl nunc, semper id sem sed, tempor pretium nisl.\n" +
                        "\n" +
                        "Sed gravida id nisl a maximus. Ut condimentum tristique cursus. Nunc nec risus et elit porttitor eleifend. Proin eget eleifend mauris, vitae pellentesque tellus. Nam convallis suscipit augue, sed pharetra mauris accumsan ac. Phasellus facilisis mattis justo eget faucibus. Nulla quis eros est. Sed ipsum lectus, placerat id dignissim quis, ullamcorper nec dolor. Integer maximus felis tempor nulla porttitor, vitae feugiat massa posuere. Ut vitae nibh mi. Aenean tortor quam, posuere eget maximus fringilla, rutrum at magna. Donec pharetra nisl vel ante tempor, at luctus ante interdum. Nam eget feugiat mauris. Etiam justo odio, tincidunt eget orci nec, ultrices pharetra ipsum. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos.\n" +
                        "\n" +
                        "Nam volutpat tincidunt est, non rutrum libero. Maecenas convallis mollis elit quis tristique. Praesent vehicula, mi et interdum vehicula, ante quam tincidunt lectus, eget pharetra sem lacus tristique mauris. Nullam tincidunt mi augue, non consequat risus tincidunt nec. Ut suscipit arcu sit amet diam vehicula finibus. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Vivamus scelerisque ex a lacus accumsan convallis. Duis ultrices malesuada nisi id finibus. Sed dignissim, mauris varius ullamcorper dapibus, lorem nisl sollicitudin eros, ac vehicula lectus risus ut leo. Cras malesuada dignissim ante in congue. Interdum et malesuada fames ac ante ipsum primis in faucibus. Sed scelerisque tristique massa, at sollicitudin felis tempus sed. Pellentesque nec urna lorem. Nam luctus euismod ligula, non porttitor sem mattis quis.\n" +
                        "\n" +
                        "Nullam in augue scelerisque, condimentum orci in, porta risus. Vestibulum a lorem ultricies, tempor mi ut, dapibus tortor. Integer in leo sed elit imperdiet consequat in et eros. Etiam sit amet orci convallis, posuere nulla nec, ornare lacus. Integer finibus lorem non venenatis tempor. Vivamus molestie tincidunt neque eget maximus. Maecenas vestibulum lacus eu tincidunt tristique. Integer sed vehicula elit. Ut fermentum eros vitae mi blandit, in cursus orci suscipit. Cras consequat ullamcorper odio, eu dictum neque dictum eget. Donec malesuada sapien sed malesuada congue. Sed tincidunt feugiat massa, ac sagittis magna aliquet in. Sed mi leo, luctus a suscipit ac, euismod ac sapien. Curabitur commodo odio maximus, vestibulum elit et, convallis urna. Donec accumsan tincidunt eros id tempus.\n" +
                        "\n" +
                        "Phasellus blandit, ipsum eget suscipit pharetra, ante neque luctus risus, sit amet ullamcorper arcu velit et ex. Curabitur varius in tellus quis aliquam. Nam faucibus enim id lectus volutpat, nec tristique lorem accumsan. Proin ac iaculis nisl, id luctus leo. Fusce auctor quam sed aliquam pretium. Maecenas cursus porta tristique. Ut sed lobortis nulla. Nulla facilisi. Nam in pulvinar massa. Vivamus tincidunt felis quis dictum posuere. Maecenas metus nunc, dictum sed finibus id, tristique a neque. Duis scelerisque nisl molestie, pretium mi ac, imperdiet nisl. Nunc non ipsum in nibh pharetra eleifend. Aliquam tristique nunc id odio molestie, a bibendum erat finibus. Sed sed lacinia eros, vel dignissim libero. Vestibulum porttitor, dolor quis imperdiet pellentesque, leo lorem eleifend dui, eget ornare dolor eros lobortis sem.\n" +
                        "\n" +
                        "Etiam at sodales odio. Nunc pretium feugiat quam et porta. Praesent dapibus eu sem at sodales. Ut ultricies tempor ornare. Donec mollis gravida est nec sollicitudin. Nulla egestas, velit at lobortis lobortis, libero massa varius dolor, sit amet faucibus eros eros id mi. Vestibulum euismod mollis urna. Proin non consequat elit. Quisque bibendum fringilla nisl id fringilla. Vestibulum tincidunt tellus ut ornare venenatis.\n" +
                        "\n" +
                        "Vivamus et semper nisl, at vehicula quam. Aliquam ultricies, dolor sit amet auctor porta, diam nisi mollis ex, et mattis ex mauris a lacus. Suspendisse tincidunt tincidunt bibendum. Etiam quis augue vitae massa lacinia facilisis. Pellentesque imperdiet faucibus urna sit amet imperdiet. Suspendisse nec orci pharetra, facilisis turpis vitae, blandit orci. Cras et nibh erat. In aliquam, sem sit amet facilisis aliquam, elit dui placerat nulla, vel condimentum orci ipsum vel lacus. ",
                5,
                new FaxRecipient() {{
                    setName("Test Recipient Clinic");
                    setFax("778-998-0876");
                }},
                new FaxAccount() {{
                    setName("The MOA Name");
                    setPhone("604-555-1212");
                    setFax("604-234-2345");
                    setSubText("Primary Care Network (PCN) ");
                    setFaxNumberOwner("MOA Name Here");
                    setLetterheadName("Dr. So Andso");
                }});
    }

    @Test
    public void testCreateCoverPage() {
        System.out.println("@@@@ testCreateCoverPage() @@@@");
        byte[] bytearray = createCoverPage();

        File tempFile = null;
        try {
            tempFile = File.createTempFile("testCoverPage", ".pdf");
            System.out.println(tempFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile)) {
            fileOutputStream.write(bytearray);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
