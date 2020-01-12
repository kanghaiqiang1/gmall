package com.atguigu.gmall.payment.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.PaymentInfo;
import com.atguigu.gmall.bean.enums.PaymentStatus;
import com.atguigu.gmall.config.LoginRequire;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.apache.catalina.manager.Constants.CHARSET;

@Controller
public class PaymentController {

    @Reference
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private AlipayClient alipayClient;

    //http://payment.gmall.com/index?orderId=112
    @RequestMapping("index")
    @LoginRequire
    public String index(HttpServletRequest request){
        String orderId = request.getParameter("orderId");
        OrderInfo orderInfo = orderService.getOrderInfo(orderId);
        request.setAttribute("orderId",orderId);
        request.setAttribute("totalAmount",orderInfo.getTotalAmount());
        return "index";
    }

    //http://payment.gmall.com/alipay/submit
    @RequestMapping("alipay/submit")
    @ResponseBody
    public String submitPayment(HttpServletRequest request, HttpServletResponse response){
        String orderId = request.getParameter("orderId");
        //根据订单id查询订单信息
        OrderInfo orderInfo = orderService.getOrderInfo(orderId);
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOrderId(orderId);
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());
        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());
        paymentInfo.setSubject("-----");
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID);
        paymentInfo.setCreateTime(new Date());
        //保存支付订单信息
        paymentService.savePaymentInfo(paymentInfo);
        //生成二维码
        //创建API对应的request
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
        //获取同步请求地址
        alipayRequest.setReturnUrl(AlipayConfig.return_payment_url);
        //获取异步请求地址
        alipayRequest.setNotifyUrl(AlipayConfig.notify_payment_url);
        //放入对应字段
        Map<String,Object> bizContnetMap = new HashMap<>();
        bizContnetMap.put("out_trade_no",paymentInfo.getOutTradeNo());
        bizContnetMap.put("product_code","FAST_INSTANT_TRADE_PAY");
        bizContnetMap.put("subject",paymentInfo.getSubject());
        bizContnetMap.put("total_amount",paymentInfo.getTotalAmount());

        alipayRequest.setBizContent(JSON.toJSONString(bizContnetMap));
        String form="";
        try {
            //调用SDK生成表单
            form = alipayClient.pageExecute(alipayRequest).getBody();
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        //字符集
        response.setContentType("text/html;charset="+CHARSET);
        return form;
    }

    @RequestMapping("/alipay/callback/return")
    public String callbackReturn(){
        return "redirect:"+AlipayConfig.return_order_url;
    }

    @RequestMapping("/alipay/callback/notify")
    @ResponseBody
    public String paymentNotify(@RequestParam Map<String,String> paramMap,HttpServletRequest request) throws AlipayApiException {
        boolean signVerified = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key, CHARSET, AlipayConfig.sign_type);
        String outTradeNo = paramMap.get("out_trade_no");
        if(signVerified){
            //如果支付状态为PAID或CLOSE，则返回fail
            PaymentInfo paymentInfo = new PaymentInfo();
            paymentInfo.setOutTradeNo(outTradeNo);
            PaymentInfo paymentInfoHas  = paymentService.getPaymentInfo(paymentInfo);
            if(PaymentStatus.PAID == paymentInfoHas.getPaymentStatus() || PaymentStatus.ClOSED == paymentInfoHas.getPaymentStatus()){
                return "failure";
            }

            String trade_status = paramMap.get("trade_status");
            //验证支付结果内容  支付成功    验签成功
            if("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)){
                //支付成功后修改支付状态
                PaymentInfo paymentInfoUpd = new PaymentInfo();
                //设置支付状态
                paymentInfoUpd.setPaymentStatus(PaymentStatus.PAID);
                //设置创建时间
                paymentInfoUpd.setCallbackTime(new Date());
                //设置内容
                paymentInfoUpd.setCallbackContent(paramMap.toString());
                paymentService.updatePaymentInfo(outTradeNo,paymentInfoUpd);
                return "success";
            }else {
                //验签失败
            }
        }
        return "failure";
    }

    @RequestMapping("refund")
    @ResponseBody
    public String refund(String orderId){
        boolean flag = paymentService.refund(orderId);
        //更改订单的支付状态
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOrderId(orderId);
        PaymentInfo paymentInfoQuery = paymentService.getPaymentInfo(paymentInfo);
        paymentInfoQuery.setPaymentStatus(PaymentStatus.ClOSED);
        paymentService.updatePaymentInfo(paymentInfoQuery.getOutTradeNo(),paymentInfoQuery);
        System.out.println("flag:"+flag);
        return "退款"+flag;
    }

}
