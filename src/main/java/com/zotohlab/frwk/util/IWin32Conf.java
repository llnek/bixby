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

package com.zotohlab.frwk.util;
import java.util.Map;
import java.util.Set;

/**
 * Handle windows config file in INI format.
 * 
 * @author kenl
 */
public interface IWin32Conf {

  public Map<?,?> getSection(Object sectionName);
  public Set<?> sectionKeys();

  public void dbgShow();

  public String getString(Object sectionName, Object property, String dft);
  public String getString(Object sectionName, Object property);
  
  public long getLong(Object sectionName, Object property, long dft);
  public long getLong(Object sectionName, Object property);
  
  public int getInt(Object sectionName, Object property, int dft);
  public int getInt(Object sectionName, Object property);
  
  public boolean getBool(Object sectionName, Object property, boolean dft);
  public boolean getBool(Object sectionName, Object property);
    
  public double getDouble(Object sectionName, Object property, double dft);
  public double getDouble(Object sectionName, Object property);

}
