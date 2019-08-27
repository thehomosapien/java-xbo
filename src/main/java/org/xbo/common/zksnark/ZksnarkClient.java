package org.xbo.common.zksnark;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannelBuilder;
import org.xbo.api.XBOZksnarkGrpc;
import org.xbo.api.ZksnarkGrpcAPI.ZksnarkRequest;
import org.xbo.api.ZksnarkGrpcAPI.ZksnarkResponse.Code;
import org.xbo.core.capsule.TransactionCapsule;
import org.xbo.protos.Protocol.Transaction;

public class ZksnarkClient {

  public static final ZksnarkClient instance = new ZksnarkClient();

  private XBOZksnarkGrpc.XBOZksnarkBlockingStub blockingStub;

  public ZksnarkClient() {
    blockingStub = XBOZksnarkGrpc.newBlockingStub(ManagedChannelBuilder
        .forTarget("127.0.0.1:60051")
        .usePlaintext()
        .build());
  }

  public boolean checkZksnarkProof(Transaction transaction, byte[] sighash, long valueBalance) {
    String txId = new TransactionCapsule(transaction).getTransactionId().toString();
    ZksnarkRequest request = ZksnarkRequest.newBuilder()
        .setTransaction(transaction)
        .setTxId(txId)
        .setSighash(ByteString.copyFrom(sighash))
        .setValueBalance(valueBalance)
        .build();
    return blockingStub.checkZksnarkProof(request).getCode() == Code.SUCCESS;
  }

  public static ZksnarkClient getInstance() {
    return instance;
  }
}
