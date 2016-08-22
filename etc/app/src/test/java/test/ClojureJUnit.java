package test;

import mikera.cljunit.ClojureTest;
import java.util.Arrays;
import java.util.List;

public class ClojureJUnit extends ClojureTest {
    @Override
    public List<String> namespaces() {
        return Arrays.asList(new String[]{
                "test.test"
        });
    }
}


