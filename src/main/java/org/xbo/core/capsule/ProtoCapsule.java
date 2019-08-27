package org.xbo.core.capsule;

public interface ProtoCapsule<T> {

  byte[] getData();

  T getInstance();
}
