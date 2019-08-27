package org.xbo.core.db.api;

import com.google.protobuf.ByteString;
import java.io.File;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.testng.annotations.Test;
import org.xbo.common.application.Application;
import org.xbo.common.application.ApplicationFactory;
import org.xbo.common.application.XBOApplicationContext;
import org.xbo.common.utils.ByteArray;
import org.xbo.common.utils.FileUtil;
import org.xbo.common.utils.Sha256Hash;
import org.xbo.core.capsule.AccountCapsule;
import org.xbo.core.capsule.AssetIssueCapsule;
import org.xbo.core.capsule.BlockCapsule;
import org.xbo.core.capsule.ExchangeCapsule;
import org.xbo.core.capsule.TransactionCapsule;
import org.xbo.core.config.DefaultConfig;
import org.xbo.core.config.args.Args;
import org.xbo.core.db.Manager;
import org.xbo.protos.Contract.AssetIssueContract;
import org.xbo.protos.Protocol.Account;
import org.xbo.protos.Protocol.Exchange;
import org.xbo.protos.Protocol.Transaction.Contract.ContractType;

public class AssetUpdateHelperTest {

  private static Manager dbManager;
  private static XBOApplicationContext context;
  private static String dbPath = "output_AssetUpdateHelperTest_test";
  private static Application AppT;

  private static ByteString assetName = ByteString.copyFrom("assetIssueName".getBytes());

  static {
    Args.setParam(new String[]{"-d", dbPath, "-w"}, "config-test-index.conf");
    Args.getInstance().setSolidityNode(true);
    context = new XBOApplicationContext(DefaultConfig.class);
    AppT = ApplicationFactory.create(context);
  }

  @BeforeClass
  public static void init() {

    dbManager = context.getBean(Manager.class);

    AssetIssueContract contract =
        AssetIssueContract.newBuilder().setName(assetName).setNum(12581).setPrecision(5).build();
    AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(contract);
    dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

    BlockCapsule blockCapsule = new BlockCapsule(1,
        Sha256Hash.wrap(ByteString.copyFrom(
            ByteArray.fromHexString(
                "0000000000000002498b464ac0292229938a342238077182498b464ac0292222"))),
        1234, ByteString.copyFrom("1234567".getBytes()));

    blockCapsule.addTransaction(new TransactionCapsule(contract, ContractType.AssetIssueContract));
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(1L);
    dbManager.getBlockIndexStore().put(blockCapsule.getBlockId());
    dbManager.getBlockStore().put(blockCapsule.getBlockId().getBytes(), blockCapsule);

    ExchangeCapsule exchangeCapsule =
        new ExchangeCapsule(
            Exchange.newBuilder()
                .setExchangeId(1L)
                .setFirstTokenId(assetName)
                .setSecondTokenId(ByteString.copyFrom("_".getBytes()))
                .build());
    dbManager.getExchangeStore().put(exchangeCapsule.createDbKey(), exchangeCapsule);

    AccountCapsule accountCapsule =
        new AccountCapsule(
            Account.newBuilder()
                .setAssetIssuedName(assetName)
                .putAsset("assetIssueName", 100)
                .putFreeAssetNetUsage("assetIssueName", 20000)
                .putLatestAssetOperationTime("assetIssueName", 30000000)
                .setAddress(ByteString.copyFrom(ByteArray.fromHexString("121212abc")))
                .build());
    dbManager.getAccountStore().put(ByteArray.fromHexString("121212abc"), accountCapsule);
  }

  @AfterClass
  public static void removeDb() {
    Args.clearParam();
    AppT.shutdownServices();
    AppT.shutdown();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }

  @Test
  public void test() {

    if (dbManager == null) {
      init();
    }
    AssetUpdateHelper assetUpdateHelper = new AssetUpdateHelper(dbManager);
    assetUpdateHelper.init();
    {
      assetUpdateHelper.updateAsset();

      String idNum = "1000001";

      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueStore().get(assetName.toByteArray());
      Assert.assertEquals(idNum, assetIssueCapsule.getId());
      Assert.assertEquals(5L, assetIssueCapsule.getPrecision());

      AssetIssueCapsule assetIssueCapsule2 =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(idNum)));

      Assert.assertEquals(idNum, assetIssueCapsule2.getId());
      Assert.assertEquals(assetName, assetIssueCapsule2.getName());
      Assert.assertEquals(0L, assetIssueCapsule2.getPrecision());
    }

    {
      assetUpdateHelper.updateExchange();

      try {
        ExchangeCapsule exchangeCapsule =
            dbManager.getExchangeV2Store().get(ByteArray.fromLong(1L));
        Assert.assertEquals("1000001", ByteArray.toStr(exchangeCapsule.getFirstTokenId()));
        Assert.assertEquals("_", ByteArray.toStr(exchangeCapsule.getSecondTokenId()));
      } catch (Exception ex) {
        throw new RuntimeException("testUpdateExchange error");
      }
    }

    {
      assetUpdateHelper.updateAccount();

      AccountCapsule accountCapsule =
          dbManager.getAccountStore().get(ByteArray.fromHexString("121212abc"));

      Assert.assertEquals(
          ByteString.copyFrom(ByteArray.fromString("1000001")), accountCapsule.getAssetIssuedID());

      Assert.assertEquals(1, accountCapsule.getAssetMapV2().size());

      Assert.assertEquals(100L, accountCapsule.getAssetMapV2().get("1000001").longValue());

      Assert.assertEquals(1, accountCapsule.getAllFreeAssetNetUsageV2().size());

      Assert.assertEquals(
          20000L, accountCapsule.getAllFreeAssetNetUsageV2().get("1000001").longValue());

      Assert.assertEquals(1, accountCapsule.getLatestAssetOperationTimeMapV2().size());

      Assert.assertEquals(
          30000000L, accountCapsule.getLatestAssetOperationTimeMapV2().get("1000001").longValue());
    }

    removeDb();
  }
}
