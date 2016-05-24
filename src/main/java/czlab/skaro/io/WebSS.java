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


package czlab.skaro.io;

/**
 * @author kenl
 */
public interface WebSS {

  public void setMaxInactiveInterval( long idleSecs);
  public void setAttribute(Object k, Object v);
  public Object getAttribute(Object k);
  public void removeAttribute(Object k);
  public void clear();
  public Iterable<?> listAttributes();
  public boolean isNew();
  public boolean isNull();
  public boolean isSSL();
  public void invalidate();
  public void setNew(boolean flag, long maxAge);
  public void setXref(Object csrf);
  public long getCreationTime();
  public long getExpiryTime();
  public Object getId();
  public Object getXref();
  public Object getLastError();
  public long getLastAccessedTime();
  public long getMaxInactiveInterval();

}


