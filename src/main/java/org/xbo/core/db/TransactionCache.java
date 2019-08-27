package org.xbo.core.db;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xbo.common.utils.ByteArray;
import org.xbo.common.utils.Sha256Hash;
import org.xbo.core.capsule.BlockCapsule;
import org.xbo.core.capsule.BytesCapsule;
import org.xbo.core.capsule.TransactionCapsule;
import org.xbo.core.db.KhaosDatabase.KhaosBlock;
import org.xbo.core.db2.common.TxCacheDB;
import org.xbo.core.exception.BadItemException;
import org.xbo.core.exception.StoreException;

@Slf4j
public class TransactionCache extends XBOStoreWithRevoking<BytesCapsule> {

  @Autowired
  public TransactionCache(@Value("trans-cache") String dbName) {
    super(dbName, TxCacheDB.class);
  }
}
