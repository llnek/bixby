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


package com.zotohlab.frwk.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A map that has case-ignored string keys.
 *
 * @author kenl
 *
 * @param <T>
 */
public class NCOrderedMap<T> extends NCMap<T> {

  private static final long serialVersionUID = -3637175588593032279L;
  private Map<String,T> _map= new LinkedHashMap<>();

  public Set<Map.Entry<String,T>> entrySet() {
    return _map.entrySet();
  }

  public T put(String key, T value) {
//    _map.put( key.toLowerCase(), value); dont need to be lowercase, right ?
    _map.put( key, value);
    return super.put(key, value);
  }

  public T remove(String key) {
//    _map.remove(key.toLowerCase());
    _map.remove(key);
    return super.remove(key);
  }

  public Set<String> keySet() {
    return _map.keySet();
  }

  public void clear() {
    _map.clear();
    super.clear();
  }

  public Collection<T> values() {
    return _map.values();
  }

  public void putAll(Map<? extends String,? extends T> m) {
    _map.putAll(m);
    super.putAll(m);
  }

}



