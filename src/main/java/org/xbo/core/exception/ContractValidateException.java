package org.xbo.core.exception;

public class ContractValidateException extends XBOException {

  public ContractValidateException() {
    super();
  }

  public ContractValidateException(String message) {
    super(message);
  }

  public ContractValidateException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
