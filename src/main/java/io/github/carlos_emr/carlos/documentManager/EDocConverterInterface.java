package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;

/**
 * Interface defining the contract for converting various electronic document formats into standard internal representations (like PDF).
 */
public interface EDocConverterInterface {
  void convert(String html, OutputStream os) throws Exception;
}