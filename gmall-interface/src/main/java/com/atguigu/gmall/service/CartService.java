package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.CartInfo;

import java.util.List;

public interface CartService {

    /**
     * 添加商品到购物车
     * @param skuId
     * @param userId
     * @param skuNum
     */
    void addToCart(String skuId, String userId, int skuNum);

    /**
     * 根据用户Id查询购物车商品列表
     * @param userId
     * @return
     */
    List<CartInfo> getCartList(String userId);

    /**
     * 合并购物车
     * @param cartInfoList
     * @param userId
     * @return
     */
    List<CartInfo> mergeToCartList(List<CartInfo> cartInfoList, String userId);

    /**
     * 删除未登录状态下的购物车
     * @param userTempId
     */
    void deleteCartList(String userTempId);

    /**
     * 选中状态的变更
     * @param isChecked
     * @param skuId
     * @param userId
     */
    void checkCart(String isChecked, String skuId, String userId);

    /**
     * 获取购物车选中的商品
     * @param userId
     * @return
     */
    List<CartInfo> getCartCheckedList(String userId);

    /**
     * 根据用户id获取数据库数据并放到缓存,验证价格使用
     * @param userId
     * @return
     */
    List<CartInfo> loadCartCache(String userId);
}
