/*
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.
*/


package com.zotohlab.skaro.etc;

/**
 * @author kenl
 */
public interface AuthPlugin  extends Plugin {

  public void checkAction(Object acctObj, Object action);
  public Object addAccount(Object options);
  public boolean hasAccount(Object options);
  public Object  login(Object user, Object pwd);
  public Iterable<?> getRoles(Object acctObj);
  public Iterable<?> getAccount(Object options);

}

