package com.atguigu.gmall.order.mq;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.enums.ProcessStatus;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.MapMessage;

@Component
public class OrderConsumer {

    @Autowired
    private OrderService orderService;
    @Reference
    private PaymentService paymentService;

    //destination：消息队列名称    containerFactory：监控器名称
    @JmsListener(destination = "PAYMENT_RESULT_QUEUE",containerFactory = "jmsQueueListener")
    public void consumerPaymentResult(MapMessage mapMessage) throws JMSException {
        String orderId = mapMessage.getString("orderId");
        String result = mapMessage.getString("result");
        if("success".equals(result)){
            orderService.updateOrderStatus(orderId, ProcessStatus.PAID);
            //通知减库存
            orderService.sendOrderStatus(orderId);
            //更改订单状态
            orderService.updateOrderStatus(orderId,ProcessStatus.NOTIFIED_WARE);
        }else {
            orderService.updateOrderStatus(orderId, ProcessStatus.UNPAID);
        }
        System.out.println("result = " + result);
        System.out.println("orderId = " + orderId);
    }

    //监听减库存结果
    @JmsListener(destination = "SKU_DEDUCT_QUEUE",containerFactory = "jmsQueueListener")
    public void consumeSkuDeduct(MapMessage mapMessage) throws JMSException {
        String orderId = mapMessage.getString("orderId");
        String  status = mapMessage.getString("status");
        if("DEDUCTED".equals(status)){
            orderService.updateOrderStatus(  orderId , ProcessStatus.WAITING_DELEVER);
        }else{
            orderService.updateOrderStatus(  orderId , ProcessStatus.STOCK_EXCEPTION);
        }
    }

    @JmsListener(destination = "PAYMENT_RESULT_CHECK_QUEUE",containerFactory = "jmsQueueListener")
    public void consumerCheckQueue(MapMessage mapMessage) throws JMSException {
        // 获取消息队列中的参数
        String orderId = mapMessage.getString("orderId");

        OrderInfo orderInfo = orderService.getOrderInfo(orderId);
        // 判断是否支付成功？
        boolean result = paymentService.checkPayment(orderInfo);
        //支付失败
        if(!result){
            //关闭订单  更新订单状态
            orderService.updateOrderStatus(orderId,ProcessStatus.CLOSED);
            System.out.println("订单已经关闭！");
        }
    }

}
