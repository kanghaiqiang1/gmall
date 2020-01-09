package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.UserAddress;
import com.atguigu.gmall.bean.UserInfo;

import java.util.List;

public interface UserService {
    /**
     * 查询所有数据
     * @return
     */
    List<UserInfo> selectAll();

    /**
     * 根据userId查询用户地址信息
     * @param userId
     * @return
     */
    List<UserAddress> getUserAddressByUserId(String userId);

    /**
     *用户登录验证
     * @param userInfo
     * @return
     */
    UserInfo login(UserInfo userInfo);

    /**
     * 验证用户是否登录
     * @param userId
     * @return
     */
    UserInfo verify(String userId);
}
