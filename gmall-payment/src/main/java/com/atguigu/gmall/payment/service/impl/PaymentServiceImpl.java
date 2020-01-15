package com.atguigu.gmall.payment.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.PaymentInfo;
import com.atguigu.gmall.config.ActiveMQUtil;
import com.atguigu.gmall.payment.mapper.PaymentMapper;
import com.atguigu.gmall.service.PaymentService;
import com.atguigu.gmall.util.HttpClient;
import com.github.wxpay.sdk.WXPayUtil;
import org.apache.activemq.ScheduledMessage;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tk.mybatis.mapper.entity.Example;

import javax.jms.*;
import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentServiceImpl implements PaymentService {

    // 服务号Id
    @Value("${appid}")
    private String appid;
    // 商户号Id
    @Value("${partner}")
    private String partner;
    // 密钥
    @Value("${partnerkey}")
    private String partnerkey;

    @Autowired
    private PaymentMapper paymentMapper;
    @Autowired
    private AlipayClient alipayClient;
    @Autowired
    private ActiveMQUtil activeMQUtil;

    @Override
    public void savePaymentInfo(PaymentInfo paymentInfo) {
        paymentMapper.insertSelective(paymentInfo);
    }

    @Override
    public void updatePaymentInfo(String outTradeNo, PaymentInfo paymentInfoUpd) {
        Example example = new Example(PaymentInfo.class);
        example.createCriteria().andEqualTo("outTradeNo",outTradeNo);
        paymentMapper.updateByExampleSelective(paymentInfoUpd,example);
    }

    @Override
    public PaymentInfo getPaymentInfo(PaymentInfo paymentInfo) {
        return paymentMapper.selectOne(paymentInfo);
    }

    @Override
    public boolean refund(String orderId) {
        AlipayTradeRefundRequest refundRequest = new AlipayTradeRefundRequest();
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOrderId(orderId);
        PaymentInfo paymentInfoQuery = getPaymentInfo(paymentInfo);

        Map<String,Object> map = new HashMap<>();
        map.put("out_trade_no",paymentInfoQuery.getOutTradeNo());
        map.put("refund_amount", paymentInfoQuery.getTotalAmount());

        refundRequest.setBizContent(JSON.toJSONString(map));
        AlipayTradeRefundResponse response = null;
        try {
            response = alipayClient.execute(refundRequest);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        if(response.isSuccess()){
            System.out.println("调用成功");
            return true;
        } else {
            System.out.println("调用失败");
            return false;
        }
    }

    @Override
    public Map createNative(String orderId, String totalAmount) {
        Map<String,String> param=new HashMap();//创建参数
        param.put("appid", appid);//公众号
        param.put("mch_id", partner);//商户号
        param.put("nonce_str", WXPayUtil.generateNonceStr());//随机字符串
        param.put("body", "尚硅谷");//商品描述
        param.put("out_trade_no", orderId);//商户订单号
        param.put("total_fee",totalAmount);//总金额（分）
        param.put("spbill_create_ip", "127.0.0.1");//IP
        param.put("notify_url", "http://d28658e346.wicp.vip/wx/callback/notify");//回调地址(随便写)
        param.put("trade_type", "NATIVE");//交易类型
        //生成xml
        try {
            String xmlParam = WXPayUtil.generateSignature(param, partnerkey);
            //调用HttpClient发送数据
            HttpClient httpClient = new HttpClient("https://api.mch.weixin.qq.com/pay/unifiedorder");
            //安全访问
            httpClient.setHttps(true);
            //放入参数
            httpClient.setParameter(param);
            httpClient.post();
            
            //获取结果
            String result = httpClient.getContent();
            Map<String, String> resultMap = WXPayUtil.xmlToMap(result);
            Map<String, String> map = new HashMap<>();
            map.put("code_url", resultMap.get("code_url"));//支付地址
            map.put("total_fee", totalAmount);//总金额
            map.put("out_trade_no",orderId);//订单号
            //返回结果
            return map;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap();
        }
    }

    @Override
    //消息提供者
    public void sendPaymentResult(PaymentInfo paymentInfo, String result) {
        Connection connection = activeMQUtil.getConnection();
        try {
            connection.start();
            //创建session
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            //创建队列
            Queue paymentResultQueue = session.createQueue("PAYMENT_RESULT_QUEUE");
            //创建消息提供者
            MessageProducer producer = session.createProducer(paymentResultQueue);
            ActiveMQMapMessage mapMessage = new ActiveMQMapMessage();
            //传入订单id和返回结果
            mapMessage.setString("orderId",paymentInfo.getOrderId());
            mapMessage.setString("result",result);
            producer.send(mapMessage);
            //事务提交
            session.commit();
            producer.close();
            session.close();
            connection.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean checkPayment(OrderInfo orderInfoQuery) {
        //判断OrderInfo是否有数据
        if(orderInfoQuery==null){
            return false;
        }
        //查询请求
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        Map<String, String> map = new HashMap<>();
        //根据第三方编号查询订单状态
        map.put("out_trade_no",orderInfoQuery.getOutTradeNo());
        request.setBizContent(JSON.toJSONString(map));
        AlipayTradeQueryResponse response = null;
        try {
             response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        if(response.isSuccess()){
            System.out.println("调用成功");
            if("TRADE_SUCCESS".equals(response.getTradeStatus()) || "TRADE_FINISHED".equals(response.getTradeStatus())){
                System.out.println("支付成功！");
                return true;
            }
        }else {
            System.out.println("调用失败");
            return false;
        }
        return false;
    }

    @Override
    public void closeOrderInfo(String orderId, int delaySec) {
        Connection connection = activeMQUtil.getConnection();
        try {
            connection.start();
            // 创建session
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            // 创建消息队列
            Queue paymentResultCheckQueue = session.createQueue("PAYMENT_RESULT_CHECK_QUEUE");
            // 创建消息提供者
            MessageProducer producer = session.createProducer(paymentResultCheckQueue);
            // 创建消息对象
            ActiveMQMapMessage activeMQMapMessage = new ActiveMQMapMessage();
            activeMQMapMessage.setString("orderId",orderId);
            // 开启延迟队列的参数设置
            activeMQMapMessage.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_CRON,delaySec*1000);
            // 发送消息
            producer.send(activeMQMapMessage);
            // 提交
            session.commit();
            // 关闭
            producer.close();
            session.close();
            connection.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }
}
