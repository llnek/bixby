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


package czlab.skaro.etc;

import java.util.Arrays;

/**
 * @author Kenneth Leung
 */
public class NameParams {

  private String[] _pms;
  private String _name;

  /**
   */
  public NameParams(String name, String[] params) {
    _pms = Arrays.copyOf(params, params.length);
    _name= name;
  }

  /**
   */
  public NameParams(String name) {
    this(name, new String[0]);
  }

  /**
   */
  public String toString() {
    StringBuilder b=new StringBuilder(_name+"/");
    if (_pms != null) for (int i=0; i < _pms.length; ++i) {
       if (i > 0) { b.append("#"); }
       b.append(_pms[i]);
    }
    return _pms.length > 0 ? b.toString() : _name;
  }

  /**
   */
  public int hashCode() {
    int hash= 31 * (31 + _name.hashCode() );
    if (_pms.length > 0) {
      hash += Arrays.hashCode(_pms );
    }
    return hash;
  }

  /**
   */
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass() ) { return false; } else {
      NameParams other = (NameParams) obj;
      if ( !_name.equals( other._name)) { return false; } else {
        return Arrays.equals(_pms, other._pms);
      }
    }
  }

}


