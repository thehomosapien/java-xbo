package org.xbo.common.runtime.vm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.spongycastle.util.encoders.Hex;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.xbo.common.crypto.ECKey;
import org.xbo.common.crypto.Hash;
import org.xbo.common.runtime.utils.MUtil;
import org.xbo.common.runtime.vm.PrecompiledContracts.MultiValidateSign;
import org.xbo.core.Wallet;
import stest.xbo.wallet.common.client.utils.AbiUtil;

@Slf4j
public class MultiValidateSignContractTest {

  private static final String METHOD_SIGN = "multivalidatesign(bytes32,bytes[],address[])";
  PrecompiledContracts.MultiValidateSign contract = new MultiValidateSign();

  private static final byte[] smellData;
  private static final byte[] longData;

  static {
    smellData = new byte[10];
    longData = new byte[100000000];
    Arrays.fill(smellData, (byte) 1);
    Arrays.fill(longData, (byte) 2);
  }

  @Test
  void staticCallTest() {
    contract.setStaticCall(true);
    List<Object> signatures = new ArrayList<>();
    List<Object> addresses = new ArrayList<>();
    byte[] hash = Hash.sha3(longData);
    //insert incorrect
    for (int i = 0; i < 27; i++) {
      ECKey key = new ECKey();
      byte[] sign = key.sign(hash).toByteArray();
      if (i % 5 == 0) {
        signatures.add(Hex.toHexString(DataWord.ONE().getData()));
      } else {
        signatures.add(Hex.toHexString(sign));
      }
      if (i == 13) {
        addresses.add(Wallet.encode58Check(MUtil.convertToXBOAddress(new byte[20])));
      } else {
        addresses.add(Wallet.encode58Check(key.getAddress()));
      }
    }
    Pair<Boolean, byte[]> ret;
    ret = validateMultiSign(hash, signatures, addresses);
    for (int i = 0; i < 27; i++) {
      if (i >= 27) {
        Assert.assertEquals(ret.getValue()[i], 0);
      } else if (i % 5 == 0) {
        Assert.assertEquals(ret.getValue()[i], 0);
      } else if (i == 13) {
        Assert.assertEquals(ret.getValue()[i], 0);
      } else {
        Assert.assertEquals(ret.getValue()[i], 1);
      }
    }
    signatures = new ArrayList<>();
    addresses = new ArrayList<>();

    //test when length >= 32
    for (int i = 0; i < 100; i++) {
      ECKey key = new ECKey();
      byte[] sign = key.sign(hash).toByteArray();
      if (i == 30) {
        signatures.add(Hex.toHexString(DataWord.ONE().getData()));
      } else {
        signatures.add(Hex.toHexString(sign));
      }
      addresses.add(Wallet.encode58Check(key.getAddress()));
    }
    ret = validateMultiSign(hash, signatures, addresses);
    Assert.assertEquals(ret.getValue().length, 32);
    for (int i = 0; i < 32; i++) {
      if (i == 30) {
        Assert.assertEquals(ret.getValue()[i], 0);
      } else {
        Assert.assertEquals(ret.getValue()[i], 1);
      }
    }

  }

  @Test
  void correctionTest() {
    contract.setStaticCall(false);
    List<Object> signatures = new ArrayList<>();
    List<Object> addresses = new ArrayList<>();
    byte[] hash = Hash.sha3(longData);
    //insert incorrect every 5 pairs
    for (int i = 0; i < 27; i++) {
      ECKey key = new ECKey();
      byte[] sign = key.sign(hash).toByteArray();
      if (i % 5 == 0) {
        signatures.add(Hex.toHexString(DataWord.ONE().getData()));
      } else {
        signatures.add(Hex.toHexString(sign));
      }
      addresses.add(Wallet.encode58Check(key.getAddress()));
    }
    Pair<Boolean, byte[]> ret = null;
    ret = validateMultiSign(hash, signatures, addresses);
    for (int i = 0; i < 27; i++) {
      if (i >= 27) {
        Assert.assertEquals(ret.getValue()[i], 0);
      } else if (i % 5 == 0) {
        Assert.assertEquals(ret.getValue()[i], 0);
      } else {
        Assert.assertEquals(ret.getValue()[i], 1);
      }
    }

    // incorrect hash
    byte[] incorrectHash = DataWord.ONE().getData();
    ret = validateMultiSign(incorrectHash, signatures, addresses);
    for (int i = 0; i < 27; i++) {
      Assert.assertEquals(ret.getValue()[i], 0);
    }
    // different length
    byte[] incorrectSign = DataWord.ONE().getData();
    List<Object> incorrectSigns = new ArrayList<>(signatures);
    incorrectSigns.remove(incorrectSigns.size() - 1);
    ret = validateMultiSign(hash, incorrectSigns, addresses);
    Assert.assertEquals(ret.getValue(), DataWord.ZERO().getData());

    //test when length >= 32
    signatures = new ArrayList<>();
    addresses = new ArrayList<>();
    for (int i = 0; i < 80; i++) {
      ECKey key = new ECKey();
      byte[] sign = key.sign(hash).toByteArray();
      if (i == 13) {
        signatures.add(Hex.toHexString(DataWord.ONE().getData()));
      } else {
        signatures.add(Hex.toHexString(sign));
      }
      addresses.add(Wallet.encode58Check(key.getAddress()));
    }
    ret = validateMultiSign(hash, signatures, addresses);
    Assert.assertEquals(ret.getValue().length, 32);
    for (int i = 0; i < 32; i++) {
      if (i == 13) {
        Assert.assertEquals(ret.getValue()[i], 0);
      } else {
        Assert.assertEquals(ret.getValue()[i], 1);
      }
    }

  }

  Pair<Boolean, byte[]> validateMultiSign(byte[] hash, List<Object> signatures, List<Object> addresses) {
    List<Object> parameters = Arrays.asList("0x" + Hex.toHexString(hash), signatures, addresses);
    byte[] input = Hex.decode(AbiUtil.parseParameters(METHOD_SIGN, parameters));
    contract.getEnergyForData(input);
    contract.setVmShouldEndInUs(System.nanoTime() / 1000 + 50 * 1000);
    Pair<Boolean, byte[]> ret = contract.execute(input);
    logger.info("BytesArray:{}，HexString:{}", Arrays.toString(ret.getValue()),
        Hex.toHexString(ret.getValue()));
    return ret;
  }


}
