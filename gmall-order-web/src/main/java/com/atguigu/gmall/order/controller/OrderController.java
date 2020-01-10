package com.atguigu.gmall.order.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.OrderDetail;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.UserAddress;
import com.atguigu.gmall.bean.enums.OrderStatus;
import com.atguigu.gmall.bean.enums.ProcessStatus;
import com.atguigu.gmall.config.CookieUtil;
import com.atguigu.gmall.config.LoginRequire;
import com.atguigu.gmall.service.CartService;
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
        return "trade";
    }

    @RequestMapping("submitOrder")
    @LoginRequire
    public String submitOrder(OrderInfo orderInfo, HttpServletRequest request){
        //获取用户id
        String userId = (String) request.getAttribute("userId");
        //保存用户id
        orderInfo.setUserId(userId);
        //订单状态
        orderInfo.setOrderStatus(OrderStatus.UNPAID);
        //支付状态
        orderInfo.setProcessStatus(ProcessStatus.UNPAID);
        //订单总金额
        orderInfo.sumTotalAmount();
        //保存订单,返回订单号
        String orderId = orderService.saveOrder(orderInfo);
        //重定向到支付页面  携带订单id
        return "redirect://payment.gmall.com/index?orderId="+orderId;
    }
}
