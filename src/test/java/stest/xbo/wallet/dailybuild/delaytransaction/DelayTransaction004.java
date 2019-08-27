package stest.xbo.wallet.dailybuild.delaytransaction;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.xbo.api.WalletGrpc;
import org.xbo.common.crypto.ECKey;
import org.xbo.common.utils.ByteArray;
import org.xbo.common.utils.Utils;
import org.xbo.core.Wallet;
import org.xbo.protos.Protocol.Account;
//import org.xbo.protos.Protocol.DeferredTransaction;
import org.xbo.protos.Protocol.SmartContract;
import org.xbo.protos.Protocol.Transaction;
import org.xbo.protos.Protocol.TransactionInfo;
import stest.xbo.wallet.common.client.Configuration;
import stest.xbo.wallet.common.client.Parameter.CommonConstant;
import stest.xbo.wallet.common.client.utils.PublicMethed;

@Slf4j
public class DelayTransaction004 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private static final long now = System.currentTimeMillis();
  Long delaySecond = 10L;

  private long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");

  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(1);
  private Long delayTransactionFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.delayTransactionFee");
  private Long cancleDelayTransactionFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.cancleDelayTransactionFee");
  ByteString assetId;
  private byte[] contractAddress = null;
  SmartContract smartContract;

  ECKey ecKey = new ECKey(Utils.getRandom());
  byte[] smartContractOwnerAddress = ecKey.getAddress();
  String smartContractOwnerKey = ByteArray.toHexString(ecKey.getPrivKeyBytes());

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = false)
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = false, description = "Delay update setting contract")
  public void test1DelayUpdateSetting() {
    //get account
    ecKey = new ECKey(Utils.getRandom());
    smartContractOwnerAddress = ecKey.getAddress();
    smartContractOwnerKey = ByteArray.toHexString(ecKey.getPrivKeyBytes());
    PublicMethed.printAddress(smartContractOwnerKey);


    Assert.assertTrue(PublicMethed.sendcoin(smartContractOwnerAddress, 2048000000, fromAddress,
        testKey002, blockingStubFull));
    //PublicMethed.freezeBalance(smartContractOwnerAddress,10000000L,3,
    //    smartContractOwnerKey,blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String contractName = "XBOTOKEN";
    String code = Configuration.getByPath("testng.conf")
        .getString("code.code_ContractScenario004_deployErc20XBOToken");
    String abi = Configuration.getByPath("testng.conf")
        .getString("abi.abi_ContractScenario004_deployErc20XBOToken");
    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, 999L,"0",0,null,
        smartContractOwnerKey, smartContractOwnerAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Long oldContractPercent = smartContract.getConsumeUserResourcePercent();
    Assert.assertTrue(PublicMethed.updateSetting(contractAddress,oldContractPercent,
        smartContractOwnerKey,smartContractOwnerAddress,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Assert.assertTrue(smartContract.getConsumeUserResourcePercent() == oldContractPercent);

    Long newContractPercent = 39L;
    final String txid = PublicMethed.updateSettingDelayGetTxid(contractAddress,newContractPercent,
        delaySecond,smartContractOwnerKey,smartContractOwnerAddress,blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    Assert.assertTrue(smartContract.getConsumeUserResourcePercent() == oldContractPercent);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    logger.info("newContractPercent: " + smartContract.getConsumeUserResourcePercent());
    Assert.assertTrue(smartContract.getConsumeUserResourcePercent() == newContractPercent);

    Long netFee = PublicMethed.getTransactionInfoById(txid,blockingStubFull).get().getReceipt()
        .getNetFee();
    Long fee = PublicMethed.getTransactionInfoById(txid,blockingStubFull).get().getFee();
    Assert.assertTrue(fee - netFee == delayTransactionFee);
    Long afterFreeNetUsaged = PublicMethed.queryAccount(smartContractOwnerKey,
        blockingStubFull).getFreeNetUsage();
    Long beforeFreeNetUsaged = PublicMethed.queryAccount(smartContractOwnerKey,
        blockingStubFull).getFreeNetUsage();
    Long inDelayFreeNetUsaged = PublicMethed.queryAccount(smartContractOwnerKey,
        blockingStubFull).getFreeNetUsage();
    logger.info("beforeFreeNetUsaged: " + beforeFreeNetUsaged);
    logger.info("inDelayFreeNetUsaged: " + inDelayFreeNetUsaged);
    logger.info("afterFreeNetUsaged: " + afterFreeNetUsaged);
    Assert.assertTrue(beforeFreeNetUsaged + 50 < inDelayFreeNetUsaged);
    Assert.assertTrue(inDelayFreeNetUsaged + 50 < afterFreeNetUsaged);

  }

  @Test(enabled = false, description = "Cancel delay update setting contract")
  public void test2CancelDelayUpdateSetting() {
    //get account
    final Long oldContractPercent = smartContract.getConsumeUserResourcePercent();
    final Long newContractPercent = 46L;

    String txid = PublicMethed.updateSettingDelayGetTxid(contractAddress,newContractPercent,
        delaySecond,smartContractOwnerKey,smartContractOwnerAddress,blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Account ownerAccount = PublicMethed.queryAccount(smartContractOwnerKey,blockingStubFull);
    final Long beforeCancelBalance = ownerAccount.getBalance();


    Assert.assertFalse(PublicMethed.cancelDeferredTransactionById(txid,fromAddress,testKey002,
        blockingStubFull));
    final String cancelTxid = PublicMethed.cancelDeferredTransactionByIdGetTxid(txid,
        smartContractOwnerAddress,smartContractOwnerKey,blockingStubFull);
    Assert.assertFalse(PublicMethed.cancelDeferredTransactionById(txid,smartContractOwnerAddress,
        smartContractOwnerKey,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    logger.info("newContractPercent: " + smartContract.getConsumeUserResourcePercent());
    Assert.assertTrue(smartContract.getConsumeUserResourcePercent() == oldContractPercent);

    final Long netFee = PublicMethed.getTransactionInfoById(cancelTxid,blockingStubFull).get()
        .getReceipt().getNetFee();
    final Long fee = PublicMethed.getTransactionInfoById(cancelTxid,blockingStubFull).get()
        .getFee();
    logger.info("net fee : " + PublicMethed.getTransactionInfoById(cancelTxid,blockingStubFull)
        .get().getReceipt().getNetFee());
    logger.info("Fee : " + PublicMethed.getTransactionInfoById(cancelTxid,blockingStubFull)
        .get().getFee());

    ownerAccount = PublicMethed.queryAccount(smartContractOwnerKey,blockingStubFull);
    Long afterCancelBalance = ownerAccount.getBalance();
    Assert.assertTrue(fee - netFee == cancleDelayTransactionFee);
    Assert.assertTrue(fee == beforeCancelBalance - afterCancelBalance);

    logger.info("beforeCancelBalance: " + beforeCancelBalance);
    logger.info("afterCancelBalance : " + afterCancelBalance);
  }





  /**
   * constructor.
   */

  @AfterClass(enabled = false)
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


