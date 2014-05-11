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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.fedoraproject.jenkins.plugins.copr.exception.CoprException;

import com.google.gson.Gson;

// temporary solution only
// will be replaced with proper copr-client library
class CoprClient {

    private URL apiurl;
    private String apilogin;
    private String apitoken;

    public CoprClient(String apiurl, String apilogin, String apitoken)
            throws MalformedURLException {
        this.apiurl = new URL(apiurl);
        this.apilogin = apilogin;
        this.apitoken = apitoken;
    }

    public CoprBuild scheduleBuild(String srpmurl, String username,
            String coprname, String buildurl)
            throws IOException, CoprException {

        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("pkgs", srpmurl));

        String json = doPost(username, coprname, params, buildurl);

        CoprResponse resp = new Gson().fromJson(json, CoprResponse.class);

        CoprBuild coprBuild;
        if (resp.outputIsOk()) {
            coprBuild = new CoprBuild(resp.getIds()[0]);
            coprBuild.setCoprClient(this);
            coprBuild.setUsername(username);
        } else {
            throw new CoprException(resp.getError());
        }

        return coprBuild;
    }

    String doGet(String username, String url) throws CoprException {
        CloseableHttpClient httpclient = HttpClients.createDefault();

        HttpGet httpget = new HttpGet(this.apiurl + url);

        try {
            httpget.setHeader("Authorization", "Basic "
                    + Base64.encodeBase64String(String.format("%s:%s",
                            apilogin, apitoken).getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            // here goes trouble
            throw new AssertionError(e);
        }

        String result;
        try {
            CloseableHttpResponse response = httpclient.execute(httpget);
            result = EntityUtils.toString(response.getEntity());
            response.close();
            httpclient.close();
        } catch (IOException e) {
            throw new CoprException("Error while processing HTTP request", e);
        }

        return result;
    }

    private String doPost(String username, String coprname,
            List<NameValuePair> params, String url) throws IOException {

        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost(new URL(apiurl, url).toString());

        try {
            httppost.setHeader("Authorization", "Basic "
                            + Base64.encodeBase64String(String.format("%s:%s",
                                    this.apilogin, this.apitoken).getBytes(
                                    "UTF-8")));
        } catch (UnsupportedEncodingException e) {
            // here goes trouble
            throw new AssertionError(e);
        }

        if (params != null) {
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params,
                    Consts.UTF_8);
            httppost.setEntity(entity);
        }

        String result;
        CloseableHttpResponse response = httpclient.execute(httppost);
        result = EntityUtils.toString(response.getEntity());
        response.close();
        httpclient.close();

        return result;
    }
}
