package org.xbo.common.storage;

import static org.xbo.common.storage.Type.VALUE_TYPE_UNKNOWN;

import java.util.Arrays;
import org.apache.commons.lang3.ArrayUtils;
import org.xbo.common.runtime.config.VMConfig;
import org.xbo.core.capsule.AccountCapsule;
import org.xbo.core.capsule.AssetIssueCapsule;
import org.xbo.core.capsule.BlockCapsule;
import org.xbo.core.capsule.BytesCapsule;
import org.xbo.core.capsule.CodeCapsule;
import org.xbo.core.capsule.ContractCapsule;
import org.xbo.core.capsule.ProposalCapsule;
import org.xbo.core.capsule.TransactionCapsule;
import org.xbo.core.capsule.VotesCapsule;
import org.xbo.core.capsule.WitnessCapsule;
import org.xbo.core.exception.BadItemException;

public class Value {

  private Type type;
  private byte[] any = null;

  /**
   * @param any
   */
  public Value(byte[] any, Type type) {
    if (any != null && any.length > 0) {
      this.any = new byte[any.length];
      System.arraycopy(any, 0, this.any, 0, any.length);
      this.type = type.clone();
    }
  }

  /**
   * @param any
   * @param type
   */
  public Value(byte[] any, int type) {
    if (any != null && any.length > 0) {
      this.any = new byte[any.length];
      System.arraycopy(any, 0, this.any, 0, any.length);
      this.type = new Type(type);
    } else {
      if (VMConfig.allowMultiSign()) {
        this.type = new Type(VALUE_TYPE_UNKNOWN);
      }
    }
  }

  /**
   * @param value
   */
  private Value(Value value) {
    if (value.getAny() != null && value.getAny().length > 0) {
      this.any = new byte[value.any.length];
      System.arraycopy(value.getAny(), 0, this.any, 0, value.getAny().length);
      this.type = value.getType().clone();
    } else {
      if (VMConfig.allowMultiSign()) {
        this.type = new Type(VALUE_TYPE_UNKNOWN);
      }
    }
  }

  /**
   * @return
   */
  public Value clone() {
    return new Value(this);
  }

  /**
   * @return
   */
  public byte[] getAny() {
    return any;
  }

  /**
   * @return
   */
  public Type getType() {
    return type;
  }

  /**
   * @param type
   */
  public void setType(Type type) {
    this.type = type;
  }

  /**
   * @param type
   */
  public void addType(Type type) {
    this.type.addType(type);
  }

  /**
   * @param type
   */
  public void addType(int type) {
    this.type.addType(type);
  }

  /**
   * @return
   */
  public AccountCapsule getAccount() {
    if (ArrayUtils.isEmpty(any)) {
      return null;
    }
    return new AccountCapsule(any);
  }

  /**
   * @return
   */
  public BytesCapsule getBytes() {
    if (ArrayUtils.isEmpty(any)) {
      return null;
    }
    return new BytesCapsule(any);
  }

  /**
   * @return
   */
  public TransactionCapsule getTransaction() {
    if (ArrayUtils.isEmpty(any)) {
      return null;
    }
    try {
      return new TransactionCapsule(any);
    } catch (BadItemException e) {
      return null;
    }
  }

  /**
   * @return
   */
  public BlockCapsule getBlock() {
    if (ArrayUtils.isEmpty(any)) {
      return null;
    }
    try {
      return new BlockCapsule(any);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * @return
   */
  public WitnessCapsule getWitness() {
    if (ArrayUtils.isEmpty(any)) {
      return null;
    }
    return new WitnessCapsule(any);

  }

  public VotesCapsule getVotes() {
    if (ArrayUtils.isEmpty(any)) {
      return null;
    }
    return new VotesCapsule(any);
  }

  /**
   * @return
   */
  public BytesCapsule getBlockIndex() {
    if (ArrayUtils.isEmpty(any)) {
      return null;
    }
    return new BytesCapsule(any);
  }

  /**
   * @return
   */
  public CodeCapsule getCode() {
    if (ArrayUtils.isEmpty(any)) {
      return null;
    }
    return new CodeCapsule(any);
  }

  /**
   * @return
   */
  public ContractCapsule getContract() {
    if (ArrayUtils.isEmpty(any)) {
      return null;
    }
    return new ContractCapsule(any);
  }


  public AssetIssueCapsule getAssetIssue() {
    if (ArrayUtils.isEmpty(any)) {
      return null;
    }
    return new AssetIssueCapsule(any);
  }

  public ProposalCapsule getProposal() {
    if (ArrayUtils.isEmpty(any)) {
      return null;
    }
    return new ProposalCapsule(any);
  }

  public BytesCapsule getDynamicProperties() {
    if (ArrayUtils.isEmpty(any)) {
      return null;
    }
    return new BytesCapsule(any);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }

    Value V = (Value) obj;
    if (Arrays.equals(this.any, V.getAny())) {
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new Integer(type.hashCode() + Arrays.hashCode(any)).hashCode();
  }

  public static Value create(byte[] any, int type) {
    return new Value(any, type);
  }

//    public static Value create(byte[] any, Type type) {
//        return new Value(any, type);
//    }

  public static Value create(byte[] any) {
    return new Value(any, Type.VALUE_TYPE_NORMAL);
  }
}
