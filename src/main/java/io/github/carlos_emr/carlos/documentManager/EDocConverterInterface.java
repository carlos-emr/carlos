package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;
/**
 * Contract for electronic document conversion services.
 * Implementations handle transforming documents between various formats, such as HTML to PDF.
 *
 * @since 2026-07-09
 */

public interface EDocConverterInterface {
  void convert(String html, OutputStream os) throws Exception;
}