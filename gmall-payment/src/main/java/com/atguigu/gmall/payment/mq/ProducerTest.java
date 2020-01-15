package com.atguigu.gmall.payment.mq;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTextMessage;

import javax.jms.*;

public class ProducerTest {
    public static void main(String[] args) throws JMSException {
        //创建连接工厂
        ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory("tcp://192.168.42.129:61616");
        //创建连接
        Connection connection = activeMQConnectionFactory.createConnection();
        //开启连接
        connection.start();
        //创建Session 第一个参数是否开启事务 第二个参数
//        Session session = connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        Session session = connection.createSession(true,Session.SESSION_TRANSACTED);
        //创建队列
        Queue queue = session.createQueue("atguigu-001");
        //创建消息提供者
        MessageProducer producer = session.createProducer(queue);
        //创建message
        ActiveMQTextMessage activeMQTextMessage = new ActiveMQTextMessage();
        activeMQTextMessage.setText("------Hello ActiveMq!------");
        //提交数据
        producer.send(activeMQTextMessage);

        //如果事务开启，在session关闭之前必须提交
        session.commit();

        producer.close();
        session.close();
        connection.close();

    }
}
