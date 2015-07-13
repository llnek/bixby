;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(set-env!

  :skaroHome "/wdrive/myspace/skaro/b.out/pack"
  :PID "cocos2d"
  :source-paths #{"src/main/clojure"
                  "src/main/java"}

  :buildVersion "0.9.0-SNAPSHOT"
  :buildDebug true

  :pod "POD-INF"
  :pmode "dev"
  :bld "b.out"

  ;; local additions
  :buildType "web"
  :wjs "scripts"
  :wcs "styles"

  :basedir (System/getProperty "user.dir")

  :dependencies '[])

(require '[clojure.data.json :as js]
         '[clojure.java.io :as io]
         '[clojure.string :as cstr]
         '[boot.core :as bcore]
         '[czlabclj.tpcl.boot
           :as b
           :refer [minitask ge fp!]]
         '[czlabclj.tpcl.antlib :as ant])

(import '[java.io File])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(set-env! :bootBuildDir (fp! (ge :basedir) (ge :bld)))
(set-env! :podDir (fp! (ge :basedir) (ge :pod)))
(set-env! :libDir (fp! (ge :basedir)
                         (ge :target-path)))

(set-env! :srcDir (fp! (ge :basedir) "src" "main"))
(set-env! :tstDir (fp! (ge :basedir) "src" "test"))
(set-env! :webDir (fp! (ge :basedir) "src" "web"))

(set-env! :buildDir (fp! (ge :podDir) "classes"))
(set-env! :qaDir (fp! (ge :podDir) "test"))

(set-env! :reportTestDir (fp! (ge :qaDir) "reports"))
(set-env! :buildTestDir (fp! (ge :qaDir) "classes"))

(set-env! :websrc (fp! (ge :bootBuildDir) (ge :wjs)))
(set-env! :webcss (fp! (ge :bootBuildDir) (ge :wcs)))
(set-env! :docs (fp! (ge :bootBuildDir) "docs"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(set-env! :COMPILER_ARGS {:line "-Xlint:deprecation -Xlint:unchecked"})

(set-env! :COMPILE_OPTS {:debug (ge :buildDebug)
                         :includeantruntime false
                         :fork true})

(set-env! :CPATH [[:location (ge :buildDir)]
                  [:fileset {:dir (ge :libDir)
                             :includes "**/*.jar"}]
                  [:fileset {:dir (fp! (ge :skaroHome) "dist")
                             :includes "**/*.jar"} ]
                  [:fileset {:dir (fp! (ge :skaroHome) "lib")
                             :includes "**/*.jar"} ]] )

(set-env! :TPATH (->> (ge :CPATH)
                      (cons [:location (ge :buildTestDir)])
                      (into [])))

(set-env! :JAVAC_OPTS (merge {:srcdir (fp! (ge :srcDir) "java")
                              :destdir (ge :buildDir)
                              :target "1.8"
                              :debugLevel "lines,vars,source"}
                              (ge :COMPILE_OPTS)))

(set-env! :CJPATH (->> (ge :CPATH)
                       (cons [:location (fp! (ge :srcDir) "clojure")])
                       (into [])))

(set-env! :TJPATH (->> (ge :CJPATH)
                       (concat [[:location (fp! (ge :tstDir) "clojure")]
                                [:location (ge :buildTestDir)]])
                       (into [])))

(set-env! :CLJC_OPTS {:classname "clojure.lang.Compile"
                      :fork true
                      :failonerror true
                      :maxmemory "2048m"})

(set-env! :CLJC_SYSPROPS {:clojure.compile.warn-on-reflection true
                          :clojure.compile.path (ge :buildDir) })

(set-env! :CJNESTED [[:sysprops (ge :CLJC_SYSPROPS)]
                     [:classpath (ge :CJPATH)]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cleanLocalJs ""
  [wappid]
  (ant/DeleteDir (io/file (ge :webDir)
                          wappid "src" (ge :bld))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cleanPublic ""
  []
  (ant/RunTarget*
    "clean/public"
    (ant/AntDelete {}
      [[:fileset {:dir (fp! (ge :basedir) "public")
                  :includes "scripts/**,styles/**,pages/**,ig/**"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- clean4Build ""
  []
  (minitask
    "clean/build"
    (do
      (ant/CleanDir (io/file (ge :basedir)
                             (ge :target-path)))
      (ant/RunTasks*
        (ant/AntDelete {}
          [[:fileset {:dir (fp! (ge :podDir) "classes")} ]
           [:fileset {:dir (fp! (ge :podDir) "lib")} ]
           [:fileset {:dir (ge :libDir)}]
           [:fileset {:dir (ge :bootBuildDir)}
                     [[:include "**/*"]
                      [:exclude "classes/clojure/**"]]]])))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- preBuild ""
  []
  (doseq [s [(fp! (ge :basedir) (ge :pod) "patch")
             (fp! (ge :basedir) (ge :pod) "lib")
             (ge :buildDir)
             (ge :websrc)
             (ge :webcss)]]
    (.mkdirs (io/file s))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compClj ""

  [& paths]

  (ant/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues(apply b/FmtCljNsps
                                (io/file (ge :srcDir) "clojure") paths)]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileJava ""
  []
  (ant/RunTarget*
    "compile/java"
    (ant/AntJavac
      (ge :JAVAC_OPTS)
      [[:compilerarg (ge :COMPILER_ARGS)]
       [:include "**/*.java"]
       [:classpath (ge :CPATH)]])
    (ant/AntCopy
      {:todir (ge :buildDir)}
      [[:fileset {:dir (fp! (ge :srcDir) "java")
                  :excludes "**/*.java"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileClj ""
  []
  (ant/RunTarget*
    "compile/clj"
    (compClj "czlabclj/odin/event"
             "czlabclj/odin/system"
             "czlabclj/odin/game")

    (compClj "czlabclj/frigga/core"
             "czlabclj/frigga/tttoe"
             "czlabclj/frigga/pong")

    (compClj "czlabclj/cocos2d/games"
             "czlabclj/cocos2d/site"
             "czlabclj/cocos2d/users"
             "czlabclj/cocos2d/util")

    (ant/AntCopy
      {:todir (ge :buildDir)}
      [[:fileset {:dir (fp! (ge :srcDir) "clojure")
                  :excludes "**/*.clj"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- jar! ""
  []
  (ant/RunTarget*
    "jar!"
    (ant/AntJar
      {:destFile (fp! (ge :podDir) "lib"
                        (str (ge :PID)
                             "-" (ge :buildVersion) ".jar"))}
      [[:fileset {:dir (ge :buildDir)} ]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- preTest ""
  []
  (.mkdirs (io/file (ge :buildTestDir)))
  (.mkdirs (io/file (ge :reportTestDir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileFrwkTest ""
  []
  (ant/RunTarget*
    "compile/java#test"
    (ant/AntJavac (merge (ge :JAVAC_OPTS)
                         {:srcdir (fp! (ge :tstDir) "java")
                          :destdir (ge :buildTestDir)})
                  [[:include "**/*.java"]
                   [:classpath (ge :TPATH)]
                   [:compilerarg (ge :COMPILER_ARGS)]])

    (ant/AntCopy {:todir (ge :buildTestDir)}
                 [[:fileset {:dir (fp! (ge :tstDir) "java")
                             :excludes "**/*.java"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileCljTest ""
  []
  (ant/RunTarget*
    "compile/clj#test"
    (ant/AntJava
      (ge :CLJC_OPTS)
      [[:sysprops (assoc (ge :CLJC_SYSPROPS)
                         :clojure.compile.path (ge :buildTestDir))]
       [:classpath (ge :TJPATH)]
       [:argvalues (b/FmtCljNsps (fp! (ge :tstDir) "clojure")
                                 "testcljc/dummy")]])
    (ant/AntCopy
      {:todir (ge :buildTestDir)}
      [[:fileset {:dir (fp! (ge :tstDir) "clojure")
                  :excludes "**/*.clj"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- runCljTest ""
  []
  (ant/RunTarget*
    "run/clj#test"
    (ant/AntJunit
      {:logFailedTests true
       :showOutput false
       :printsummary true
       :fork true
       :haltonfailure true}
      [[:classpath (ge :TJPATH)]
       [:formatter {:type "plain"
                    :useFile false}]
       [:test {:name "czlab.frwk.util.ClojureJUnit"
               :todir (ge :reportTestDir)}
              [[:formatter {:type "xml"}]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- runJavaTest ""
  []
  (ant/RunTarget*
    "run/java#test"
    (ant/AntJunit
      {:logFailedTests true
       :showOutput false
       :printsummary true
       :fork true
       :haltonfailure true}
      [[:classpath (ge :TPATH)]
       [:formatter {:type "plain"
                    :useFile false}]
       [:batchtest {:todir (ge :reportTestDir)}
                   [[:fileset {:dir (ge :buildTestDir)}
                              [[:include "**/JUTest.*"]]]
                    [:formatter {:type "xml"}]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- buildCljTest ""
  []
  (minitask
    "preTest"
    (ant/DeleteDir (io/file (ge :buildTestDir) "czlab"))
    (preTest))
  (compileFrwkTest)
  (compileCljTest))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- buildJavaTest ""
  []
  (minitask
    "preTest"
    (ant/DeleteDir (io/file (ge :buildTestDir) "czlab"))
    (preTest))
  (compileFrwkTest))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- babel->cb ""

  [wappid f & {:keys [postgen dir paths]
               :or {:postgen false
                    :dir false
                    :paths []}
               :as args }]

  (let [dir (io/file (ge :webDir) wappid "src")
        out (io/file (ge :websrc) wappid)
        mid (cstr/join "/" paths)
        des (-> (io/file out mid)
                (.getParentFile)) ]
    (cond
      (true? postgen)
      (let [bf (io/file dir (ge :bld) mid)]
        (spit bf
              (-> (slurp bf)
                  (.replaceAll "\\/\\*@@" "")
                  (.replaceAll "@@\\*\\/" "")))
        (ant/MoveFile bf (doto des (.mkdirs))))

      (.isDirectory f)
      (if (= (ge :bld) (.getName f))
        nil
        {})

      :else
      (if (and (not (.startsWith mid "cc"))
               (.endsWith mid ".js"))
        {:work-dir dir
         :args ["--modules" "amd"
                "--module-ids" mid
                "--out-dir" (ge :bld) ]}
        (do
          (ant/CopyFile (io/file dir mid)
                        (doto des (.mkdirs)))
          nil)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileJS ""

  [wappid]

  (let [root (io/file (ge :webDir) wappid "src")
        tks (atom []) ]

    (cleanLocalJs wappid)
    (try
      (b/BabelTree root (partial babel->cb wappid))
    (finally
      (cleanLocalJs wappid)))

    (when true
      (ant/RunTasks*
        (ant/AntExec
          {:executable "jsdoc"
           :dir (ge :basedir)
           :spawn true}
          [[:argvalues [(fp! (ge :basedir)
                               (ge :bld)
                               (ge :wjs) wappid)
                         "-c"
                         (fp! (ge :basedir) "jsdoc.json")
                         "-d"
                         (fp! (ge :docs) wappid)]]])))

    (.mkdirs (io/file (ge :basedir) "public" "ig" "lib" "game"))
    (.mkdirs (io/file (ge :basedir) "public" "scripts"))

    (case wappid

      "cocos2d"
      (->> [[:fileset {:dir (fp! (ge :websrc) wappid)}]]
           (ant/AntCopy
             {:todir (fp! (ge :basedir)
                            "public/ig/lib") })
           (conj @tks)
           (reset! tks))

      "main"
      (->> [[:fileset {:dir (fp! (ge :websrc) wappid) } ]]
           (ant/AntCopy
             {:todir (fp! (ge :basedir)
                            "public/scripts") })
           (conj @tks)
           (reset! tks))
      ;;else
      (do
        (->> (ant/AntCopy
               {:file (fp! (ge :srcDir) "resources/main.js")
                :todir (fp! (ge :basedir)
                              "public/ig/lib/game" wappid)} )
             (conj @tks)
             (reset! tks))
        (->> [[:fileset {:dir (fp! (ge :websrc) wappid)} ]]
             (ant/AntCopy
               {:todir (fp! (ge :basedir)
                              "public/ig/lib/game" wappid)})
             (conj @tks)
             (reset! tks))))

    (apply ant/RunTasks* (reverse @tks))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- jiggleTheIndexFile

  [wappid des cocos]

  (ant/CopyFile (io/file (ge :srcDir) "resources" "index.html")
                (io/file des))
  (let [almond (atom "<script src=\"/public/vendors/almond/almond.js\"></script>")
        bdir (atom (str "/public/ig/lib/game/" wappid))
        ccdir (atom "/public/extlibs/")
        cfg (atom "")
        json (-> (slurp (fp! (ge :webDir)
                               wappid
                               "info/game.json")
                        :encoding "utf-8")
                 (js/read-str :key-fn keyword))
        html (-> (slurp (fp! des "index.html") :encoding "utf-8")
                 (cstr/replace "@@DESCRIPTION@@" (:description json))
                 (cstr/replace "@@KEYWORDS@@" (:keywords json))
                 (cstr/replace "@@TITLE@@" (:name json))
                 (cstr/replace "@@LAYOUT@@" (:layout json))
                 (cstr/replace "@@HEIGHT@@" (str (:height json)))
                 (cstr/replace "@@WIDTH@@" (str (:width json)))
                 (cstr/replace "@@UUID@@" (:uuid json))) ]
    (if (or (= (ge :pmode) "release")
            cocos)
      (do
        (reset! ccdir "frameworks/")
        (reset! bdir "")
        (reset! almond "")
        (reset! cfg (slurp (fp! (ge :srcDir)
                                  "resources" "cocos2d.js")
                           :encoding "utf-8")))
      (do
        (reset! cfg (slurp (fp! (ge :webDir)
                                  wappid "src" "ccconfig.js")
                           :encoding "utf-8"))))
    (spit (str des "/index.html")
          (-> html
              (cstr/replace "@@AMDREF@@" @almond)
              (cstr/replace "@@BOOTDIR@@" @bdir)
              (cstr/replace "@@CCDIR@@" @ccdir)
              (cstr/replace "@@CCCONFIG@@" @cfg))
            :encoding "utf-8")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileSCSS ""

  [wappid]

  (ant/RunTarget*
    "compile/scss"
    (ant/AntCopy
      {:todir (fp! (ge :webcss) wappid)}
      [[:fileset {:dir (fp! (ge :webDir) wappid "styles")
                  :includes "**/*.scss"}]])
    (ant/AntApply
      {:executable "sass" :parallel false}
      [[:fileset {:dir (fp! (ge :webcss) wappid)
                  :includes "**/*.scss"}]
       [:arglines ["--sourcemap=none"]]
       [:srcfile {}]
       [:chainedmapper
        {}
        [{:type :glob :from "*.scss" :to "*.css"}
         {:type :glob :from "*" :to (fp! (ge :webcss) wappid "*")}]]
       [:targetfile {}]])
    (ant/AntCopy
      {:todir (fp! (ge :webcss) wappid)}
      [[:fileset {:dir (fp! (ge :webDir) wappid "styles")
                  :includes "**/*.css"}]])
    (ant/AntMkdir {:dir (fp! (ge :basedir)
                               "public/styles" wappid)})
    (ant/AntCopy
      {:todir (fp! (ge :basedir)
                     "public/styles" wappid)}
      [[:fileset {:dir (fp! (ge :webcss) wappid)
                  :includes "**/*.css"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileMedia ""

  [wappid]

  (ant/RunTarget*
    "compile/media"
    (ant/AntMkdir {:dir (fp! (ge :basedir)
                               "public/ig/res" wappid)})
    (ant/AntCopy
      {:todir (fp! (ge :basedir)
                     "public/ig/res" wappid)}
      [[:fileset {:dir (fp! (ge :webDir) wappid "res/sd")}]
       [:fileset {:dir (fp! (ge :webDir) wappid "res")
                  :includes "sfx/**/*"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileInfo ""

  [wappid]

  (when-not (or (= "cocos2d" wappid)
                (= "main" wappid))
    (ant/RunTarget*
      "compile/info"
      (ant/AntMkdir {:dir (fp! (ge :basedir)
                                 "public/ig/info" wappid)})
      (ant/AntCopy
        {:todir (fp! (ge :basedir) "public/ig/info" wappid)}
        [[:fileset {:dir (fp! (ge :webDir) wappid "info")}]]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;

(defn- compilePages ""

  [wappid]

  (case wappid
    ("main" "cocos2d")
    (ant/RunTasks*
      (ant/AntCopy
        {:todir (fp! (ge :basedir) "public/pages" wappid)}
        [[:fileset {:dir (fp! (ge :webDir) wappid "pages")}]]))
    ;;else
    (jiggleTheIndexFile wappid
                        (fp! (ge :basedir)
                               "public/pages" wappid)
                        false)
  ))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- finzApp ""

  [wappid]

  (when (not= (ge :pmode) "release")
    (let [des (fp! (ge :basedir)
                     "public/ig/lib/game" wappid)]
      (ant/CopyFile (io/file (ge :srcDir)
                             "resources" "project.json")
                    (io/file des))
      (ant/CopyFile (io/file (ge :srcDir)
                             "resources" "main.js")
                    (io/file des)))
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- buildOneWebApp ""

  [^File dir]

  (let [wappid (.getName dir)]
    (.mkdirs (io/file (fp! (ge :websrc) wappid)))
    (.mkdirs (io/file (fp! (ge :webcss) wappid)))
    (compileJS wappid)
    (compileSCSS wappid)
    (compileMedia wappid)
    (compileInfo wappid)
    (compilePages wappid)
    (finzApp wappid)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- buildWebApps ""
  []
  (let [isDir? #(.isDirectory %)
        dirs (->> (.listFiles (io/file (ge :webDir)))
                  (filter isDir?))]
    (doall (map #(buildOneWebApp %) dirs))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- finzBuild ""

  []

  (let [ps (fp! (ge :basedir) "public" "vendors/")
        t1 (ant/AntDelete
             {:file (fp! (ge :basedir)
                         "public/c/webcommon.js")} )
        t2 (->> [[:fileset {:file (fp! ps "almond/almond.js")} ]
                 [:fileset {:file (fp! ps "ramda/ramda.js") } ]
                 [:fileset {:file (fp! ps "l10njs/l10n.js")} ]
                 [:fileset {:file (fp! ps "mustache/mustache.js")} ]
                 [:fileset {:file (fp! ps "helpers/dbg.js") } ]
                 [:fileset {:file (fp! ps "js-signals/signals.js") } ]
                 [:fileset {:file (fp! ps "ash-js/ash.js")} ]
                 [:fileset {:file (fp! ps "jquery-plugins/detectmobilebrowser.js")} ]
                 [:fileset {:file (fp! ps "crypto-js/components/core-min.js")} ]
                 [:fileset {:file (fp! ps "crypto-js/components/enc-utf16-min.js")} ]
                 [:fileset {:file (fp! ps "crypto-js/components/enc-base64-min.js")} ]
                 [:fileset {:file (fp! ps "cherimoia/skaro.js")} ]
                 [:fileset {:file (fp! ps "cherimoia/caesar.js")} ]]
                (ant/AntConcat
                  {:destfile (fp! (ge :basedir)
                                  "public/c/webcommon.js")
                   :append true})) ]
    (ant/RunTarget* "finz/build" t1 t2)
  ))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- yuiCSS ""
  []
  (ant/RunTarget*
    "yui/css"
    (ant/AntApply
      {:executable "java" :parallel false}
      [[:fileset {:dir (fp! (ge :basedir) "public/styles")
                  :excludes "**/*.min.css"
                  :includes "**/*.css"}]
       [:arglines ["-jar"]]
       [:argpaths [(str (ge :skaroHome)
                        "/lib/yuicompressor-2.4.8.jar")]]
       [:srcfile {}]
       [:arglines ["-o"]]
       [:chainedmapper {}
        [{:type :glob :from "*.css"
                      :to "*.min.css"}
         {:type :glob :from "*"
                      :to (fp! (ge :basedir) "public/styles/*")}]]
       [:targetfile {}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- yuiJS ""
  []
  (ant/RunTarget*
    "yui/js"
    (ant/AntApply
      {:executable "java" :parallel false}
      [[:fileset {:dir (fp! (ge :basedir) "public/scripts")
                  :excludes "**/*.min.js"
                  :includes "**/*.js"}]
       [:arglines ["-jar"]]
       [:argpaths [(str (ge :skaroHome)
                        "/lib/yuicompressor-2.4.8.jar")]]
       [:srcfile {}]
       [:arglines ["-o"]]
       [:chainedmapper {}
        [{:type :glob :from "*.js"
                      :to "*.min.js"}
         {:type :glob :from "*"
                      :to (fp! (ge :basedir) "public/scripts/*")}]]
       [:targetfile {}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- buildr "" []
  (clean4Build)
  (cleanPublic)
  (preBuild)
  (compileJava)
  (compileClj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; task definitions ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask dev ""
  []
  (bcore/with-pre-wrap fileset
    (println "##############################################################################")
    (print (format  "# building: %s\n" (ge :PID)))
    (print (format  "# type: %s\n" (ge :buildType)))
    (println "##############################################################################")
    (buildr)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask games ""
  []
  (bcore/with-pre-wrap fileset
    (cleanPublic)
    (buildWebApps)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask release ""
  []
  (bcore/with-pre-wrap fileset
    (println "##############################################################################")
    (print (format  "# releasing: %s\n" (ge :PID)))
    (print (format  "# type: %s\n" (ge :buildType)))
    (println "##############################################################################")
    (set-env! :pmode "release")
    (buildr)
    (jar!)
    (buildWebApps)
    (yuiCSS)
    (yuiJS)
    (finzBuild)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask testclj

  "test clj frwk"
  []

  (bcore/with-pre-wrap fileset
    (buildCljTest)
    (runCljTest)
    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask testjava

  "test java frwk"
  []

  (bcore/with-pre-wrap fileset
    (buildJavaTest)
    (runJavaTest)
    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkGame ""
  [appid]
  (let [pubtime (b/FmtTime "yyyy-MM-dd")
        appkey (b/RandUUID)]

    ;;(format "Creating new game: %s" appid)

    (doseq [s ["src/n" "src/s" "src/p"
               "src/i18n" "res/sfx"]]
      (.mkdirs (io/file (fp! (ge :webDir) appid s))))

    (doseq [s ["hdr" "hds" "sd"]]
      (.mkdirs (io/file (ge :webDir) appid "res" s "pics"))
      (.mkdirs (io/file (ge :webDir) appid "res" s "fon")))

    (doseq [s ["info" "pages" "styles"]]
      (.mkdirs (io/file (ge :webDir) appid s)))

    (ant/CopyFile (io/file (ge :srcDir) "resources" "game.json")
                  (io/file (ge :webDir) appid "info"))
    (ant/CopyFile (io/file (ge :srcDir) "resources" "game.mf")
                  (io/file (ge :webDir) appid "info"))

    (b/ReplaceFile (fp! (ge :webDir) appid "info" "game.mf")
                   #(-> %
                        (cstr/replace "@@PUBDATE@@" pubtime)
                        (cstr/replace "@@APPID@@" appid)))

    (b/ReplaceFile (fp! (ge :webDir) appid "info" "game.json")
                   #(cstr/replace % "@@UUID@@" appkey))

    (doseq [s ["game.js" "splash.js" "mmenu.js"
               "hud.js" "config.js" "protos.js"]]
      (ant/CopyFile (io/file (ge :srcDir) "resources" s)
                    (io/file (ge :webDir) appid "src" "p")))

    (ant/CopyFile (io/file (ge :srcDir) "resources" "gnodes.js")
                  (io/file (ge :webDir) appid "src" "n"))

    (ant/CopyFile (io/file (ge :srcDir) "resources" "cobjs.js")
                  (io/file (ge :webDir) appid "src" "n"))

    (doseq [s ["stager.js" "factory.js"
               "motion.js" "resolve.js" "sysobjs.js"]]
      (ant/CopyFile (io/file (ge :srcDir) "resources" s)
                    (io/file (ge :webDir) appid "src" "s")))

    (b/ReplaceFile (fp! (ge :webDir)
                          appid "src" "p" "config.js")
                   #(cstr/replace % "@@UUID@@" appkey))

    (ant/CopyFile (io/file (ge :srcDir)
                           "resources" "l10n.js")
                  (io/file (ge :webDir)
                           appid "src" "i18n""l10n.js"))

    (ant/CopyFile (io/file (ge :srcDir)
                           "resources" "ccconfig.js")
                  (io/file (ge :webDir) appid "src"))

    (ant/CopyFile (io/file (ge :srcDir)
                           "resources" "proj.json")
                  (io/file (ge :webDir)
                           appid "src" "project.json"))

    (b/ReplaceFile (fp! (ge :webDir)
                          appid "src" "project.json")
                   #(cstr/replace % "@@APPID@@" appid))

    (b/ReplaceFile (fp! (ge :webDir)
                          appid "src" "ccconfig.js")
                   #(cstr/replace % "@@APPID@@" appid))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask newgame

  ""
  [n id VAL str "game id"]

  (bcore/with-pre-wrap fileset
    (mkGame id)
    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cocos->new ""
  [appid]
  (ant/RunTarget*
    "cocos/new"
    (ant/AntMkdir {:dir (fp! (ge :basedir) "cocos")})
    (ant/AntExec
      {:executable "cocos"}
      [[:argvalues ["new" "-l" "js" "-t"
                    "runtime" "--ios-bundleid"
                    (str "com.zotohlab.p." appid)
                    "-d" (fp! (ge :basedir) "cocos") appid]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask cocos+new

  ""
  [n id VAL str "game id"]

  (bcore/with-pre-wrap fileset
    (cocos->new id)
    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- deploy->app ""

  [appid]

  (let [vendors (fp! (ge :basedir) "public/vendors")
        despath (fp! (ge :basedir) "cocos" appid)
        srcpath (fp! (ge :webDir) appid)
        des (io/file despath)
        src (io/file srcpath)
        dd2 (io/file des "res")
        dd1 (io/file des "src") ]

    (when-not (.exists des) (cocos->new appid))
    ;;(print (format "Deploying game: %s\n" appid))
    (ant/CleanDir dd2)
    (ant/CleanDir dd1)
    ;; resources
    (doseq [s ["hdr" "hds" "sd" "sfx"]]
      (ant/RunTasks*
        (ant/AntCopy
          {:todir (io/file dd2 s "cocos2d") }
          [[:fileset {:dir (io/file (ge :webDir)
                                    "cocos2d" "res" s)} ]])
        (ant/AntCopy
          {:todir (io/file dd2 s appid)}
          [[:fileset {:dir (io/file (ge :srcpath) "res" s)} ]])))
    ;; js code
    (ant/RunTasks*
      (ant/AntCopy
        {:todir (io/file dd1 "zotohlab")}
        [[:fileset {:dir (io/file (ge :webDir)
                                  "cocos2d"
                                  "src" "zotohlab")} ]])
      (ant/AntCopy
        {:todir (io/file dd1 appid)}
        [[:fileset {:dir (io/file srcpath "src")} ]])
      (ant/AntCopy
        {:todir (io/file dd1 "helpers")}
        [[:fileset {:dir (io/file vendors "helpers")
                    :includes "dbg.js"}]]))

    (doseq [s ["almond" "js-signals" "ash-js"
               "rxjs" "ramda" "cherimoia"
               "l10njs" "cookies" "mustache"]]
      (ant/RunTasks*
        (ant/AntCopy
          {:todir (io/file dd1  s)}
          [[:fileset {:dir (io/file vendors s)} ]])))

    ;; boot stuff
    (ant/RunTasks*
      (ant/AntCopy
        {:todir despath
         :overwrite true}
        [[:fileset {:dir (io/file (ge :srcDir) "resources")
                    :includes "project.json,main.js,index.html"}]]))

    (let [j1 (-> (slurp (io/file (ge :srcDir)
                                 "resources" "project.json")
                        :encoding "utf-8")
                 (js/read-str :key-fn keyword))
          j2 (-> (slurp (io/file srcpath "src" "project.json")
                        :encoding "utf-8")
                 (js/read-str :key-fn keyword)) ]
      (->> (update-in j1
                      [:jsList]
                      #(concat % (:jsList j2)))
           (js/write-str )
           (spit (io/file despath "project.json"))))

    (jiggleTheIndexFile appid
                        (io/file (ge :basedir) "cocos" appid) true)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask deployapp

  ""
  [n id VAL str "game id"]

  (bcore/with-pre-wrap fileset
    (let [srcpath (fp! (ge :webDir) id)
          src (io/file srcpath)]
      (if-not (.exists src)
        (print (format "Invalid game: %s\n" id))
        (deploy->app id)))
    fileset))


(deftask hihi "" [] (println (get-env)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF
