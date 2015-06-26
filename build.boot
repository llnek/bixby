(set-env!
  :source-paths #{"src/main/java"}
  :dependencies '[

    [bouncycastle/bcprov-jdk15on "152" ]
    [bouncycastle/bcmail-jdk15on "152" ]
    [bouncycastle/bcpkix-jdk15on "152" ]
    [org.jasypt/jasypt "1.9.2" ]
    [org.mindrot/jbcrypt "0.3m" ]

    [org.slf4j/slf4j-api "1.7.10" ]
    [log4j/log4j "1.2.17" ]

    [ch.qos.logback/logback-classic "1.1.3" ]
    [ch.qos.logback/logback-core "1.1.3" ]

    [net.sourceforge.jregex/jregex "1.2_01" ]
    [net.sf.jopt-simple/jopt-simple "4.6" ]
    [com.google.guava/guava "18.0" ]
    [com.google.code.findbugs/jsr305 "2.0.3" ]
    [joda-time/joda-time "2.7" ]
    [org.zeroturnaround/zt-exec "1.6" ]
    [org.zeroturnaround/zt-zip "1.7" ]
    [org.apache.axis/axis "1.4" ]
    [org.apache.axis/axis-jaxrpc "1.4" ]
    [org.jetlang/jetlang "0.2.12" ]

    [org.jdom/jdom2 "2.0.6" ]

    [com.fasterxml.jackson.core/jackson-core "2.4.4" ]
    [com.fasterxml.jackson.core/jackson-databind "2.4.4" ]
    [com.fasterxml.jackson.core/jackson-annotations "2.4.4" ]

    [com.google.code.gson/gson "2.3.1" ]

    [org.apache.commons/commons-compress "1.9" ]
    [org.apache.commons/commons-lang3 "3.4" ]
    [commons-net/commons-net "3.3" ]
    [commons-io/commons-io "2.4" ]

    [commons-logging/commons-logging "1.2" ]
    [org.apache.commons/commons-email "1.4" ]
    [commons-codec/commons-codec "1.10" ]
    [commons-fileupload/commons-fileupload "1.3.1" ]
    [commons-dbutils/commons-dbutils "1.6" ]
    [com.sun.mail/javax.mail "1.5.3" ]

    [org.apache.ivy/ivy "2.4.0" ]
    [org.apache.ant/ant "1.9.5" ]
    [org.apache.ant/ant-launcher "1.9.5" ]
    [org.apache.ant/ant-junit4 "1.9.5" ]
    [org.apache.ant/ant-junit "1.9.5" ]
    [org.apache.ant/ant-apache-log4j "1.9.5" :exclusions [log4j]]

    [ant-contrib/ant-contrib "1.0b3" :exclusions [ant]]
    ;;[org.codehaus.gant/gant_groovy2.4 "1.9.12" ]

    [com.jolbox/bonecp "0.8.0.RELEASE" ]

    [org.apache.httpcomponents/httpcore-nio "4.4" ]
    [org.apache.httpcomponents/httpcore "4.4" ]
    [org.apache.httpcomponents/httpclient "4.4" ]
    [io.netty/netty-all "4.0.28.Final" ]

    [com.corundumstudio.socketio/netty-socketio "1.7.7" :exclusions [io.netty]]

    [org.eclipse.jetty/jetty-xml "9.2.11.v20150529"  ]
    [org.eclipse.jetty/jetty-server "9.2.11.v20150529"  ]
    [org.eclipse.jetty/jetty-continuation "9.2.11.v20150529"  ]
    [org.eclipse.jetty/jetty-servlet "9.2.11.v20150529"  ]
    [org.eclipse.jetty/jetty-server "9.2.11.v20150529"  ]
    [org.eclipse.jetty/jetty-util "9.2.11.v20150529"  ]
    [org.eclipse.jetty/jetty-security "9.2.11.v20150529"  ]
    [org.eclipse.jetty/jetty-webapp "9.2.11.v20150529"  ]
    [org.eclipse.jetty.websocket/websocket-api "9.2.11.v20150529"  ]
    [org.eclipse.jetty.websocket/websocket-common "9.2.11.v20150529"  ]
    [org.eclipse.jetty.websocket/websocket-servlet "9.2.11.v20150529"  ]
    [org.eclipse.jetty.websocket/websocket-client "9.2.11.v20150529"  ]
    [org.eclipse.jetty.websocket/websocket-server "9.2.11.v20150529"  ]

    [org.codehaus.groovy/groovy-all "2.4.3" ]

    [com.sun.tools/tools "1.8.0"  ]
    [org.javassist/javassist "3.19.0-GA"  ]

    [com.github.spullara.mustache.java/compiler "0.9.0" ]

    [org.freemarker/freemarker "2.3.22" ]

    [com.yahoo.platform.yui/yuicompressor "2.4.8"  :exclusions [rhino]]

    [org.apache.geronimo.specs/geronimo-jms_1.1_spec "1.1.1" ]
    [com.h2database/h2 "1.4.187" ]
    [org.postgresql/postgresql "9.4-1201-jdbc41" ]

    [org.clojure/math.numeric-tower "0.0.4" ]
    [org.clojure/math.combinatorics "0.0.8" ]
    [org.clojure/tools.logging "0.3.1" ]
    [org.clojure/tools.nrepl "0.2.8" ]
    [org.clojure/tools.reader "0.8.16" ]
    [org.clojure/data.codec "0.1.0" ]
    [org.clojure/data.csv "0.1.2" ]
    [org.clojure/java.jdbc "0.3.6" ]
    [org.clojure/java.data "0.1.1" ]
    [org.clojure/java.jmx "0.3.0" ]
    [org.clojure/data.json "0.2.6" ]
    [org.clojure/data.xml "0.0.8" ]
    [org.clojure/core.cache "0.6.3" ]
    [org.clojure/core.match "0.2.2" ]
    [org.clojure/tools.cli "0.3.1" ]
    [org.clojure/data.generators "0.1.2" ]
    [org.clojure/core.async "0.1.346.0-17112a-alpha" ]
    [org.clojure/core.logic "0.8.10" ]
    [org.clojure/algo.monads "0.1.5" ]
    [org.clojure/algo.generic "0.1.2" ]
    [org.clojure/core.memoize "0.5.7" ]
    [codox/codox.core "0.8.12" ]

    [org.clojure/clojure "1.6.0" ]
    [org.clojure/clojurescript "0.0-3058" ]

    [org.apache.shiro/shiro-core "1.2.3" ]
    [org.mozilla/rhino "1.7.6" ]
    [jline/jline "1.0" ]

    [net.mikera/cljunit "0.3.1" ]
    [junit/junit "4.12"  ]
    [com.googlecode.jslint4java/jslint4java "2.0.5" ]

  ])

