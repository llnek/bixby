/**
 * A parent-last classloader that will try the child classloader first and then the parent.
 * This takes a fair bit of doing because java really prefers parent-first.
 *
 * For those not familiar with class loading trickery, be wary
 */
private static class ParentLastURLClassLoader extends ClassLoader
{
    private ChildURLClassLoader childClassLoader;

    /**
     * This class allows me to call findClass on a classloader
     */
    private static class FindClassClassLoader extends ClassLoader
    {
        public FindClassClassLoader(ClassLoader parent)
        {
            super(parent);
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException
        {
            return super.findClass(name);
        }
    }

    /**
     * This class delegates (child then parent) for the findClass method for a URLClassLoader.
     * We need this because findClass is protected in URLClassLoader
     */
    private static class ChildURLClassLoader extends URLClassLoader
    {
        private FindClassClassLoader realParent;

        public ChildURLClassLoader( URL[] urls, FindClassClassLoader realParent )
        {
            super(urls, null);

            this.realParent = realParent;
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException
        {
            try
            {
                // first try to use the URLClassLoader findClass
                return super.findClass(name);
            }
            catch( ClassNotFoundException e )
            {
                // if that fails, we ask our real parent classloader to load the class (we give up)
                return realParent.loadClass(name);
            }
        }
    }

    public ParentLastURLClassLoader(List<URL> classpath)
    {
        super(Thread.currentThread().getContextClassLoader());

        URL[] urls = classpath.toArray(new URL[classpath.size()]);

        childClassLoader = new ChildURLClassLoader( urls, new FindClassClassLoader(this.getParent()) );
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
    {
        try
        {
            // first we try to find a class inside the child classloader
            return childClassLoader.findClass(name);
        }
        catch( ClassNotFoundException e )
        {
            // didn't find it, try the parent
            return super.loadClass(name, resolve);
        }
    }
}public class ChildFirstURLClassLoader extends URLClassLoader {

    private ClassLoader system;

    public ChildFirstURLClassLoader(URL[] classpath, ClassLoader parent) {
        super(classpath, parent);
        system = getSystemClassLoader();
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        // First, check if the class has already been loaded
        Class<?> c = findLoadedClass(name);
        if (c == null) {
            if (system != null) {
                try {
                    // checking system: jvm classes, endorsed, cmd classpath, etc.
                    c = system.loadClass(name);
                }
                catch (ClassNotFoundException ignored) {
                }
            }
            if (c == null) {
                try {
                    // checking local
                    c = findClass(name);
                } catch (ClassNotFoundException e) {
                    // checking parent
                    // This call to loadClass may eventually call findClass again, in case the parent doesn't find anything.
                    c = super.loadClass(name, resolve);
                }
            }
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }

    @Override
    public URL getResource(String name) {
        URL url = null;
        if (system != null) {
            url = system.getResource(name);
        }
        if (url == null) {
            url = findResource(name);
            if (url == null) {
                // This call to getResource may eventually call findResource again, in case the parent doesn't find anything.
                url = super.getResource(name);
            }
        }
        return url;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        /**
        * Similar to super, but local resources are enumerated before parent resources
        */
        Enumeration<URL> systemUrls = null;
        if (system != null) {
            systemUrls = system.getResources(name);
        }
        Enumeration<URL> localUrls = findResources(name);
        Enumeration<URL> parentUrls = null;
        if (getParent() != null) {
            parentUrls = getParent().getResources(name);
        }
        final List<URL> urls = new ArrayList<URL>();
        if (systemUrls != null) {
            while(systemUrls.hasMoreElements()) {
                urls.add(systemUrls.nextElement());
            }
        }
        if (localUrls != null) {
            while (localUrls.hasMoreElements()) {
                urls.add(localUrls.nextElement());
            }
        }
        if (parentUrls != null) {
            while (parentUrls.hasMoreElements()) {
                urls.add(parentUrls.nextElement());
            }
        }
        return new Enumeration<URL>() {
            Iterator<URL> iter = urls.iterator();

            public boolean hasMoreElements() {
                return iter.hasNext();
            }
            public URL nextElement() {
                return iter.next();
            }
        };
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        URL url = getResource(name);
        try {
            return url != null ? url.openStream() : null;
        } catch (IOException e) {
        }
        return null;
    }

}
