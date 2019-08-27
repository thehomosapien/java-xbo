package org.xbo.common.application;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.xbo.common.logsfilter.EventPluginLoader;
import org.xbo.common.overlay.discover.DiscoverServer;
import org.xbo.common.overlay.discover.node.NodeManager;
import org.xbo.common.overlay.server.ChannelManager;
import org.xbo.core.db.Manager;

public class XBOApplicationContext extends AnnotationConfigApplicationContext {

  public XBOApplicationContext() {
  }

  public XBOApplicationContext(DefaultListableBeanFactory beanFactory) {
    super(beanFactory);
  }

  public XBOApplicationContext(Class<?>... annotatedClasses) {
    super(annotatedClasses);
  }

  public XBOApplicationContext(String... basePackages) {
    super(basePackages);
  }

  @Override
  public void destroy() {

    Application appT = ApplicationFactory.create(this);
    appT.shutdownServices();
    appT.shutdown();

    DiscoverServer discoverServer = getBean(DiscoverServer.class);
    discoverServer.close();
    ChannelManager channelManager = getBean(ChannelManager.class);
    channelManager.close();
    NodeManager nodeManager = getBean(NodeManager.class);
    nodeManager.close();

    Manager dbManager = getBean(Manager.class);
    dbManager.stopRepushThread();
    dbManager.stopRepushTriggerThread();
    super.destroy();
  }
}
