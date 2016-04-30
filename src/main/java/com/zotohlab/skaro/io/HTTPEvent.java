/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.
*/


package com.zotohlab.skaro.io;


import java.net.HttpCookie;
import java.util.Set;

import com.zotohlab.frwk.io.XData;

/**
 * @author kenl
 */
public interface HTTPEvent  extends IOEvent {

  public HttpCookie getCookie(String name);

  public Iterable<HttpCookie> getCookies();

  public boolean isKeepAlive();

  public XData data();

  public boolean hasData();

  public long contentLength();

  public String contentType();

  public String encoding();

  public String contextPath();

  public Iterable<String> getHeaderValues(String nm);
  public Set<String> getHeaders();
  public String getHeaderValue(String nm);
  public boolean hasHeader(String nm);

  public Iterable<String> getParameterValues(String nm);
  public Set<String> getParameters();
  public String getParameterValue(String nm);
  public boolean hasParameter(String nm);

  public String localAddr();

  public String localHost();

  public int localPort();

  public String method();

  public String protocol();
  public String host();

  public String queryString();

  public String remoteAddr();

  public String remoteHost();

  public int remotePort();

  public String scheme();

  public String serverName();

  public int serverPort();

  public boolean isSSL();

  public String getUri();

  public String getRequestURL();

  //------------

  public HTTPResult getResultObj();
  public void replyResult();

}



