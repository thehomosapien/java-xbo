package org.xbo.common.runtime;

import lombok.Setter;
import org.xbo.common.runtime.vm.program.InternalTransaction.TrxType;
import org.xbo.common.runtime.vm.program.ProgramResult;
import org.xbo.core.exception.ContractExeException;
import org.xbo.core.exception.ContractValidateException;
import org.xbo.core.exception.VMIllegalException;


public interface Runtime {

  void execute() throws ContractValidateException, ContractExeException, VMIllegalException;

  void go();

  TrxType getTrxType();

  void finalization();

  ProgramResult getResult();

  String getRuntimeError();

  void setEnableEventLinstener(boolean enableEventLinstener);
}
