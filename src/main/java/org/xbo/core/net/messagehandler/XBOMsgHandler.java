package org.xbo.core.net.messagehandler;

import org.xbo.core.exception.P2pException;
import org.xbo.core.net.message.XBOMessage;
import org.xbo.core.net.peer.PeerConnection;

public interface XBOMsgHandler {

  void processMessage(PeerConnection peer, XBOMessage msg) throws P2pException;

}
