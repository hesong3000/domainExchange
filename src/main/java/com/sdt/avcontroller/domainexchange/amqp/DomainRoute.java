package com.sdt.avcontroller.domainexchange.amqp;

import java.io.Serializable;
import java.util.List;

public class DomainRoute implements Serializable {
    public boolean isBroadcast() {
        return isBroadcast;
    }

    public void setBroadcast(boolean broadcast) {
        isBroadcast = broadcast;
    }

    public String getHeadDomainID(){
        if(domainRoute.isEmpty())
            return "";
        return domainRoute.get(0);
    }

    public void appendDomainIDToRoute(String DomainID){
        domainRoute.add(DomainID);
    }

    public void removeHeadDomianID(String DomainID){
        if(domainRoute.isEmpty())
            return;
        if(domainRoute.get(0).compareTo(DomainID)!=0)
            return;
        domainRoute.remove(0);
    }

    private boolean isBroadcast;
    private List<String> domainRoute;
}
