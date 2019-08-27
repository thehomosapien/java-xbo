package org.xbo.core.services.http;

import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import io.netty.util.internal.StringUtil;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbo.api.GrpcAPI.Return;
import org.xbo.api.GrpcAPI.Return.response_code;
import org.xbo.api.GrpcAPI.TransactionExtention;
import org.xbo.common.utils.ByteArray;
import org.xbo.core.Wallet;
import org.xbo.core.capsule.TransactionCapsule;
import org.xbo.core.exception.ContractValidateException;
import org.xbo.protos.Contract.TriggerSmartContract;
import org.xbo.protos.Protocol.Transaction;
import org.xbo.protos.Protocol.Transaction.Contract.ContractType;


@Component
@Slf4j(topic = "API")
public class TriggerConstantContractServlet extends RateLimiterServlet {

  private final String functionSelector = "function_selector";

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
  }

  protected void validateParameter(String contract) {
    JSONObject jsonObject = JSONObject.parseObject(contract);
    if (!jsonObject.containsKey("owner_address")
        || StringUtil.isNullOrEmpty(jsonObject.getString("owner_address"))) {
      throw new InvalidParameterException("owner_address isn't set.");
    }
    if (!jsonObject.containsKey("contract_address")
        || StringUtil.isNullOrEmpty(jsonObject.getString("contract_address"))) {
      throw new InvalidParameterException("contract_address isn't set.");
    }
    if (!jsonObject.containsKey(functionSelector)
        || StringUtil.isNullOrEmpty(jsonObject.getString(functionSelector))) {
      throw new InvalidParameterException("function_selector isn't set.");
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    TriggerSmartContract.Builder build = TriggerSmartContract.newBuilder();
    TransactionExtention.Builder xboExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();
    boolean visible = false;
    try {
      String contract = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(contract);
      visible = Util.getVisiblePost(contract);
      validateParameter(contract);
      JsonFormat.merge(contract, build, visible);
      JSONObject jsonObject = JSONObject.parseObject(contract);
      String selector = jsonObject.getString(functionSelector);
      String parameter = jsonObject.getString("parameter");
      String data = Util.parseMethod(selector, parameter);
      build.setData(ByteString.copyFrom(ByteArray.fromHexString(data)));
      long feeLimit = Util.getJsonLongValue(jsonObject, "fee_limit");

      TransactionCapsule xboCap = wallet
          .createTransactionCapsule(build.build(), ContractType.TriggerSmartContract);

      Transaction.Builder txBuilder = xboCap.getInstance().toBuilder();
      Transaction.raw.Builder rawBuilder = xboCap.getInstance().getRawData().toBuilder();
      rawBuilder.setFeeLimit(feeLimit);
      txBuilder.setRawData(rawBuilder);

      Transaction xbo = wallet
          .triggerConstantContract(build.build(), new TransactionCapsule(txBuilder.build()),
              xboExtBuilder,
              retBuilder);
      xbo = Util.setTransactionPermissionId(jsonObject, xbo);
      xboExtBuilder.setTransaction(xbo);
      retBuilder.setResult(true).setCode(response_code.SUCCESS);
    } catch (ContractValidateException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getMessage()));
    } catch (Exception e) {
      String errString = null;
      if (e.getMessage() != null) {
        errString = e.getMessage().replaceAll("[\"]", "\'");
      }
      retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + errString));
    }
    xboExtBuilder.setResult(retBuilder);
    response.getWriter().println(Util.printTransactionExtention(xboExtBuilder.build(), visible));
  }
}