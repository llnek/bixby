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

package com.zotohlab.frwk.jmx;

import org.apache.commons.lang3.StringUtils;
import java.util.Arrays;

/**
 * @author
 */
public class NameParams {

  private String[] _pms;
  private String _name;

  public NameParams(String name, String[] params) {
    _pms = Arrays.copyOf(params, params.length);
    _name= name;
  }

  public NameParams(String name) {
    this(name, new String[0]);
  }

  public String toString() {
    return _pms.length > 0 ? _name + "/" + StringUtils.join(_pms, '#') : _name;
  }

  public int hashCode() {
    int hash= 31 * (31 + _name.hashCode() );
    if (_pms.length > 0) {
      hash += Arrays.hashCode(_pms );
    }
    return hash;
  }

  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass() ) { return false; } else {
      NameParams other = (NameParams) obj;
      if ( !_name.equals( other._name)) { return false; } else {
        return Arrays.equals(_pms, other._pms);
      }
    }
  }

}


