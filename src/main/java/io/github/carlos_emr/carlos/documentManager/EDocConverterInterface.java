package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;

/**
 * Defines the contract for converting various electronic document formats (like RTF or Word) into a standardized format (such as PDF) for clinical viewing.
 */
public interface EDocConverterInterface {
  void convert(String html, OutputStream os) throws Exception;
}