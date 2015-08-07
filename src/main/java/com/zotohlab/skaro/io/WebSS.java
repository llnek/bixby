// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013-2015, Ken Leung. All rights reserved.

package com.zotohlab.skaro.io;

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


