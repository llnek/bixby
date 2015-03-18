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

package com.zotohlab.frwk.dbio;

import java.sql.Connection;

/**
 * @author kenl
 */
public interface  Transactable {

  /**
   * param fn  Acts like a closure.  The fn is executed within the context of a
   *           transaction.
   */
  public Object execWith(Object fn) ;

  public Connection begin();

  public void commit(Connection c);

  public void rollback(Connection c);

}

