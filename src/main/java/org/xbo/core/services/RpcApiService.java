package org.xbo.core.services;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbo.api.DatabaseGrpc.DatabaseImplBase;
import org.xbo.api.GrpcAPI;
import org.xbo.api.GrpcAPI.AccountNetMessage;
import org.xbo.api.GrpcAPI.AccountPaginated;
import org.xbo.api.GrpcAPI.AccountResourceMessage;
import org.xbo.api.GrpcAPI.Address;
import org.xbo.api.GrpcAPI.AddressPrKeyPairMessage;
import org.xbo.api.GrpcAPI.AssetIssueList;
import org.xbo.api.GrpcAPI.BlockExtention;
import org.xbo.api.GrpcAPI.BlockLimit;
import org.xbo.api.GrpcAPI.BlockList;
import org.xbo.api.GrpcAPI.BlockListExtention;
import org.xbo.api.GrpcAPI.BlockReference;
import org.xbo.api.GrpcAPI.BytesMessage;
import org.xbo.api.GrpcAPI.DecryptNotes;
import org.xbo.api.GrpcAPI.DecryptNotesMarked;
import org.xbo.api.GrpcAPI.DelegatedResourceList;
import org.xbo.api.GrpcAPI.DelegatedResourceMessage;
import org.xbo.api.GrpcAPI.DiversifierMessage;
import org.xbo.api.GrpcAPI.EasyTransferAssetByPrivateMessage;
import org.xbo.api.GrpcAPI.EasyTransferAssetMessage;
import org.xbo.api.GrpcAPI.EasyTransferByPrivateMessage;
import org.xbo.api.GrpcAPI.EasyTransferMessage;
import org.xbo.api.GrpcAPI.EasyTransferResponse;
import org.xbo.api.GrpcAPI.EmptyMessage;
import org.xbo.api.GrpcAPI.ExchangeList;
import org.xbo.api.GrpcAPI.ExpandedSpendingKeyMessage;
import org.xbo.api.GrpcAPI.IncomingViewingKeyDiversifierMessage;
import org.xbo.api.GrpcAPI.IncomingViewingKeyMessage;
import org.xbo.api.GrpcAPI.IvkDecryptAndMarkParameters;
import org.xbo.api.GrpcAPI.Node;
import org.xbo.api.GrpcAPI.NodeList;
import org.xbo.api.GrpcAPI.NoteParameters;
import org.xbo.api.GrpcAPI.NumberMessage;
import org.xbo.api.GrpcAPI.PaginatedMessage;
import org.xbo.api.GrpcAPI.PaymentAddressMessage;
import org.xbo.api.GrpcAPI.PrivateParameters;
import org.xbo.api.GrpcAPI.PrivateParametersWithoutAsk;
import org.xbo.api.GrpcAPI.ProposalList;
import org.xbo.api.GrpcAPI.Return;
import org.xbo.api.GrpcAPI.Return.response_code;
import org.xbo.api.GrpcAPI.SpendAuthSigParameters;
import org.xbo.api.GrpcAPI.SpendResult;
import org.xbo.api.GrpcAPI.TransactionApprovedList;
import org.xbo.api.GrpcAPI.TransactionExtention;
import org.xbo.api.GrpcAPI.TransactionList;
import org.xbo.api.GrpcAPI.TransactionListExtention;
import org.xbo.api.GrpcAPI.TransactionSignWeight;
import org.xbo.api.GrpcAPI.ViewingKeyMessage;
import org.xbo.api.GrpcAPI.WitnessList;
import org.xbo.api.WalletExtensionGrpc;
import org.xbo.api.WalletGrpc.WalletImplBase;
import org.xbo.api.WalletSolidityGrpc.WalletSolidityImplBase;
import org.xbo.common.application.Service;
import org.xbo.common.crypto.ECKey;
import org.xbo.common.overlay.discover.node.NodeHandler;
import org.xbo.common.overlay.discover.node.NodeManager;
import org.xbo.common.utils.ByteArray;
import org.xbo.common.utils.Sha256Hash;
import org.xbo.common.utils.StringUtil;
import org.xbo.common.utils.Utils;
import org.xbo.core.Wallet;
import org.xbo.core.WalletSolidity;
import org.xbo.core.capsule.AccountCapsule;
import org.xbo.core.capsule.BlockCapsule;
import org.xbo.core.capsule.TransactionCapsule;
import org.xbo.core.capsule.WitnessCapsule;
import org.xbo.core.config.args.Args;
import org.xbo.core.db.Manager;
import org.xbo.core.exception.BadItemException;
import org.xbo.core.exception.ContractValidateException;
import org.xbo.core.exception.ItemNotFoundException;
import org.xbo.core.exception.NonUniqueObjectException;
import org.xbo.core.exception.StoreException;
import org.xbo.core.exception.VMIllegalException;
import org.xbo.core.services.ratelimiter.RateLimiterInterceptor;
import org.xbo.core.exception.ZksnarkException;
import org.xbo.core.zen.address.DiversifierT;
import org.xbo.core.zen.address.IncomingViewingKey;

import org.xbo.protos.Contract;
import org.xbo.protos.Contract.AccountCreateContract;
import org.xbo.protos.Contract.AccountPermissionUpdateContract;
import org.xbo.protos.Contract.AssetIssueContract;
import org.xbo.protos.Contract.ClearABIContract;
import org.xbo.protos.Contract.IncrementalMerkleVoucherInfo;
import org.xbo.protos.Contract.OutputPointInfo;
import org.xbo.protos.Contract.ParticipateAssetIssueContract;
import org.xbo.protos.Contract.TransferAssetContract;
import org.xbo.protos.Contract.TransferContract;
import org.xbo.protos.Contract.UnfreezeAssetContract;
import org.xbo.protos.Contract.UpdateEnergyLimitContract;
import org.xbo.protos.Contract.UpdateSettingContract;
import org.xbo.protos.Contract.VoteWitnessContract;
import org.xbo.protos.Contract.WitnessCreateContract;
import org.xbo.protos.Protocol;
import org.xbo.protos.Protocol.Account;
import org.xbo.protos.Protocol.Block;
import org.xbo.protos.Protocol.DynamicProperties;
import org.xbo.protos.Protocol.Exchange;
import org.xbo.protos.Protocol.NodeInfo;
import org.xbo.protos.Protocol.Proposal;
import org.xbo.protos.Protocol.Transaction;
import org.xbo.protos.Protocol.Transaction.Contract.ContractType;
import org.xbo.protos.Protocol.TransactionInfo;
import org.xbo.protos.Protocol.TransactionSign;

@Component
@Slf4j(topic = "API")
public class RpcApiService implements Service {

  private static final long BLOCK_LIMIT_NUM = 100;
  private static final long TRANSACTION_LIMIT_NUM = 1000;
  private int port = Args.getInstance().getRpcPort();
  private Server apiServer;
  @Autowired
  private Manager dbManager;
  @Autowired
  private NodeManager nodeManager;
  @Autowired
  private WalletSolidity walletSolidity;
  @Autowired
  private Wallet wallet;
  @Autowired
  private NodeInfoService nodeInfoService;
  @Autowired
  private RateLimiterInterceptor rateLimiterInterceptor;

  @Getter
  private DatabaseApi databaseApi = new DatabaseApi();
  private WalletApi walletApi = new WalletApi();
  @Getter
  private WalletSolidityApi walletSolidityApi = new WalletSolidityApi();

