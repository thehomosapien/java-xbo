package org.xbo.core.services.http;

import com.alibaba.fastjson.JSONObject;
import java.io.IOException;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbo.core.Wallet;
import org.xbo.protos.Contract.UpdateAssetContract;
import org.xbo.protos.Protocol.Transaction;
import org.xbo.protos.Protocol.Transaction.Contract.ContractType;


@Component
@Slf4j(topic = "API")
public class UpdateAssetServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {

  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String contract = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(contract);
      boolean visible = Util.getVisiblePost(contract);
      UpdateAssetContract.Builder build = UpdateAssetContract.newBuilder();
      JsonFormat.merge(contract, build, visible);
      Transaction tx = wallet
          .createTransactionCapsule(build.build(), ContractType.UpdateAssetContract).getInstance();
      JSONObject jsonObject = JSONObject.parseObject(contract);
      tx = Util.setTransactionPermissionId(jsonObject, tx);

      response.getWriter().println(Util.printCreateTransaction(tx, visible));
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
