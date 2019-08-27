package org.xbo.core.net.messagehandler;

import com.googlecode.cqengine.query.simple.In;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbo.core.config.args.Args;
import org.xbo.core.exception.P2pException;
import org.xbo.core.exception.P2pException.TypeEnum;
import org.xbo.core.net.XBONetDelegate;
import org.xbo.core.net.message.TransactionMessage;
import org.xbo.core.net.message.TransactionsMessage;
import org.xbo.core.net.message.XBOMessage;
import org.xbo.core.net.peer.Item;
import org.xbo.core.net.service.AdvService;
import org.xbo.core.net.peer.PeerConnection;
import org.xbo.protos.Protocol.Inventory.InventoryType;
import org.xbo.protos.Protocol.ReasonCode;
import org.xbo.protos.Protocol.Transaction;
import org.xbo.protos.Protocol.Transaction.Contract.ContractType;

@Slf4j(topic = "net")
@Component
public class TransactionsMsgHandler implements XBOMsgHandler {

  @Autowired
  private XBONetDelegate xboNetDelegate;

  @Autowired
  private AdvService advService;

  private static int MAX_XBO_SIZE = 50_000;

  private static int MAX_SMART_CONTRACT_SUBMIT_SIZE = 100;

//  private static int TIME_OUT = 10 * 60 * 1000;

  private BlockingQueue<TrxEvent> smartContractQueue = new LinkedBlockingQueue(MAX_XBO_SIZE);

  private BlockingQueue<Runnable> queue = new LinkedBlockingQueue();

  private int threadNum = Args.getInstance().getValidateSignThreadNum();
  private ExecutorService xboHandlePool = new ThreadPoolExecutor(threadNum, threadNum, 0L,
      TimeUnit.MILLISECONDS, queue);

  private ScheduledExecutorService smartContractExecutor = Executors
      .newSingleThreadScheduledExecutor();

  class TrxEvent {

    @Getter
    private PeerConnection peer;
    @Getter
    private TransactionMessage msg;
    @Getter
    private long time;

    public TrxEvent(PeerConnection peer, TransactionMessage msg) {
      this.peer = peer;
      this.msg = msg;
      this.time = System.currentTimeMillis();
    }
  }

  public void init() {
    handleSmartContract();
  }

  public void close() {
    smartContractExecutor.shutdown();
  }

  public boolean isBusy() {
    return queue.size() + smartContractQueue.size() > MAX_XBO_SIZE;
  }

  @Override
  public void processMessage(PeerConnection peer, XBOMessage msg) throws P2pException {
    TransactionsMessage transactionsMessage = (TransactionsMessage) msg;
    check(peer, transactionsMessage);
    for (Transaction xbo : transactionsMessage.getTransactions().getTransactionsList()) {
      int type = xbo.getRawData().getContract(0).getType().getNumber();
      if (type == ContractType.TriggerSmartContract_VALUE
          || type == ContractType.CreateSmartContract_VALUE) {
        if (!smartContractQueue.offer(new TrxEvent(peer, new TransactionMessage(xbo)))) {
          logger.warn("Add smart contract failed, queueSize {}:{}", smartContractQueue.size(),
              queue.size());
        }
      } else {
        xboHandlePool.submit(() -> handleTransaction(peer, new TransactionMessage(xbo)));
      }
    }
  }

  private void check(PeerConnection peer, TransactionsMessage msg) throws P2pException {
    for (Transaction xbo : msg.getTransactions().getTransactionsList()) {
      Item item = new Item(new TransactionMessage(xbo).getMessageId(), InventoryType.XBO);
      if (!peer.getAdvInvRequest().containsKey(item)) {
        throw new P2pException(TypeEnum.BAD_MESSAGE,
            "xbo: " + msg.getMessageId() + " without request.");
      }
      peer.getAdvInvRequest().remove(item);
    }
  }

  private void handleSmartContract() {
    smartContractExecutor.scheduleWithFixedDelay(() -> {
      try {
        while (queue.size() < MAX_SMART_CONTRACT_SUBMIT_SIZE) {
          TrxEvent event = smartContractQueue.take();
          xboHandlePool.submit(() -> handleTransaction(event.getPeer(), event.getMsg()));
        }
      } catch (Exception e) {
        logger.error("Handle smart contract exception.", e);
      }
    }, 1000, 20, TimeUnit.MILLISECONDS);
  }

  private void handleTransaction(PeerConnection peer, TransactionMessage xbo) {
    if (peer.isDisconnect()) {
      logger.warn("Drop xbo {} from {}, peer is disconnect.", xbo.getMessageId(),
          peer.getInetAddress());
      return;
    }

    if (advService.getMessage(new Item(xbo.getMessageId(), InventoryType.XBO)) != null) {
      return;
    }

    try {
      xboNetDelegate.pushTransaction(xbo.getTransactionCapsule());
      advService.broadcast(xbo);
    } catch (P2pException e) {
      logger.warn("Trx {} from peer {} process failed. type: {}, reason: {}",
          xbo.getMessageId(), peer.getInetAddress(), e.getType(), e.getMessage());
      if (e.getType().equals(TypeEnum.BAD_XBO)) {
        peer.disconnect(ReasonCode.BAD_TX);
      }
    } catch (Exception e) {
      logger.error("Trx {} from peer {} process failed.", xbo.getMessageId(), peer.getInetAddress(),
          e);
    }
  }
}