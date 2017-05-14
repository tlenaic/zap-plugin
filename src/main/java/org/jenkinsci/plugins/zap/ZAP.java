package org.jenkinsci.plugins.zap;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.slaves.NodeSpecific;
import hudson.slaves.SlaveComputer;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;
import org.zaproxy.clientapi.core.ApiResponseList;
import org.zaproxy.clientapi.core.ApiResponseSet;
import org.zaproxy.clientapi.core.ClientApi;
import org.zaproxy.clientapi.core.ClientApiException;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.tools.ant.BuildException;
import org.jenkinsci.remoting.RoleChecker;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.FilePath.FileCallable;

public class ZAP extends AbstractDescribableImpl<ZAP> implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String API_KEY = "ZAPROXY-PLUGIN";

    /* Command Line Options - Not exposed through the API */
    private static final String CMD_LINE_DIR = "-dir";
    private static final String CMD_LINE_HOST = "-host";
    private static final String CMD_LINE_PORT = "-port";
    private static final String CMD_LINE_DAEMON = "-daemon";
    private static final String CMD_LINE_CONFIG = "-config";
    private static final String CMD_LINE_API_KEY = "api.key";

    private int timeout;
    private String installationEnvVar;
    private String homeDir;
    private AbstractBuild<?, ?> build;
    private BuildListener listener;
    private Launcher launcher;
    private String host;
    private int port;
    private List<String> command = new ArrayList<String>();
    private ArrayList<ZAPCmdLine> commandLineArgs;
    private EnvVars envVars;
    
    /* ZAP executable files */
    private static final String ZAP_PROG_NAME_BAT = "zap.bat";
    private static final String ZAP_PROG_NAME_SH = "zap.sh";

    public ZAP(AbstractBuild<?, ?> build, BuildListener listener, Launcher launcher,
            int timeout, String installationEnvVar, String homeDir,
            String host, int port, ArrayList<ZAPCmdLine> commandLineArgs) throws IOException, InterruptedException {
        this.build = build;
        this.listener = listener;
        this.launcher = launcher;
        this.timeout = timeout;
        this.installationEnvVar = installationEnvVar;
        this.homeDir = homeDir;
        this.host = host;
        this.port = port;
        this.commandLineArgs = commandLineArgs;
        this.envVars = build.getEnvironment(listener);;
        System.out.println(this.toString());
    }

    private void init () {
        
    }

    @Override
    public String toString() {
        String s = "";
        s += "Something";
        s += "\n";
        return s;
    }

    public String getInstallationDir() throws IOException, InterruptedException {
        return this.build.getEnvironment(this.listener).get(this.installationEnvVar);
    }

    public String getAppName() throws IOException, InterruptedException {
        Node node = build.getBuiltOn();
        String appName = "";

        /* Append zap program following Master/Slave and Windows/Unix */
        if ("".equals(node.getNodeName())) { // Master
            if (File.pathSeparatorChar == ':')
                appName = "/" + ZAP_PROG_NAME_SH;
            else
                appName = "\\" + ZAP_PROG_NAME_BAT;
        } else if ("Unix".equals(((SlaveComputer) node.toComputer()).getOSDescription()))
            appName = "/" + ZAP_PROG_NAME_SH;
        else
            appName = "\\" + ZAP_PROG_NAME_BAT;
        return appName;
    }

    public FilePath getWorkspace(){
        FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            Node node = build.getBuiltOn();
            if (node == null)
                throw new NullPointerException("No such build node: " + build.getBuiltOnStr());
            throw new NullPointerException("No workspace from node " + node + " which is computer " + node.toComputer() + " and has channel " + node.getChannel());
        }
        return workspace;
    }

    public FilePath getAppPath() throws IOException, InterruptedException {
        FilePath fp =  new FilePath(getWorkspace().getChannel(), getInstallationDir() + getAppName());
        Utils.loggerMessage(listener, 0, "[{0}] CONFIGURE RUN COMMANDS for [ {1} ]", Utils.ZAP, fp.getRemote());
        return fp;
    }

    public void checkParams(String installationDir) throws IllegalArgumentException, IOException, InterruptedException {
        Utils.loggerMessage(listener, 0, "[{0}] PLUGIN VALIDATION (PLG), VARIABLE VALIDATION AND ENVIRONMENT INJECTOR EXPANSION (EXP)", Utils.ZAP);

        if (installationDir == null || installationDir.isEmpty())
            throw new IllegalArgumentException("ZAP INSTALLATION DIRECTORY IS MISSING, PROVIDED [ " + installationDir + " ]");
        else
            Utils.loggerMessage(listener, 1, "ZAP INSTALLATION DIRECTORY = [ {0} ]", installationDir);
    }

    public List<String> getCommand() {
        return this.command;
    }

    public void setCommand() throws IOException, InterruptedException {
        this.command.add(getAppPath().getRemote());
        this.command.add(CMD_LINE_DAEMON);
        this.command.add(CMD_LINE_HOST);
        this.command.add(host);
        this.command.add(CMD_LINE_PORT);
        this.command.add(Integer.toString(port));
        this.command.add(CMD_LINE_CONFIG);
        this.command.add(CMD_LINE_API_KEY + "=" + API_KEY);
        this.command.add(CMD_LINE_DIR);
        this.command.add(homeDir);
        
        System.out.println("this.command: " + this.command.size());
        System.out.println("this.commandLineArgs: " + this.commandLineArgs.size());
        /* Adds command line arguments if it's provided */
        if (!this.commandLineArgs.isEmpty()) addZapCmdLine(this.command, this.commandLineArgs);
    }

    /**
     * Add list of command line to the list in param
     *
     * @param list
     *            of type List<String>: the list to attach ZAP command line to.
     * @param cmdList
     *            of type ArrayList<ZAPCmdLine>: the list of ZAP command line options and their values.
     */
    private void addZapCmdLine(List<String> list, ArrayList<ZAPCmdLine> cmdList) {
        System.out.println("extra: " + cmdList.size());
        System.out.println("original: " + list.size());
        for (ZAPCmdLine zapCmd : cmdList) {
            if (zapCmd.getCmdLineOption() != null && !zapCmd.getCmdLineOption().isEmpty()) list.add(zapCmd.getCmdLineOption());
            if (zapCmd.getCmdLineValue() != null && !zapCmd.getCmdLineValue().isEmpty()) list.add(zapCmd.getCmdLineValue());
        }
    }

    // If the JDK was changed through the job's configurations, then update the Build's Environment Variables, can now remove the Java field to save space. Important, check if the 1.6 LTS is working with this or not.
    public void setBuildJDK() throws IOException, InterruptedException {
        EnvVars envVars = getEnvVars();

        System.out.println("setBuildJDK INSIDE: " + build.getBuildVariables().entrySet().size());

        for (Map.Entry<String, String> e : build.getBuildVariables().entrySet()) {
            envVars.put(e.getKey(), e.getValue());
            System.out.println("KEY: " + e.getKey() + "VALUE: " + e.getValue());
        }

        FilePath workDir = new FilePath(getWorkspace().getChannel(), getInstallationDir());

        System.out.println("INSIDE THE SETTER");
        AbstractProject<?, ?> project = build.getProject();
        //JDK 8u131 test
        System.out.println("project.getJDK()");
        JDK jdk = project.getJDK();
        System.out.println("JDK: " + (jdk == null));

        System.out.println("Jenkins.getInstance().getJDK(\"JDK 8u131 test\")");
        jdk = Jenkins.getInstance().getJDK("JDK 8u131 test");
        System.out.println("JDK: " + (jdk == null));

        if (jdk != null) {
            // System.out.println("JDK: " + jdkToUse.getHome());
            // System.out.println("JDK: " + jdkToUse.getName());
            Computer computer = Computer.currentComputer();
            /* just in case we are not in a build */
            if (computer != null)
                jdk = jdk.forNode(computer.getNode(), listener);
            jdk.buildEnvVars(envVars);
        }
        // System.out.println();
        // printMap(getEnvVars());
        // System.out.println();

    }

    private EnvVars getEnvVars () throws IOException, InterruptedException {
        return this.envVars;
    }

    public void setCommandLineArgs(String key, String value) {
        switch (key) {
        case "HOST":
            command.add(CMD_LINE_HOST);
            command.add(value);
            break;
        case "PORT":
            command.add(CMD_LINE_PORT);
            command.add(value);
            break;
        case "HOME":
            command.add(CMD_LINE_DIR);
            command.add(value);
            break;
        default:
            command.add(key);
            command.add(value);
            break;
        }
    }

    private void printMap(Map mp) {
        Iterator it = mp.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            System.out.println(pair.getKey() + " = " + pair.getValue());
            it.remove(); // avoids a ConcurrentModificationException
        }
    }
    // public void setDefaultCmdLineArgs(String zap, String host, String port) {
    // cmdLineArgs.add(zap);
    // cmdLineArgs.add(CMD_LINE_DAEMON);
    // cmdLineArgs.add(CMD_LINE_HOST);
    // cmdLineArgs.add(host);
    // cmdLineArgs.add(CMD_LINE_PORT);
    // cmdLineArgs.add(port);
    // cmdLineArgs.add(CMD_LINE_CONFIG);
    // cmdLineArgs.add(CMD_LINE_API_KEY + "=" + API_KEY);
    // }
}
