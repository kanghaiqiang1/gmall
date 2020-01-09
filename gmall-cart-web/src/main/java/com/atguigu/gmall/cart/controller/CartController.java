package com.atguigu.gmall.cart.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.config.CookieUtil;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.ManageService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

@Controller
public class CartController {

    @Reference
    private CartService cartService;
    @Reference
    private ManageService manageService;

    @RequestMapping("addToCart")
    public String addToCart(HttpServletRequest request, HttpServletResponse response){
        String skuNum = request.getParameter("skuNum");
        String skuId = request.getParameter("skuId");
        //如果用户已经登录，则有用户id
        String userId = (String) request.getAttribute("userId");
        //如果未登录
        if(userId==null){
            //判断是否存在于cookie中
            userId = CookieUtil.getCookieValue(request,"userKey",false);
            if(userId==null){
                //设置临时用户id  用来保存购物车
                userId = UUID.randomUUID().toString().replace("-","");
                CookieUtil.setCookie(request,response,"userKey",userId,60*60*24*7,false);
            }
        }
        cartService.addToCart(skuId,userId,Integer.parseInt(skuNum));
        //返回商品
        SkuInfo skuInfo = manageService.getSkuInfo(skuId);
        request.setAttribute("skuInfo",skuInfo);
        //保存添加商品的数量
        request.setAttribute("skuNum",skuNum);

        return "success";
    }
}
