// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013, Ken Leung. All rights reserved.

package com.zotohlab.wflow;

import com.zotohlab.frwk.core.Gettable;
import com.zotohlab.frwk.core.Identifiable;
import com.zotohlab.frwk.core.Settable;
import com.zotohlab.frwk.server.Event;
import com.zotohlab.frwk.server.ServerLike;

/**
 * @author kenl
 */
public interface Job extends Gettable , Settable, Identifiable {
  
  public void setLastResult( Object v) ;

  public void clrLastResult() ;

  public Object getLastResult() ;

  public ServerLike container();
  
  public void finz();
  
  public Event<?> event() ;

}




