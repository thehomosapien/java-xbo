package org.xbo.common.overlay.server;

import com.google.protobuf.ByteString;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.xbo.common.backup.BackupManager;
import org.xbo.common.backup.BackupManager.BackupStatusEnum;
import org.xbo.common.overlay.discover.node.Node;
import org.xbo.core.config.args.Args;
import org.xbo.core.db.WitnessScheduleStore;
import org.xbo.core.services.WitnessService;
import org.xbo.protos.Protocol.ReasonCode;

@Slf4j(topic = "net")
@Component
public class FastForward {

  @Autowired
  private ApplicationContext ctx;

  private ChannelManager channelManager;

  private BackupManager backupManager;

  private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

  private Args args = Args.getInstance();
  private List<Node> fastForwardNodes = args.getFastForwardNodes();
  private ByteString witnessAddress = ByteString
      .copyFrom(args.getLocalWitnesses().getWitnessAccountAddress());
  private int keySize = args.getLocalWitnesses().getPrivateKeys().size();

  public void init() {

    logger.info("Fast forward config, isWitness: {}, keySize: {}, fastForwardNodes: {}",
        args.isWitness(), keySize, fastForwardNodes.size());

    if (!args.isWitness() || keySize == 0 || fastForwardNodes.size() == 0) {
      return;
    }

    channelManager = ctx.getBean(ChannelManager.class);
    backupManager = ctx.getBean(BackupManager.class);
    WitnessScheduleStore witnessScheduleStore = ctx.getBean(WitnessScheduleStore.class);

    executorService.scheduleWithFixedDelay(() -> {
      try {
        if (witnessScheduleStore.getActiveWitnesses().contains(witnessAddress) &&
            backupManager.getStatus().equals(BackupStatusEnum.MASTER)) {
          connect();
        } else {
          disconnect();
        }
      } catch (Throwable t) {
        logger.info("Execute failed.", t);
      }
    }, 0, 1, TimeUnit.MINUTES);
  }

  private void connect() {
    fastForwardNodes.forEach(node -> {
      InetAddress address = new InetSocketAddress(node.getHost(), node.getPort()).getAddress();
      channelManager.getActiveNodes().put(address, node);
    });
  }

  private void disconnect() {
    fastForwardNodes.forEach(node -> {
      InetAddress address = new InetSocketAddress(node.getHost(), node.getPort()).getAddress();
      channelManager.getActiveNodes().remove(address);
      channelManager.getActivePeers().forEach(channel -> {
        if (channel.getInetAddress().equals(address)) {
          channel.disconnect(ReasonCode.RESET);
        }
      });
    });
  }
}
