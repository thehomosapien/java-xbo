package org.xbo.core.db;

import static org.xbo.common.runtime.vm.program.InternalTransaction.TrxType.XBO_CONTRACT_CALL_TYPE;
import static org.xbo.common.runtime.vm.program.InternalTransaction.TrxType.XBO_CONTRACT_CREATION_TYPE;
import static org.xbo.common.runtime.vm.program.InternalTransaction.TrxType.XBO_PRECOMPILED_TYPE;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.util.StringUtils;
import org.xbo.common.runtime.Runtime;
import org.xbo.common.runtime.RuntimeImpl;
import org.xbo.common.runtime.config.VMConfig;
import org.xbo.common.runtime.vm.program.InternalTransaction;
import org.xbo.common.runtime.vm.program.InternalTransaction.TrxType;
import org.xbo.common.runtime.vm.program.Program.BadJumpDestinationException;
import org.xbo.common.runtime.vm.program.Program.IllegalOperationException;
import org.xbo.common.runtime.vm.program.Program.JVMStackOverFlowException;
import org.xbo.common.runtime.vm.program.Program.OutOfEnergyException;
import org.xbo.common.runtime.vm.program.Program.OutOfMemoryException;
import org.xbo.common.runtime.vm.program.Program.OutOfTimeException;
import org.xbo.common.runtime.vm.program.Program.PrecompiledContractException;
import org.xbo.common.runtime.vm.program.Program.StackTooLargeException;
import org.xbo.common.runtime.vm.program.Program.StackTooSmallException;
import org.xbo.common.runtime.vm.program.Program.TransferException;
import org.xbo.common.runtime.vm.program.ProgramResult;
import org.xbo.common.runtime.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.xbo.common.storage.DepositImpl;
import org.xbo.common.utils.Sha256Hash;
import org.xbo.core.Constant;
import org.xbo.core.Wallet;
import org.xbo.core.capsule.AccountCapsule;
import org.xbo.core.capsule.BlockCapsule;
import org.xbo.core.capsule.ContractCapsule;
import org.xbo.core.capsule.ReceiptCapsule;
import org.xbo.core.capsule.TransactionCapsule;
import org.xbo.core.capsule.TransactionResultCapsule;
import org.xbo.core.config.args.Args;
import org.xbo.core.exception.BalanceInsufficientException;
import org.xbo.core.exception.ContractExeException;
import org.xbo.core.exception.ContractValidateException;
import org.xbo.core.exception.ReceiptCheckErrException;
import org.xbo.core.exception.VMIllegalException;
import org.xbo.protos.Contract.TriggerSmartContract;
import org.xbo.protos.Protocol.SmartContract.ABI;
import org.xbo.protos.Protocol.Transaction;
import org.xbo.protos.Protocol.Transaction.Contract.ContractType;
import org.xbo.protos.Protocol.Transaction.Result.contractResult;

@Slf4j(topic = "TransactionTrace")
public class TransactionTrace {

  private TransactionCapsule xbo;

  private ReceiptCapsule receipt;

  private Manager dbManager;

  private Runtime runtime;

  private EnergyProcessor energyProcessor;

  private InternalTransaction.TrxType xboType;

  private long txStartTimeInMs;

  public TransactionCapsule getTrx() {
    return xbo;
  }

  public enum TimeResultType {
    NORMAL,
    LONG_RUNNING,
    OUT_OF_TIME
  }

  @Getter
  @Setter
  private TimeResultType timeResultType = TimeResultType.NORMAL;

  public TransactionTrace(TransactionCapsule xbo, Manager dbManager) {
    this.xbo = xbo;
    Transaction.Contract.ContractType contractType = this.xbo.getInstance().getRawData()
        .getContract(0).getType();
    switch (contractType.getNumber()) {
      case ContractType.TriggerSmartContract_VALUE:
        xboType = XBO_CONTRACT_CALL_TYPE;
        break;
      case ContractType.CreateSmartContract_VALUE:
        xboType = XBO_CONTRACT_CREATION_TYPE;
        break;
      default:
        xboType = XBO_PRECOMPILED_TYPE;
    }

    this.dbManager = dbManager;
    this.receipt = new ReceiptCapsule(Sha256Hash.ZERO_HASH);

    this.energyProcessor = new EnergyProcessor(this.dbManager);
  }

