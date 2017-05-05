package org.jenkinsci.plugins.zap;

import org.zaproxy.clientapi.core.ClientApi;

import hudson.model.Action;

public class MyAction implements Action {

    public String hello;
    public int low;
    private ClientApi goranAPI;
    
    public MyAction() {
        this.hello = "";
        this.low = -1;
        this.goranAPI = null;
        System.out.println();
        System.out.println("output: " + hello);
        System.out.println("low: " + low);
        System.out.println("api: " + goranAPI);
    }

    public MyAction(String hello, int low, ClientApi i) {
        this.hello = hello;
        this.low = low;
        this.goranAPI = i;
        System.out.println();
        System.out.println("output: " + hello);
        System.out.println("low: " + low);
        System.out.println("api: " + goranAPI);
    }

    public String getHello(){
        return this.hello;
    }

    public void setHello(String hello){
        this.hello = hello;
    }
    
    public int getLow(){
        return this.low;
    }

    public void setLow(int low){
        this.low = low;
    }
    
    public ClientApi getClientApi(){
        return this.goranAPI;
    }
    
    public void setClientApi(ClientApi i){
        this.goranAPI=i;
    }

    @Override
    public String getDisplayName() { return "My Action"; }

    @Override
    public String getIconFileName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getUrlName() {
        // TODO Auto-generated method stub
        return null;
    }

}
