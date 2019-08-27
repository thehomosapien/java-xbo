package org.xbo.core.services.http;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbo.api.GrpcAPI.BytesMessage;
import org.xbo.core.Wallet;


@Component
@Slf4j(topic = "API")
public class GetRcmServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      BytesMessage result = wallet.getRcm();
      boolean visible = Util.getVisible(request);

      if (result != null) {
        response.getWriter().println(JsonFormat.printToString(result, visible));
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

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    doPost(request, response);
  }
}
