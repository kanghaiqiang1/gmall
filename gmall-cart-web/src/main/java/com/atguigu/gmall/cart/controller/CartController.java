package com.atguigu.gmall.cart.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.config.CookieUtil;
import com.atguigu.gmall.config.LoginRequire;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.ManageService;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
public class CartController {

    @Reference
    private CartService cartService;
    @Reference
    private ManageService manageService;

    @RequestMapping("addToCart")
    @LoginRequire(autoRedirect = false)
    public String addToCart(HttpServletRequest request, HttpServletResponse response){
        String skuNum = request.getParameter("skuNum");
        String skuId = request.getParameter("skuId");
        //如果用户已经登录，则有用户id
        String userId = (String) request.getAttribute("userId");
        //如果未登录
        if(userId==null){
            //判断是否存在于cookie中
            userId = CookieUtil.getCookieValue(request,"user-key",false);
            if(userId==null){
                //设置临时用户id  用来保存购物车
                userId = UUID.randomUUID().toString().replace("-","");
                CookieUtil.setCookie(request,response,"user-key",userId,60*60*24*7,false);
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

    @RequestMapping("cartList")
    @LoginRequire(autoRedirect = false)
    public String cartList(HttpServletRequest request){
        String userId = (String) request.getAttribute("userId");
        List<CartInfo> cartInfoList = new ArrayList<>();
        String userTempId = CookieUtil.getCookieValue(request, "user-key", false);
        if(userId!=null){
            //用户已登录，先查询登录前是否添加过购物车
            if(!StringUtils.isEmpty(userTempId)){
                cartInfoList = cartService.getCartList(userTempId);
                if(cartInfoList!=null && cartInfoList.size()>0){
                    //合并购物车
                    cartInfoList = cartService.mergeToCartList(cartInfoList,userId);
                    //删除未登录状态下的购物车
                    cartService.deleteCartList(userTempId);
                }
            }
            if(StringUtils.isEmpty(userTempId) || (cartInfoList == null || cartInfoList.size() == 0)){
                //未登录状态下没有加入购物车，直接查询数据库
                cartInfoList = cartService.getCartList(userId);
            }
        }else {
            if(!StringUtils.isEmpty(userTempId)){
                cartInfoList = cartService.getCartList(userTempId);
            }
        }
        request.setAttribute("cartInfoList",cartInfoList);
        return "cartList";
    }

    @RequestMapping("checkCart")
    @ResponseBody
    @LoginRequire(autoRedirect = false)
    public void checkCart(HttpServletRequest request, HttpServletResponse response){
        // 调用服务层
        String isChecked = request.getParameter("isChecked");
        String skuId = request.getParameter("skuId");
        // 获取用户Id
        String userId = (String) request.getAttribute("userId");
        if(userId == null){
            //如果未登录，从cookie中获取临时用户id
            userId = CookieUtil.getCookieValue(request,"user-key",false);
        }
        cartService.checkCart(isChecked, skuId, userId);
    }

    @RequestMapping("toTrade")
    @LoginRequire
    public String toTrade(HttpServletRequest request){
        //获取参数
        String userId = (String) request.getAttribute("userId");
        //获取临时用户id
        String userTempId = CookieUtil.getCookieValue(request, "user-key", false);
        //有临时用户id，合并购物车并删除临时购物车
        if(userTempId!=null){
            List<CartInfo> cartList = cartService.getCartList(userTempId);
            if(cartList!=null && cartList.size()>0){
                cartService.mergeToCartList(cartList,userId);
                cartService.deleteCartList(userTempId);
            }
        }
        return "redirect://trade.gmall.com/trade";
    }
}
