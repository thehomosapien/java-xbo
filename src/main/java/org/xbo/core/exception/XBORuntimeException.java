package org.xbo.core.exception;

public class XBORuntimeException extends RuntimeException {

  public XBORuntimeException() {
    super();
  }

  public XBORuntimeException(String message) {
    super(message);
  }

  public XBORuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  public XBORuntimeException(Throwable cause) {
    super(cause);
  }

  protected XBORuntimeException(String message, Throwable cause,
      boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }


}
