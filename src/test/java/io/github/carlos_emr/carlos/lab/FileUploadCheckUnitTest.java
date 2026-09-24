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
package io.github.carlos_emr.carlos.lab;

import io.github.carlos_emr.carlos.commn.dao.FileUploadCheckDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pins the duplicate lookup that, unlike addFile, does not swallow failures.
 * @since 2026-09-24
 */
@Tag("unit")
@Tag("lab")
class FileUploadCheckUnitTest extends CarlosUnitTestBase {
    private static final byte[] CONTENT = "MSH|recorded lab content".getBytes(StandardCharsets.UTF_8);

    private FileUploadCheckDao dao;

    @BeforeEach
    void setUpDao() {
        dao = mock(FileUploadCheckDao.class);
        registerMock(FileUploadCheckDao.class, dao);
    }

    @Test
    void shouldReportRecorded_whenChecksumRowExists() throws Exception {
        when(dao.findByMd5Sum(DigestUtils.md5Hex(CONTENT)))
                .thenReturn(List.of(new io.github.carlos_emr.carlos.commn.model.FileUploadCheck()));

        assertThat(FileUploadCheck.isFileRecorded(new ByteArrayInputStream(CONTENT))).isTrue();
    }

    @Test
    void shouldReportNotRecorded_whenNoChecksumRowExists() throws Exception {
        when(dao.findByMd5Sum(anyString())).thenReturn(List.of());

        assertThat(FileUploadCheck.isFileRecorded(new ByteArrayInputStream(CONTENT))).isFalse();
    }

    @Test
    void shouldPropagateDatabaseFailure_whenLookupThrows() {
        when(dao.findByMd5Sum(anyString())).thenThrow(new IllegalStateException("database unavailable"));

        // addFile would turn this into UNSUCCESSFUL_SAVE; this lookup must not look like "not recorded".
        assertThatThrownBy(() -> FileUploadCheck.isFileRecorded(new ByteArrayInputStream(CONTENT)))
                .isInstanceOf(IllegalStateException.class);
    }
}
