/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Goran Sarenkapa (JordanGS), and a number of other of contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.jenkinsci.plugins.zap;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
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

/**
 * Contains methods to start and execute ZAPManagement. Members variables are bound to the config.jelly placed to {@link "com/github/jenkinsci/zaproxyplugin/ZAPManagement/config.jelly"}
 *
 * @author Lenaic Tchokogoue
 * @author Goran Sarenkapa
 * @author Mostafa AbdelMoez
 * @author Tanguy de Lignières
 * @author Abdellah Azougarh
 * @author Thilina Madhusanka
 * @author Johann Ollivier-Lapeyre
 * @author Ludovic Roucoux
 *
 * @see <a href= "https://github.com/zaproxy/zap-api-java/tree/master/subprojects/zap-clientapi"> [JAVA] Client API</a> The pom should show the artifact being from maven central.
 */
public class ZAPManagement extends AbstractDescribableImpl<ZAPManagement> implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String API_KEY = "ZAPROXY-PLUGIN";

    /* Command Line Options - Not exposed through the API */
    private static final String CMD_LINE_DIR = "-dir";
    private static final String CMD_LINE_HOST = "-host";
    private static final String CMD_LINE_PORT = "-port";
    private static final String CMD_LINE_DAEMON = "-daemon";
    private static final String CMD_LINE_CONFIG = "-config";
    private static final String CMD_LINE_API_KEY = "api.key";

    // private static final int timeout = 60; /* Time total to wait for ZAP initialization. After this time, the program is stopped. */

    /* ZAP executable files */
    private static final String ZAP_PROG_NAME_BAT = "zap.bat";
    private static final String ZAP_PROG_NAME_SH = "zap.sh";

    @DataBoundConstructor
    public ZAPManagement(boolean buildThresholds, int hThresholdValue, int hSoftValue, int mThresholdValue, int  mSoftValue, int lThresholdValue, int lSoftValue, int iThresholdValue, int iSoftValue, int cumulValue) {

        /* Post Build Step*/
        this.buildThresholds=buildThresholds;
        this.hThresholdValue= hThresholdValue;
        this.hSoftValue = hSoftValue;
        this.mThresholdValue = mThresholdValue;
        this.mSoftValue = mSoftValue;
        this.lThresholdValue = lThresholdValue;
        this.lSoftValue = lSoftValue;
        this.iThresholdValue = iThresholdValue;
        this.iSoftValue = iSoftValue;
        this.cumulValue = cumulValue;
        System.out.println(this.toString());
    }

    /**
     * Evaluated values will return null, this is printed to console on save
     *
     * @return
     */

    @Override
    public String toString() {
        String s = "";
        s += "\n";
        s += "\n";
        s += "Post Build action\n";
        s += "\n";
        s += "Manage Threshold\n";
        s += "-------------------------------------------------------\n";
        s += "high ThresholdValue [" + hThresholdValue + "]\n";
        s += "high SoftValue [" + hSoftValue + "]\n";
        s += "medium ThresholdValue [" + mThresholdValue + "]\n";
        s += "medium SoftValue [" + mSoftValue + "]\n";
        s += "low ThresholdValue [" + lThresholdValue + "]\n";
        s += "low SoftValue [" + lSoftValue + "]\n";
        s += "info ThresholdValue [" + iThresholdValue + "]\n";
        s += "low SoftValue [" + iSoftValue + "]\n";
        s += "cumulative Value [" + cumulValue + "]\n";
        return s;
    }

    private String retrieveZapHomeWithToolInstall(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException {
        EnvVars env = null;
        Node node = null;
        String installPath = null;
        if (getAutoInstall()) {
            env = build.getEnvironment(listener);
            node = build.getBuiltOn();
            for (ToolDescriptor<?> desc : ToolInstallation.all())
                for (ToolInstallation tool : desc.getInstallations())
                    if (tool.getName().equals(getToolUsed())) {
                        if (tool instanceof NodeSpecific) tool = (ToolInstallation) ((NodeSpecific<?>) tool).forNode(node, listener);
                        if (tool instanceof EnvironmentSpecific) tool = (ToolInstallation) ((EnvironmentSpecific<?>) tool).forEnvironment(env);
                        installPath = tool.getHome();
                        return installPath;
                    }
        }
        else installPath = build.getEnvironment(listener).get(this.getInstallationEnvVar());
        return installPath;
    }

    /**
     * Return the ZAP program name with separator prefix (\zap.bat or /zap.sh) depending of the build node and the OS.
     *
     * @param build
     * @return of type String: the ZAP program name with separator prefix (\zap.bat or /zap.sh).
     * @throws IOException
     * @throws InterruptedException
     */
    private String getZAPProgramNameWithSeparator(AbstractBuild<?, ?> build) throws IOException, InterruptedException {
        Node node = build.getBuiltOn();
        String zapProgramName = "";

        /* Append zap program following Master/Slave and Windows/Unix */
        if ("".equals(node.getNodeName())) { // Master
            if (File.pathSeparatorChar == ':') zapProgramName = "/" + ZAP_PROG_NAME_SH;
            else zapProgramName = "\\" + ZAP_PROG_NAME_BAT;
        }
        else if ("Unix".equals(((SlaveComputer) node.toComputer()).getOSDescription())) zapProgramName = "/" + ZAP_PROG_NAME_SH;
        else zapProgramName = "\\" + ZAP_PROG_NAME_BAT;
        return zapProgramName;
    }

    /**
     * Verify parameters of the build setup are correct (null, empty, negative ...)
     *
     * @param build
     * @param listener
     *            of type BuildListener: the display log listener during the Jenkins job execution.
     * @throws InterruptedException
     * @throws IOException
     * @throws Exception
     *             throw an exception if a parameter is invalid.
     */
    private void checkParams(AbstractBuild<?, ?> build, BuildListener listener) throws IllegalArgumentException, IOException, InterruptedException {
        zapProgram = retrieveZapHomeWithToolInstall(build, listener);
        Utils.loggerMessage(listener, 0, "[{0}] PLUGIN VALIDATION (PLG), VARIABLE VALIDATION AND ENVIRONMENT INJECTOR EXPANSION (EXP)", Utils.ZAP);

        if (this.zapProgram == null || this.zapProgram.isEmpty()) throw new IllegalArgumentException("ZAP INSTALLATION DIRECTORY IS MISSING, PROVIDED [ " + this.zapProgram + " ]");
        else Utils.loggerMessage(listener, 1, "ZAP INSTALLATION DIRECTORY = [ {0} ]", this.zapProgram);
    }


    public Proc startZAP(AbstractBuild<?, ?> build, BuildListener listener, Launcher launcher) throws IllegalArgumentException, IOException, InterruptedException {
        Utils.loggerMessage(listener, 0, "Utils gogi got in");

        zapHost=this.getHost();
        zapPort = this.getPort();
        checkParams(build, listener);

        FilePath ws = build.getWorkspace();
        if (ws == null) {
            Node node = build.getBuiltOn();
            if (node == null) throw new NullPointerException("No such build node: " + build.getBuiltOnStr());
            throw new NullPointerException("No workspace from node " + node + " which is computer " + node.toComputer() + " and has channel " + node.getChannel());
        }

        /* Contains the absolute path to ZAP program */
        FilePath zapPathWithProgName = new FilePath(ws.getChannel(), zapProgram + getZAPProgramNameWithSeparator(build));
        Utils.loggerMessage(listener, 0, "[{0}] CONFIGURE RUN COMMANDS for [ {1} ]", Utils.ZAP, zapPathWithProgName.getRemote());

        listener.getLogger().println("my action host:" + zapHost);

        /* Command to start ZAProxy with parameters */
        List<String> cmd = new ArrayList<String>();
        cmd.add(zapPathWithProgName.getRemote());
        cmd.add(CMD_LINE_DAEMON);
        cmd.add(CMD_LINE_HOST);
        cmd.add(zapHost);
        cmd.add(CMD_LINE_PORT);
        cmd.add(Integer.toString(zapPort));
        cmd.add(CMD_LINE_CONFIG);
        cmd.add(CMD_LINE_API_KEY + "=" + API_KEY);

         /* Set the default directory used by ZAP if it's defined and if a scan is provided */
        if (this.getHomeDir() != null && !this.getHomeDir().isEmpty()) {
            cmd.add(CMD_LINE_DIR);
            cmd.add(this.getHomeDir());
        }
        /* Adds command line arguments if it's provided */
        if (!this.commandLineArgs.isEmpty()) addZapCmdLine(cmd,this.getCommandLineArgs());


        EnvVars envVars = build.getEnvironment(listener);
        /* on Windows environment variables are converted to all upper case, but no such conversions are done on Unix, so to make this cross-platform, convert variables to all upper cases. */
        for (Map.Entry<String, String> e : build.getBuildVariables().entrySet())
            envVars.put(e.getKey(), e.getValue());
        FilePath workDir = new FilePath(ws.getChannel(), zapProgram);

        /* JDK choice */
        computeJdkToUse(build, listener, envVars);

        /* Launch ZAP process on remote machine (on master if no remote machine) */
        Utils.loggerMessage(listener, 0, "[{0}] EXECUTE LAUNCH COMMAND", Utils.ZAP);
        Proc proc = launcher.launch().cmds(cmd).envs(envVars).stdout(listener).pwd(workDir).start();

        /* Call waitForSuccessfulConnectionToZap(int, BuildListener) remotely */
        Utils.lineBreak(listener);
        Utils.loggerMessage(listener, 0, "[{0}] INITIALIZATION [ START ]", Utils.ZAP);
        build.getWorkspace().act(new WaitZAPManagementInitCallable(listener, this));
        Utils.lineBreak(listener);
        Utils.loggerMessage(listener, 0, "[{0}] INITIALIZATION [ SUCCESSFUL ]", Utils.ZAP);
        Utils.lineBreak(listener);
        return proc;
    }

    /**
     * Set the JDK to use to start ZAP.
     *
     * @param build
     * @param listener
     *            of type BuildListener: the display log listener during the Jenkins job execution.
     * @param env
     *            of type EnvVars: list of environment variables. Used to set the path to the JDK.
     * @throws IOException
     * @throws InterruptedException
     */
    private void computeJdkToUse(AbstractBuild<?, ?> build, BuildListener listener, EnvVars env) throws IOException, InterruptedException {
        JDK jdkToUse = getJdkToUse(build.getProject());
        if (jdkToUse != null) {
            Computer computer = Computer.currentComputer();
            /* just in case we are not in a build */
            if (computer != null) jdkToUse = jdkToUse.forNode(computer.getNode(), listener);
            jdkToUse.buildEnvVars(env);
        }
    }

    /**
     * @return JDK to be used with this project.
     */
    private JDK getJdkToUse(AbstractProject<?, ?> project) {
        JDK jdkToUse = Jenkins.getInstance().getJDK("InheritFromJob");
        if (jdkToUse == null) jdkToUse = project.getJDK();
        return jdkToUse;
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
        for (ZAPCmdLine zapCmd : cmdList) {
            if (zapCmd.getCmdLineOption() != null && !zapCmd.getCmdLineOption().isEmpty()) list.add(zapCmd.getCmdLineOption());
            if (zapCmd.getCmdLineValue() != null && !zapCmd.getCmdLineValue().isEmpty()) list.add(zapCmd.getCmdLineValue());
        }
    }

    /**
     * Wait for ZAP's initialization such that it is ready to use at the end of the method, otherwise catch the exception. If there is a remote machine, then this method will be launched there.
     *
     * @param listener
     *            of type BuildListener: the display log listener during the Jenkins job execution.
     * @see <a href= "https://groups.google.com/forum/#!topic/zaproxy-develop/gZxYp8Og960"> [JAVA] Avoid sleep to wait ZAProxy initialization</a>
     */
    private void waitForSuccessfulConnectionToZap(BuildListener listener) {
        listener.getLogger().println("waitForSuccessfulConnectionToZap");
        int timeoutInMs = (int) TimeUnit.SECONDS.toMillis(this.getTimeout());
        int connectionTimeoutInMs = timeoutInMs;
        int pollingIntervalInMs = (int) TimeUnit.SECONDS.toMillis(1);
        boolean connectionSuccessful = false;
        long startTime = System.currentTimeMillis();
        Socket socket = null;
        do
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(getHost(), getPort()), connectionTimeoutInMs);
                connectionSuccessful = true;
            }
            catch (SocketTimeoutException ignore) {
                listener.error(ExceptionUtils.getStackTrace(ignore));
                throw new BuildException("Unable to connect to ZAP's proxy after " + this.getTimeout() + " seconds.");

            }
            catch (IOException ignore) {
            /* Try again but wait some time first */
                try {
                    Thread.sleep(pollingIntervalInMs);
                }
                catch (InterruptedException e) {
                    listener.error(ExceptionUtils.getStackTrace(ignore));
                    throw new BuildException("The task was interrupted while sleeping between connection polling.", e);
                }

                long ellapsedTime = System.currentTimeMillis() - startTime;
                if (ellapsedTime >= timeoutInMs) {
                    listener.error(ExceptionUtils.getStackTrace(ignore));
                    throw new BuildException("Unable to connect to ZAP's proxy after " + this.getTimeout() + " seconds.");
                }
                connectionTimeoutInMs = (int) (timeoutInMs - ellapsedTime);
            }
            finally {
                if (socket != null) try {
                    socket.close();
                }
                catch (IOException e) {
                    listener.error(ExceptionUtils.getStackTrace(e));
                }
            }
        while (!connectionSuccessful);
    }

    /**
     * Execute ZAPDriver method following build's setup and stop ZAP at the end. Note: No param's to executeZAP method since they would also need to be accessible in Builder, somewhat redundant.
     *
     * @param listener
     *            of type BuildListener: the display log listener during the Jenkins job execution.
     * @param workspace
     *            of type FilePath: a {@link FilePath} representing the build's workspace.
     * @return of type: boolean DESC: true if no exception is caught, false otherwise.
     */
    public Result executeZAP(BuildListener listener, FilePath workspace) {
        listener.getLogger().println("executeZAP");
        Result buildResult = Result.SUCCESS;
        boolean buildSuccess = true;


        /* Check to make sure that plugin's are installed with ZAP if they are selected in the UI. */

        ClientApi clientApi = new ClientApi(getHost(), getPort(), API_KEY);


        try {
            if (buildSuccess) {
                    /* LOAD SESSION */
                    /*
                     * @class org.zaproxy.clientapi.gen.Core
                     *
                     * @method loadSession
                     *
                     * @param String apikey
                     * @param String name
                     *
                     * @throws ClientApiException
                     */
                clientApi.core.loadSession(this.getSessionFilePath());

                /* SETUP THRESHOLDING */
                Utils.lineBreak(listener);
                buildResult = ManageThreshold( listener, clientApi, this.hThresholdValue, this.hSoftValue, this.mThresholdValue, this.mSoftValue, this.lThresholdValue, this.lSoftValue, this.iThresholdValue, this.iSoftValue, this.cumulValue);

            }
        }
        catch (Exception e) {
            listener.error(ExceptionUtils.getStackTrace(e));
            buildSuccess = false;
            buildResult=Result.ABORTED;
        }
        finally {
            try {
                stopZAP(listener, clientApi);
            }
            catch (ClientApiException e) {
                listener.error(ExceptionUtils.getStackTrace(e));
                buildSuccess = false;
                buildResult=Result.ABORTED;
            }
        }
        Utils.lineBreak(listener);
        return buildResult;
    }

    /**
     * ManageThreshold define build value failed, pass , unstable.
     *
     * @param listener
     *            of type BuildListener: the display log listener during the Jenkins job execution.
     * @param clientApi
     *            of type ClientApi: the ZAP client API to call method.
     * @param hThresholdValue
     *            of type int: the Weight of the alert severity high.
     * @param hSoftValue
     *            of type int: the threshold of the alert severity high.
     * @param mThresholdValue
     *            of type int: the Weight of the alert severity meduim.
     * @param mSoftValue
     *            of type int: the threshold of the alert severity meduim.
     * @param lThresholdValue
     *            of type int: the Weight of the alert severity low.
     * @param lSoftValue
     *            of type int: the threshold of the alert severity low.
     * @param iThresholdValue
     *             of type int: the Weight of the alert severity informational.
     * @param iSoftValue
     *            of type int: the threshold of the alert severity informational.
     * @param cumulValue
     *            of type int: the cumulative threshold of the alerts.
     *
     */

    private Result ManageThreshold(BuildListener listener,ClientApi clientApi, int hThresholdValue, int hSoftValue, int mThresholdValue, int  mSoftValue, int lThresholdValue, int lSoftValue, int iThresholdValue, int iSoftValue, int cumulValue) throws ClientApiException, IOException {

        Utils.lineBreak(listener);
        Utils.loggerMessage(listener, 0, "START : COMPUTE THRESHOLD", Utils.ZAP);
        Result buildResult = Result.SUCCESS;

        Utils.lineBreak(listener);
        int nbAlertHigh = countAlertbySeverity(clientApi, "High");
        Utils.loggerMessage(listener, 1, "ALERTS High COUNT [ {1} ]", Utils.ZAP, Integer.toString(nbAlertHigh));

        int nbAlertMedium = countAlertbySeverity(clientApi, "Medium");
        Utils.loggerMessage(listener, 1, "ALERTS Medium COUNT [ {1} ]", Utils.ZAP, Integer.toString(nbAlertMedium));

        int nbAlertLow = countAlertbySeverity(clientApi, "Low");
        //setLowAlert(nbAlertLow);
        Utils.loggerMessage(listener, 1, "ALERTS Low COUNT [ {1} ]", Utils.ZAP, Integer.toString(nbAlertLow));

        int nbAlertInfo =countAlertbySeverity(clientApi, "Informational");
        Utils.loggerMessage(listener, 1, "ALERTS Informational COUNT [ {1} ]", Utils.ZAP, Integer.toString(nbAlertInfo));

        int hScale = computeProduct(hThresholdValue,nbAlertHigh);
        int mScale = computeProduct(mThresholdValue,nbAlertMedium);
        int lScale = computeProduct(lThresholdValue,nbAlertLow);
        int iScale = computeProduct(iThresholdValue,nbAlertInfo);

        if((mScale > mSoftValue) || (lScale > lSoftValue ) || (iScale > iSoftValue)){buildResult = Result.UNSTABLE;}
        if((hScale > hSoftValue) || ((hScale+mScale+lScale+iScale)> cumulValue)){buildResult = Result.FAILURE;}


        Utils.loggerMessage(listener, 0, "END : COMPUTING THRESHOLD", Utils.ZAP);

        return  buildResult;

    }

    /**
     * computeProduct do the product of two Integer.
     *
     * @param a
     *            of type Integer.
     * @param b
     *            of type Integer.
     */
    public int computeProduct(int a, int b){
        int res;
        res = a * b;
        return res;
    }

    /**
     * countAlertbySeverity count the number of alert by severity.
     *
     * @param clientApi
     *            of type ClientApi: the ZAP client API to call method.
     * @param risk
     *            of type string : it's the alert severity.
     */
    public int countAlertbySeverity(ClientApi clientApi,String risk)throws ClientApiException{
        int nbAlert = 0;
        List<String> tempid = new ArrayList<String>();
        tempid.add("begin");

        List allAlerts1 = ((ApiResponseList) clientApi.core.alerts("","","")).getItems();
        for(int i=0;i<allAlerts1.size();i++) {
            ApiResponseSet temp = ((ApiResponseSet) allAlerts1.get(i));
            if (!tempid.contains(temp.getValue("alert").toString())) {
                if (risk.equals(temp.getValue("risk").toString())) {nbAlert++;}
            }
            tempid.add(temp.getValue("alert").toString());
        }

        return nbAlert;
    }

    /**
     * Stop ZAP if it has been previously started.
     *
     * @param listener
     *            of type BuildListener: the display log listener during the Jenkins job execution.
     * @param clientApi
     *            of type ClientApi: the ZAP client API to call method.
     * @throws ClientApiException
     */
    private void stopZAP(BuildListener listener, ClientApi clientApi) throws ClientApiException {
        if (clientApi != null) {
            Utils.lineBreak(listener);
            Utils.loggerMessage(listener, 0, "[{0}] SHUTDOWN [ START ]", Utils.ZAP);
            Utils.lineBreak(listener);
            /**
             * @class ApiResponse org.zaproxy.clientapi.gen.Core
             *
             * @method shutdown
             *
             * @param String apikey
             *
             * @throws ClientApiException
             */
            clientApi.core.shutdown();
        }
        else Utils.loggerMessage(listener, 0, "[{0}] SHUTDOWN [ ERROR ]", Utils.ZAP);
    }

    /**
     * This class allows to launch a method on a remote machine (if there is, otherwise, on a local machine). The method launched is to wait the complete initialization of ZAProxy.
     **/
    private static class WaitZAPManagementInitCallable implements FileCallable<Void> {

        private static final long serialVersionUID = 1L;

        private BuildListener listener;
        private ZAPManagement zaproxy;

        public WaitZAPManagementInitCallable(BuildListener listener, ZAPManagement zaproxy) {
            this.listener = listener;
            this.zaproxy = zaproxy;
        }

        @Override
        public Void invoke(File f, VirtualChannel channel) {
            zaproxy.waitForSuccessfulConnectionToZap(listener);
            return null;
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException { /* N/A */ }
    }

    @Extension
    public static class ZAPManagementDescriptorImpl extends Descriptor<ZAPManagement> implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * To persist global configuration information, simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */

        /** Represents the build's workspace */
        private FilePath workspace;

        public void setWorkspace(FilePath ws) { this.workspace = ws; }

        @Override
        public String getDisplayName() { return null; }

        /**
         * In order to load the persisted global configuration, you have to call load() in the constructor.
         */
        public ZAPManagementDescriptorImpl() {
            load();
        }
    }

    /*
     * Variable Declaration Getters allows to load members variables into UI. Setters
     */
    @Override
    public ZAPManagementDescriptorImpl getDescriptor() { return (ZAPManagementDescriptorImpl) super.getDescriptor(); }

    /* Overridden for better type safety. If your plugin doesn't really define any property on Descriptor, you don't have to do this. */

    private String contextId; /* ID of the created context. */

    private String userId; /* ID of the created user. */

    /* Post Build Step >> Manage Threshold */

    private int nbAlertLow;
    private void setLowAlert (int a){
        nbAlertLow = a;
    }
    public int getLowAlert(){
        return nbAlertLow;
    }

    //    private final String jdk; /* The IDK to use to start ZAP. */
