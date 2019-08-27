package org.xbo.core.db2;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xbo.common.application.Application;
import org.xbo.common.application.ApplicationFactory;
import org.xbo.common.application.XBOApplicationContext;
import org.xbo.common.storage.leveldb.LevelDbDataSourceImpl;
import org.xbo.common.utils.FileUtil;
import org.xbo.core.Constant;
import org.xbo.core.config.DefaultConfig;
import org.xbo.core.config.args.Args;
import org.xbo.core.db.CheckTmpStore;
import org.xbo.core.db2.RevokingDbWithCacheNewValueTest.TestRevokingXBOStore;
import org.xbo.core.db2.RevokingDbWithCacheNewValueTest.TestSnapshotManager;
import org.xbo.core.db2.SnapshotRootTest.ProtoCapsuleTest;
import org.xbo.core.db2.core.ISession;
import org.xbo.core.db2.core.SnapshotManager;
import org.xbo.core.exception.BadItemException;
import org.xbo.core.exception.ItemNotFoundException;

@Slf4j
public class SnapshotManagerTest {

  private SnapshotManager revokingDatabase;
  private XBOApplicationContext context;
  private Application appT;
  private TestRevokingXBOStore xboDatabase;

  @Before
  public void init() {
    Args.setParam(new String[]{"-d", "output_revokingStore_test"},
        Constant.TEST_CONF);
    context = new XBOApplicationContext(DefaultConfig.class);
    appT = ApplicationFactory.create(context);
    revokingDatabase = new TestSnapshotManager();
    revokingDatabase.enable();
    xboDatabase = new TestRevokingXBOStore("testSnapshotManager-test");
    revokingDatabase.add(xboDatabase.getRevokingDB());
    revokingDatabase.setCheckTmpStore(context.getBean(CheckTmpStore.class));
  }

  @After
  public void removeDb() {
    Args.clearParam();
    appT.shutdownServices();
    appT.shutdown();
    context.destroy();
    xboDatabase.close();
    FileUtil.deleteDir(new File("output_revokingStore_test"));
    revokingDatabase.getCheckTmpStore().getDbSource().closeDB();
    xboDatabase.close();
  }

  @Test
  public synchronized void testRefresh()
      throws BadItemException, ItemNotFoundException {
    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }

    revokingDatabase.setMaxFlushCount(0);
    revokingDatabase.setUnChecked(false);
    revokingDatabase.setMaxSize(5);
    ProtoCapsuleTest protoCapsule = new ProtoCapsuleTest("refresh".getBytes());
    for (int i = 1; i < 11; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("refresh" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        xboDatabase.put(protoCapsule.getData(), testProtoCapsule);
        tmpSession.commit();
      }
    }

    revokingDatabase.flush();
    Assert.assertEquals(new ProtoCapsuleTest("refresh10".getBytes()),
        xboDatabase.get(protoCapsule.getData()));
  }

  @Test
  public synchronized void testClose() {
    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }

    revokingDatabase.setMaxFlushCount(0);
    revokingDatabase.setUnChecked(false);
    revokingDatabase.setMaxSize(5);
    ProtoCapsuleTest protoCapsule = new ProtoCapsuleTest("close".getBytes());
    for (int i = 1; i < 11; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("close" + i).getBytes());
      try (ISession _ = revokingDatabase.buildSession()) {
        xboDatabase.put(protoCapsule.getData(), testProtoCapsule);
      }
    }
    Assert.assertEquals(null,
        xboDatabase.get(protoCapsule.getData()));

  }
}
