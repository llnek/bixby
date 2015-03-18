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

package com.zotohlab.frwk.core;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * @author kenl
 */
public interface Configuration {

  public Configuration getChild( String name);
  public List<?> getSequence( String name);

  public boolean contains( String name);
  public int size();

  public String getString( String name, String dft);
  public long getLong( String name, long dft);
  public double getDouble( String name, double dft);
  public boolean getBool( String name, boolean dft);
  public Date getDate( String name);

  public Set<String> getKeys();

}


