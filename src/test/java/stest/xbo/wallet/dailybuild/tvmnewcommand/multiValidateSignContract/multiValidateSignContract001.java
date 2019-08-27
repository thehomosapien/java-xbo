package stest.xbo.wallet.dailybuild.tvmnewcommand.multiValidateSignContract;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.spongycastle.util.encoders.Hex;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.xbo.api.GrpcAPI.TransactionExtention;
import org.xbo.api.WalletGrpc;
import org.xbo.api.WalletSolidityGrpc;
import org.xbo.common.crypto.ECKey;
import org.xbo.common.crypto.Hash;
import org.xbo.common.utils.ByteArray;
import org.xbo.common.utils.Utils;
import org.xbo.core.Wallet;
import stest.xbo.wallet.common.client.Configuration;
import stest.xbo.wallet.common.client.Parameter;
import stest.xbo.wallet.common.client.utils.PublicMethed;

@Slf4j
public class multiValidateSignContract001 {

  private final String testNetAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] testNetAccountAddress = PublicMethed.getFinalAddress(testNetAccountKey);
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private ManagedChannel channelSolidity = null;

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;

  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;


  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;

  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);

  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);
  byte[] contractAddress = null;


  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contractExcAddress = ecKey1.getAddress();
  String contractExcKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  String txid = "";

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(contractExcKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
    txid = PublicMethed
        .sendcoinGetTransactionId(contractExcAddress, 1000000000L, testNetAccountAddress,
            testNetAccountKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String filePath = "src/test/resources/soliditycode/multivalidatesign001.sol";
    String contractName = "Demo";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, null, contractExcKey,
        contractExcAddress, blockingStubFull);
  }


  @Test(enabled = true, description = "Correct data test pure multivalidatesign")
  public void test01CorrectData() {
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    List<Object> signatures = new ArrayList<>();
    List<Object> addresses = new ArrayList<>();
    byte[] hash = Hash.sha3(txid.getBytes());
    for (int i = 0; i < 27; i++) {
      ECKey key = new ECKey();
      byte[] sign = key.sign(hash).toByteArray();
      signatures.add(Hex.toHexString(sign));
      addresses.add(Wallet.encode58Check(key.getAddress()));
    }
    List<Object> parameters = Arrays.asList("0x" + Hex.toHexString(hash), signatures, addresses);
    String input = parametersString(parameters);
    TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(contractAddress,
            "testArray(bytes32,bytes[],address[])", input, false,
            0, 0, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    logger.info("Code = " + transactionExtention.getResult().getCode());
    logger.info("Message = " + transactionExtention.getResult().getMessage().toStringUtf8());
    Assert
        .assertEquals(1, ByteArray.toInt(transactionExtention.getConstantResult(0).toByteArray()));
    Assert.assertEquals("SUCCESS", transactionExtention.getResult().getCode().toString());


  }


  @Test(enabled = true, description = "Incorrect address test pure multivalidatesign")
  public void test02IncorrectAddress() {
    List<Object> signatures = new ArrayList<>();
    List<Object> addresses = new ArrayList<>();
    byte[] hash = Hash.sha3(txid.getBytes());
    for (int i = 0; i < 27; i++) {
      ECKey key = new ECKey();
      byte[] sign = key.sign(hash).toByteArray();
      signatures.add(Hex.toHexString(sign));
      addresses.add(Wallet.encode58Check(key.getAddress()));
    }
    addresses.remove(0);
    List<Object> parameters = Arrays.asList("0x" + Hex.toHexString(hash), signatures, addresses);
    String input = parametersString(parameters);
    TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(contractAddress,
            "testArray(bytes32,bytes[],address[])", input, false,
            0, 0, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    logger.info("Code = " + transactionExtention.getResult().getCode());
    logger.info("Message = " + transactionExtention.getResult().getMessage().toStringUtf8());
    Assert
        .assertEquals(2, ByteArray.toInt(transactionExtention.getConstantResult(0).toByteArray()));
    Assert.assertEquals("SUCCESS", transactionExtention.getResult().getCode().toString());

  }

  @Test(enabled = true, description = "Incorrect signatures test pure multivalidatesign")
  public void test03IncorrectSignatures() {
    List<Object> signatures = new ArrayList<>();
    List<Object> addresses = new ArrayList<>();
    byte[] hash = Hash.sha3(txid.getBytes());
    for (int i = 0; i < 27; i++) {
      ECKey key = new ECKey();
      byte[] sign = key.sign(hash).toByteArray();
      signatures.add(Hex.toHexString(sign));
      addresses.add(Wallet.encode58Check(key.getAddress()));
    }
    signatures.remove(0);
    List<Object> parameters = Arrays.asList("0x" + Hex.toHexString(hash), signatures, addresses);
    String input = parametersString(parameters);
    TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(contractAddress,
            "testArray(bytes32,bytes[],address[])", input, false,
            0, 0, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    Assert
        .assertEquals(2, ByteArray.toInt(transactionExtention.getConstantResult(0).toByteArray()));
    Assert.assertEquals("SUCCESS", transactionExtention.getResult().getCode().toString());

  }

  @Test(enabled = true, description = "Incorrect hash test pure multivalidatesign")
  public void test04IncorrectHash() {
    String txid = PublicMethed
        .sendcoinGetTransactionId(contractExcAddress, 1000000000L, testNetAccountAddress,
            testNetAccountKey,
            blockingStubFull);
    String incorrecttxid = PublicMethed
        .sendcoinGetTransactionId(contractExcAddress, 1000000000L, testNetAccountAddress,
            testNetAccountKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String filePath = "src/test/resources/soliditycode/multivalidatesign001.sol";
    String contractName = "Demo";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, null, contractExcKey,
        contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    List<Object> signatures = new ArrayList<>();
    List<Object> addresses = new ArrayList<>();
    byte[] hash = Hash.sha3(txid.getBytes());
    for (int i = 0; i < 27; i++) {
      ECKey key = new ECKey();
      byte[] sign = key.sign(hash).toByteArray();
      signatures.add(Hex.toHexString(sign));
      addresses.add(Wallet.encode58Check(key.getAddress()));
    }
    List<Object> parameters = Arrays
        .asList("0x" + Hex.toHexString(Hash.sha3(incorrecttxid.getBytes())), signatures, addresses);
    String input = parametersString(parameters);
    TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(contractAddress,
            "testArray(bytes32,bytes[],address[])", input, false,
            0, 0, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    logger.info(transactionExtention.toString());
    Assert
        .assertEquals(2, ByteArray.toInt(transactionExtention.getConstantResult(0).toByteArray()));
    Assert.assertEquals("SUCCESS", transactionExtention.getResult().getCode().toString());
  }

  @Test(enabled = true, description = "Extra long addresses and signatures array test pure multivalidatesign")
  public void test05ExtraLongArray() {
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    List<Object> signatures = new ArrayList<>();
    List<Object> addresses = new ArrayList<>();
    byte[] hash = Hash.sha3(txid.getBytes());
    for (int i = 0; i < 150; i++) {
      ECKey key = new ECKey();
      byte[] sign = key.sign(hash).toByteArray();
      signatures.add(Hex.toHexString(sign));
      addresses.add(Wallet.encode58Check(key.getAddress()));
    }
    List<Object> parameters = Arrays.asList("0x" + Hex.toHexString(hash), signatures, addresses);
    String input = parametersString(parameters);
    System.out.println(input);
    System.out.println(ByteArray.toHexString(contractAddress));
    TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(contractAddress,
            "testArray(bytes32,bytes[],address[])", input, false,
            0, 0, "0", 0, contractExcAddress, contractExcKey, blockingStubFull);
    logger.info("Code = " + transactionExtention.getResult().getCode());
    logger.info("Message = " + transactionExtention.getResult().getMessage().toStringUtf8());
    Assert
        .assertEquals(
            "class org.xbo.common.runtime.vm.program.Program$OutOfTimeException : CPU timeout for 'ISZERO' operation executing",
            transactionExtention.getResult().getMessage().toStringUtf8());
    Assert
        .assertEquals("CONTRACT_EXE_ERROR", transactionExtention.getResult().getCode().toString());
  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    long balance = PublicMethed.queryAccount(contractExcKey, blockingStubFull).getBalance();
    PublicMethed.sendcoin(testNetAccountAddress, balance, contractExcAddress, contractExcKey,
        blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelFull1 != null) {
      channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private String parametersString(List<Object> parameters) {
    String[] inputArr = new String[parameters.size()];
    int i = 0;
    for (Object parameter : parameters) {
      if (parameter instanceof List) {
        StringBuilder sb = new StringBuilder();
        for (Object item : (List) parameter) {
          if (sb.length() != 0) {
            sb.append(",");
          }
          sb.append("\"").append(item).append("\"");
        }
        inputArr[i++] = "[" + sb.toString() + "]";
      } else {
        inputArr[i++] =
            (parameter instanceof String) ? ("\"" + parameter + "\"") : ("" + parameter);
      }
    }
    String input = StringUtils.join(inputArr, ',');
    return input;
  }
}