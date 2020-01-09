package com.atguigu.gmall.cart.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.cart.cartConst.CartConst;
import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.ManageService;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private CartInfoMapper cartInfoMapper;
    @Reference
    private ManageService manageService;
    @Autowired
    private RedisUtil redisUtil;
    /*
        1.  先查看数据库中是否有该商品
            select * from cartInfo where userId = ? and skuId = ?
            true: 数量相加upd
            false: 直接添加
        2.  放入redis！
     */
    @Override
    public void addToCart(String skuId, String userId, int skuNum) {
        Jedis jedis = redisUtil.getJedis();
        String cartKey = CartConst.USER_KEY_PREFIX+skuId+CartConst.USER_CART_KEY_SUFFIX;
        //根据用户id和商品id在数据库中查询是否有该商品
        Example example = new Example(CartInfo.class);
        example.createCriteria().andEqualTo("userId",userId).andEqualTo("skuId",skuId);
        CartInfo cartInfoExist = cartInfoMapper.selectOneByExample(example);
        if(cartInfoExist!=null){
            //修改商品数量
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum()+skuNum);
            //初始化商品实时价格
            cartInfoExist.setSkuPrice(cartInfoExist.getCartPrice());
            cartInfoMapper.updateByPrimaryKeySelective(cartInfoExist);
        }else {
            SkuInfo skuInfo = manageService.getSkuInfo(skuId);
            CartInfo cartInfo1 = new CartInfo();

            cartInfo1.setSkuPrice(skuInfo.getPrice());
            cartInfo1.setCartPrice(skuInfo.getPrice());
            cartInfo1.setSkuNum(skuNum);
            cartInfo1.setSkuId(skuId);
            cartInfo1.setUserId(userId);
            cartInfo1.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo1.setSkuName(skuInfo.getSkuName());
            //直接添加到数据库
            cartInfoMapper.insertSelective(cartInfo1);

            cartInfoExist = cartInfo1;
        }
        //将商品添加到redis缓存
        jedis.hset(cartKey,skuId, JSON.toJSONString(cartInfoExist));
        //设置购物车过期时间
        setCartkeyExpireTime(skuId, jedis, cartKey);
    }

    private void setCartkeyExpireTime(String skuId, Jedis jedis, String cartKey) {
        //根据用户的过期时间设置购物车过期时间    如果未登录，设置默认过期时间
        String userKey = CartConst.USER_KEY_PREFIX+skuId+CartConst.USERINFOKEY_SUFFIX;
        if(jedis.exists(userKey)){
            Long ttl = jedis.ttl(userKey);
            //将用户的过期时间设置给购物车
            jedis.expire(cartKey,ttl.intValue());
        }else {
            //设置默认过期时间
            jedis.expire(cartKey,7*24*3600);
        }
    }
}