  private boolean needVM() {
    return this.xboType == XBO_CONTRACT_CALL_TYPE || this.xboType == XBO_CONTRACT_CREATION_TYPE;
  }

  public void init(BlockCapsule blockCap) {
    init(blockCap, false);
  }

  //pre transaction check
  public void init(BlockCapsule blockCap, boolean eventPluginLoaded) {
    txStartTimeInMs = System.currentTimeMillis();
    DepositImpl deposit = DepositImpl.createRoot(dbManager);
    runtime = new RuntimeImpl(this, blockCap, deposit, new ProgramInvokeFactoryImpl());
    runtime.setEnableEventLinstener(eventPluginLoaded);
  }

  public void checkIsConstant() throws ContractValidateException, VMIllegalException {
    if (VMConfig.allowTvmConstantinople()) {
      return;
    }

    TriggerSmartContract triggerContractFromTransaction = ContractCapsule
        .getTriggerContractFromTransaction(this.getTrx().getInstance());
    if (TrxType.XBO_CONTRACT_CALL_TYPE == this.xboType) {
      DepositImpl deposit = DepositImpl.createRoot(dbManager);
      ContractCapsule contract = deposit
          .getContract(triggerContractFromTransaction.getContractAddress().toByteArray());
      if (contract == null) {
        logger.info("contract: {} is not in contract store", Wallet
            .encode58Check(triggerContractFromTransaction.getContractAddress().toByteArray()));
        throw new ContractValidateException("contract: " + Wallet
            .encode58Check(triggerContractFromTransaction.getContractAddress().toByteArray())
            + " is not in contract store");
      }
      ABI abi = contract.getInstance().getAbi();
      if (Wallet.isConstant(abi, triggerContractFromTransaction)) {
        throw new VMIllegalException("cannot call constant method");
      }
    }
  }

  //set bill
  public void setBill(long energyUsage) {
    if (energyUsage < 0) {
      energyUsage = 0L;
    }
    receipt.setEnergyUsageTotal(energyUsage);
  }

  //set net bill
  public void setNetBill(long netUsage, long netFee) {
    receipt.setNetUsage(netUsage);
    receipt.setNetFee(netFee);
  }

  public void addNetBill(long netFee) {
    receipt.addNetFee(netFee);
  }

  public void exec()
      throws ContractExeException, ContractValidateException, VMIllegalException {
    /*  VM execute  */
    runtime.execute();
    runtime.go();

    if (XBO_PRECOMPILED_TYPE != runtime.getTrxType()) {
      if (contractResult.OUT_OF_TIME
          .equals(receipt.getResult())) {
        setTimeResultType(TimeResultType.OUT_OF_TIME);
      } else if (System.currentTimeMillis() - txStartTimeInMs
          > Args.getInstance().getLongRunningTime()) {
        setTimeResultType(TimeResultType.LONG_RUNNING);
      }
    }
  }

  public void finalization() throws ContractExeException {
    try {
      pay();
    } catch (BalanceInsufficientException e) {
      throw new ContractExeException(e.getMessage());
    }
    runtime.finalization();
  }

