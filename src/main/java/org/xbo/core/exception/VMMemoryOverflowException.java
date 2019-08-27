package org.xbo.core.exception;

public class VMMemoryOverflowException extends XBOException {

  public VMMemoryOverflowException() {
    super("VM memory overflow");
  }

  public VMMemoryOverflowException(String message) {
    super(message);
  }

}