  private static final String CONTRACT_VALIDATE_ERROR = "contract validate error : ";

  @Override
  public void init() {

  }

  @Override
  public void init(Args args) {
  }

  @Override
  public void start() {
    try {
      NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(port).addService(databaseApi);
      Args args = Args.getInstance();

      if (args.getRpcThreadNum() > 0) {
        serverBuilder = serverBuilder
            .executor(Executors.newFixedThreadPool(args.getRpcThreadNum()));
      }

      if (args.isSolidityNode()) {
        serverBuilder = serverBuilder.addService(walletSolidityApi);
        if (args.isWalletExtensionApi()) {
          serverBuilder = serverBuilder.addService(new WalletExtensionApi());
        }
      } else {
        serverBuilder = serverBuilder.addService(walletApi);
      }

      // Set configs from config.conf or default value
      serverBuilder
          .maxConcurrentCallsPerConnection(args.getMaxConcurrentCallsPerConnection())
          .flowControlWindow(args.getFlowControlWindow())
          .maxConnectionIdle(args.getMaxConnectionIdleInMillis(), TimeUnit.MILLISECONDS)
          .maxConnectionAge(args.getMaxConnectionAgeInMillis(), TimeUnit.MILLISECONDS)
          .maxMessageSize(args.getMaxMessageSize())
          .maxHeaderListSize(args.getMaxHeaderListSize());

      // add a ratelimiter interceptor
      serverBuilder.intercept(rateLimiterInterceptor);

      apiServer = serverBuilder.build();
      rateLimiterInterceptor.init(apiServer);

      apiServer.start();
    } catch (IOException e) {
      logger.debug(e.getMessage(), e);
    }

    logger.info("RpcApiService started, listening on " + port);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.err.println("*** shutting down gRPC server since JVM is shutting down");
      //server.this.stop();
      System.err.println("*** server shut down");
    }));
  }

  private TransactionExtention transaction2Extention(Transaction transaction) {
    if (transaction == null) {
      return null;
    }
    TransactionExtention.Builder xboExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();
    xboExtBuilder.setTransaction(transaction);
    xboExtBuilder.setTxid(Sha256Hash.of(transaction.getRawData().toByteArray()).getByteString());
    retBuilder.setResult(true).setCode(response_code.SUCCESS);
    xboExtBuilder.setResult(retBuilder);
    return xboExtBuilder.build();
  }

  private BlockExtention block2Extention(Block block) {
    if (block == null) {
      return null;
    }
    BlockExtention.Builder builder = BlockExtention.newBuilder();
    BlockCapsule blockCapsule = new BlockCapsule(block);
    builder.setBlockHeader(block.getBlockHeader());
    builder.setBlockid(ByteString.copyFrom(blockCapsule.getBlockId().getBytes()));
    for (int i = 0; i < block.getTransactionsCount(); i++) {
      Transaction transaction = block.getTransactions(i);
      builder.addTransactions(transaction2Extention(transaction));
    }
    return builder.build();
  }

  private StatusRuntimeException getRunTimeException(Exception e) {
    if (e != null ) {
      return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
    } else {
      return Status.INTERNAL.withDescription("unknown").asRuntimeException();
    }
  }

  @Override
  public void stop() {
    if (apiServer != null) {
      apiServer.shutdown();
    }
  }

  /**
   * ...
   */
  public void blockUntilShutdown() {
    if (apiServer != null) {
      try {
        apiServer.awaitTermination();
      } catch (InterruptedException e) {
        logger.warn("{}", e);
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * DatabaseApi.
   */
  public class DatabaseApi extends DatabaseImplBase {

    @Override
    public void getBlockReference(org.xbo.api.GrpcAPI.EmptyMessage request,
        io.grpc.stub.StreamObserver<org.xbo.api.GrpcAPI.BlockReference> responseObserver) {
      long headBlockNum = dbManager.getDynamicPropertiesStore()
          .getLatestBlockHeaderNumber();
      byte[] blockHeaderHash = dbManager.getDynamicPropertiesStore()
          .getLatestBlockHeaderHash().getBytes();
      BlockReference ref = BlockReference.newBuilder()
          .setBlockHash(ByteString.copyFrom(blockHeaderHash))
          .setBlockNum(headBlockNum)
          .build();
      responseObserver.onNext(ref);
      responseObserver.onCompleted();
    }

    @Override
    public void getNowBlock(EmptyMessage request, StreamObserver<Block> responseObserver) {
      Block block = null;
      try {
        block = dbManager.getHead().getInstance();
      } catch (StoreException e) {
        logger.error(e.getMessage());
      }
      responseObserver.onNext(block);
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum(NumberMessage request, StreamObserver<Block> responseObserver) {
      Block block = null;
      try {
        block = dbManager.getBlockByNum(request.getNum()).getInstance();
      } catch (StoreException e) {
        logger.error(e.getMessage());
      }
      responseObserver.onNext(block);
      responseObserver.onCompleted();
    }

    @Override
    public void getDynamicProperties(EmptyMessage request,
        StreamObserver<DynamicProperties> responseObserver) {
      DynamicProperties.Builder builder = DynamicProperties.newBuilder();
      builder.setLastSolidityBlockNum(
          dbManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum());
      DynamicProperties dynamicProperties = builder.build();
      responseObserver.onNext(dynamicProperties);
      responseObserver.onCompleted();
    }
  }

  /**
   * WalletSolidityApi.
   */
  public class WalletSolidityApi extends WalletSolidityImplBase {

    @Override
    public void getAccount(Account request, StreamObserver<Account> responseObserver) {
      ByteString addressBs = request.getAddress();
      if (addressBs != null) {
        Account reply = wallet.getAccount(request);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAccountById(Account request, StreamObserver<Account> responseObserver) {
      ByteString id = request.getAccountId();
      if (id != null) {
        Account reply = wallet.getAccountById(request);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void listWitnesses(EmptyMessage request, StreamObserver<WitnessList> responseObserver) {
      responseObserver.onNext(wallet.getWitnessList());
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueList(EmptyMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      responseObserver.onNext(wallet.getAssetIssueList());
      responseObserver.onCompleted();
    }

    @Override
    public void getPaginatedAssetIssueList(PaginatedMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      responseObserver.onNext(wallet.getAssetIssueList(request.getOffset(), request.getLimit()));
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueByName(BytesMessage request,
        StreamObserver<AssetIssueContract> responseObserver) {
      ByteString assetName = request.getValue();
      if (assetName != null) {
        try {
          responseObserver.onNext(wallet.getAssetIssueByName(assetName));
        } catch (NonUniqueObjectException e) {
          responseObserver.onNext(null);
          logger.error("Solidity NonUniqueObjectException: {}", e.getMessage());
        }
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueListByName(BytesMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      ByteString assetName = request.getValue();

      if (assetName != null) {
        responseObserver.onNext(wallet.getAssetIssueListByName(assetName));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueById(BytesMessage request,
        StreamObserver<AssetIssueContract> responseObserver) {
      ByteString assetId = request.getValue();

      if (assetId != null) {
        responseObserver.onNext(wallet.getAssetIssueById(assetId.toStringUtf8()));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getNowBlock(EmptyMessage request, StreamObserver<Block> responseObserver) {
      responseObserver.onNext(wallet.getNowBlock());
      responseObserver.onCompleted();
    }

    @Override
    public void getNowBlock2(EmptyMessage request,
        StreamObserver<BlockExtention> responseObserver) {
      responseObserver.onNext(block2Extention(wallet.getNowBlock()));
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum(NumberMessage request, StreamObserver<Block> responseObserver) {
      long num = request.getNum();
      if (num >= 0) {
        Block reply = wallet.getBlockByNum(num);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum2(NumberMessage request,
        StreamObserver<BlockExtention> responseObserver) {
      long num = request.getNum();
      if (num >= 0) {
        Block reply = wallet.getBlockByNum(num);
        responseObserver.onNext(block2Extention(reply));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }


    @Override
    public void getDelegatedResource(DelegatedResourceMessage request,
        StreamObserver<DelegatedResourceList> responseObserver) {
      responseObserver
          .onNext(wallet.getDelegatedResource(request.getFromAddress(), request.getToAddress()));
      responseObserver.onCompleted();
    }

    @Override
    public void getDelegatedResourceAccountIndex(BytesMessage request,
        StreamObserver<org.xbo.protos.Protocol.DelegatedResourceAccountIndex> responseObserver) {
      responseObserver
          .onNext(wallet.getDelegatedResourceAccountIndex(request.getValue()));
      responseObserver.onCompleted();
    }

    @Override
    public void getExchangeById(BytesMessage request,
        StreamObserver<Exchange> responseObserver) {
      ByteString exchangeId = request.getValue();

      if (Objects.nonNull(exchangeId)) {
        responseObserver.onNext(wallet.getExchangeById(exchangeId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void listExchanges(EmptyMessage request,
        StreamObserver<ExchangeList> responseObserver) {
      responseObserver.onNext(wallet.getExchangeList());
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionCountByBlockNum(NumberMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      NumberMessage.Builder builder = NumberMessage.newBuilder();
      try {
        Block block = dbManager.getBlockByNum(request.getNum()).getInstance();
        builder.setNum(block.getTransactionsCount());
      } catch (StoreException e) {
        logger.error(e.getMessage());
        builder.setNum(-1);
      }
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionById(BytesMessage request,
        StreamObserver<Transaction> responseObserver) {
      ByteString id = request.getValue();
      if (null != id) {
        Transaction reply = wallet.getTransactionById(id);

        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionInfoById(BytesMessage request,
        StreamObserver<TransactionInfo> responseObserver) {
      ByteString id = request.getValue();
      if (null != id) {
        TransactionInfo reply = wallet.getTransactionInfoById(id);

        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void generateAddress(EmptyMessage request,
        StreamObserver<GrpcAPI.AddressPrKeyPairMessage> responseObserver) {
      ECKey ecKey = new ECKey(Utils.getRandom());
      byte[] priKey = ecKey.getPrivKeyBytes();
      byte[] address = ecKey.getAddress();
      String addressStr = Wallet.encode58Check(address);
      String priKeyStr = Hex.encodeHexString(priKey);
      AddressPrKeyPairMessage.Builder builder = AddressPrKeyPairMessage.newBuilder();
      builder.setAddress(addressStr);
      builder.setPrivateKey(priKeyStr);
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getMerkleTreeVoucherInfo(OutputPointInfo request,
        StreamObserver<IncrementalMerkleVoucherInfo> responseObserver) {

      try {
        IncrementalMerkleVoucherInfo witnessInfo = wallet
            .getMerkleTreeVoucherInfo(request);
        responseObserver.onNext(witnessInfo);
      } catch (Exception ex) {
        responseObserver.onError(getRunTimeException(ex));
      }

      responseObserver.onCompleted();
    }

    @Override
    public void scanNoteByIvk(GrpcAPI.IvkDecryptParameters request,
        StreamObserver<GrpcAPI.DecryptNotes> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();
      try {
        DecryptNotes decryptNotes = wallet
            .scanNoteByIvk(startNum, endNum, request.getIvk().toByteArray());
        responseObserver.onNext(decryptNotes);
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }
  
    @Override
    public void scanAndMarkNoteByIvk(GrpcAPI.IvkDecryptAndMarkParameters request,
        StreamObserver<GrpcAPI.DecryptNotesMarked> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();
      try {
        DecryptNotesMarked decryptNotes = wallet.scanAndMarkNoteByIvk(startNum, endNum,
                request.getIvk().toByteArray(),
                request.getAk().toByteArray(),
                request.getNk().toByteArray());
        responseObserver.onNext(decryptNotes);
      } catch (BadItemException | ZksnarkException | InvalidProtocolBufferException
              | ItemNotFoundException e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void scanNoteByOvk(GrpcAPI.OvkDecryptParameters request,
        StreamObserver<GrpcAPI.DecryptNotes> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();
      try {
        DecryptNotes decryptNotes = wallet
            .scanNoteByOvk(startNum, endNum, request.getOvk().toByteArray());
        responseObserver.onNext(decryptNotes);
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void isSpend(NoteParameters request, StreamObserver<SpendResult> responseObserver) {
      try {
        responseObserver.onNext(wallet.isSpend(request));
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }
  }

  /**
   * WalletExtensionApi.
   */
  public class WalletExtensionApi extends WalletExtensionGrpc.WalletExtensionImplBase {

    private TransactionListExtention transactionList2Extention(TransactionList transactionList) {
      if (transactionList == null) {
        return null;
      }
      TransactionListExtention.Builder builder = TransactionListExtention.newBuilder();
      for (Transaction transaction : transactionList.getTransactionList()) {
        builder.addTransaction(transaction2Extention(transaction));
      }
      return builder.build();
    }

    @Override
    public void getTransactionsFromThis(AccountPaginated request,
        StreamObserver<TransactionList> responseObserver) {
      ByteString thisAddress = request.getAccount().getAddress();
      long offset = request.getOffset();
      long limit = request.getLimit();
      if (null != thisAddress && offset >= 0 && limit >= 0) {
        TransactionList reply = walletSolidity
            .getTransactionsFromThis(thisAddress, offset, limit);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionsFromThis2(AccountPaginated request,
        StreamObserver<TransactionListExtention> responseObserver) {
      ByteString thisAddress = request.getAccount().getAddress();
      long offset = request.getOffset();
      long limit = request.getLimit();
      if (null != thisAddress && offset >= 0 && limit >= 0) {
        TransactionList reply = walletSolidity
            .getTransactionsFromThis(thisAddress, offset, limit);
        responseObserver.onNext(transactionList2Extention(reply));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionsToThis(AccountPaginated request,
        StreamObserver<TransactionList> responseObserver) {
      ByteString toAddress = request.getAccount().getAddress();
      long offset = request.getOffset();
      long limit = request.getLimit();
      if (null != toAddress && offset >= 0 && limit >= 0) {
        TransactionList reply = walletSolidity
            .getTransactionsToThis(toAddress, offset, limit);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionsToThis2(AccountPaginated request,
        StreamObserver<TransactionListExtention> responseObserver) {
      ByteString toAddress = request.getAccount().getAddress();
      long offset = request.getOffset();
      long limit = request.getLimit();
      if (null != toAddress && offset >= 0 && limit >= 0) {
        TransactionList reply = walletSolidity
            .getTransactionsToThis(toAddress, offset, limit);
        responseObserver.onNext(transactionList2Extention(reply));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }
  }

  /**
   * WalletApi.
   */
  public class WalletApi extends WalletImplBase {

    private BlockListExtention blocklist2Extention(BlockList blockList) {
      if (blockList == null) {
        return null;
      }
      BlockListExtention.Builder builder = BlockListExtention.newBuilder();
      for (Block block : blockList.getBlockList()) {
        builder.addBlock(block2Extention(block));
      }
      return builder.build();
    }

    @Override
    public void getAccount(Account req, StreamObserver<Account> responseObserver) {
      ByteString addressBs = req.getAddress();
      if (addressBs != null) {
        Account reply = wallet.getAccount(req);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAccountById(Account req, StreamObserver<Account> responseObserver) {
      ByteString accountId = req.getAccountId();
      if (accountId != null) {
        Account reply = wallet.getAccountById(req);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createTransaction(TransferContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver
            .onNext(
                createTransactionCapsule(request, ContractType.TransferContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug("ContractValidateException: {}", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createTransaction2(TransferContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.TransferContract, responseObserver);
    }

    private void createTransactionExtention(Message request, ContractType contractType,
        StreamObserver<TransactionExtention> responseObserver) {
      TransactionExtention.Builder xboExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();
      try {
        TransactionCapsule xbo = createTransactionCapsule(request, contractType);
        xboExtBuilder.setTransaction(xbo.getInstance());
        xboExtBuilder.setTxid(xbo.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
      } catch (ContractValidateException e) {
        retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString.copyFromUtf8(CONTRACT_VALIDATE_ERROR + e.getMessage()));
        logger.debug("ContractValidateException: {}", e.getMessage());
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        logger.info("exception caught" + e.getMessage());
      }
      xboExtBuilder.setResult(retBuilder);
      responseObserver.onNext(xboExtBuilder.build());
      responseObserver.onCompleted();
    }

    private TransactionCapsule createTransactionCapsule(com.google.protobuf.Message message,
        ContractType contractType) throws ContractValidateException {
      return wallet.createTransactionCapsule(message, contractType);
    }

    @Override
    public void getTransactionSign(TransactionSign req,
        StreamObserver<Transaction> responseObserver) {
      TransactionCapsule retur = wallet.getTransactionSign(req);
      responseObserver.onNext(retur.getInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionSign2(TransactionSign req,
        StreamObserver<TransactionExtention> responseObserver) {
      TransactionExtention.Builder xboExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();
      try {
        TransactionCapsule xbo = wallet.getTransactionSign(req);
        xboExtBuilder.setTransaction(xbo.getInstance());
        xboExtBuilder.setTxid(xbo.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        logger.info("exception caught" + e.getMessage());
      }
      xboExtBuilder.setResult(retBuilder);
      responseObserver.onNext(xboExtBuilder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void addSign(TransactionSign req,
        StreamObserver<TransactionExtention> responseObserver) {
      TransactionExtention.Builder xboExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();
      try {
        TransactionCapsule xbo = wallet.addSign(req);
        xboExtBuilder.setTransaction(xbo.getInstance());
        xboExtBuilder.setTxid(xbo.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        logger.info("exception caught" + e.getMessage());
      }
      xboExtBuilder.setResult(retBuilder);
      responseObserver.onNext(xboExtBuilder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionSignWeight(Transaction req,
        StreamObserver<TransactionSignWeight> responseObserver) {
      TransactionSignWeight tsw = wallet.getTransactionSignWeight(req);
      responseObserver.onNext(tsw);
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionApprovedList(Transaction req,
        StreamObserver<TransactionApprovedList> responseObserver) {
      TransactionApprovedList tal = wallet.getTransactionApprovedList(req);
      responseObserver.onNext(tal);
      responseObserver.onCompleted();
    }

    @Override
    public void createAddress(BytesMessage req,
        StreamObserver<BytesMessage> responseObserver) {
      byte[] address = wallet.createAdresss(req.getValue().toByteArray());
      BytesMessage.Builder builder = BytesMessage.newBuilder();
      builder.setValue(ByteString.copyFrom(address));
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    private EasyTransferResponse easyTransfer(byte[] privateKey, ByteString toAddress,
        long amount) {
      TransactionCapsule transactionCapsule;
      GrpcAPI.Return.Builder returnBuilder = GrpcAPI.Return.newBuilder();
      EasyTransferResponse.Builder responseBuild = EasyTransferResponse.newBuilder();
      try {
        ECKey ecKey = ECKey.fromPrivate(privateKey);
        byte[] owner = ecKey.getAddress();
        TransferContract.Builder builder = TransferContract.newBuilder();
        builder.setOwnerAddress(ByteString.copyFrom(owner));
        builder.setToAddress(toAddress);
        builder.setAmount(amount);
        transactionCapsule = createTransactionCapsule(builder.build(),
            ContractType.TransferContract);
        transactionCapsule.sign(privateKey);
        GrpcAPI.Return retur = wallet.broadcastTransaction(transactionCapsule.getInstance());
        responseBuild.setTransaction(transactionCapsule.getInstance());
        responseBuild.setTxid(transactionCapsule.getTransactionId().getByteString());
        responseBuild.setResult(retur);
      } catch (ContractValidateException e) {
        returnBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getMessage()));
        responseBuild.setResult(returnBuilder.build());
      } catch (Exception e) {
        returnBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        responseBuild.setResult(returnBuilder.build());
      }

      return responseBuild.build();
    }

    private EasyTransferResponse easyTransferAsset(byte[] privateKey, ByteString toAddress,
        String assetId, long amount) {
      TransactionCapsule transactionCapsule;
      GrpcAPI.Return.Builder returnBuilder = GrpcAPI.Return.newBuilder();
      EasyTransferResponse.Builder responseBuild = EasyTransferResponse.newBuilder();
      try {
        ECKey ecKey = ECKey.fromPrivate(privateKey);
        byte[] owner = ecKey.getAddress();
        TransferAssetContract.Builder builder = TransferAssetContract.newBuilder();
        builder.setOwnerAddress(ByteString.copyFrom(owner));
        builder.setToAddress(toAddress);
        builder.setAssetName(ByteString.copyFrom(assetId.getBytes()));
        builder.setAmount(amount);
        transactionCapsule = createTransactionCapsule(builder.build(),
            ContractType.TransferAssetContract);
        transactionCapsule.sign(privateKey);
        GrpcAPI.Return retur = wallet.broadcastTransaction(transactionCapsule.getInstance());
        responseBuild.setTransaction(transactionCapsule.getInstance());
        responseBuild.setTxid(transactionCapsule.getTransactionId().getByteString());
        responseBuild.setResult(retur);
      } catch (ContractValidateException e) {
        returnBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getMessage()));
        responseBuild.setResult(returnBuilder.build());
      } catch (Exception e) {
        returnBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        responseBuild.setResult(returnBuilder.build());
      }

      return responseBuild.build();
    }

    @Override
    public void easyTransfer(EasyTransferMessage req,
        StreamObserver<EasyTransferResponse> responseObserver) {
      byte[] privateKey = wallet.pass2Key(req.getPassPhrase().toByteArray());
      EasyTransferResponse response = easyTransfer(privateKey, req.getToAddress(), req.getAmount());
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void easyTransferAsset(EasyTransferAssetMessage req,
        StreamObserver<EasyTransferResponse> responseObserver) {
      byte[] privateKey = wallet.pass2Key(req.getPassPhrase().toByteArray());
      EasyTransferResponse response = easyTransferAsset(privateKey, req.getToAddress(),
          req.getAssetId(), req.getAmount());
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void easyTransferByPrivate(EasyTransferByPrivateMessage req,
        StreamObserver<EasyTransferResponse> responseObserver) {
      byte[] privateKey = req.getPrivateKey().toByteArray();
      EasyTransferResponse response = easyTransfer(privateKey, req.getToAddress(), req.getAmount());
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void easyTransferAssetByPrivate(EasyTransferAssetByPrivateMessage req,
        StreamObserver<EasyTransferResponse> responseObserver) {
      byte[] privateKey = req.getPrivateKey().toByteArray();
      EasyTransferResponse response = easyTransferAsset(privateKey, req.getToAddress(),
          req.getAssetId(), req.getAmount());
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void broadcastTransaction(Transaction req,
        StreamObserver<GrpcAPI.Return> responseObserver) {
      GrpcAPI.Return retur = wallet.broadcastTransaction(req);
      responseObserver.onNext(retur);
      responseObserver.onCompleted();
    }

    @Override
    public void createAssetIssue(AssetIssueContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.AssetIssueContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug("ContractValidateException: {}", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createAssetIssue2(AssetIssueContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.AssetIssueContract, responseObserver);
    }

    @Override
    public void unfreezeAsset(UnfreezeAssetContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.UnfreezeAssetContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug("ContractValidateException: {}", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void unfreezeAsset2(UnfreezeAssetContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UnfreezeAssetContract, responseObserver);
    }

    //refactor、test later
    private void checkVoteWitnessAccount(VoteWitnessContract req) {
      //send back to cli
      ByteString ownerAddress = req.getOwnerAddress();
      Preconditions.checkNotNull(ownerAddress, "OwnerAddress is null");

      AccountCapsule account = dbManager.getAccountStore().get(ownerAddress.toByteArray());
      Preconditions.checkNotNull(account,
          "OwnerAddress[" + StringUtil.createReadableString(ownerAddress) + "] not exists");

      int votesCount = req.getVotesCount();
      Preconditions.checkArgument(votesCount <= 0, "VotesCount[" + votesCount + "] <= 0");
      Preconditions.checkArgument(account.getXBOPower() < votesCount,
          "xbo power[" + account.getXBOPower() + "] <  VotesCount[" + votesCount + "]");

      req.getVotesList().forEach(vote -> {
        ByteString voteAddress = vote.getVoteAddress();
        WitnessCapsule witness = dbManager.getWitnessStore()
            .get(voteAddress.toByteArray());
        String readableWitnessAddress = StringUtil.createReadableString(voteAddress);

        Preconditions.checkNotNull(witness, "witness[" + readableWitnessAddress + "] not exists");
        Preconditions.checkArgument(vote.getVoteCount() <= 0,
            "VoteAddress[" + readableWitnessAddress + "],VotesCount[" + vote
                .getVoteCount() + "] <= 0");
      });
    }

    @Override
    public void voteWitnessAccount(VoteWitnessContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.VoteWitnessContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug("ContractValidateException: {}", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void voteWitnessAccount2(VoteWitnessContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.VoteWitnessContract, responseObserver);
    }

    @Override
    public void updateSetting(UpdateSettingContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UpdateSettingContract,
          responseObserver);
    }

    @Override
    public void updateEnergyLimit(UpdateEnergyLimitContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UpdateEnergyLimitContract,
          responseObserver);
    }

    @Override
    public void clearContractABI(ClearABIContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ClearABIContract,
          responseObserver);
    }

    @Override
    public void createWitness(WitnessCreateContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.WitnessCreateContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug("ContractValidateException: {}", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createWitness2(WitnessCreateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.WitnessCreateContract, responseObserver);
    }

    @Override
    public void createAccount(AccountCreateContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.AccountCreateContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug("ContractValidateException: {}", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createAccount2(AccountCreateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.AccountCreateContract, responseObserver);
    }

    @Override
    public void updateWitness(Contract.WitnessUpdateContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.WitnessUpdateContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug("ContractValidateException: {}", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void updateWitness2(Contract.WitnessUpdateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.WitnessUpdateContract, responseObserver);
    }

    @Override
    public void updateAccount(Contract.AccountUpdateContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.AccountUpdateContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug("ContractValidateException: {}", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void setAccountId(Contract.SetAccountIdContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.SetAccountIdContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug("ContractValidateException: {}", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void updateAccount2(Contract.AccountUpdateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.AccountUpdateContract, responseObserver);
    }

    @Override
    public void updateAsset(Contract.UpdateAssetContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request,
                ContractType.UpdateAssetContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug("ContractValidateException", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void updateAsset2(Contract.UpdateAssetContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UpdateAssetContract, responseObserver);
    }

    @Override
    public void freezeBalance(Contract.FreezeBalanceContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.FreezeBalanceContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug("ContractValidateException: {}", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void freezeBalance2(Contract.FreezeBalanceContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.FreezeBalanceContract, responseObserver);
    }

    @Override
    public void unfreezeBalance(Contract.UnfreezeBalanceContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.UnfreezeBalanceContract)
                .getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug("ContractValidateException: {}", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void unfreezeBalance2(Contract.UnfreezeBalanceContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UnfreezeBalanceContract, responseObserver);
    }

    @Override
    public void withdrawBalance(Contract.WithdrawBalanceContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.WithdrawBalanceContract)
                .getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug("ContractValidateException: {}", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void withdrawBalance2(Contract.WithdrawBalanceContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.WithdrawBalanceContract, responseObserver);
    }

    @Override
    public void proposalCreate(Contract.ProposalCreateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ProposalCreateContract, responseObserver);
    }


    @Override
    public void proposalApprove(Contract.ProposalApproveContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ProposalApproveContract, responseObserver);
    }

    @Override
    public void proposalDelete(Contract.ProposalDeleteContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ProposalDeleteContract, responseObserver);
    }

//    @Override
//    public void buyStorage(Contract.BuyStorageContract request,
//        StreamObserver<TransactionExtention> responseObserver) {
//      createTransactionExtention(request, ContractType.BuyStorageContract, responseObserver);
//    }
//
//    @Override
//    public void buyStorageBytes(Contract.BuyStorageBytesContract request,
//        StreamObserver<TransactionExtention> responseObserver) {
//      createTransactionExtention(request, ContractType.BuyStorageBytesContract, responseObserver);
//    }
//
//    @Override
//    public void sellStorage(Contract.SellStorageContract request,
//        StreamObserver<TransactionExtention> responseObserver) {
//      createTransactionExtention(request, ContractType.SellStorageContract, responseObserver);
//    }

    @Override
    public void exchangeCreate(Contract.ExchangeCreateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ExchangeCreateContract, responseObserver);
    }


    @Override
    public void exchangeInject(Contract.ExchangeInjectContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ExchangeInjectContract, responseObserver);
    }

    @Override
    public void exchangeWithdraw(Contract.ExchangeWithdrawContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ExchangeWithdrawContract, responseObserver);
    }

    @Override
    public void exchangeTransaction(Contract.ExchangeTransactionContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ExchangeTransactionContract,
          responseObserver);
    }

    @Override
    public void getNowBlock(EmptyMessage request, StreamObserver<Block> responseObserver) {
      responseObserver.onNext(wallet.getNowBlock());
      responseObserver.onCompleted();
    }

    @Override
    public void getNowBlock2(EmptyMessage request,
        StreamObserver<BlockExtention> responseObserver) {
      Block block = wallet.getNowBlock();
      responseObserver.onNext(block2Extention(block));
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum(NumberMessage request, StreamObserver<Block> responseObserver) {
      responseObserver.onNext(wallet.getBlockByNum(request.getNum()));
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum2(NumberMessage request,
        StreamObserver<BlockExtention> responseObserver) {
      Block block = wallet.getBlockByNum(request.getNum());
      responseObserver.onNext(block2Extention(block));
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionCountByBlockNum(NumberMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      NumberMessage.Builder builder = NumberMessage.newBuilder();
      try {
        Block block = dbManager.getBlockByNum(request.getNum()).getInstance();
        builder.setNum(block.getTransactionsCount());
      } catch (StoreException e) {
        logger.error(e.getMessage());
        builder.setNum(-1);
      }
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void listNodes(EmptyMessage request, StreamObserver<NodeList> responseObserver) {
      List<NodeHandler> handlerList = nodeManager.dumpActiveNodes();

      Map<String, NodeHandler> nodeHandlerMap = new HashMap<>();
      for (NodeHandler handler : handlerList) {
        String key = handler.getNode().getHexId() + handler.getNode().getHost();
        nodeHandlerMap.put(key, handler);
      }

      NodeList.Builder nodeListBuilder = NodeList.newBuilder();

      nodeHandlerMap.entrySet().stream()
          .forEach(v -> {
            org.xbo.common.overlay.discover.node.Node node = v.getValue().getNode();
            nodeListBuilder.addNodes(Node.newBuilder().setAddress(
                Address.newBuilder()
                    .setHost(ByteString.copyFrom(ByteArray.fromString(node.getHost())))
                    .setPort(node.getPort())));
          });

      responseObserver.onNext(nodeListBuilder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void transferAsset(TransferAssetContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver
            .onNext(createTransactionCapsule(request, ContractType.TransferAssetContract)
                .getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug("ContractValidateException: {}", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void transferAsset2(TransferAssetContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.TransferAssetContract, responseObserver);
    }

    @Override
    public void participateAssetIssue(ParticipateAssetIssueContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver
            .onNext(createTransactionCapsule(request, ContractType.ParticipateAssetIssueContract)
                .getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug("ContractValidateException: {}", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void participateAssetIssue2(ParticipateAssetIssueContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ParticipateAssetIssueContract,
          responseObserver);
    }

    @Override
    public void getAssetIssueByAccount(Account request,
        StreamObserver<AssetIssueList> responseObserver) {
      ByteString fromBs = request.getAddress();

      if (fromBs != null) {
        responseObserver.onNext(wallet.getAssetIssueByAccount(fromBs));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAccountNet(Account request,
        StreamObserver<AccountNetMessage> responseObserver) {
      ByteString fromBs = request.getAddress();

      if (fromBs != null) {
        responseObserver.onNext(wallet.getAccountNet(fromBs));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAccountResource(Account request,
        StreamObserver<AccountResourceMessage> responseObserver) {
      ByteString fromBs = request.getAddress();

      if (fromBs != null) {
        responseObserver.onNext(wallet.getAccountResource(fromBs));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueByName(BytesMessage request,
        StreamObserver<AssetIssueContract> responseObserver) {
      ByteString assetName = request.getValue();
      if (assetName != null) {
        try {
          responseObserver.onNext(wallet.getAssetIssueByName(assetName));
        } catch (NonUniqueObjectException e) {
          responseObserver.onNext(null);
          logger.debug("FullNode NonUniqueObjectException: {}", e.getMessage());
        }
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueListByName(BytesMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      ByteString assetName = request.getValue();

      if (assetName != null) {
        responseObserver.onNext(wallet.getAssetIssueListByName(assetName));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueById(BytesMessage request,
        StreamObserver<AssetIssueContract> responseObserver) {
      ByteString assetId = request.getValue();

      if (assetId != null) {
        responseObserver.onNext(wallet.getAssetIssueById(assetId.toStringUtf8()));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockById(BytesMessage request, StreamObserver<Block> responseObserver) {
      ByteString blockId = request.getValue();

      if (Objects.nonNull(blockId)) {
        responseObserver.onNext(wallet.getBlockById(blockId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getProposalById(BytesMessage request,
        StreamObserver<Proposal> responseObserver) {
      ByteString proposalId = request.getValue();

      if (Objects.nonNull(proposalId)) {
        responseObserver.onNext(wallet.getProposalById(proposalId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getExchangeById(BytesMessage request,
        StreamObserver<Exchange> responseObserver) {
      ByteString exchangeId = request.getValue();

      if (Objects.nonNull(exchangeId)) {
        responseObserver.onNext(wallet.getExchangeById(exchangeId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByLimitNext(BlockLimit request,
        StreamObserver<BlockList> responseObserver) {
      long startNum = request.getStartNum();
      long endNum = request.getEndNum();

      if (endNum > 0 && endNum > startNum && endNum - startNum <= BLOCK_LIMIT_NUM) {
        responseObserver.onNext(wallet.getBlocksByLimitNext(startNum, endNum - startNum));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByLimitNext2(BlockLimit request,
        StreamObserver<BlockListExtention> responseObserver) {
      long startNum = request.getStartNum();
      long endNum = request.getEndNum();

      if (endNum > 0 && endNum > startNum && endNum - startNum <= BLOCK_LIMIT_NUM) {
        responseObserver
            .onNext(blocklist2Extention(wallet.getBlocksByLimitNext(startNum, endNum - startNum)));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByLatestNum(NumberMessage request,
        StreamObserver<BlockList> responseObserver) {
      long getNum = request.getNum();

      if (getNum > 0 && getNum < BLOCK_LIMIT_NUM) {
        responseObserver.onNext(wallet.getBlockByLatestNum(getNum));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByLatestNum2(NumberMessage request,
        StreamObserver<BlockListExtention> responseObserver) {
      long getNum = request.getNum();

      if (getNum > 0 && getNum < BLOCK_LIMIT_NUM) {
        responseObserver.onNext(blocklist2Extention(wallet.getBlockByLatestNum(getNum)));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionById(BytesMessage request,
        StreamObserver<Transaction> responseObserver) {
      ByteString transactionId = request.getValue();

      if (Objects.nonNull(transactionId)) {
        responseObserver.onNext(wallet.getTransactionById(transactionId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void deployContract(org.xbo.protos.Contract.CreateSmartContract request,
        io.grpc.stub.StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.CreateSmartContract, responseObserver);
    }

    public void totalTransaction(EmptyMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      responseObserver.onNext(wallet.totalTransaction());
      responseObserver.onCompleted();
    }

    @Override
    public void getNextMaintenanceTime(EmptyMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      responseObserver.onNext(wallet.getNextMaintenanceTime());
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueList(EmptyMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      responseObserver.onNext(wallet.getAssetIssueList());
      responseObserver.onCompleted();
    }

    @Override
    public void triggerContract(Contract.TriggerSmartContract request,
        StreamObserver<TransactionExtention> responseObserver) {

      callContract(request, responseObserver, false);
    }

    @Override
    public void triggerConstantContract(Contract.TriggerSmartContract request,
        StreamObserver<TransactionExtention> responseObserver) {

      callContract(request, responseObserver, true);
    }

    private void callContract(Contract.TriggerSmartContract request,
        StreamObserver<TransactionExtention> responseObserver, boolean isConstant) {
      TransactionExtention.Builder xboExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();
      try {
        TransactionCapsule xboCap = createTransactionCapsule(request,
            ContractType.TriggerSmartContract);
        Transaction xbo;
        if (isConstant) {
          xbo = wallet.triggerConstantContract(request, xboCap, xboExtBuilder, retBuilder);
        } else {
          xbo = wallet.triggerContract(request, xboCap, xboExtBuilder, retBuilder);
        }
        xboExtBuilder.setTransaction(xbo);
        xboExtBuilder.setTxid(xboCap.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
        xboExtBuilder.setResult(retBuilder);
      } catch (ContractValidateException | VMIllegalException e) {
        retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString.copyFromUtf8(CONTRACT_VALIDATE_ERROR + e.getMessage()));
        xboExtBuilder.setResult(retBuilder);
        logger.warn("ContractValidateException: {}", e.getMessage());
      } catch (RuntimeException e) {
        retBuilder.setResult(false).setCode(response_code.CONTRACT_EXE_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        xboExtBuilder.setResult(retBuilder);
        logger.warn("When run constant call in VM, have RuntimeException: " + e.getMessage());
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        xboExtBuilder.setResult(retBuilder);
        logger.warn("unknown exception caught: " + e.getMessage(), e);
      } finally {
        responseObserver.onNext(xboExtBuilder.build());
        responseObserver.onCompleted();
      }
    }

    public void getPaginatedAssetIssueList(PaginatedMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      responseObserver.onNext(wallet.getAssetIssueList(request.getOffset(), request.getLimit()));
      responseObserver.onCompleted();
    }

    @Override
    public void getContract(BytesMessage request,
        StreamObserver<Protocol.SmartContract> responseObserver) {
      Protocol.SmartContract contract = wallet.getContract(request);
      responseObserver.onNext(contract);
      responseObserver.onCompleted();
    }

    public void listWitnesses(EmptyMessage request,
        StreamObserver<WitnessList> responseObserver) {
      responseObserver.onNext(wallet.getWitnessList());
      responseObserver.onCompleted();
    }

    @Override
    public void listProposals(EmptyMessage request,
        StreamObserver<ProposalList> responseObserver) {
      responseObserver.onNext(wallet.getProposalList());
      responseObserver.onCompleted();
    }


    @Override
    public void getDelegatedResource(DelegatedResourceMessage request,
        StreamObserver<DelegatedResourceList> responseObserver) {
      responseObserver
          .onNext(wallet.getDelegatedResource(request.getFromAddress(), request.getToAddress()));
      responseObserver.onCompleted();
    }

    public void getDelegatedResourceAccountIndex(BytesMessage request,
        StreamObserver<org.xbo.protos.Protocol.DelegatedResourceAccountIndex> responseObserver) {
      responseObserver
          .onNext(wallet.getDelegatedResourceAccountIndex(request.getValue()));
      responseObserver.onCompleted();
    }

    @Override
    public void getPaginatedProposalList(PaginatedMessage request,
        StreamObserver<ProposalList> responseObserver) {
      responseObserver
          .onNext(wallet.getPaginatedProposalList(request.getOffset(), request.getLimit()));
      responseObserver.onCompleted();

    }

    @Override
    public void getPaginatedExchangeList(PaginatedMessage request,
        StreamObserver<ExchangeList> responseObserver) {
      responseObserver
          .onNext(wallet.getPaginatedExchangeList(request.getOffset(), request.getLimit()));
      responseObserver.onCompleted();

    }

    @Override
    public void listExchanges(EmptyMessage request,
        StreamObserver<ExchangeList> responseObserver) {
      responseObserver.onNext(wallet.getExchangeList());
      responseObserver.onCompleted();
    }

    @Override
    public void getChainParameters(EmptyMessage request,
        StreamObserver<Protocol.ChainParameters> responseObserver) {
      responseObserver.onNext(wallet.getChainParameters());
      responseObserver.onCompleted();
    }

    @Override
    public void generateAddress(EmptyMessage request,
        StreamObserver<GrpcAPI.AddressPrKeyPairMessage> responseObserver) {
      ECKey ecKey = new ECKey(Utils.getRandom());
      byte[] priKey = ecKey.getPrivKeyBytes();
      byte[] address = ecKey.getAddress();
      String addressStr = Wallet.encode58Check(address);
      String priKeyStr = Hex.encodeHexString(priKey);
      AddressPrKeyPairMessage.Builder builder = AddressPrKeyPairMessage.newBuilder();
      builder.setAddress(addressStr);
      builder.setPrivateKey(priKeyStr);
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionInfoById(BytesMessage request,
        StreamObserver<TransactionInfo> responseObserver) {
      ByteString id = request.getValue();
      if (null != id) {
        TransactionInfo reply = wallet.getTransactionInfoById(id);

        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getNodeInfo(EmptyMessage request, StreamObserver<NodeInfo> responseObserver) {
      try {
        responseObserver.onNext(nodeInfoService.getNodeInfo().transferToProtoEntity());
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void accountPermissionUpdate(AccountPermissionUpdateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.AccountPermissionUpdateContract,
          responseObserver);
    }

//    @Override
//    public void getNullifier(BytesMessage request, StreamObserver<BytesMessage> responseObserver) {
//      ByteString id = request.getValue();
//      if (null != id) {
//        BytesMessage xboId = wallet.getNullifier(id);
//
//        responseObserver.onNext(xboId);
//      } else {
//        responseObserver.onNext(null);
//      }
//      responseObserver.onCompleted();
//    }

//    @Override
//    public void getMerklePath(BytesMessage request, StreamObserver<MerklePath> responseObserver) {
//      ByteString rt = request.getValue();
//      if (null != rt) {
//        MerklePath merklePath = wallet.getMerklePath(rt);
//
//        responseObserver.onNext(merklePath);
//      } else {
//        responseObserver.onNext(null);
//      }
//      responseObserver.onCompleted();
//    }

//    @Override
//    public void getBestMerkleRoot(EmptyMessage request,
//        StreamObserver<BytesMessage> responseObserver) {
//      byte[] rt = wallet.getBestMerkleRoot();
//      if (rt == null) {
//        responseObserver.onNext(null);
//      } else {
//        responseObserver
//            .onNext(BytesMessage.newBuilder().setValue(ByteString.copyFrom(rt)).build());
//      }
//      responseObserver.onCompleted();
//    }

    @Override
    public void getMerkleTreeVoucherInfo(OutputPointInfo request,
        StreamObserver<IncrementalMerkleVoucherInfo> responseObserver) {

      try {
        IncrementalMerkleVoucherInfo witnessInfo = wallet
            .getMerkleTreeVoucherInfo(request);
        responseObserver.onNext(witnessInfo);
      } catch (Exception ex) {
        responseObserver.onError(getRunTimeException(ex));
      }

      responseObserver.onCompleted();
    }

//    @Override
//    public void getZKBlockByLimitNext(BlockLimit request,
//        StreamObserver<BlockListExtention> responseObserver) {
//      long startNum = request.getStartNum();
//      long endNum = request.getEndNum();
//
//      if (endNum > 0 && endNum > startNum && endNum - startNum <= BLOCK_LIMIT_NUM) {
//        responseObserver.onNext(blocklist2Extention(
//            wallet.getZKBlocksByLimitNext(startNum, endNum - startNum)));
//      } else {
//        responseObserver.onNext(null);
//      }
//      responseObserver.onCompleted();
//    }
//
//    @Override
//    public void getMerkleTreeOfBlock(NumberMessage request,
//        StreamObserver<BlockIncrementalMerkleTree> responseObserver) {
//      long blockNumber = request.getNum();
//      if (blockNumber >= 0) {
//        BlockIncrementalMerkleTree.Builder builder = BlockIncrementalMerkleTree.newBuilder();
//        builder.setNumber(blockNumber);
//        IncrementalMerkleTree tree = wallet.getMerkleTreeOfBlock(blockNumber);
//        if (tree != null) {
//          builder.setMerkleTree(tree);
//          responseObserver.onNext(builder.build());
//        } else {
//          responseObserver.onNext(null);
//        }
//      } else {
//        responseObserver.onNext(null);
//      }
//      responseObserver.onCompleted();
//    }

    @Override
    public void createShieldedTransaction(PrivateParameters request,
        StreamObserver<TransactionExtention> responseObserver) {

      TransactionExtention.Builder xboExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();

      try {
        TransactionCapsule xbo = wallet.createShieldedTransaction(request);
        xboExtBuilder.setTransaction(xbo.getInstance());
        xboExtBuilder.setTxid(xbo.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
      } catch (ContractValidateException | ZksnarkException e) {
        retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString.copyFromUtf8(CONTRACT_VALIDATE_ERROR + e.getMessage()));
        logger.debug("ContractValidateException: {}", e.getMessage());
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        logger.info("exception caught: " + e.getMessage());
      }

      xboExtBuilder.setResult(retBuilder);
      responseObserver.onNext(xboExtBuilder.build());
      responseObserver.onCompleted();

    }

    @Override
    public void createShieldedTransactionWithoutSpendAuthSig(PrivateParametersWithoutAsk request,
        StreamObserver<TransactionExtention> responseObserver) {

      TransactionExtention.Builder xboExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();

      try {
        TransactionCapsule xbo = wallet.createShieldedTransactionWithoutSpendAuthSig(request);
        xboExtBuilder.setTransaction(xbo.getInstance());
        xboExtBuilder.setTxid(xbo.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
      } catch (ContractValidateException | ZksnarkException e) {
        retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString.copyFromUtf8(CONTRACT_VALIDATE_ERROR + e.getMessage()));
        logger.debug("ContractValidateException: {}", e.getMessage());
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        logger.info("exception caught: " + e.getMessage());
      }

      xboExtBuilder.setResult(retBuilder);
      responseObserver.onNext(xboExtBuilder.build());
      responseObserver.onCompleted();

    }

    @Override
    public void getSpendingKey(EmptyMessage request,
        StreamObserver<BytesMessage> responseObserver) {
      try {
        responseObserver.onNext(wallet.getSpendingKey());
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getRcm(EmptyMessage request,
        StreamObserver<BytesMessage> responseObserver) {
      try {
        responseObserver.onNext(wallet.getRcm());
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getExpandedSpendingKey(BytesMessage request,
        StreamObserver<ExpandedSpendingKeyMessage> responseObserver) {
      ByteString spendingKey = request.getValue();

      try {
        ExpandedSpendingKeyMessage response = wallet.getExpandedSpendingKey(spendingKey);
        responseObserver.onNext(response);
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
      }

      responseObserver.onCompleted();
    }

    @Override
    public void getAkFromAsk(BytesMessage request, StreamObserver<BytesMessage> responseObserver) {
      ByteString ak = request.getValue();

      try {
        responseObserver.onNext(wallet.getAkFromAsk(ak));
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
      }

      responseObserver.onCompleted();
    }

    @Override
    public void getNkFromNsk(BytesMessage request, StreamObserver<BytesMessage> responseObserver) {
      ByteString nk = request.getValue();

      try {
        responseObserver.onNext(wallet.getNkFromNsk(nk));
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
      }

      responseObserver.onCompleted();
    }

    @Override
    public void getIncomingViewingKey(ViewingKeyMessage request,
        StreamObserver<IncomingViewingKeyMessage> responseObserver) {
      ByteString ak = request.getAk();
      ByteString nk = request.getNk();

      try {
        responseObserver.onNext(wallet.getIncomingViewingKey(ak.toByteArray(), nk.toByteArray()));
      } catch (ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
      }

      responseObserver.onCompleted();
    }

    @Override
    public void getDiversifier(EmptyMessage request,
        StreamObserver<DiversifierMessage> responseObserver) {
      try {
        DiversifierMessage d = wallet.getDiversifier();
        responseObserver.onNext(d);
      } catch (ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }



    @Override
    public void getZenPaymentAddress(IncomingViewingKeyDiversifierMessage request,
        StreamObserver<PaymentAddressMessage> responseObserver) {
      IncomingViewingKeyMessage ivk = request.getIvk();
      DiversifierMessage d = request.getD();

      try {
        PaymentAddressMessage saplingPaymentAddressMessage =
            wallet.getPaymentAddress(new IncomingViewingKey(ivk.getIvk().toByteArray()),
                new DiversifierT(d.getD().toByteArray()));

        responseObserver.onNext(saplingPaymentAddressMessage);
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();

    }

    @Override
    public void scanNoteByIvk(GrpcAPI.IvkDecryptParameters request,
        StreamObserver<GrpcAPI.DecryptNotes> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();
      try {
        DecryptNotes decryptNotes = wallet
            .scanNoteByIvk(startNum, endNum, request.getIvk().toByteArray());
        responseObserver.onNext(decryptNotes);
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();

    }
    
    @Override
    public void scanAndMarkNoteByIvk(GrpcAPI.IvkDecryptAndMarkParameters request,
            StreamObserver<GrpcAPI.DecryptNotesMarked> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();
      try {
        DecryptNotesMarked decryptNotes = wallet.scanAndMarkNoteByIvk(startNum, endNum,
                request.getIvk().toByteArray(),
                request.getAk().toByteArray(),
                request.getNk().toByteArray());
        responseObserver.onNext(decryptNotes);
      } catch (BadItemException | ZksnarkException | InvalidProtocolBufferException
              | ItemNotFoundException e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void scanNoteByOvk(GrpcAPI.OvkDecryptParameters request,
        StreamObserver<GrpcAPI.DecryptNotes> responseObserver) {
      long startNum = request.getStartBlockIndex();
      long endNum = request.getEndBlockIndex();
      try {
        DecryptNotes decryptNotes = wallet
            .scanNoteByOvk(startNum, endNum, request.getOvk().toByteArray());
        responseObserver.onNext(decryptNotes);
      } catch (BadItemException | ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void isSpend(NoteParameters request, StreamObserver<SpendResult> responseObserver) {
      try {
        responseObserver.onNext(wallet.isSpend(request));
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createShieldNullifier(GrpcAPI.NfParameters request,
        StreamObserver<GrpcAPI.BytesMessage> responseObserver) {
      try {
        BytesMessage nf = wallet
            .createShieldNullifier(request);
        responseObserver.onNext(nf);
      } catch (ZksnarkException e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createSpendAuthSig(SpendAuthSigParameters request,
        StreamObserver<GrpcAPI.BytesMessage> responseObserver) {
      try {
        BytesMessage spendAuthSig = wallet.createSpendAuthSig(request);
        responseObserver.onNext(spendAuthSig);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getShieldTransactionHash(Transaction request,
        StreamObserver<GrpcAPI.BytesMessage> responseObserver) {
      try {
        BytesMessage transactionHash = wallet.getShieldTransactionHash(request);
        responseObserver.onNext(transactionHash);
      } catch (Exception e) {
        responseObserver.onError(getRunTimeException(e));
      }
      responseObserver.onCompleted();
    }
  }
}