  /**
   * pay actually bill(include ENERGY and storage).
   */
  public void pay() throws BalanceInsufficientException {
    byte[] originAccount;
    byte[] callerAccount;
    long percent = 0;
    long originEnergyLimit = 0;
    switch (xboType) {
      case XBO_CONTRACT_CREATION_TYPE:
        callerAccount = TransactionCapsule.getOwner(xbo.getInstance().getRawData().getContract(0));
        originAccount = callerAccount;
        break;
      case XBO_CONTRACT_CALL_TYPE:
        TriggerSmartContract callContract = ContractCapsule
            .getTriggerContractFromTransaction(xbo.getInstance());
        ContractCapsule contractCapsule =
            dbManager.getContractStore().get(callContract.getContractAddress().toByteArray());

        callerAccount = callContract.getOwnerAddress().toByteArray();
        originAccount = contractCapsule.getOriginAddress();
        percent = Math
            .max(Constant.ONE_HUNDRED - contractCapsule.getConsumeUserResourcePercent(), 0);
        percent = Math.min(percent, Constant.ONE_HUNDRED);
        originEnergyLimit = contractCapsule.getOriginEnergyLimit();
        break;
      default:
        return;
    }

    // originAccount Percent = 30%
    AccountCapsule origin = dbManager.getAccountStore().get(originAccount);
    AccountCapsule caller = dbManager.getAccountStore().get(callerAccount);
    receipt.payEnergyBill(
        dbManager,
        origin,
        caller,
        percent, originEnergyLimit,
        energyProcessor,
        dbManager.getWitnessController().getHeadSlot());
  }

  public boolean checkNeedRetry() {
    if (!needVM()) {
      return false;
    }
    return xbo.getContractRet() != contractResult.OUT_OF_TIME && receipt.getResult()
        == contractResult.OUT_OF_TIME;
  }

  public void check() throws ReceiptCheckErrException {
    if (!needVM()) {
      return;
    }
    if (Objects.isNull(xbo.getContractRet())) {
      throw new ReceiptCheckErrException("null resultCode");
    }
    if (!xbo.getContractRet().equals(receipt.getResult())) {
      logger.info(
          "this tx id: {}, the resultCode in received block: {}, the resultCode in self: {}",
          Hex.toHexString(xbo.getTransactionId().getBytes()), xbo.getContractRet(),
          receipt.getResult());
      throw new ReceiptCheckErrException("Different resultCode");
    }
  }

  public ReceiptCapsule getReceipt() {
    return receipt;
  }

  public void setResult() {
    if (!needVM()) {
      return;
    }
    RuntimeException exception = runtime.getResult().getException();
    if (Objects.isNull(exception) && StringUtils
        .isEmpty(runtime.getRuntimeError()) && !runtime.getResult().isRevert()) {
      receipt.setResult(contractResult.SUCCESS);
      return;
    }
    if (runtime.getResult().isRevert()) {
      receipt.setResult(contractResult.REVERT);
      return;
    }
    if (exception instanceof IllegalOperationException) {
      receipt.setResult(contractResult.ILLEGAL_OPERATION);
      return;
    }
    if (exception instanceof OutOfEnergyException) {
      receipt.setResult(contractResult.OUT_OF_ENERGY);
      return;
    }
    if (exception instanceof BadJumpDestinationException) {
      receipt.setResult(contractResult.BAD_JUMP_DESTINATION);
      return;
    }
    if (exception instanceof OutOfTimeException) {
      receipt.setResult(contractResult.OUT_OF_TIME);
      return;
    }
    if (exception instanceof OutOfMemoryException) {
      receipt.setResult(contractResult.OUT_OF_MEMORY);
      return;
    }
    if (exception instanceof PrecompiledContractException) {
      receipt.setResult(contractResult.PRECOMPILED_CONTRACT);
      return;
    }
    if (exception instanceof StackTooSmallException) {
      receipt.setResult(contractResult.STACK_TOO_SMALL);
      return;
    }
    if (exception instanceof StackTooLargeException) {
      receipt.setResult(contractResult.STACK_TOO_LARGE);
      return;
    }
    if (exception instanceof JVMStackOverFlowException) {
      receipt.setResult(contractResult.JVM_STACK_OVER_FLOW);
      return;
    }
    if (exception instanceof TransferException) {
      receipt.setResult(contractResult.TRANSFER_FAILED);
      return;
    }

    logger.info("uncaught exception", exception);
    receipt.setResult(contractResult.UNKNOWN);
  }

  public String getRuntimeError() {
    return runtime.getRuntimeError();
  }

  public ProgramResult getRuntimeResult() {
    return runtime.getResult();
  }

  public Runtime getRuntime() {
    return runtime;
  }
}
