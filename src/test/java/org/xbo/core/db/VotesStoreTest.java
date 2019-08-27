package org.xbo.core.db;

import com.google.protobuf.ByteString;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xbo.common.application.XBOApplicationContext;
import org.xbo.common.utils.FileUtil;
import org.xbo.core.Constant;
import org.xbo.core.capsule.VotesCapsule;
import org.xbo.core.config.DefaultConfig;
import org.xbo.core.config.args.Args;
import org.xbo.protos.Protocol.Vote;

@Slf4j
public class VotesStoreTest {

  private static final String dbPath = "output-votesStore-test";
  private static XBOApplicationContext context;
  VotesStore votesStore;

  static {
    Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
    context = new XBOApplicationContext(DefaultConfig.class);
  }

  @Before
  public void initDb() {
    this.votesStore = context.getBean(VotesStore.class);
  }

  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }

  @Test
  public void putAndGetVotes() {
    List<Vote> oldVotes = new ArrayList<Vote>();

    VotesCapsule votesCapsule = new VotesCapsule(ByteString.copyFromUtf8("100000000x"), oldVotes);
    this.votesStore.put(votesCapsule.createDbKey(), votesCapsule);

    Assert.assertTrue("votesStore is empyt", votesStore.iterator().hasNext());
    Assert.assertTrue(votesStore.has(votesCapsule.createDbKey()));
    VotesCapsule votesSource = this.votesStore
        .get(ByteString.copyFromUtf8("100000000x").toByteArray());
    Assert.assertEquals(votesCapsule.getAddress(), votesSource.getAddress());
    Assert.assertEquals(ByteString.copyFromUtf8("100000000x"), votesSource.getAddress());

//    votesCapsule = new VotesCapsule(ByteString.copyFromUtf8(""), oldVotes);
//    this.votesStore.put(votesCapsule.createDbKey(), votesCapsule);
//    votesSource = this.votesStore.get(ByteString.copyFromUtf8("").toByteArray());
//    Assert.assertEquals(votesStore.getAllVotes().size(), 2);
//    Assert.assertEquals(votesCapsule.getAddress(), votesSource.getAddress());
//    Assert.assertEquals(null, votesSource.getAddress());
  }
}