
package czlab.skaro.loaders;


import java.net.URLClassLoader;
import java.net.URL;
import java.util.Enumeration;

/**
 */
class ClassLoaderEx extends ClassLoader {

  ClassLoaderEx(ClassLoader p) {
    super(p);
  }

  @Override
  public Class<?> findClass(String name) {
    return super.findClass(name);
  }

  @Override
  public Class<?> loadClass(String name) {
    return super.loadClass(name);
  }

  @Override
  public Class<?> loadClass(String name, boolean resolve) {
    return super.loadClass(name, resolve);
  }

}


/**
 * URL class loader that exposes the `addURL`
 * and `getURLs` methods in URLClassLoader.
 */
class URLClassLoaderEx extends URLClassLoader {


  /**/
  public URLClassLoaderEx(URL[] urls,ClassLoader p) {
    super(urls, p);
  }

  @Override
  public void addURL(URL u) {
    super.addURL(u);
  }

  @Override
  public URL[] getURLs() {
    return super.getURLs();
  }

}

/**
 * A mutable class loader that gives preference to its own URLs over the parent class loader
 * when loading classes and resources.
 */
public class CFClassLoader extends URLClassLoaderEx {

  private ClassLoaderEx _parent;

  public CFClassLoader(URL[] urls, ClassLoader p) {
    super(urls, null);
    _parent = new ClassLoaderEx(p);
  }

  @Override
  public Class<?> loadClass(String name, boolean resolve) {
    try {
      return super.loadClass(name, resolve);
    } catch (ClassNotFoundException e) {
      return _parent.loadClass(name, resolve);
    }
  }

  @Override
  public URL getResource(String name) {
    URL url = super.findResource(name);
    return (url != null) ? url : _parent.getResource(name);
  }

  @Override
  public Enumeration<URL> getResources(String name) {
    Enumeration<URL> e1= super.findResources(name);
    Enumeration<URL> r2= _parent.getResources(name);
    ArrayList<URL> = new ArrayList<>;
    while (e1.hasMoreElements()) {
      out.add(e1.nextElement());
    }
    while (e2.hasMoreElements()) {
      out.add(e1.nextElement());
    }
    return Collections.enumeration(out);
  }

  @Override
  public void addURL(URL u) {
    super.addURL(url);
  }

}


