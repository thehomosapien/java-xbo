package org.xbo.core.services.http;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbo.api.GrpcAPI.BytesMessage;
import org.xbo.common.utils.ByteArray;
import org.xbo.core.Wallet;

@Component
@Slf4j(topic = "API")
public class GetSpendingKeyServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      BytesMessage reply = wallet.getSpendingKey();

      String base58check = Wallet.encode58Check(reply.toByteArray());
      String hexString = ByteArray.toHexString(reply.toByteArray());
      System.out.println("b58 is: " + base58check + ", hex is: " + hexString);
      response.getWriter().println(JsonFormat.printToString(reply, visible));
    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      BytesMessage reply = wallet.getSpendingKey();
      if (reply != null) {
        response.getWriter().println(JsonFormat.printToString(reply, visible));
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }
}
