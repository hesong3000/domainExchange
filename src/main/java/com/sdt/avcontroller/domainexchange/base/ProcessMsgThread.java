package com.sdt.avcontroller.domainexchange.base;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sdt.avcontroller.domainexchange.amqp.DomainRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.LinkedList;
import java.util.List;

@Component(value="processMsgThread")
public class ProcessMsgThread extends Thread{
    private static Logger logger = LoggerFactory.getLogger(ProcessMsgThread.class);
    //本服务可处理的目的domain_id
    @Value("${spring.rabbitmq.local_domain.avail-dst-domain}")
    private String aval_dst_domain_id;
    @Value("${spring.rabbitmq.local_domain.self-domain}")
    private String self_domain_id;
    @Resource(name="localRabbitTemplate")
    private RabbitTemplate localDomainRabbitTemplate;
    @Resource(name="remoteRabbitTemplate")
    private RabbitTemplate remoteDomainRabbitTemplate;
    @Value("${spring.rabbitmq.local_domain.queue}")
    private String local_domain_queue;
    @Value("${spring.rabbitmq.remote_domain.queue}")
    private String remote_domain_queue;

    private final String outerExchangeName = "domainExchange";
    private final String innerExchangeName = "licodeExchange";
    private final String innerRoutekey = "wktest_key";

