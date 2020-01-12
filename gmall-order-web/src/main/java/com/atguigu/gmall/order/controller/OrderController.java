package com.atguigu.gmall.order.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.*;
import com.atguigu.gmall.bean.enums.OrderStatus;
import com.atguigu.gmall.bean.enums.ProcessStatus;
import com.atguigu.gmall.config.CookieUtil;
import com.atguigu.gmall.config.LoginRequire;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.ManageService;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@Controller
public class OrderController {

    @Reference
    private CartService cartService;
    @Reference
    private UserService userService;
    @Reference
    private OrderService orderService;
    @Reference
    private ManageService manageService;

    //跳转到去结算页面
    @RequestMapping("trade")
    @LoginRequire
    public String tradeInit(HttpServletRequest request){
        //获取用户id
        String userId = (String) request.getAttribute("userId");
        //获取购物车选中的商品
        List<CartInfo> cartCheckedList = cartService.getCartCheckedList(userId);
        //获取收货人地址
        List<UserAddress> userAddressList = userService.getUserAddressByUserId(userId);
        request.setAttribute("userAddressList",userAddressList);

        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (CartInfo cartInfo : cartCheckedList) {
            //添加到商品明细表中
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setSkuId(cartInfo.getSkuId());
            orderDetail.setSkuName(cartInfo.getSkuName());
            orderDetail.setImgUrl(cartInfo.getImgUrl());
            orderDetail.setSkuNum(cartInfo.getSkuNum());
            orderDetail.setOrderPrice(cartInfo.getCartPrice());
            orderDetailList.add(orderDetail);
        }
        request.setAttribute("orderDetailList",orderDetailList);
        //获取订单总金额
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(orderDetailList);
        orderInfo.sumTotalAmount();
        request.setAttribute("totalAmount",orderInfo.getTotalAmount());
        //获取流水号
        String tradeNo = orderService.getTradeNo(userId);
        request.setAttribute("tradeNo",tradeNo);
        return "trade";
    }

    @RequestMapping("submitOrder")
    @LoginRequire
    public String submitOrder(OrderInfo orderInfo, HttpServletRequest request){
        //获取用户id
        String userId = (String) request.getAttribute("userId");
        //从页面获取流水号
        String tradeNo = request.getParameter("tradeNo");
        boolean flag = orderService.checkTradeCode(userId,tradeNo);
        if(!flag){
            request.setAttribute("errMsg","该页面已失效，请重新结算!");
            return "tradeFail";
        }
        //保存用户id
        orderInfo.setUserId(userId);
        //订单状态
        orderInfo.setOrderStatus(OrderStatus.UNPAID);
        //支付状态
        orderInfo.setProcessStatus(ProcessStatus.UNPAID);
        //订单总金额
        orderInfo.sumTotalAmount();
        //获取订单明细
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            //验证库存
            boolean result = orderService.checkStock(orderDetail.getSkuId(),orderDetail.getSkuNum());
            if(!result){
                request.setAttribute("errMsg",orderDetail.getSkuName()+"商品库存不足，请联系客服！");
                return "tradeFail";
            }
            //验证价格
            SkuInfo skuInfo = manageService.getSkuInfo(orderDetail.getSkuId());
            int compare = skuInfo.getPrice().compareTo(orderDetail.getOrderPrice());
            if(compare!=0){
                request.setAttribute("errMsg",orderDetail.getSkuName()+"商品价格有变动，请重新下单！");
                //重新加载数据库，并将数据放入缓存
                cartService.loadCartCache(userId);
                return "tradeFail";
            }
        }
        //保存订单,返回订单号
        String orderId = orderService.saveOrder(orderInfo);
        //删除流水号
        orderService.deleteTradeCode(userId,tradeNo);
        //重定向到支付页面  携带订单id
        return "redirect://payment.gmall.com/index?orderId="+orderId;
    }
}
