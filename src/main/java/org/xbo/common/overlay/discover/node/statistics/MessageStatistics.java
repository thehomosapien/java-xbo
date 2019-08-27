package org.xbo.common.overlay.discover.node.statistics;

import lombok.extern.slf4j.Slf4j;
import org.xbo.common.net.udp.message.UdpMessageTypeEnum;
import org.xbo.common.overlay.message.Message;
import org.xbo.core.net.message.FetchInvDataMessage;
import org.xbo.core.net.message.InventoryMessage;
import org.xbo.core.net.message.MessageTypes;
import org.xbo.core.net.message.TransactionsMessage;

@Slf4j
public class MessageStatistics {

  //udp discovery
  public final MessageCount discoverInPing = new MessageCount();
  public final MessageCount discoverOutPing = new MessageCount();
  public final MessageCount discoverInPong = new MessageCount();
  public final MessageCount discoverOutPong = new MessageCount();
  public final MessageCount discoverInFindNode = new MessageCount();
  public final MessageCount discoverOutFindNode = new MessageCount();
  public final MessageCount discoverInNeighbours = new MessageCount();
  public final MessageCount discoverOutNeighbours = new MessageCount();

  //tcp p2p
  public final MessageCount p2pInHello = new MessageCount();
  public final MessageCount p2pOutHello = new MessageCount();
  public final MessageCount p2pInPing = new MessageCount();
  public final MessageCount p2pOutPing = new MessageCount();
  public final MessageCount p2pInPong = new MessageCount();
  public final MessageCount p2pOutPong = new MessageCount();
  public final MessageCount p2pInDisconnect = new MessageCount();
  public final MessageCount p2pOutDisconnect = new MessageCount();

  //tcp xbo
  public final MessageCount xboInMessage = new MessageCount();
  public final MessageCount xboOutMessage = new MessageCount();

  public final MessageCount xboInSyncBlockChain = new MessageCount();
  public final MessageCount xboOutSyncBlockChain = new MessageCount();
  public final MessageCount xboInBlockChainInventory = new MessageCount();
  public final MessageCount xboOutBlockChainInventory = new MessageCount();

  public final MessageCount xboInTrxInventory = new MessageCount();
  public final MessageCount xboOutTrxInventory = new MessageCount();
  public final MessageCount xboInTrxInventoryElement = new MessageCount();
  public final MessageCount xboOutTrxInventoryElement = new MessageCount();

  public final MessageCount xboInBlockInventory = new MessageCount();
  public final MessageCount xboOutBlockInventory = new MessageCount();
  public final MessageCount xboInBlockInventoryElement = new MessageCount();
  public final MessageCount xboOutBlockInventoryElement = new MessageCount();

  public final MessageCount xboInTrxFetchInvData = new MessageCount();
  public final MessageCount xboOutTrxFetchInvData = new MessageCount();
  public final MessageCount xboInTrxFetchInvDataElement = new MessageCount();
  public final MessageCount xboOutTrxFetchInvDataElement = new MessageCount();

  public final MessageCount xboInBlockFetchInvData = new MessageCount();
  public final MessageCount xboOutBlockFetchInvData = new MessageCount();
  public final MessageCount xboInBlockFetchInvDataElement = new MessageCount();
  public final MessageCount xboOutBlockFetchInvDataElement = new MessageCount();


  public final MessageCount xboInTrx = new MessageCount();
  public final MessageCount xboOutTrx = new MessageCount();
  public final MessageCount xboInTrxs = new MessageCount();
  public final MessageCount xboOutTrxs = new MessageCount();
  public final MessageCount xboInBlock = new MessageCount();
  public final MessageCount xboOutBlock = new MessageCount();
  public final MessageCount xboOutAdvBlock = new MessageCount();

  public void addUdpInMessage(UdpMessageTypeEnum type) {
    addUdpMessage(type, true);
  }

  public void addUdpOutMessage(UdpMessageTypeEnum type) {
    addUdpMessage(type, false);
  }

  public void addTcpInMessage(Message msg) {
    addTcpMessage(msg, true);
  }

  public void addTcpOutMessage(Message msg) {
    addTcpMessage(msg, false);
  }

