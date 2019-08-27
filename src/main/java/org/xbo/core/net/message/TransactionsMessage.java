package org.xbo.core.net.message;

import java.util.List;
import org.xbo.core.capsule.TransactionCapsule;
import org.xbo.protos.Protocol;
import org.xbo.protos.Protocol.Transaction;

public class TransactionsMessage extends XBOMessage {

  private Protocol.Transactions transactions;

  public TransactionsMessage(List<Transaction> xbos) {
    Protocol.Transactions.Builder builder = Protocol.Transactions.newBuilder();
    xbos.forEach(xbo -> builder.addTransactions(xbo));
    this.transactions = builder.build();
    this.type = MessageTypes.XBOS.asByte();
    this.data = this.transactions.toByteArray();
  }

  public TransactionsMessage(byte[] data) throws Exception {
    super(data);
    this.type = MessageTypes.XBOS.asByte();
    this.transactions = Protocol.Transactions.parseFrom(getCodedInputStream(data));
    if (isFilter()) {
      compareBytes(data, transactions.toByteArray());
      TransactionCapsule.validContractProto(transactions.getTransactionsList());
    }
  }

  public Protocol.Transactions getTransactions() {
    return transactions;
  }

  @Override
  public String toString() {
    return new StringBuilder().append(super.toString()).append("xbo size: ")
        .append(this.transactions.getTransactionsList().size()).toString();
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

}
