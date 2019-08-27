package org.xbo.core.net;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xbo.common.overlay.server.Channel;
import org.xbo.common.overlay.server.MessageQueue;
import org.xbo.core.net.message.XBOMessage;
import org.xbo.core.net.peer.PeerConnection;

@Component
@Scope("prototype")
public class XBONetHandler extends SimpleChannelInboundHandler<XBOMessage> {

  protected PeerConnection peer;

  private MessageQueue msgQueue;

  @Autowired
  private XBONetService xboNetService;

//  @Autowired
//  private XBONetHandler (final ApplicationContext ctx){
//    xboNetService = ctx.getBean(XBONetService.class);
//  }

  @Override
  public void channelRead0(final ChannelHandlerContext ctx, XBOMessage msg) throws Exception {
    msgQueue.receivedMessage(msg);
    xboNetService.onMessage(peer, msg);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    peer.processException(cause);
  }

  public void setMsgQueue(MessageQueue msgQueue) {
    this.msgQueue = msgQueue;
  }

  public void setChannel(Channel channel) {
    this.peer = (PeerConnection) channel;
  }

}