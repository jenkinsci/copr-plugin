/*
 * The MIT License
 * 
 * Copyright (c) 2014 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.fedoraproject.jenkins.plugins.copr;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import hudson.util.FormValidation;
import hudson.util.Secret;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

import org.fedoraproject.jenkins.plugins.copr.CoprBuild.CoprBuildStatus;
import org.fedoraproject.jenkins.plugins.copr.exception.CoprException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Plugin for building RPM packages in Copr.
 * 
 * Copr is a lightweight buildsystem that allows users to create packages, put
 * them into repositories, and make it easy for other users to install them.
 * 
 * @see <a href="https://fedorahosted.org/copr">https://fedorahosted.org/copr</a>
 * 
 * @author Michal Srb
 */
public class CoprPlugin extends Notifier {

    protected static final Logger LOGGER = Logger.getLogger(CoprPlugin.class
            .getName());

    private static final String LOG_PREFIX = "Copr plugin: ";

    private final String coprname;
    private final String username;
    private final String srpm;
    private final Secret apilogin;
    private final Secret apitoken;
    private final String apiurl;
    private final String srpmscript;
    private final boolean prepareSrpm;
    private final String coprTimeout;
    private final boolean waitForCoprBuild;

    @DataBoundConstructor
    public CoprPlugin(String coprname, String username, String srpm,
            Secret apilogin, Secret apitoken, String apiurl, String srpmscript,
            boolean prepareSrpm, String coprTimeout, boolean waitForCoprBuild) {
        this.coprname = coprname;
        this.username = username;
        this.srpm = srpm;
        this.apilogin = apilogin;
        this.apitoken = apitoken;
        this.apiurl = apiurl;
        this.srpmscript = srpmscript;
        this.prepareSrpm = prepareSrpm;
        this.coprTimeout = coprTimeout;
        this.waitForCoprBuild = waitForCoprBuild;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {

        // TODO:
        // create repository in Copr if it doesn't exist yet
        // add button to check if provided information are correct (API URL,
        // credentials?)
        // credentials-plugin

        listener.getLogger().println(LOG_PREFIX + "Running Copr plugin");

        if (build.getResult() != Result.SUCCESS) {
            listener.getLogger()
                    .println(LOG_PREFIX + "Build was unsuccessful. Nothing to build in Copr.");
            return true;
        }

        if (prepareSrpm) {
            Result srpmres = prepareSrpm(build, launcher, listener);

            listener.getLogger().println(LOG_PREFIX + srpmres.toString());

            if (srpmres != Result.SUCCESS) {
                return false;
            }
        }

        EnvVars env = build.getEnvironment(listener);
        String srpmstr = env.expand(srpm);
        URL srpmurl = getSrpmUrl(srpmstr, build, listener);

        CoprClient copr = new CoprClient(apiurl, apilogin.getPlainText(), apitoken.getPlainText());
        CoprBuild coprBuild;

        String buildurl = apiurl
                + String.format("/api/coprs/%s/%s/new_build/", username, coprname);
        try {
            coprBuild = copr.scheduleBuild(srpmurl.toString(), username,
                    coprname, buildurl);
        } catch (CoprException e) {
            listener.getLogger().println(e);
            return false;
        }

        listener.getLogger().println(LOG_PREFIX + "New Copr job has been scheduled");

        if (waitForCoprBuild) {
            if (!waitForCoprBuild(coprBuild, listener)) {
                return false;
            }
        }

        return true;
    }

    private Result prepareSrpm(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) throws InterruptedException {
        CommandInterpreter shell;
        if (launcher.isUnix()) {
            shell = new Shell(srpmscript);
        } else {
            shell = new BatchFile(srpmscript);
        }

        return shell.perform(build, launcher, listener) ? Result.SUCCESS
                : Result.FAILURE;
    }

    private URL getSrpmUrl(String srpmurl, AbstractBuild<?, ?> build,
            BuildListener listener) throws IOException, InterruptedException {

        URL url;
        // TODO: add some URL validation
        try {
            url = new URL(srpmurl);
        } catch (MalformedURLException e) {
            String jobUrl = build.getEnvironment(listener).get("JOB_URL");
            if (jobUrl == null) {
                // oops
                throw new AssertionError(String.format(LOG_PREFIX
                        + "JOB_URL env. variable is not set"));
            }
            url = new URL(jobUrl + "/ws/");
            url = new URL(url, srpmurl);
        }

        return url;
    }

    private boolean waitForCoprBuild(CoprBuild coprBuild, BuildListener listener)
            throws InterruptedException {

        // total time to wait for Copr to finish the build (in seconds)
        long timeout = Long.parseLong(coprTimeout) * 60;

        listener.getLogger().println(
                LOG_PREFIX + "Waiting for Copr to finish the build ("
                        + coprTimeout + " minutes)");

        CoprBuildStatus bstatus = CoprBuildStatus.PENDING;
        while (bstatus == CoprBuildStatus.PENDING
                || bstatus == CoprBuildStatus.RUNNING) {

            if (timeout >= 60) {
                // check every 60 seconds if build finished yet
                Thread.sleep(60000);
                timeout -= 60;
            } else if (timeout > 0) {
                Thread.sleep(timeout * 1000);
                timeout = 0;
            } else {
                listener.getLogger()
                        .println(LOG_PREFIX + "Time is up and Copr hasn't finished the build yet.");
                return false;
            }

            try {
                bstatus = coprBuild.getStatut();
            } catch (CoprException e) {
                listener.getLogger().println(e);
                return false;
            }

            listener.getLogger().println(
                    LOG_PREFIX + "build status is " + bstatus.toString());
        }

        if (bstatus != CoprBuildStatus.SUCCEEDED) {
            listener.getLogger().println(
                    LOG_PREFIX + "build failed: " + bstatus.toString());
            return false;
        }

        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public String getCoprname() {
        return coprname;
    }

    public String getUsername() {
        return username;
    }

    public String getSrpm() {
        return srpm;
    }

    public Secret getApilogin() {
        return apilogin;
    }

    public Secret getApitoken() {
        return apitoken;
    }

    public String getApiurl() {
        return apiurl;
    }

    public String getSrpmscript() {
        return srpmscript;
    }

    public boolean getPrepareSrpm() {
        return prepareSrpm;
    }

    public String getCoprTimeout() {
        return coprTimeout;
    }

    public boolean getWaitForCoprBuild() {
        return waitForCoprBuild;
    }

    @Extension
    public static final class DescriptorImpl extends
            BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            load();
        }

        public FormValidation doCheckCoprTimeout(@QueryParameter String value) {
            try {
                Integer.parseInt(value);
            } catch (NumberFormatException _) {
                return FormValidation.error("Not a valid number");
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Build RPM in Copr";
        }
    }
}
