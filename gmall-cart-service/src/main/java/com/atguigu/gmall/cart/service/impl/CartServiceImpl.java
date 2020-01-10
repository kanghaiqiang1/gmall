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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        String cartKey = CartConst.USER_KEY_PREFIX + userId + CartConst.USER_CART_KEY_SUFFIX;
        //根据用户id和商品id在数据库中查询是否有该商品
        Example example = new Example(CartInfo.class);
        example.createCriteria().andEqualTo("userId", userId).andEqualTo("skuId", skuId);
        CartInfo cartInfoExist = cartInfoMapper.selectOneByExample(example);
        if (cartInfoExist != null) {
            //修改商品数量
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum() + skuNum);
            //初始化商品实时价格
            cartInfoExist.setSkuPrice(cartInfoExist.getCartPrice());
            cartInfoMapper.updateByPrimaryKeySelective(cartInfoExist);
        } else {
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
        jedis.hset(cartKey, skuId, JSON.toJSONString(cartInfoExist));
        //设置购物车过期时间
        setCartkeyExpireTime(skuId, jedis, cartKey);
    }

    @Override
    public List<CartInfo> getCartList(String userId) {
        /*
        1.  获取redis中的购物车数据
        2.  如果redis 没有，从mysql 获取并放入缓存
         */
        List<CartInfo> cartInfoList = new ArrayList<>();
        Jedis jedis = redisUtil.getJedis();
        String cartKey = CartConst.USER_KEY_PREFIX + userId + CartConst.USER_CART_KEY_SUFFIX;
        if (cartKey != null && cartKey.length() > 0) {
            List<String> stringList = jedis.hvals(cartKey);
            for (String cartInfoJson : stringList) {
                cartInfoList.add(JSON.parseObject(cartInfoJson, CartInfo.class));
            }
            cartInfoList.sort(new Comparator<CartInfo>() {
                @Override
                public int compare(CartInfo o1, CartInfo o2) {
                    return o1.getId().compareTo(o2.getId());
                }
            });
        } else {
            cartInfoList = loadCartCache(userId);
        }

        return cartInfoList;
    }

    @Override
    public List<CartInfo> mergeToCartList(List<CartInfo> cartInfoList, String userId) {
        //获取登录用户购物车数据
        List<CartInfo> cartInfoLoginList = cartInfoMapper.selectCartListWithCurPrice(userId);
        //购物车不为空，合并
        if (cartInfoLoginList != null && cartInfoLoginList.size() > 0) {
            //遍历未登录状态下的购物车
            for (CartInfo cartInfoNoLogin : cartInfoList) {
                boolean isMatch = false;
                //遍历登录用户的购物车
                for (CartInfo cartInfoLogin : cartInfoLoginList) {
                    //如果有相同商品，直接增加数量
                    if (cartInfoLogin.getSkuId().equals(cartInfoNoLogin.getSkuId())) {
                        cartInfoLogin.setSkuNum(cartInfoNoLogin.getSkuNum()+cartInfoLogin.getSkuNum());
                        //更新数据库
                        cartInfoMapper.updateByPrimaryKeySelective(cartInfoLogin);
                        isMatch = true;
                    }
                }
                if (!isMatch) {
                    cartInfoNoLogin.setId(null);
                    cartInfoNoLogin.setUserId(userId);
                    //商品不相同，直接添加到数据库
                    cartInfoMapper.insertSelective(cartInfoNoLogin);
                }
            }
        } else {
            //购物车为空，直接添加到数据库
            for (CartInfo cartInfoNoLogin : cartInfoList) {
                cartInfoNoLogin.setId(null);
                cartInfoNoLogin.setUserId(userId);
                cartInfoMapper.insertSelective(cartInfoNoLogin);
            }
        }
        //获取数据库数据并放入缓存
        List<CartInfo> cartInfoList1 = loadCartCache(userId);
        for (CartInfo cartInfoDB : cartInfoList1) {
            for (CartInfo cartInfo : cartInfoList) {
                //同一个商品
                if(cartInfoDB.getSkuId().equals(cartInfo.getSkuId())){
                    //未登录为1，数据库不为1
                    if("1".equals(cartInfo.getIsChecked())){
                        if(!"1".equals(cartInfoDB.getIsChecked())){
                            // 修改数据库字段为1
                            cartInfoDB.setIsChecked("1");
                            //修改数据库中被选中状态
                            checkCart(cartInfo.getIsChecked(),cartInfo.getSkuId(),userId);
                        }
                    }
                }
            }
        }
        return cartInfoList1;
    }

    @Override
    public void deleteCartList(String userTempId) {
        //先删除数据库中的数据
        Example example = new Example(CartInfo.class);
        example.createCriteria().andEqualTo("userId",userTempId);
        cartInfoMapper.deleteByExample(example);
        //再删除redis缓存
        Jedis jedis = redisUtil.getJedis();
        String cartKey = CartConst.USER_KEY_PREFIX + userTempId + CartConst.USER_CART_KEY_SUFFIX;
        jedis.del(cartKey);
        jedis.close();
    }

    @Override
    public void checkCart(String isChecked, String skuId, String userId) {
        //修改数据库中的数据
        Example example = new Example(CartInfo.class);
        example.createCriteria().andEqualTo("userId",userId).andEqualTo("skuId",skuId);
        CartInfo cartInfo = new CartInfo();
        cartInfo.setIsChecked(isChecked);
        System.out.println("----修改数据----");
        cartInfoMapper.updateByExampleSelective(cartInfo,example);
        //修改缓存  先删除再添加
        Jedis jedis = redisUtil.getJedis();
        String cartKey = CartConst.USER_KEY_PREFIX + userId + CartConst.USER_CART_KEY_SUFFIX;
        jedis.hdel(cartKey);
        List<CartInfo> cartInfoList = cartInfoMapper.selectByExample(example);
        if(cartInfoList!=null && cartInfoList.size()>0){
            CartInfo info = cartInfoList.get(0);
            // 数据初始化实时价格！
            info.setSkuPrice(info.getCartPrice());
            jedis.hset(cartKey,skuId,JSON.toJSONString(info));
        }
        jedis.close();
    }

    //根据用户id从数据库获取数据并放入缓存
    private List<CartInfo> loadCartCache(String userId) {
        Jedis jedis = redisUtil.getJedis();
        String cartKey = CartConst.USER_KEY_PREFIX + userId + CartConst.USER_CART_KEY_SUFFIX;
        //从数据库中查询
        List<CartInfo> cartInfoList = cartInfoMapper.selectCartListWithCurPrice(userId);
        if (cartInfoList == null || cartInfoList.size() == 0) {
            return null;
        }
        String skuId = null;
        for (CartInfo cartInfo : cartInfoList) {
            //将数据放入缓存
            skuId = cartInfo.getSkuId();
            jedis.hset(cartKey, skuId, JSON.toJSONString(cartInfo));
        }
        //设置超时时间
        setCartkeyExpireTime(skuId, jedis, cartKey);
        jedis.close();
        return cartInfoList;
    }

    private void setCartkeyExpireTime(String skuId, Jedis jedis, String cartKey) {
        //根据用户的过期时间设置购物车过期时间    如果未登录，设置默认过期时间
        String userKey = CartConst.USER_KEY_PREFIX + skuId + CartConst.USERINFOKEY_SUFFIX;
        if (jedis.exists(userKey)) {
            Long ttl = jedis.ttl(userKey);
            //将用户的过期时间设置给购物车
            jedis.expire(cartKey, ttl.intValue());
        } else {
            //设置默认过期时间
            jedis.expire(cartKey, 7 * 24 * 3600);
        }
    }
}
