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


@Component
@Slf4j(topic = "API")
public class GetIncomingViewingKeyServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String input = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(input);
      boolean visible = Util.getVisiblePost(input);
      JSONObject jsonObject = JSONObject.parseObject(input);

      String ak = jsonObject.getString("ak");
      String nk = jsonObject.getString("nk");

      GrpcAPI.IncomingViewingKeyMessage ivk = wallet
          .getIncomingViewingKey(ByteArray.fromHexString(ak), ByteArray.fromHexString(nk));

      response.getWriter()
          .println(JsonFormat.printToString(ivk, visible));

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
      String ak = request.getParameter("ak");
      String nk = request.getParameter("nk");

      GrpcAPI.IncomingViewingKeyMessage ivk = wallet
          .getIncomingViewingKey(ByteArray.fromHexString(ak), ByteArray.fromHexString(nk));

      response.getWriter()
          .println(JsonFormat.printToString(ivk, visible));
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
