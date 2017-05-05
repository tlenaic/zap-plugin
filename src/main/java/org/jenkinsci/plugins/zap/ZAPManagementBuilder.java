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
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;

/**
 * 
 * @author Goran Sarenkapa
 * 
 */
public class ZAPManagementBuilder extends Recorder {


    @DataBoundConstructor
    public ZAPManagementBuilder(ZAPManagement management) {
        this.management = management;
    }

    private ZAPManagement management;

    public ZAPManagement getManagement() { return management; }

    private Proc proc = null;

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override /* @Override for better type safety, not needed if plugin doesn't define any property on Descriptor */
    public ZAPManagementBuilderDescriptorImpl getDescriptor() { return (ZAPManagementBuilderDescriptorImpl) super.getDescriptor(); }

    /** Method called when the build is launching */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        MyAction abs = build.getAction(MyAction.class);
        System.out.println("my action INFO: " + abs.getHello());
        System.out.println("my action LOW: " + abs.getLow());
        System.out.println("my action API: " + abs.getClientApi());
        listener.getLogger().println("hello, im coming from the build listener");
        listener.getLogger().println("[ZAP Jenkins Plugin] POST-BUILD MANAGEMENT");
        listener.getLogger().println("");
        listener.getLogger().println("gogi");
        listener.getLogger().println("");
        listener.getLogger().println("");
        try {
            Utils.lineBreak(listener);
            Utils.loggerMessage(listener, 0, "[{0}] START BUILD STEP", Utils.ZAP);
            Utils.lineBreak(listener);
            listener.getLogger().println("management: " + management);
            listener.getLogger().println("build: " + build);
            listener.getLogger().println("listener: " + listener);
            listener.getLogger().println("launcher: " + launcher);
            
            proc = management.startZAP(build, listener, launcher);
            listener.getLogger().println("after proc assignment");
        }
        catch (Exception e) {
            listener.getLogger().println("we failed");
            e.printStackTrace();
            listener.error(ExceptionUtils.getStackTrace(e));
            return false;
        }
        
        boolean res;
        try {
            res = build.getWorkspace().act(new ZAPManagementCallable(listener, this.management));
            proc.joinWithTimeout(60L, TimeUnit.MINUTES, listener);
            Utils.lineBreak(listener);
            Utils.lineBreak(listener);
            Utils.loggerMessage(listener, 0, "[{0}] SHUTDOWN [ SUCCESSFUL ]", Utils.ZAP);
            Utils.lineBreak(listener);
        }
        catch (Exception e) {
            e.printStackTrace();
            listener.error(ExceptionUtils.getStackTrace(e));
            return false;
        }
        return res;
    }

    @Extension
    public static final class ZAPManagementBuilderDescriptorImpl extends BuildStepDescriptor<Publisher> {

        /* In order to load the persisted global configuration, you have to call load() in the constructor. */
        public ZAPManagementBuilderDescriptorImpl() { load(); }

        /* Indicates that this builder can be used with all kinds of project types */
        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) { return true; }

        /* This human readable name is used in the configuration screen. */
        @Override
        public String getDisplayName() { return Messages.jenkins_jobconfig_addpostbuild_zap(); }
    }

    /**
     * Used to execute ZAP remotely.
     */
    private static class ZAPManagementCallable implements FileCallable<Boolean> {

        private static final long serialVersionUID = 1L;
        private BuildListener listener;
        private ZAPManagement management;

        public ZAPManagementCallable(BuildListener listener, ZAPManagement management) {
            this.listener = listener;
            this.management = management;
        }

        @Override
        public Boolean invoke(File f, VirtualChannel channel) { return management.executeZAP(listener, new FilePath(f)); }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException { /* N/A */ }
    }
}
