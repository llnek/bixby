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

package com.zotohlab.frwk.crypto;

import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 *
 * @author kenl
 *
 */
public interface PasswordAPI {

  /**
   * Does this password hashed to match the target?
   *
   * @param targetHashed
   * @return
   */
  public boolean validateHash(String targetHashed);
  public char[] toCharArray();

  /**
   * A tuple(2) ['hashed value' 'salt']
   *
   * @return
   */
  public ImmutablePair<String,String> stronglyHashed();

  /**
   * A tuple(2) ['hashed value' 'salt']
   *
   * @return
   */
  public ImmutablePair<String,String> hashed();

  public String encoded();
  public String text();

}


