
package czlab.skaro.loaders;

/**
 * A class loader which makes some
 * protected methods in ClassLoader accessible.
 */
class ParClassLoader extends ClassLoader {

  ParClassLoader(ClassLoader p) {
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


