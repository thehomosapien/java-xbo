package org.xbo.core.services.http;

import java.io.IOException;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbo.api.GrpcAPI.BlockLimit;
import org.xbo.api.GrpcAPI.BlockList;
import org.xbo.core.Wallet;


@Component
@Slf4j(topic = "API")
public class GetBlockByLimitNextServlet extends RateLimiterServlet {

  private static final long BLOCK_LIMIT_NUM = 100;
  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      long startNum = Long.parseLong(request.getParameter("startNum"));
      long endNum = Long.parseLong(request.getParameter("endNum"));
      if (endNum > 0 && endNum > startNum && endNum - startNum <= BLOCK_LIMIT_NUM) {
        BlockList reply = wallet.getBlocksByLimitNext(startNum, endNum - startNum);
        if (reply != null) {
          response.getWriter().println(Util.printBlockList(reply, visible));
          return;
        }
      }
      response.getWriter().println("{}");
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
      String input = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(input);
      boolean visible = Util.getVisiblePost(input);
      BlockLimit.Builder build = BlockLimit.newBuilder();
      JsonFormat.merge(input, build, visible);
      long startNum = build.getStartNum();
      long endNum = build.getEndNum();
      if (endNum > 0 && endNum > startNum && endNum - startNum <= BLOCK_LIMIT_NUM) {
        BlockList reply = wallet.getBlocksByLimitNext(startNum, endNum - startNum);
        if (reply != null) {
          response.getWriter().println(Util.printBlockList(reply, visible));
          return;
        }
      }
      response.getWriter().println("{}");
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