    @Autowired
    private MsgHolder msgHolder;
    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()){
            try {
                String msg = msgHolder.popMsg();
                logger.info("recv msg {}", msg);
                JSONObject jsonObject = JSON.parseObject(msg);
                String recvQueueName = jsonObject.getString("recvQueueName");
                JSONArray domain_route = jsonObject.getJSONArray("domain_route");
                if(recvQueueName==null || domain_route==null){
                    logger.warn("recv msg lack recvQueueName or domain_route content, msg: {}", msg);
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<DomainRoute> recv_domain_list = domain_route.toJavaList(DomainRoute.class);
                int recv_domain_list_size = recv_domain_list.size();

                //1、先判断是不是广播消息，如果有一个DomainRoute的broadcast是true的话，则此条消息全网广播
                boolean isBroadcastMsg = false;
                for(int index=0; index<recv_domain_list_size;index++){
                    DomainRoute domainRoute = recv_domain_list.get(index);
                    if(domainRoute.isBroadcast()==true) {
                        isBroadcastMsg = true;
                        break;
                    }
                }

                if(isBroadcastMsg==true){
                    ProcBroadcastMsg(jsonObject);
                    continue;
                }

                ProcDirectMsg(jsonObject);

            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void ProcBroadcastMsg(JSONObject jsonObject){
        String recvQueueName = jsonObject.getString("recvQueueName");
        //如果此条消息来自远端
        if(recvQueueName.compareTo(remote_domain_queue)==0){
            //1、先把此消息发到本域内网
            String msgFromQueueName = jsonObject.getString("msgFromQueueName");
            if(msgFromQueueName==null){
                //msgFromQueueName为空表示此条消息来自本域的内网
                jsonObject.remove("recvQueueName");
                logger.info("send to remote exchange {}, msg: {}", outerExchangeName, jsonObject);
                if(jsonObject.containsKey("msgFromQueueName")==true)
                    jsonObject.remove("msgFromQueueName");
                jsonObject.put("msgFromQueueName",local_domain_queue);
                localDomainRabbitTemplate.convertAndSend(outerExchangeName, "", jsonObject.toJSONString());

                //推送到远端的内网
                logger.info("send to remote exchange {}, msg: {}", innerExchangeName, jsonObject);
                localDomainRabbitTemplate.convertAndSend(innerExchangeName, innerRoutekey, jsonObject.toJSONString());
                return;
            }
            if(msgFromQueueName.compareTo(recvQueueName)==0)
            {
                logger.info("avoid duplicate proc msg");
                return;
            }

            jsonObject.remove("recvQueueName");
            logger.info("send to local exchange {}, msg: {}", innerExchangeName, jsonObject);
            localDomainRabbitTemplate.convertAndSend(innerExchangeName, innerRoutekey, jsonObject.toJSONString());
            //2、要发到本域外网，需叠加msgFromQueueName字段，标识此条消息来自哪里
            if(jsonObject.containsKey("msgFromQueueName")==true)
                jsonObject.remove("msgFromQueueName");
            jsonObject.put("msgFromQueueName",local_domain_queue);
            logger.info("send to local exchange {}, msg: {}", outerExchangeName, jsonObject);
            localDomainRabbitTemplate.convertAndSend(outerExchangeName, "", jsonObject.toJSONString());
        }else if(recvQueueName.compareTo(local_domain_queue)==0){
            //先过滤掉msgFromQueueName来自自己的队列的消息
            String msgFromQueueName = jsonObject.getString("msgFromQueueName");
            //如果发自本地内网的广播消息，则msgFromQueueName为空
            if(msgFromQueueName==null){
                //msgFromQueueName为空表示此条消息来自本域的内网
                jsonObject.remove("recvQueueName");
                logger.info("send to remote exchange {}, msg: {}", outerExchangeName, jsonObject);
                if(jsonObject.containsKey("msgFromQueueName")==true)
                    jsonObject.remove("msgFromQueueName");
                jsonObject.put("msgFromQueueName",remote_domain_queue);
                remoteDomainRabbitTemplate.convertAndSend(outerExchangeName, "", jsonObject.toJSONString());

                //推送到远端的内网
                logger.info("send to remote exchange {}, msg: {}", innerExchangeName, jsonObject);
                remoteDomainRabbitTemplate.convertAndSend(innerExchangeName, innerRoutekey, jsonObject.toJSONString());
                return;
            }
            //如果不是发自本地内网的广播消息，则如下处理//此处判断目的是不向跨域消息的来源方向重复发送消息
            if(msgFromQueueName.compareTo(recvQueueName)==0){
                logger.info("avoid duplicate proc msg");
                return;
            }

            jsonObject.remove("recvQueueName");
            if(jsonObject.containsKey("msgFromQueueName")==true)
                jsonObject.remove("msgFromQueueName");
            jsonObject.put("msgFromQueueName", remote_domain_queue);
            logger.info("send to remote exchange {}, msg: {}", outerExchangeName, jsonObject);
            remoteDomainRabbitTemplate.convertAndSend(outerExchangeName, "", jsonObject.toJSONString());
            //推送到远端的内网
            logger.info("send to remote exchange {}, msg: {}", innerExchangeName, jsonObject);
            remoteDomainRabbitTemplate.convertAndSend(innerExchangeName, innerRoutekey, jsonObject.toJSONString());
        }
    }

    private void ProcDirectMsg(JSONObject jsonObject){
        JSONArray domain_route = jsonObject.getJSONArray("domain_route");
        @SuppressWarnings("unchecked")
        List<DomainRoute> recv_domain_list = domain_route.toJavaList(DomainRoute.class);
        String recvQueueName = jsonObject.getString("recvQueueName");
        jsonObject.remove("recvQueueName");
        int recv_domain_list_size = recv_domain_list.size();
        //如果此条消息来自自己的exchangequeue
        if(recvQueueName.compareTo(local_domain_queue)==0){
            for(int index=0; index<recv_domain_list_size;index++){
                DomainRoute domainRoute = recv_domain_list.get(index);
                if(domainRoute.getDomainRoute().size()==0)
                    continue;
                String headDomain = domainRoute.getHeadDomainID();
                if(headDomain.compareTo(aval_dst_domain_id)==0){
                    //aval_dst_domain_id是目的DomainID
                    if(domainRoute.getRouteTTL()==1){
                        //如果下一跳Domain是本服务对应的远端Domain，且下一跳为终止Domian节点，则直接发送至远端的Domain内网交换机即可
                        System.out.println("remoteDomainRabbitTemplate send msg "+jsonObject);
                        remoteDomainRabbitTemplate.convertAndSend(innerExchangeName,innerRoutekey,jsonObject.toJSONString());
                    }else {
                        //如果下一跳是本服务对应的远端Domain，且下一跳不为终止Domain节点，则只保留header为本服务远端Domain的路由表（且删除header）
                        //发送至远端Domain的
                        List<DomainRoute> new_domain_list = new LinkedList<>();
                        int sub_size = recv_domain_list.size();
                        for(int sub_index=0;sub_index<sub_size;sub_index++){
                            DomainRoute domainRoute1 = new DomainRoute(recv_domain_list.get(sub_index));
                            if(domainRoute1.getHeadDomainID().compareTo(aval_dst_domain_id)==0 && domainRoute1.getRouteTTL()>1){
                                domainRoute1.removeHeadDomianID(aval_dst_domain_id);
                                new_domain_list.add(domainRoute1);
                            }
                        }

                        if(new_domain_list.size()>0){
                            JSONObject new_jsonObject = (JSONObject)jsonObject.clone();
                            new_jsonObject.remove("domain_route");
                            JSONArray domain_array = JSONArray.parseArray(JSONObject.toJSONString(new_domain_list));
                            new_jsonObject.put("domain_route", domain_array);
                            remoteDomainRabbitTemplate.convertAndSend(outerExchangeName,"",new_jsonObject.toJSONString());
                        }
                    }
                }
            }
        }else if(recvQueueName.compareTo(remote_domain_queue)==0){
            //消息来自远端，只处理DomainRoute的Head为selfDomain的消息，其余在Domain中清除
            for(int index=0; index<recv_domain_list_size;index++){
                DomainRoute domainRoute = recv_domain_list.get(index);
                if(domainRoute.getDomainRoute().size()==0)
                    continue;
                String headDomain = domainRoute.getHeadDomainID();
                if(headDomain.compareTo(self_domain_id)==0){
                    //如果消息路由的下一跳为自己，且为最终节点，则发送消息至本域内网
                    if(domainRoute.getRouteTTL()==1){
                        localDomainRabbitTemplate.convertAndSend(innerExchangeName,innerRoutekey,jsonObject.toJSONString());
                    }else{
                        //只保留DomainRoute路由中HEAD是自己的路由
                        List<DomainRoute> new_domain_list = new LinkedList<>();
                        int sub_size = recv_domain_list.size();
                        for(int sub_index=0;sub_index<sub_size;sub_index++){
                            DomainRoute domainRoute1 = new DomainRoute(recv_domain_list.get(sub_index));
                            if(domainRoute1.getHeadDomainID().compareTo(self_domain_id)==0 && domainRoute1.getRouteTTL()>1){
                                domainRoute1.removeHeadDomianID(self_domain_id);
                                new_domain_list.add(domainRoute1);
                            }
                        }
                        //发送至本域的domainExchange交换机，用以向下广播
                        if(new_domain_list.size()>0){
                            JSONObject new_jsonObject = (JSONObject)jsonObject.clone();
                            new_jsonObject.remove("domain_route");
                            JSONArray domain_array = JSONArray.parseArray(JSONObject.toJSONString(new_domain_list));
                            new_jsonObject.put("domain_route", domain_array);
                            localDomainRabbitTemplate.convertAndSend(outerExchangeName,"",new_jsonObject.toJSONString());
                        }
                    }
                }
            }
        }
    }
}
