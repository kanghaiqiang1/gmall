package com.atguigu.gmall.payment.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.service.PaymentService;
import com.atguigu.gmall.util.StreamUtil;
import com.github.wxpay.sdk.WXPayUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@Controller
public class WxPayController {

    // 密钥
    @Value("${partnerkey}")
    private String partnerkey;

    @Reference
    private PaymentService paymentService;
    @Reference
    private OrderService orderService;

    @RequestMapping("wx/submit")
    @ResponseBody   //Object --> Json
    public Map createNative(String orderId){
        //根据订单id查询订单信息
        OrderInfo orderInfo = orderService.getOrderInfo(orderId);
        //根据订单id和订单总金额创建二维码
        Map map = paymentService.createNative(orderId,orderInfo.getTotalAmount().toString());
        System.out.println(map.get("code_url"));
        return map;
    }

    @RequestMapping("")
    @ResponseBody
    public String wxNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        //获取流
        ServletInputStream inputStream = request.getInputStream();
        String xmlParam = StreamUtil.inputStream2String(inputStream, "UTF-8");

        //验签
        if(WXPayUtil.isSignatureValid(xmlParam,partnerkey)){
            //验签成功
            Map<String, String> paramMap = WXPayUtil.xmlToMap(xmlParam);
            String resultCode = paramMap.get("result_code");
            if(resultCode!=null && resultCode.equals("SUCCESS")){
                //修改支付状态    消息队列形式

                //支付成功  返回参数
                Map<String, String> returnMap  = new HashMap<>();
                returnMap.put("return_code","SUCCESS");
                returnMap.put("return_msg","OK");
                //微信是以xml形式传递数据
                String returnXml = WXPayUtil.mapToXml(returnMap);
                response.setContentType("text/xml");
                System.out.println("交易编号："+paramMap.get("out_trade_no")+"支付成功！");
                return returnXml;
            }else {
                System.out.println(paramMap.get("return_code")+"---"+paramMap.get("return_msg"));
                return null;
            }
        }else {
            return null;
        }
    }

}
