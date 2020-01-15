package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.PaymentInfo;

import java.util.Map;

public interface PaymentService {

    /**
     * 保存交易记录
     */
    void savePaymentInfo(PaymentInfo paymentInfo);

    /**
     * 根据outTradeNo修改订单支付状态
     * @param outTradeNo
     * @param paymentInfoUpd
     */
    void updatePaymentInfo(String outTradeNo, PaymentInfo paymentInfoUpd);

    /**
     * 根据outTradeNo获取支付订单状态
     * @param paymentInfo
     * @return
     */
    PaymentInfo getPaymentInfo(PaymentInfo paymentInfo);

    /**
     * 退款
     * @param orderId
     * @return
     */
    boolean refund(String orderId);

    /**
     * 生成微信二维码
     * @param orderId
     * @param totalAmount
     * @return
     */
    Map createNative(String orderId, String totalAmount);

    /**
     * 支付成功通知   消息队列
     * @param paymentInfoHas
     * @param result
     */
    void sendPaymentResult(PaymentInfo paymentInfoHas, String result);

    /**
     * 根据订单判断支付结果
     * @param orderInfoQuery
     * @return
     */
    boolean checkPayment(OrderInfo orderInfoQuery);

    /**
     * 延迟队列关闭订单
     * @param orderId
     * @param delaySec
     */
    void closeOrderInfo(String orderId, int delaySec);
}
