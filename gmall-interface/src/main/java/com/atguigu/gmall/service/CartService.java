package com.atguigu.gmall.service;

public interface CartService {

    /**
     * 添加商品到购物车
     * @param skuId
     * @param userId
     * @param skuNum
     */
    void addToCart(String skuId, String userId, int skuNum);
}
