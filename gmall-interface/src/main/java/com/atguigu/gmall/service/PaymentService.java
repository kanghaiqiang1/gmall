package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.PaymentInfo;

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
}
