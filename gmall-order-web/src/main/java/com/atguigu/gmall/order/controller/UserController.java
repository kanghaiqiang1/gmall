package com.atguigu.gmall.order.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.UserAddress;
import com.atguigu.gmall.bean.UserInfo;
import com.atguigu.gmall.service.UserService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserController {

    @Reference
    private UserService userService;

    /**
     * 查询所有数据
     * @return
     */
    @RequestMapping("findAll")
    public List<UserInfo> findAll(){
        return userService.selectAll();
    }

    /**
     * 根据userId查询用户地址信息
     * @param userId
     * @return
     */
    @RequestMapping("trade")
    public List<UserAddress> getUserAddressByUserId(String userId){
        return userService.getUserAddressByUserId(userId);
    }
}
