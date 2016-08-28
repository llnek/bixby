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

package czlab.skaro.server;

import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.ClassFileTransformer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.net.MalformedURLException;
import org.apache.commons.io.IOUtils;
import java.io.FilenameFilter;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**/
public class CljAppLoader extends URLClassLoader {

  private final List<ClassFileTransformer> _transformers = new CopyOnWriteArrayList<>();
  private final Set<String> _exts=new HashSet<String>();
  private final ClassLoader _parent;
  private boolean _loaded;

  static {
    registerAsParallelCapable();
  }

  public static CljAppLoader newInstance(File homeDir, File appDir) {
    ClassLoader c= Thread.currentThread().getContextClassLoader();
    ClassLoader s= ClassLoader.getSystemClassLoader();
    return new CljAppLoader(c==null ? s : c).init(homeDir,appDir);
  }

  /**/
  private CljAppLoader(ClassLoader p) {
    super(new URL[]{}, p);
    _parent=getParent();
    if (_parent==null) {
      throw new IllegalArgumentException("no parent classloader.");
    }
    _exts.add(".jar");
    _exts.add(".zip");
  }

  /**/
  private boolean isFileSupported(String file) {
    int dot = file.lastIndexOf('.');
    return dot >= 0 && _exts.contains(file.substring(dot));
  }

  /**/
  private boolean isSystem(String name) {
    if (name != null) {
      return (name.startsWith("javax.") ||
              name.startsWith("java.") ||
              name.startsWith("javax/") ||
              name.startsWith("java/"));
    }
    return false;
  }

  @Override
  public Enumeration<URL> getResources(String name)
  throws IOException {
    boolean sys= isSystem(name);
    Enumeration<URL> p = !sys ? null:_parent.getResources(name);
    Enumeration<URL> t = (sys && !p.hasMoreElements()) ? null : this.findResources(name);
    List<URL> s = toList(t);
    s.addAll(toList(p));
    return Collections.enumeration(s);
  }

  /**/
  private List<URL> toList(Enumeration<URL> e) {
    return e==null ? new ArrayList<URL>() : Collections.list(e);
  }

  @Override
  public URL getResource(String name) {
    boolean tried_parent= false;
    boolean sys=isSystem(name);
    ClassLoader source=null;
    URL url= null;

    if (_parent != null && sys) {
      tried_parent= true;
      source=_parent;
      url=_parent.getResource(name);
    }

    if (url == null) {
      url= this.findResource(name);
      source=this;
      if (url == null && name.startsWith("/")) {
        url= this.findResource(name.substring(1));
      }
    }

    if (url == null && !tried_parent && sys) {
      if (_parent!=null) {
        tried_parent=true;
        source=_parent;
        url= _parent.getResource(name);
      }
    }

    return url;
  }

  @Override
  public Class<?> loadClass(String name)
  throws ClassNotFoundException {
    return loadClass(name, false);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve)
  throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      Class<?> c= findLoadedClass(name);
      ClassNotFoundException ex= null;
      boolean tried_parent= false;
      boolean sys=isSystem(name);
      ClassLoader source=null;

      if (c == null && _parent != null && sys) {
        tried_parent= true;
        source=_parent;
        try {
          c= _parent.loadClass(name);
        } catch (ClassNotFoundException e) {
          ex= e;
        }
      }

      if (c == null) {
        try {
          source=this;
          c= this.findClass(name);
        } catch (ClassNotFoundException e) {
          ex= e;
        }
      }

      if (c == null && _parent != null && !tried_parent && sys) {
        tried_parent=true;
        source=_parent;
        c= _parent.loadClass(name);
      }

      if (c == null && ex!=null) {
        throw ex;
      }

      if (resolve) {
        resolveClass(c);
      }

      return c;
    }
  }

  /**/
  public void addTransformer(ClassFileTransformer t) {
    _transformers.add(t);
  }

  /**/
  public boolean removeTransformer(ClassFileTransformer t) {
    return _transformers.remove(t);
  }

  @Override
  protected Class<?> findClass(final String name)
  throws ClassNotFoundException {
    Class<?> clazz=null;
    if (_transformers.isEmpty()) {
      clazz = super.findClass(name);
    }
    else {
      String path = name.replace('.', '/').concat(".class");
      URL url = getResource(path);
      if (url==null) {
        throw new ClassNotFoundException(name);
      }

      try (InputStream content= url.openStream()) {
        byte[] bytes = IOUtils.toByteArray(content);
        for (ClassFileTransformer t : _transformers) {
          byte[] tmp = t.transform(this,name,null,null,bytes);
          if (tmp != null) { bytes = tmp; }
        }
        clazz=defineClass(name,bytes,0,bytes.length);
      } catch (IOException e) {
        throw new ClassNotFoundException(name,e);
      } catch (IllegalClassFormatException e) {
        throw new ClassNotFoundException(name,e);
      }
    }
    return clazz;
  }

  @Override
  public void close() throws IOException {
    super.close();
  }

  @Override
  public String toString() {
    return "CljAppLoader@"+Long.toHexString(hashCode());
  }

  /**/
  private void init0(File baseDir) {
    File p= new File(baseDir, "patch");
    File b= new File(baseDir, "lib");
    File d= new File(baseDir, "dist");
    findUrls(p).findUrls(d).findUrls(b);
  }

  /**
   */
  private CljAppLoader init(File homeDir, File appDir) {
    File s= new File(appDir, "src/main/clojure");
    File j= new File(appDir, "build/j");
    File c= new File(appDir, "build/c");
    File d= new File(appDir, "build/d");
    File p= new File(appDir, "patch");
    File b= new File(appDir, "target");

    c.mkdirs();
    if (!_loaded) {
      findUrls(p);
      addUrl(s);
      addUrl(j);
      addUrl(c);
      findUrls(d);
      findUrls(b);
      init0(homeDir);
    }
    _loaded=true;
    return this;
  }

  /**
   */
  private CljAppLoader findUrls(File dir) {
    if (dir.exists() ) {
      dir.listFiles(new FilenameFilter() {
        public boolean accept(File f,String n) {
          if (n.endsWith(".jar")) {
            addUrl(new File(f,n));
          }
          return false;
        }
      });
    }
    return this;
  }

  /**
   */
  private CljAppLoader addUrl(File f) {
    if (f.exists())
    try {
      addURL( f.toURI().toURL() );
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
    return this;
  }

}


