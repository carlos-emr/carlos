package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;


/**
 * Interface defining the contract for converting various electronic document formats (e.g., TIF to PDF).
 */
public interface EDocConverterInterface {
  void convert(String html, OutputStream os) throws Exception;
}