//
//    public String getJdk() { return jdk; }
//
//    /* Gets the JDK that this Sonar builder is configured with, or null. */
//    public JDK getJDK() { return Jenkins.getInstance().getJDK(jdk); }
//***********
    private static String zapHost;
    private static int zapPort;

    //*************/
    private String zapProgram; /* Path to the ZAP security tool. */

    private final boolean buildThresholds;
    public boolean isbuildThresholds() {return buildThresholds;}

    private final int hThresholdValue;
    public int gethThresholdValue() {return hThresholdValue;}

    private final int hSoftValue;
    public int gethSoftValue() {return hSoftValue;}

    private final int mThresholdValue;
    public int getmThresholdValue() {return mThresholdValue;}

    private final int mSoftValue;
    public int getmSoftValue() {return mSoftValue;}

    private final int lThresholdValue;
    public int getlThresholdValue() {return lThresholdValue;}

    private final int lSoftValue;
    public int getlSoftValue() {return lSoftValue;}

    private final int iThresholdValue;
    public int getiThresholdValue() {return iThresholdValue;}

    private final int iSoftValue;
    public int getiSoftValue() {return iSoftValue;}

    private final int cumulValue;

    public Boolean execute;
    public int getcumulValue() {return cumulValue;}

    /*****************************/

    private boolean buildStatus;
    private boolean autoInstall;
    private int timeout;
    private String installationEnvVar;
    private String homeDir;
    private String host;
    private int port;
    private String toolUsed;
    public String sessionFilePath;

    public void setBuildStatus(boolean buildStatus) {this.buildStatus = buildStatus;}

    public boolean getAutoInstall(){return this.autoInstall;}

    public void setAutoInstall(boolean autoInstall) {this.autoInstall = autoInstall;}

    private String getToolUsed(){return  this.toolUsed;}

    public void setToolUsed(String toolUsed) {this.toolUsed = toolUsed;}

    private int getTimeout() {return this.timeout;}

    public void setTimeout(int timeout) {this.timeout = timeout;}

    private String getInstallationEnvVar() {return this.installationEnvVar;}

    public void setInstallationEnvVar(String installationEnvVar) {this.installationEnvVar = installationEnvVar;}

    private String getHomeDir() {return this.homeDir;}

    public void setHomeDir(String homeDir) {this.homeDir = homeDir;}

    public String getHost() {return this.host;}

    public void setHost(String host) {this.host = host;}

    public int getPort() {return this.port;}

    public void setPort(int port) {this.port = port;}

    public String getSessionFilePath(){return this.sessionFilePath;}

    public void setSessionFilePath(String sessionFilePath){this.sessionFilePath = sessionFilePath;}

    private ArrayList<ZAPCmdLine> commandLineArgs; /* List of all ZAP command lines specified by the user ArrayList because it needs to be Serializable (whereas List is not Serializable). */

    public ArrayList<ZAPCmdLine> getCommandLineArgs() { return commandLineArgs; }

    public void setCommandLineArgs(ArrayList<ZAPCmdLine> commandLineArgs) { this.commandLineArgs = commandLineArgs; }
}
