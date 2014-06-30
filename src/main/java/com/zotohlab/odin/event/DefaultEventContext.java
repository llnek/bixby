/*??
// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2014 Cherimoia, LLC. All rights reserved.
 ??*/

package com.zotohlab.odin.event;


import com.zotohlab.odin.event.EventContext;
import com.zotohlab.odin.game.Session;

/**
 * @author kenl
 */
public class DefaultEventContext implements EventContext {

  private Object attachement;
  private Session session;

  public DefaultEventContext(Session session, Object attachement) {
    this.attachement = attachement;
    this.session = session;
  }

  public DefaultEventContext() {
  }

  public Object getAtt() {
    return attachement;
  }

  public Session getSession() {
    return session;
  }

  public void setAtt(Object attachement) {
    this.attachement = attachement;
  }

  public void setSession(Session session) {
    this.session = session;
  }

}

