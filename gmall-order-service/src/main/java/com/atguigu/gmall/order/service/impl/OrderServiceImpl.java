package com.atguigu.gmall.order.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.OrderDetail;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.enums.ProcessStatus;
import com.atguigu.gmall.config.ActiveMQUtil;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.util.HttpClientUtil;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import redis.clients.jedis.Jedis;

import javax.jms.*;
import javax.jms.Queue;
import java.util.*;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private ActiveMQUtil activeMQUtil;

    //多表操作需要加事务
    @Override
    @Transactional
    public String saveOrder(OrderInfo orderInfo) {
        // 设置创建时间
        orderInfo.setCreateTime(new Date());
        // 设置失效时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE,1);
        orderInfo.setExpireTime(calendar.getTime());
        // 生成第三方支付编号
        String outTradeNo="ATGUIGU"+System.currentTimeMillis()+""+new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);
        //插入数据库
        orderInfoMapper.insertSelective(orderInfo);

        //插入订单明细
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            orderDetail.setOrderId(orderInfo.getId());
            orderDetailMapper.insertSelective(orderDetail);
        }
       //返回订单id
        return  orderInfo.getId();
    }

    @Override
    public String getTradeNo(String userId) {
        Jedis jedis = redisUtil.getJedis();
        String tradeKey = "user:"+userId+":tradeCode";
        String tradeCode = UUID.randomUUID().toString();
        jedis.setex(tradeKey,10*60,tradeCode);
        jedis.close();
        return tradeCode;
    }

    @Override
    public boolean checkTradeCode(String userId, String tradeNo) {
        Jedis jedis = redisUtil.getJedis();
        String tradeKey = "user:"+userId+":tradeCode";
        String tradeCode = jedis.get(tradeKey);
        jedis.close();
        if(tradeCode!=null && tradeCode.equals(tradeNo)){
            return true;
        }else {
            return false;
        }
    }

    @Override
    public void deleteTradeCode(String userId, String tradeNo) {
        Jedis jedis = redisUtil.getJedis();
        String tradeKey = "user:"+userId+":tradeCode";
        jedis.del(tradeKey);
        jedis.close();
    }

    @Override
    public boolean checkStock(String skuId, Integer skuNum) {
        String result = HttpClientUtil.doGet("http://www.gware.com/hasStock?skuId=" + skuId + "&num=" + skuNum);
        if ("1".equals(result)){
            return  true;
        }else {
            return  false;
        }
    }

    @Override
    public OrderInfo getOrderInfo(String orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectByPrimaryKey(orderId);
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrderId(orderId);
        List<OrderDetail> orderDetailList = orderDetailMapper.select(orderDetail);
        //将订单明细表放入订单表中
        orderInfo.setOrderDetailList(orderDetailList);
        return orderInfo;
    }

    @Override
    public void updateOrderStatus(String orderId, ProcessStatus processStatus) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setProcessStatus(processStatus);
        orderInfo.setOrderStatus(processStatus.getOrderStatus());
        orderInfoMapper.updateByPrimaryKeySelective(orderInfo);
    }

    @Override
    public void sendOrderStatus(String orderId) {
        //获取连接
        Connection connection = activeMQUtil.getConnection();
        String orderJson = initWareOrder(orderId);
        try {
            //开启连接
            connection.start();
            //创建session
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            //创建消息队列
            Queue orderResultQueue = session.createQueue("ORDER_RESULT_QUEUE");
            //创建消息提供者
            MessageProducer producer = session.createProducer(orderResultQueue);
            //创建消息对象
            ActiveMQTextMessage message = new ActiveMQTextMessage();
            message.setText(orderJson);
            producer.send(message);
            //提交事务
            session.commit();
            producer.close();
            session.close();
            connection.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<OrderInfo> splitOrder(String orderId, String wareSkuMap) {
        //先获取原始订单
        OrderInfo orderInfoOrigin = getOrderInfo(orderId);
        //新建子订单集合
        List<OrderInfo> subOrderInfoList = new ArrayList<>();
        //[{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}]
        //将仓库商品对照转成mapList  进行遍历拆单
        List<Map> mapList = JSON.parseArray(wareSkuMap, Map.class);
        for (Map map : mapList) {
            String wareId = (String) map.get("wareId");
            List<String> skuIds = (List<String>) map.get("skuIds");
            //  生成订单主表，从原始订单复制，新的订单号，父订单
            OrderInfo subOrderInfo = new OrderInfo();
            BeanUtils.copyProperties(orderInfoOrigin,subOrderInfo);
            subOrderInfo.setId(null);
            //父id为原始订单id
            subOrderInfo.setParentOrderId(orderInfoOrigin.getId());
            // 重新生成第三方支付编号
            String outTradeNo="ATGUIGU"+System.currentTimeMillis()+""+new Random().nextInt(1000);
            subOrderInfo.setOutTradeNo(outTradeNo);
            //仓库id
            subOrderInfo.setWareId(wareId);
            //获取订单明细表
            List<OrderDetail> orderDetailList = orderInfoOrigin.getOrderDetailList();
            List<OrderDetail> subOrderDetaiList = new ArrayList<>();
            for (OrderDetail orderDetail : orderDetailList) {
                for (String skuId : skuIds) {
                    if(skuId.equals(orderDetail.getSkuId())){
                        orderDetail.setId(null);
                        subOrderDetaiList.add(orderDetail);
                    }
                }
            }
            //添加订单明细
            subOrderInfo.setOrderDetailList(subOrderDetaiList);
            //根据订单明细计算订单总价
            subOrderInfo.sumTotalAmount();
            //保存到数据库
            saveOrder(subOrderInfo);
            subOrderInfoList.add(subOrderInfo);
        }
        //更改原始订单状态为已拆单
        updateOrderStatus(orderId,ProcessStatus.SPLIT);
        //  返回一个新生成的子订单列表
        return subOrderInfoList;
    }

    private String initWareOrder(String orderId) {
        OrderInfo orderInfo = getOrderInfo(orderId);
        Map map = initWareOrder(orderInfo);
        return JSON.toJSONString(map);
    }

    public Map initWareOrder(OrderInfo orderInfo) {
        Map<String, Object> map = new HashMap<>();
        map.put("orderId",orderInfo.getId());
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel",orderInfo.getConsigneeTel());
        map.put("orderComment",orderInfo.getOrderComment());
        map.put("orderBody","------------");
        map.put("deliveryAddress",orderInfo.getDeliveryAddress());
        map.put("paymentWay","2");
        //仓库id  拆单使用
        map.put("wareId",orderInfo.getWareId());

        List<Object> detailList = new ArrayList<>();
        //获取商品明细表
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            Map detailMap = new HashMap();
            detailMap.put("skuId",orderDetail.getSkuId());
            detailMap.put("skuName",orderDetail.getSkuName());
            detailMap.put("skuNum",orderDetail.getSkuNum());
            detailList.add(detailMap);
        }
        map.put("details",detailList);
        return map;
    }
}
