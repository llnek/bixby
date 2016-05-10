/* Licensed under the Apache License, Version 2.0 (the "License");
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
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved. */


package com.zotohlab.skaro.io;

import java.net.HttpCookie;
import java.net.URL;

/**
 * @author kenl
 */
public interface HTTPResult extends IOResult {

  public void setProtocolVersion(String ver);
  public void setStatus(int code);
  public void addCookie(HttpCookie c);

  public void containsHeader(String name);
  public void removeHeader(String name);
  public void clearHeaders();

  public void addHeader(String name, String value);
  public void setHeader(String name, String value);

  public void setChunked(boolean c);
  public void setContent(Object data);

  public void setRedirect(URL location);
  public int getStatus();

}



