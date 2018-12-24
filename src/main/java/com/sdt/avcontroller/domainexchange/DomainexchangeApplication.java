package com.sdt.avcontroller.domainexchange;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sdt.avcontroller.domainexchange.amqp.DomainRoute;
import com.sdt.avcontroller.domainexchange.base.ProcessMsgThread;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.LinkedList;
import java.util.List;

@SpringBootApplication
public class DomainexchangeApplication {
	public static void main(String[] args) {
		DomainRoute domainRoute = new DomainRoute();
		domainRoute.setBroadcast(false);
		domainRoute.appendDomainIDToRoute("Domain_A");
		domainRoute.appendDomainIDToRoute("Domain_B");
		DomainRoute domainRoute2 = new DomainRoute();
		domainRoute2.setBroadcast(false);
		domainRoute2.appendDomainIDToRoute("Domain_C");
		domainRoute2.appendDomainIDToRoute("Domain_D");
		JSONObject domain_msg = new JSONObject();



		SpringApplication app = new SpringApplication(DomainexchangeApplication.class);
		ConfigurableApplicationContext context = app.run(args);
		ProcessMsgThread processMsgThread = (ProcessMsgThread)context.getBean("processMsgThread");
		processMsgThread.start();
	}
}

