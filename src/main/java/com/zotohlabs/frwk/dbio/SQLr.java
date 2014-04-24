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
// Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
 ??*/

package com.zotohlabs.frwk.dbio;

import java.sql.Connection;
import java.util.*;

public interface SQLr {

  public List<?> findSome(Object model, Map<String,Object> filters, String ordering);

  public List<?> findSome(Object model, Map<String,Object> filters);

  public List<?> findAll(Object model, String ordering);

  public List<?> findAll(Object model);

  public Object findOne(Object model, Map<String,Object> filters);

  public Object update(Object obj);
  public Object delete(Object obj);
  public Object insert(Object obj);
  public List<?> select(Object model, String sql, List<?> params);

  public List<?> select(String sql, List<?> params);

  public Object executeWithOutput(String sql, List<?> params);

  public Object execute(String sql, List<?> params);

  public int countAll(Object model);

  public void purge(Object model);

  public MetaCache getMetaCache();

}




