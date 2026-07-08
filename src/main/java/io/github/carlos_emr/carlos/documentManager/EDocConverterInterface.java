package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;
/**
 * Defines the contract for converting various document formats (e.g., TIFF, DOC) into standard EMR-compatible web formats.
 */

public interface EDocConverterInterface {
  void convert(String html, OutputStream os) throws Exception;
}