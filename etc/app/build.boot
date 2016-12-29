;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.

(set-env!

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :description ""
  ;;:url ""

  :dependencies '[
    ;;[czlab/czlab-wabbit "0.1.0"]
    ;;[com.cemerick/pomegranate "0.3.1"]
    ;;[net.mikera/cljunit "0.6.0"]
    ;;[codox/codox "0.10.2"]
    ;;[junit/junit "4.12"]
  ]

  :source-paths #{"src/main/clojure" "src/main/java"}
  :test-runner "test.ClojureJUnit"
  :version "0.1.0-SNAPSHOT"
  :debug true
  :project '@@APPDOMAIN@@)

(require
  '[czlab.tpcl.boot :as b :refer [fp! ge se!]]
  '[clojure.tools.logging :as log]
  '[clojure.java.io :as io]
  '[clojure.string :as cs]
  '[boot.core :as bc]
  '[czlab.xlib.core :as xc]
  '[czlab.xlib.antlib :as a])

(import
  '[java.io File])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(b/bootEnv!
  {:wabbitDir (xc/fpath (xc/sysProp "wabbit.home.dir"))
   :wabbitLibs (fn [_]
                 [[:fileset {:dir (fp! (ge :wabbitDir) "patch")
                             :includes "**/*.jar"}]
                  [:fileset {:dir (fp! (ge :wabbitDir) "lib")
                             :includes "**/*.jar"}]
                  [:fileset {:dir (fp! (ge :wabbitDir) "dist")
                             :includes "**/*.jar"}]])
   :wjs "scripts"
   :wcs "styles"
   :websrc (fn [_] (fp! (ge :wzzDir) (ge :wjs)))
   :webcss (fn [_] (fp! (ge :wzzDir) (ge :wcs)))
   :webDir (fn [_] (fp! (ge :basedir) "src/web"))}
  #(let [c (ge :CPATH)]
     (se! :CPATH (fn [_] (concat c (ge :wabbitLibs))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cleanLocalJs ""

  [wappid]

  (a/deleteDir (fp! (ge :webDir) wappid "scripts" (ge :bld))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onbabel ""

  [wappid f & {:keys [postgen dir paths]
               :or {:postgen false
                    :dir false
                    :paths []}
               :as args }]

  (let
    [dir (io/file (ge :webDir) wappid "scripts")
     out (io/file (ge :websrc) wappid)
     mid (cs/join "/" paths)
     des (-> (io/file out mid)
             (.getParentFile)) ]
    (cond
      (true? postgen)
      (let [bf (io/file dir (ge :bld) mid)]
        (b/replaceFile! bf
                        #(-> (cs/replace % "/*@@" "")
                             (cs/replace "@@*/" "")))
        (a/moveFile bf des))

      (.isDirectory f)
      (if (= (ge :bld) (.getName f)) nil {})

      :else
      (if (and (not (.startsWith mid "cc"))
               (.endsWith mid ".js"))
        {:work-dir dir
         :args ["--modules" "amd"
                "--module-ids" mid
                "--out-dir" (ge :bld) ]}
        (do
          (a/copyFile (io/file dir mid) des)
          nil)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileJS ""

  [wappid]

  (let [root (io/file (ge :webDir) wappid "scripts")
        tks (atom []) ]
    (try
      (cleanLocalJs wappid)
      ;;(b/babelTree root (partial onbabel wappid))
    (finally
      (cleanLocalJs wappid)))

    (when false
      (a/runTasks*
        (a/antExec
          {:executable "jsdoc"
           :dir (ge :basedir)
           :spawn true}
          [[:argvalues [(fp! (ge :websrc) wappid)
                         "-c"
                         (fp! (ge :basedir)
                              "jsdoc.json")
                         "-d"
                         (fp! (ge :docs) wappid)]]])))

    (->> (a/antCopy
           {:todir (fp! (ge :basedir) "public/scripts" wappid) }
           [[:fileset {:dir (fp! (ge :websrc) wappid) } ]])
         (conj @tks)
         (reset! tks))

    (apply a/runTasks* (reverse @tks))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileSCSS ""

  [wappid]

  (a/runTasks*
    (a/antCopy
      {:todir (io/file (ge :webcss) wappid)}
      [[:fileset {:dir (fp! (ge :webDir) wappid "styles")
                  :includes "**/*.scss"}]])
    (a/antApply
      {:executable "sass" :parallel false}
      [[:fileset {:dir (fp! (ge :webcss) wappid)
                  :includes "**/*.scss"}]
       [:arglines ["--sourcemap=none"]]
       [:srcfile {}]
       [:chainedmapper
        {}
        [{:type :glob
          :from "*.scss" :to "*.css"}
         {:type :glob
          :from "*"
          :to (fp! (ge :webcss) wappid "*")}]]
       [:targetfile {}]])
    (a/antCopy
      {:todir (io/file (ge :webcss) wappid)}
      [[:fileset {:dir (fp! (ge :webDir) wappid "styles")
                  :includes "**/*.css"}]])
    (a/antMkdir {:dir (fp! (ge :basedir)
                           "public/styles" wappid)})
    (a/antCopy
      {:todir (fp! (ge :basedir)
                   "public/styles" wappid)}
      [[:fileset {:dir (fp! (ge :webcss) wappid)
                  :includes "**/*.css"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileMedia ""

  [wappid]

  (a/runTasks*
    (a/antMkdir {:dir (fp! (ge :basedir)
                           "public/media" wappid)})
    (a/antCopy
      {:todir (fp! (ge :basedir)
                   "public/media" wappid)}
      [[:fileset {:dir (fp! (ge :webDir) wappid "media")}] ])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compilePages ""

  [wappid]

  (a/runTasks*
    (a/antCopy
      {:todir (fp! (ge :basedir) "public/pages" wappid)}
      [[:fileset {:dir (fp! (ge :webDir) wappid "pages")}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- finzApp "" [wappid])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- buildOneWebApp ""

  [^File dir]

  (let [wappid (.getName dir)]
    (.mkdirs (io/file (fp! (ge :websrc) wappid)))
    (.mkdirs (io/file (fp! (ge :webcss) wappid)))
    (doto wappid
      (compileJS)
      (compileSCSS)
      (compileMedia)
      (compilePages)
      (finzApp))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- buildWebApps ""

  []

  (let [isDir? #(.isDirectory %)
        dirs (->> (io/file (ge :webDir))
                  (.listFiles )
                  (filter isDir?))]
    (doall (map #(buildOneWebApp %) dirs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- yuiCSS ""

  []

  (a/runTasks*
    (a/antApply
      {:executable "java" :parallel false}
      [[:fileset {:dir (fp! (ge :basedir) "public/styles")
                  :excludes "**/*.min.css"
                  :includes "**/*.css"}]
       [:arglines ["-jar"]]
       [:argpaths [(str (ge :wabbitHome)
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
  (a/runTasks*
    (a/antApply
      {:executable "java" :parallel false}
      [[:fileset {:dir (fp! (ge :basedir) "public/scripts")
                  :excludes "**/*.min.js"
                  :includes "**/*.js"}]
       [:arglines ["-jar"]]
       [:argpaths [(str (ge :wabbitHome)
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
;; task definitions ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask dev ""
  []
  (comp (b/initBuild)
        (b/libjars)
        (b/buildr)
        (b/pom!)
        (b/jar!)))

(deftask Xdev ""
  []
  (bc/with-pre-wrap fileset
    (let [m (b/dbgBootVars)]
      (printf "%s\n" (:l-vars m))
      (printf "%s\n" (:u-vars m)))
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask tst

  "for test only"
  []

  (comp (b/testJava)
        (b/testClj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask webdev ""
  []
  (bc/with-pre-wrap fileset


    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask rel ""
  []
  (se! :pmode "release")
  (comp (dev) (webdev)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


