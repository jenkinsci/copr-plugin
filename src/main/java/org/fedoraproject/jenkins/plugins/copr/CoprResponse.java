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

//temporary solution only
//will be replaced with proper copr-client library
public class CoprResponse {
    private String output;
    private String error;
    private String message;
    private String id;
    private long[] ids;
    private String status;

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getId() {
        return id;
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="EI_EXPOSE_REP")
    public long[] getIds() {
        return ids;
    }

    public String getStatus() {
        return status;
    }

    public boolean outputIsOk() {
        return (output.equals("ok")) ? true : false;
    }
}
