package org.xbo.core.services.http;

import com.alibaba.fastjson.JSONObject;
import java.io.IOException;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbo.api.GrpcAPI;
import org.xbo.common.utils.ByteArray;
import org.xbo.core.Wallet;
import org.xbo.core.zen.address.DiversifierT;
import org.xbo.core.zen.address.IncomingViewingKey;


@Component
@Slf4j(topic = "API")
public class GetZenPaymentAddressServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String input = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(input);
      JSONObject jsonObject = JSONObject.parseObject(input);
      boolean visible = Util.getVisiblePost(input);

      String ivk = jsonObject.getString("ivk");
      String d = jsonObject.getString("d");

      GrpcAPI.PaymentAddressMessage s = wallet
          .getPaymentAddress(new IncomingViewingKey(ByteArray.fromHexString(ivk)),
              new DiversifierT(ByteArray.fromHexString(d)));

      response.getWriter()
          .println(JsonFormat.printToString(s, visible));

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
    try {
      boolean visible = Util.getVisible(request);
      String ivk = request.getParameter("ivk");
      String d = request.getParameter("d");

      GrpcAPI.PaymentAddressMessage s = wallet
          .getPaymentAddress(new IncomingViewingKey(ByteArray.fromHexString(ivk)),
              new DiversifierT(ByteArray.fromHexString(d)));

      response.getWriter()
          .println(JsonFormat.printToString(s, visible));

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
