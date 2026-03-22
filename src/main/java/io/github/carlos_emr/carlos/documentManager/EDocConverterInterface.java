package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;

/**
 * Interface for HTML-to-PDF document converters in the CARLOS EMR document management system.
 *
 * <p>Implementations of this interface convert HTML content into PDF format and write the
 * result to the provided output stream. The primary implementation is {@link InternalEDocConverter}
 * which uses the wkhtmltopdf library. A fallback renderer using Flying Saucer is available in
 * {@link ConvertToEdoc#fallbackRender}.
 *
 * @see InternalEDocConverter
 * @see ConvertToEdoc
 * @since 2018-10-15
 */
public interface EDocConverterInterface {

  /**
   * Converts an HTML document string to PDF and writes the result to the output stream.
   *
   * @param html String the complete HTML document to convert
   * @param os OutputStream the output stream to write the generated PDF content to
   * @throws Exception if the conversion process fails
   */
  void convert(String html, OutputStream os) throws Exception;
}