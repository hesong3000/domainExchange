package com.sdt.avcontroller.domainexchange;

import com.sdt.avcontroller.domainexchange.base.ProcessMsgThread;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class DomainexchangeApplication {
	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(DomainexchangeApplication.class);
		ConfigurableApplicationContext context = app.run(args);
		ProcessMsgThread processMsgThread = (ProcessMsgThread)context.getBean("processMsgThread");
		processMsgThread.start();
	}
}

