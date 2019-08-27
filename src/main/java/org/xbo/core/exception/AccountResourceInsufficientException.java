package org.xbo.core.exception;

public class AccountResourceInsufficientException extends XBOException {

  public AccountResourceInsufficientException() {
    super("Insufficient bandwidth and balance to create new account");
  }

  public AccountResourceInsufficientException(String message) {
    super(message);
  }
}

