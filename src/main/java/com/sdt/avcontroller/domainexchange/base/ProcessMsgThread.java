package com.sdt.avcontroller.domainexchange.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component(value="processMsgThread")
public class ProcessMsgThread extends Thread{
    private static Logger log = LoggerFactory.getLogger(ProcessMsgThread.class);


    @Autowired
    private MsgHolder msgHolder;
    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()){
            try {
                String msg = msgHolder.popMsg();



            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
