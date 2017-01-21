/**
 * Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package czlab.wabbit.sys;

import java.lang.instrument.IllegalClassFormatException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.lang.instrument.ClassFileTransformer;
import static org.slf4j.LoggerFactory.getLogger;
import java.net.MalformedURLException;
import org.apache.commons.io.IOUtils;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;

/**
 */
@SuppressWarnings("unused")
public class CljPodLoader extends URLClassLoader {

  public static final Logger TLOG= getLogger(CljPodLoader.class);
  private final Set<String> _exts=new HashSet<String>();
  private final
    List<ClassFileTransformer>
    _transformers = new CopyOnWriteArrayList<>();
  private final ClassLoader _parent;
  private boolean _loaded;

  static {
    registerAsParallelCapable();
  }

  /**
   */
  public static CljPodLoader newInstance(File homeDir, File appDir) {
    ClassLoader c= Thread.currentThread().getContextClassLoader();
    ClassLoader s= ClassLoader.getSystemClassLoader();
    CljPodLoader app= new CljPodLoader(c==null ? s : c);
    app.init(homeDir,appDir);
    return app;
  }

  /**/
  private CljPodLoader(ClassLoader p) {
    super(new URL[]{}, p);
    _parent=getParent();
    if (_parent==null) {
      throw new IllegalArgumentException("no parent classloader");
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
      return name.startsWith("org.xml.sax.") ||
             name.startsWith("org.w3c.dom.") ||
             name.startsWith("com.sun.") ||
             name.startsWith("sun.") ||
             name.startsWith("javax.") ||
             name.startsWith("java.") ||
             name.startsWith("org/xml/sax/") ||
             name.startsWith("org/w3c/dom/") ||
             name.startsWith("com/sun/") ||
             name.startsWith("sun/") ||
             name.startsWith("javax/") ||
             name.startsWith("java/");
    }
    return false;
  }

  @Override
  public Enumeration<URL> getResources(String name)
  throws IOException {
    boolean sys= isSystem(name);
    Enumeration<URL> p = !sys ? null : _parent.getResources(name);
    Enumeration<URL> t = (sys && !p.hasMoreElements())
      ? null : this.findResources(name);
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
      url= findResource(name);
      source=this;
      if (url == null && name.startsWith("/")) {
        url= findResource(name.substring(1));
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

      //TLOG.info("loadClass##### {}", name);

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
          c= findClass(name);
        } catch (ClassNotFoundException e) {
          ex= e;
        }
      }

      if (c == null && _parent != null &&
          !tried_parent && sys) {
        tried_parent=true;
        source=_parent;
        c= _parent.loadClass(name);
      }

      if (c == null && ex != null) {
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
    //TLOG.info("findClass##### {}", name);
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
    return "CljPodLoader@"+Long.toHexString(hashCode());
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
  private CljPodLoader init(File homeDir, File appDir) {
    File s= new File(appDir, "src/main/clojure");
    File c= new File(appDir, "out/classes");
    File p= new File(appDir, "patch");
    File b= new File(appDir, "lib");

    //TLOG.info("classloader#init\nhome={}\ncwd={}", homeDir, appDir);
    //c.mkdirs();
    if (!_loaded) {
      findUrls(p);
      addUrl(c);
      findUrls(b);
      init0(homeDir);
    }
    _loaded=true;
    return this;
  }

  /**
   */
  private CljPodLoader findUrls(File dir) {
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
  private CljPodLoader addUrl(File f) {
    if (f.exists())
    try {
      addURL( f.toURI().toURL() );
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
    return this;
  }

}


