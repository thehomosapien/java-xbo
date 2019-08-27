package org.xbo.core.net.message;

import org.xbo.common.overlay.message.Message;
import org.xbo.common.utils.Sha256Hash;
import org.xbo.core.capsule.TransactionCapsule;
import org.xbo.core.exception.BadItemException;
import org.xbo.core.exception.P2pException;
import org.xbo.protos.Protocol.Transaction;

public class TransactionMessage extends XBOMessage {

  private TransactionCapsule transactionCapsule;

  public TransactionMessage(byte[] data) throws Exception {
    super(data);
    this.transactionCapsule = new TransactionCapsule(getCodedInputStream(data));
    this.type = MessageTypes.XBO.asByte();
    if (Message.isFilter()) {
      compareBytes(data, transactionCapsule.getInstance().toByteArray());
      transactionCapsule
          .validContractProto(transactionCapsule.getInstance().getRawData().getContract(0));
    }
  }

  public TransactionMessage(Transaction xbo) {
    this.transactionCapsule = new TransactionCapsule(xbo);
    this.type = MessageTypes.XBO.asByte();
    this.data = xbo.toByteArray();
  }

  @Override
  public String toString() {
    return new StringBuilder().append(super.toString())
        .append("messageId: ").append(super.getMessageId()).toString();
  }

  @Override
  public Sha256Hash getMessageId() {
    return this.transactionCapsule.getTransactionId();
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

  public TransactionCapsule getTransactionCapsule() {
    return this.transactionCapsule;
  }
}
