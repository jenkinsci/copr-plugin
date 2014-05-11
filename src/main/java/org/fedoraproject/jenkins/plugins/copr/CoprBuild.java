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

import org.fedoraproject.jenkins.plugins.copr.exception.CoprException;

import com.google.gson.Gson;

//temporary solution only
//will be replaced with proper copr-client library
class CoprBuild {
    private long id;
    private CoprClient copr;
    private String username;

    public CoprBuild(long id) {
        this.id = id;
    }

    public CoprBuildStatus getStatut() throws CoprException {
        String url = "/api/coprs/build_status/%d/";

        String json = copr.doGet(username, String.format(url, id));

        CoprResponse resp = new Gson().fromJson(json, CoprResponse.class);

        if (!resp.outputIsOk()) {
            throw new CoprException(resp.getError());
        }

        if (resp.getStatus().equals("pending")) {
            return CoprBuildStatus.PENDING;
        } else if (resp.getStatus().equals("running")) {
            return CoprBuildStatus.RUNNING;
        } else if (resp.getStatus().equals("failed")) {
            return CoprBuildStatus.FAILED;
        } else if (resp.getStatus().equals("succeeded")) {
            return CoprBuildStatus.SUCCEEDED;
        } else if (resp.getStatus().equals("canceled")) {
            return CoprBuildStatus.CANCELED;
        }

        throw new CoprException("Unknown build status");
    }

    public long getId() {
        return id;
    }

    void setCoprClient(CoprClient copr) {
        if (this.copr == null) {
            this.copr = copr;
        }
    }

    void setUsername(String username) {
        this.username = username;
    }

    public static enum CoprBuildStatus {
        PENDING, RUNNING, FAILED, SUCCEEDED, CANCELED
    }
}
