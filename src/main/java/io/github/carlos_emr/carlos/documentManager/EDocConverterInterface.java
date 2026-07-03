package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;

/**
 * Defines the contract for converting electronic documents between various formats.
 */
public interface EDocConverterInterface {
  void convert(String html, OutputStream os) throws Exception;
}