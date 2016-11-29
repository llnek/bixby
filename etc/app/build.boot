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

  :buildVersion "0.1.0-SNAPSHOT"
  :buildDebug true
  :buildType "@@TYPE@@"

  :DOMAIN "@@APPDOMAIN@@"
  :PID "@@APPID@@"

  :source-paths #{"src/main/clojure"
                  "src/main/java"}

  :dependencies '[ ] )

(require
  '[czlab.tpcl.boot :as b :refer [fp! ge
                                  testjava testclj]]
  '[clojure.java.io :as io]
  '[clojure.string :as cs]
  '[boot.core :as bc]
  '[czlab.tpcl.antlib :as a])

(import
  '[java.io File])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(b/BootEnvVars
  {:wjs "scripts"
   :wcs "styles"
   :websrc #(set-env! %2 (fp! (ge :wzzDir) (ge :wjs)))
   :webcss #(set-env! %2 (fp! (ge :wzzDir) (ge :wcs)))
   :webDir #(set-env! %2 (fp! (ge :basedir) "src/web"))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(b/BootEnvPaths)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cleanLocalJs ""

  [wappid]

  (a/DeleteDir (fp! (ge :webDir) wappid "scripts" (ge :bld))))

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
        (b/ReplaceFile bf
                       #(-> (cs/replace % "/*@@" "")
                            (cs/replace "@@*/" "")))
        (a/MoveFile bf des))

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
          (a/CopyFile (io/file dir mid) des)
          nil)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileJS ""

  [wappid]

  (let [root (io/file (ge :webDir) wappid "scripts")
        tks (atom []) ]
    (try
      (cleanLocalJs wappid)
      (b/BabelTree root (partial onbabel wappid))
    (finally
      (cleanLocalJs wappid)))

    (when false
      (a/RunTasks*
        (a/AntExec
          {:executable "jsdoc"
           :dir (ge :basedir)
           :spawn true}
          [[:argvalues [(fp! (ge :websrc) wappid)
                         "-c"
                         (fp! (ge :basedir)
                              "jsdoc.json")
                         "-d"
                         (fp! (ge :docs) wappid)]]])))

    (->> (a/AntCopy
           {:todir (fp! (ge :basedir) "public/scripts" wappid) }
           [[:fileset {:dir (fp! (ge :websrc) wappid) } ]])
         (conj @tks)
         (reset! tks))

    (apply a/RunTasks* (reverse @tks))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileSCSS ""

  [wappid]

  (a/RunTasks*
    (a/AntCopy
      {:todir (io/file (ge :webcss) wappid)}
      [[:fileset {:dir (fp! (ge :webDir) wappid "styles")
                  :includes "**/*.scss"}]])
    (a/AntApply
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
    (a/AntCopy
      {:todir (io/file (ge :webcss) wappid)}
      [[:fileset {:dir (fp! (ge :webDir) wappid "styles")
                  :includes "**/*.css"}]])
    (a/AntMkdir {:dir (fp! (ge :basedir)
                           "public/styles" wappid)})
    (a/AntCopy
      {:todir (fp! (ge :basedir)
                   "public/styles" wappid)}
      [[:fileset {:dir (fp! (ge :webcss) wappid)
                  :includes "**/*.css"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileMedia ""

  [wappid]

  (a/RunTasks*
    (a/AntMkdir {:dir (fp! (ge :basedir)
                           "public/media" wappid)})
    (a/AntCopy
      {:todir (fp! (ge :basedir)
                   "public/media" wappid)}
      [[:fileset {:dir (fp! (ge :webDir) wappid "media")}] ])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compilePages ""

  [wappid]

  (a/RunTasks*
    (a/AntCopy
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

  (a/RunTasks*
    (a/AntApply
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
  (a/RunTasks*
    (a/AntApply
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
(deftask clean4build "clean,pre-build"

  []

  (bc/with-pre-wrap fileset
    (b/Clean4Build)
    (b/PreBuild)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask dev "clean,resolve,build"

  []

  (comp (clean4build)
        (b/libjars)
        (b/buildr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask webdev ""

  []

  (bc/with-pre-wrap fileset

    (when (= "web" (ge :buildType))
      (b/CleanPublic)
      (fn [& args]
        (a/CleanDir (fp! (ge :websrc)))
        (a/CleanDir (fp! (ge :webcss))))
      (buildWebApps)
      (yuiCSS)
      (yuiJS))

    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask release ""
  []

  (set-env! :pmode "release")
  (comp (dev) (webdev)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


