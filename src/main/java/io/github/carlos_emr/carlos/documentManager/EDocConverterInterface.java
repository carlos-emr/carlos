package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;

/**
 * Defines the contract for converting document content, such as HTML pages, into format-agnostic output streams like PDFs.
 */
public interface EDocConverterInterface {
  void convert(String html, OutputStream os) throws Exception;
}