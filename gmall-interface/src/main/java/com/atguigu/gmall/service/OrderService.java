package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.enums.ProcessStatus;

import java.util.List;
import java.util.Map;

public interface OrderService {

    /**
     * 保存订单
     * @param orderInfo
     * @return
     */
    String saveOrder(OrderInfo orderInfo);

    /**
     * 获取流水号
     * @param userId
     * @return
     */
    String getTradeNo(String userId);

    /**
     * 验证流水号
     * @param userId
     * @param tradeNo
     * @return
     */
    boolean checkTradeCode(String userId, String tradeNo);

    /**
     * 删除流水号
     * @param userId
     * @param tradeNo
     */
    void deleteTradeCode(String userId,String tradeNo);

    /**
     * 验证商品库存
     * @param skuId
     * @param skuNum
     * @return
     */
    boolean checkStock(String skuId, Integer skuNum);

    /**
     * 根据订单id获取订单数据
     * @param orderId
     * @return
     */
    OrderInfo getOrderInfo(String orderId);

    /**
     * 修改订单支付状态
     * @param orderId
     * @param processStatus
     */
    void updateOrderStatus(String orderId, ProcessStatus processStatus);

    /**
     * 通知减库存
     * @param orderId
     */
    void sendOrderStatus(String orderId);

    /**
     * 仓库拆单
     * @param orderId
     * @param wareSkuMap
     * @return
     */
    List<OrderInfo> splitOrder(String orderId, String wareSkuMap);

    /**
     * 初始化仓库
     * @param orderInfo
     * @return
     */
    Map initWareOrder(OrderInfo orderInfo);
}