  private void addUdpMessage(UdpMessageTypeEnum type, boolean flag) {
    switch (type) {
      case DISCOVER_PING:
        if (flag) {
          discoverInPing.add();
        } else {
          discoverOutPing.add();
        }
        break;
      case DISCOVER_PONG:
        if (flag) {
          discoverInPong.add();
        } else {
          discoverOutPong.add();
        }
        break;
      case DISCOVER_FIND_NODE:
        if (flag) {
          discoverInFindNode.add();
        } else {
          discoverOutFindNode.add();
        }
        break;
      case DISCOVER_NEIGHBORS:
        if (flag) {
          discoverInNeighbours.add();
        } else {
          discoverOutNeighbours.add();
        }
        break;
      default:
        break;
    }
  }

  private void addTcpMessage(Message msg, boolean flag) {

    if (flag) {
      xboInMessage.add();
    } else {
      xboOutMessage.add();
    }

    switch (msg.getType()) {
      case P2P_HELLO:
        if (flag) {
          p2pInHello.add();
        } else {
          p2pOutHello.add();
        }
        break;
      case P2P_PING:
        if (flag) {
          p2pInPing.add();
        } else {
          p2pOutPing.add();
        }
        break;
      case P2P_PONG:
        if (flag) {
          p2pInPong.add();
        } else {
          p2pOutPong.add();
        }
        break;
      case P2P_DISCONNECT:
        if (flag) {
          p2pInDisconnect.add();
        } else {
          p2pOutDisconnect.add();
        }
        break;
      case SYNC_BLOCK_CHAIN:
        if (flag) {
          xboInSyncBlockChain.add();
        } else {
          xboOutSyncBlockChain.add();
        }
        break;
      case BLOCK_CHAIN_INVENTORY:
        if (flag) {
          xboInBlockChainInventory.add();
        } else {
          xboOutBlockChainInventory.add();
        }
        break;
      case INVENTORY:
        InventoryMessage inventoryMessage = (InventoryMessage) msg;
        int inventorySize = inventoryMessage.getInventory().getIdsCount();
        if (flag) {
          if (inventoryMessage.getInvMessageType() == MessageTypes.XBO) {
            xboInTrxInventory.add();
            xboInTrxInventoryElement.add(inventorySize);
          } else {
            xboInBlockInventory.add();
            xboInBlockInventoryElement.add(inventorySize);
          }
        } else {
          if (inventoryMessage.getInvMessageType() == MessageTypes.XBO) {
            xboOutTrxInventory.add();
            xboOutTrxInventoryElement.add(inventorySize);
          } else {
            xboOutBlockInventory.add();
            xboOutBlockInventoryElement.add(inventorySize);
          }
        }
        break;
      case FETCH_INV_DATA:
        FetchInvDataMessage fetchInvDataMessage = (FetchInvDataMessage) msg;
        int fetchSize = fetchInvDataMessage.getInventory().getIdsCount();
        if (flag) {
          if (fetchInvDataMessage.getInvMessageType() == MessageTypes.XBO) {
            xboInTrxFetchInvData.add();
            xboInTrxFetchInvDataElement.add(fetchSize);
          } else {
            xboInBlockFetchInvData.add();
            xboInBlockFetchInvDataElement.add(fetchSize);
          }
        } else {
          if (fetchInvDataMessage.getInvMessageType() == MessageTypes.XBO) {
            xboOutTrxFetchInvData.add();
            xboOutTrxFetchInvDataElement.add(fetchSize);
          } else {
            xboOutBlockFetchInvData.add();
            xboOutBlockFetchInvDataElement.add(fetchSize);
          }
        }
        break;
      case XBOS:
        TransactionsMessage transactionsMessage = (TransactionsMessage) msg;
        if (flag) {
          xboInTrxs.add();
          xboInTrx.add(transactionsMessage.getTransactions().getTransactionsCount());
        } else {
          xboOutTrxs.add();
          xboOutTrx.add(transactionsMessage.getTransactions().getTransactionsCount());
        }
        break;
      case XBO:
        if (flag) {
          xboInMessage.add();
        } else {
          xboOutMessage.add();
        }
        break;
      case BLOCK:
        if (flag) {
          xboInBlock.add();
        }
        xboOutBlock.add();
        break;
      default:
        break;
    }
  }

}
