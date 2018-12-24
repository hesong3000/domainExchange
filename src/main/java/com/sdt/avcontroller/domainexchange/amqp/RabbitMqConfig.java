package com.sdt.avcontroller.domainexchange.amqp;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.ChannelAwareMessageListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RabbitMqConfig {
    private static Logger log = LoggerFactory.getLogger(RabbitMqConfig.class);
    @Value("${spring.rabbitmq.local_domain.queue}")
    private String localDomainQueueName;
    @Value("${spring.rabbitmq.remote_domain.queue}")
    private String remoteDomainQueueName;

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
                    System.out.println("execlocal"+msg);
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
                    System.out.println("execremote"+msg);
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
            channel.exchangeDeclare("domainExchange", ExchangeTypes.FANOUT,true);
            channel.queueBind(remoteDomainQueueName,"domainExchange","");
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            return "remoteDomainQueue";
        }
    }
}
