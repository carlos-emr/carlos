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
package io.github.carlos_emr.carlos.lab.ca.all.upload.handlers;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.ArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pins rejection of uploads that contain no HL7 messages.
 * @since 2026-09-25
 */
@Tag("unit")
@Tag("lab")
class CMLHandlerUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldRejectUpload_whenMessageListIsEmpty() {
        registerMock(Hl7TextInfoDao.class, mock(Hl7TextInfoDao.class));
        try (MockedStatic<Utilities> utilities = mockStatic(Utilities.class)) {
            utilities.when(() -> Utilities.separateMessages("synthetic.hl7")).thenReturn(new ArrayList<>());
            CMLHandler handler = new CMLHandler();
            assertThat(handler.parse(null, "test", "synthetic.hl7", 1, "127.0.0.1")).isNull();
            assertThat(handler.getLastLabNo()).isNull();
        }
    }
}
