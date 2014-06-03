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


package com.zotohlab.mock.jms;

import javax.naming.*;
import java.util.Hashtable;


/**
 * @author kenl
 *
 */
public class MockContext implements Context {
  @Override
  public Object lookup(Name name) throws NamingException {
    return null;
  }

  @Override
  public Object lookup(String name) throws NamingException {
    if ("qcf".equals(name)) { return new MockQueueConnFactory(); }
    if ("tcf".equals(name)) { return new MockTopicConnFactory(); }
    if ("cf".equals(name)) { return new MockConnFactory(); }
    if (name.startsWith("queue.")) return new MockQueue(name);
    if (name.startsWith("topic.")) return new MockTopic(name);
    return null;
  }

  @Override
  public void bind(Name name, Object obj) throws NamingException {

  }

  @Override
  public void bind(String name, Object obj) throws NamingException {

  }

  @Override
  public void rebind(Name name, Object obj) throws NamingException {

  }

  @Override
  public void rebind(String name, Object obj) throws NamingException {

  }

  @Override
  public void unbind(Name name) throws NamingException {

  }

  @Override
  public void unbind(String name) throws NamingException {

  }

  @Override
  public void rename(Name oldName, Name newName) throws NamingException {

  }

  @Override
  public void rename(String oldName, String newName) throws NamingException {

  }

  @Override
  public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
    return null;
  }

  @Override
  public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
    return null;
  }

  @Override
  public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
    return null;
  }

  @Override
  public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
    return null;
  }

  @Override
  public void destroySubcontext(Name name) throws NamingException {

  }

  @Override
  public void destroySubcontext(String name) throws NamingException {

  }

  @Override
  public Context createSubcontext(Name name) throws NamingException {
    return null;
  }

  @Override
  public Context createSubcontext(String name) throws NamingException {
    return null;
  }

  @Override
  public Object lookupLink(Name name) throws NamingException {
    return null;
  }

  @Override
  public Object lookupLink(String name) throws NamingException {
    return null;
  }

  @Override
  public NameParser getNameParser(Name name) throws NamingException {
    return null;
  }

  @Override
  public NameParser getNameParser(String name) throws NamingException {
    return null;
  }

  @Override
  public Name composeName(Name name, Name prefix) throws NamingException {
    return null;
  }

  @Override
  public String composeName(String name, String prefix) throws NamingException {
    return null;
  }

  @Override
  public Object addToEnvironment(String propName, Object propVal) throws NamingException {
    return null;
  }

  @Override
  public Object removeFromEnvironment(String propName) throws NamingException {
    return null;
  }

  @Override
  public Hashtable<?, ?> getEnvironment() throws NamingException {
    return null;
  }

  @Override
  public void close() throws NamingException {

  }

  @Override
  public String getNameInNamespace() throws NamingException {
    return null;
  }
}
