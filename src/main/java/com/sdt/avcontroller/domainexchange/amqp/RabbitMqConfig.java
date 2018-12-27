package com.sdt.avcontroller.domainexchange.amqp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.rabbitmq.client.Channel;
import com.sdt.avcontroller.domainexchange.base.MsgHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.ChannelAwareMessageListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RabbitMqConfig {
    private static Logger logger = LoggerFactory.getLogger(RabbitMqConfig.class);
    @Value("${spring.rabbitmq.local_domain.queue}")
    private String localDomainQueueName;
    @Value("${spring.rabbitmq.remote_domain.queue}")
    private String remoteDomainQueueName;
    @Autowired
    MsgHolder msgHolder;

    @Bean(name="localDomainConnectionFactory")
    @Primary
    public ConnectionFactory localDomainConnectionFactory(
            @Value("${spring.rabbitmq.local_domain.host}") String host,
            @Value("${spring.rabbitmq.local_domain.port}") int port,
            @Value("${spring.rabbitmq.local_domain.username}") String username,
            @Value("${spring.rabbitmq.local_domain.password}") String password){
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setPort(port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);
        return connectionFactory;
    }

    @Bean(name="remoteDomainConnectionFactory")
    public ConnectionFactory remoteDomainConnectionFactory(
            @Value("${spring.rabbitmq.remote_domain.host}") String host,
            @Value("${spring.rabbitmq.remote_domain.port}") int port,
            @Value("${spring.rabbitmq.remote_domain.username}") String username,
            @Value("${spring.rabbitmq.remote_domain.password}") String password){
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setPort(port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);
        return connectionFactory;
    }

    @Bean(name="localRabbitTemplate")
    public RabbitTemplate localRabbitTemplate(
            @Qualifier("localDomainConnectionFactory") ConnectionFactory connectionFactory){
        RabbitTemplate domainRabbitTemplate = new RabbitTemplate(connectionFactory);
        return domainRabbitTemplate;
    }

    @Bean(name="remoteRabbitTemplate")
    public RabbitTemplate remoteRabbitTemplate(
            @Qualifier("remoteDomainConnectionFactory") ConnectionFactory connectionFactory){
        RabbitTemplate domainRabbitTemplate = new RabbitTemplate(connectionFactory);
        return domainRabbitTemplate;
    }

    @Bean
    public SimpleMessageListenerContainer execLocalMessageContainer(@Qualifier("localDomainConnectionFactory") ConnectionFactory connectionFactory){
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(localDomainQueueName);
        container.setConcurrentConsumers(1);
        container.setMessageListener((ChannelAwareMessageListener) (message, channel) -> {
            byte[] body = message.getBody();
            if(null != body) {
                try {
                    String msg = new String(body);
                    JSONObject requestMsg = JSON.parseObject(msg);
                    //增加接收queueName，后续使用
                    logger.info("recv from {}, msg {}",localDomainQueueName,msg);
                    requestMsg.put("recvQueueName", localDomainQueueName);
                    msgHolder.pushMsg(requestMsg.toJSONString());
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        });
        return container;
    }

    @Bean
    public SimpleMessageListenerContainer execRemoteMessageContainer(@Qualifier("remoteDomainConnectionFactory") ConnectionFactory connectionFactory){
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(remoteDomainQueueName);
        container.setConcurrentConsumers(1);
        container.setMessageListener((ChannelAwareMessageListener) (message, channel) -> {
            byte[] body = message.getBody();
            if(null != body) {
                try {
                    String msg = new String(body);
                    JSONObject requestMsg = JSON.parseObject(msg);
                    //增加接收queueName，后续使用
                    logger.info("recv from {}, msg {}",remoteDomainQueueName,msg);
                    requestMsg.put("recvQueueName", remoteDomainQueueName);
                    msgHolder.pushMsg(requestMsg.toJSONString());
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        });
        return container;
    }

    /*queue、exchange、queueBind*/
    @Bean
    public String localDomainQueue(
            @Qualifier("localDomainConnectionFactory") ConnectionFactory connectionFactory) {
        try {
            Channel channel = connectionFactory.createConnection().createChannel(false);
            channel.queueDeclare(localDomainQueueName, false, false, false, null);
            channel.queuePurge(localDomainQueueName);
            channel.exchangeDeclare("domainExchange", ExchangeTypes.FANOUT,true);
            channel.queueBind(localDomainQueueName,"domainExchange","");
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            return "localDomainQueue";
        }
    }

    @Bean
    public String remoteDomainQueue(
            @Qualifier("remoteDomainConnectionFactory") ConnectionFactory connectionFactory) {
        try {
            Channel channel = connectionFactory.createConnection().createChannel(false);
            channel.queueDeclare(remoteDomainQueueName, false, false, false, null);
            channel.queuePurge(remoteDomainQueueName);
            channel.exchangeDeclare("domainExchange", ExchangeTypes.FANOUT,true);
            channel.queueBind(remoteDomainQueueName,"domainExchange","");
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            return "remoteDomainQueue";
        }
    }
}
