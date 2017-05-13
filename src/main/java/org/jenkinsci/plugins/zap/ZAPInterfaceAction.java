package org.jenkinsci.plugins.zap;

import java.util.ArrayList;

import org.zaproxy.clientapi.core.ClientApi;

import hudson.model.Action;

public class ZAPInterfaceAction implements Action {

    public String hello;
    public int low;
    private ClientApi goranAPI;
    private int timeout;
    private String installationEnvVar;
    private String homeDir;
    private String host;
    private int port;
    ArrayList<ZAPCmdLine> commandLineArgs;
    
    public ZAPInterfaceAction() {
        this.hello = "";
        this.low = -1;
        this.goranAPI = null;
        this.timeout = -1;
        this.homeDir = "";
        this.installationEnvVar = "";
        this.host = "";
        this.port = 0;
        this.commandLineArgs = null;
        System.out.println();
        System.out.println("timeout: " + timeout);
        System.out.println("homeDir: " + homeDir);
        System.out.println("installationEnv: " + installationEnvVar);
        System.out.println("output: " + hello);
        System.out.println("low: " + low);
        System.out.println("api: " + goranAPI);
    }

    public ZAPInterfaceAction(String hello, int low, ClientApi i, int timeout, String installationEnvVar, String homeDir, String host, int port, ArrayList<ZAPCmdLine> commandLineArgs) {
        this.hello = hello;
        this.low = low;
        this.goranAPI = i;
        this.timeout = timeout;
        this.installationEnvVar = installationEnvVar;
        this.homeDir = homeDir;
        this.host = host;
        this.port = port;
        this.commandLineArgs = commandLineArgs;
        System.out.println();
        System.out.println("timeout: " + timeout);
        System.out.println("homeDir: " + homeDir);
        System.out.println("output: " + hello);
        System.out.println("low: " + low);
        System.out.println("api: " + goranAPI);
        System.out.println("commandLineArgs: " + commandLineArgs.size());
        
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getInstallationEnvVar() {
        return this.installationEnvVar;
    }

    public void setInstallationEnvVar(String installationEnvVar) {
        this.installationEnvVar = installationEnvVar;
    }

    public String getHomeDir() {
        return this.homeDir;
    }

    public void setHomeDir(String homeDir) {
        this.homeDir = homeDir;
    }
    
    public String getHost() {
        return this.host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return this.port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public ArrayList<ZAPCmdLine> getCommandLineArgs(){
        return commandLineArgs;
    }
    
    public void setCommandLineArgs(ArrayList<ZAPCmdLine> commandLineArgs){
        this.commandLineArgs = commandLineArgs;
    }

    public String getHello() {
        return this.hello;
    }

    public void setHello(String hello) {
        this.hello = hello;
    }

    public int getLow() {
        return this.low;
    }

    public void setLow(int low) {
        this.low = low;
    }

    public ClientApi getClientApi() {
        return this.goranAPI;
    }

    public void setClientApi(ClientApi i) {
        this.goranAPI = i;
    }

    @Override
    public String getDisplayName() {
        return "My Action";
    }

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
