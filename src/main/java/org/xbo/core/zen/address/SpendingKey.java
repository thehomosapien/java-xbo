package org.xbo.core.zen.address;

import java.util.Optional;
import java.util.Random;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.xbo.common.utils.ByteArray;
import org.xbo.common.zksnark.JLibrustzcash;
import org.xbo.common.zksnark.JLibsodium;
import org.xbo.core.Constant;
import org.xbo.core.exception.BadItemException;
import org.xbo.core.exception.ZksnarkException;

@AllArgsConstructor
public class SpendingKey {

  @Setter
  @Getter
  public byte[] value;

  public static SpendingKey random() throws ZksnarkException {
    while (true) {
      SpendingKey sk = new SpendingKey(randomUint256());
      if (sk.fullViewingKey().isValid()) {
        return sk;
      }
    }
  }

  public static SpendingKey decode(String hex) {
    SpendingKey sk = new SpendingKey(ByteArray.fromHexString(hex));
    return sk;
  }

  private static byte[] randomUint256() {
    return generatePrivateKey(0L);
  }

  public static byte[] generatePrivateKey(long seed) {
    byte[] result = new byte[32];
    if (seed != 0L) {
      new Random(seed).nextBytes(result);
    } else {
      new Random().nextBytes(result);
    }
    Integer i = result[0] & 0x0F;
    result[0] = i.byteValue();
    return result;
  }

  public String encode() {
    return ByteArray.toHexString(value);
  }

  public ExpandedSpendingKey expandedSpendingKey() throws ZksnarkException {
    return new ExpandedSpendingKey(
        PRF.prfAsk(this.value), PRF.prfNsk(this.value), PRF.prfOvk(this.value));
  }

  public FullViewingKey fullViewingKey() throws ZksnarkException {
    return expandedSpendingKey().fullViewingKey();
  }

  public PaymentAddress defaultAddress() throws BadItemException, ZksnarkException {
    Optional<PaymentAddress> addrOpt =
        fullViewingKey().inViewingKey().address(defaultDiversifier());
    if (addrOpt.isPresent()){
      return addrOpt.get();
    } else {
      return null;
    }
  }

  public DiversifierT defaultDiversifier() throws BadItemException, ZksnarkException {
    byte[] res = new byte[Constant.ZC_DIVERSIFIER_SIZE];
    byte[] blob = new byte[34];
    //ZksnarkUtils.sort(this.value);
    System.arraycopy(this.value, 0, blob, 0, 32);
    blob[32] = 3;
    blob[33] = 0;
    while (true) {
      long state = JLibsodium.initState();
      try {
        JLibsodium.cryptoGenerichashBlake2bInitSaltPersonal(
            state, null, 0, 64, null, Constant.ZXBO_EXPANDSEED_PERSONALIZATION);
        JLibsodium.cryptoGenerichashBlake2bUpdate(state, blob, 34);
        JLibsodium.cryptoGenerichashBlake2bFinal(state, res, 11);
        if (JLibrustzcash.librustzcashCheckDiversifier(res)) {
          break;
        } else if (blob[33] == (byte) 255) {
          throw new BadItemException(
              "librustzcash_check_diversifier did not return valid diversifier");
        }
        blob[33] += 1;
      } finally {
        JLibsodium.freeState(state);
      }
    }
    DiversifierT diversifierT = new DiversifierT();
    diversifierT.setData(res);
    return diversifierT;
  }

  private static class PRF {

    public static byte[] prfAsk(byte[] sk) throws ZksnarkException {
      byte[] ask = new byte[32];
      byte t = 0x00;
      byte[] tmp = prfExpand(sk, t);
      JLibrustzcash.librustzcashToScalar(tmp, ask);
      return ask;
    }

    public static byte[] prfNsk(byte[] sk) throws ZksnarkException {
      byte[] nsk = new byte[32];
      byte t = 0x01;
      byte[] tmp = prfExpand(sk, t);
      JLibrustzcash.librustzcashToScalar(tmp, nsk);
      return nsk;
    }

    public static byte[] prfOvk(byte[] sk) {
      byte[] ovk = new byte[32];
      byte t = 0x02;
      byte[] tmp = prfExpand(sk, t);
      System.arraycopy(tmp, 0, ovk, 0, 32);
      return ovk;
    }

    private static byte[] prfExpand(byte[] sk, byte t) {
      byte[] res = new byte[64];
      byte[] blob = new byte[33];
      System.arraycopy(sk, 0, blob, 0, 32);
      blob[32] = t;
      long state = JLibsodium.initState();
      try {
        JLibsodium.cryptoGenerichashBlake2bInitSaltPersonal(
            state, null, 0, 64, null, Constant.ZXBO_EXPANDSEED_PERSONALIZATION);
        JLibsodium.cryptoGenerichashBlake2bUpdate(state, blob, 33);
        JLibsodium.cryptoGenerichashBlake2bFinal(state, res, 64);
      } finally {
        JLibsodium.freeState(state);
      }
      return res;
    }
  }
}
