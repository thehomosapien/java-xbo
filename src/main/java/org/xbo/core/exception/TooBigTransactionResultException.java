package org.xbo.core.exception;

public class TooBigTransactionResultException extends XBOException {

  public TooBigTransactionResultException() {
    super("too big transaction result");
  }

  public TooBigTransactionResultException(String message) {
    super(message);
  }
}
