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

package com.zotohlab.frwk.server;

import java.util.HashMap;
import java.util.Map;

/**
 * @author kenl
 *
 */
public class NulEmitter implements Emitter{

  private static final Map<String,?> _cfg = new HashMap<>();
  private ServerLike _server;

  public NulEmitter(ServerLike s) {
    _server=s;
  }

  @Override
  public Object getConfig() {
    return _cfg;
  }

  @Override
  public ServerLike container() {
    return _server;
  }

  @Override
  public void dispatch(Event evt, Object options) {
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public boolean isActive() {
    return false;
  }

  @Override
  public void suspend() {
  }

  @Override
  public void resume() {
  }

  @Override
  public EventHolder release(Object obj) {
    return null;
  }

  @Override
  public void hold(EventHolder obj) {
  }

}






