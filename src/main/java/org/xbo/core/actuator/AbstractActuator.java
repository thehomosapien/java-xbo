package org.xbo.core.actuator;

import com.google.protobuf.Any;
import org.xbo.common.storage.Deposit;
import org.xbo.core.capsule.TransactionResultCapsule;
import org.xbo.core.db.Manager;
import org.xbo.core.exception.ContractExeException;

public abstract class AbstractActuator implements Actuator {

  protected Any contract;
  protected Manager dbManager;

  public Deposit getDeposit() {
    return deposit;
  }

  public void setDeposit(Deposit deposit) {
    this.deposit = deposit;
  }

  protected Deposit deposit;

  AbstractActuator(Any contract, Manager dbManager) {
    this.contract = contract;
    this.dbManager = dbManager;
  }
}
