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

(ns ^{:doc ""
      :author "kenl" }

  czlab.tpcl.antlib

  (:import
    [org.apache.tools.ant.taskdefs.optional.unix Symlink]
    [java.beans Introspector PropertyDescriptor]
    [java.lang.reflect Method]
    [java.util Stack]
    [java.io File]
    [org.apache.tools.ant.taskdefs
     Javadoc Java Copy Chmod
     Concat Move Mkdir Tar
     Replace ExecuteOn
     Delete Jar Zip ExecTask Javac]
    [org.apache.tools.ant.listener
     AnsiColorLogger
     TimestampedLogger]
    [org.apache.tools.ant.types
     Commandline$Argument
     Commandline$Marker
     PatternSet$NameEntry
     Environment$Variable
     Reference FileSet Path DirSet]
    [org.apache.tools.ant
     NoBannerLogger
     Project Target Task]
    [org.apache.tools.ant.taskdefs.optional.junit
     FormatterElement$TypeAttribute
     JUnitTask$SummaryAttribute
     JUnitTask$ForkMode
     JUnitTask
     JUnitTest
     BatchTest
     FormatterElement]
    [org.apache.tools.ant.util FileNameMapper
     GlobPatternMapper ChainedMapper]
    [org.apache.tools.ant.taskdefs
     Javadoc$AccessType
     Replace$Replacefilter
     Replace$NestedString
     Tar$TarFileSet
     Tar$TarCompressionMethod
     Javac$ImplementationSpecificArgument])

  ;;put here but not used, reason is to trick compiler
  ;;to drag in the files and compile it without
  ;;reflection warnings
  (:use [flatland.ordered.set]
        [flatland.ordered.map])

  (:require
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [clojure.string :as cs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
(declare maybeCfgNested)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- capstr

  "Just capitalize the 1st character"

  ^String
  [^String s]

  (str (.toUpperCase (.substring s 0 1))
       (.substring s 1)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn antProject

  "Create a new ant project"

  ^Project
  []

  (let [lg (doto
             ;;(TimestampedLogger.)
             (AnsiColorLogger.)
             ;;(NoBannerLogger.)
             (.setOutputPrintStream System/out)
             (.setErrorPrintStream System/err)
             (.setMessageOutputLevel Project/MSG_INFO))]
    (doto (Project.)
      (.init)
      (.setName "project-x")
      (.addBuildListener lg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn execTarget

  "Run and execute a target"

  [^Target target]

  (.executeTarget (.getProject target) (.getName target)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getBeanInfo ""
  [cz]
  (->> (-> (Introspector/getBeanInfo cz)
           (.getPropertyDescriptors))
       (reduce (fn [memo pd]
                 (assoc memo
                        (keyword (.getName pd)) pd))
               {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;create a default project.
(def ^:private dftprj (atom (antProject)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;cache ant task names as symbols, and cache bean-info of class.
(let [beans (atom {})
      syms (atom [])]
  (doseq [[k v] (.getTaskDefinitions @dftprj)]
    (when (.isAssignableFrom Task v)
      (let [n (str "ant" (capstr k))]
        (reset! syms (conj @syms n k)))
      (swap! beans assoc v (getBeanInfo v))))
  (def ^:private tasks (atom (partition 2 (map #(symbol %) @syms))))
  (def ^:private props (atom @beans)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeListProps

  "Dynamically add bean info for non-task classes"

  [cz]

  (let [b (getBeanInfo cz)]
    (swap! props assoc cz b)
    b))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- method?

  "Find this setter method via best match"

  [^Class cz ^String m]

  (let [arr (make-array java.lang.Class 1)]
    (some
      (fn [^Class z]
        (aset #^"[Ljava.lang.Class;" arr 0 z)
        (try
          [(.getMethod cz m arr) z]
          (catch Exception _)))
      ;;add more types when needed
      [java.lang.String
       java.io.File
       Boolean/TYPE
       java.lang.Boolean
       Integer/TYPE
       java.lang.Integer
       Long/TYPE
       java.lang.Long
       org.apache.tools.ant.types.Path ])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^:private koerce "Best attempt to convert a value." (fn [_ a b] [a (class b)]))

(defmethod koerce [Integer/TYPE String] [_ _ ^String v] (Integer/parseInt v (int 10)))
(defmethod koerce [Integer String] [_ _ ^String v] (Integer/parseInt v (int 10)))

(defmethod koerce [Integer/TYPE Long] [_ _ ^Long v] (.intValue v))
(defmethod koerce [Integer Long] [_ _ ^Long v] (.intValue v))

(defmethod koerce [Integer/TYPE Integer] [_ _ ^Integer v] v)
(defmethod koerce [Integer Integer] [_ _ ^Integer v] v)

(defmethod koerce [Long/TYPE String] [_ _ ^String v] (Long/parseLong v (int 10)))
(defmethod koerce [Long String] [_ _ ^String v] (Long/parseLong v (int 10)))

(defmethod koerce [Long/TYPE Long] [_ _ ^Long v] v)
(defmethod koerce [Long Long] [_ _ ^Long v] v)

(defmethod koerce [Path File] [^Project pj _ ^File v] (Path. pj (.getCanonicalPath v)))
(defmethod koerce [Path String] [^Project pj _ ^String v] (Path. pj v))

(defmethod koerce [File String] [_ _ ^String v] (io/file v))
(defmethod koerce [File File] [_ _ v] v)

(defmethod koerce :default [_ pz _] (Exception. (str "expected class " pz)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- coerce

  "Best attempt to convert a given value"

  [pj pz value]

  (cond
    (or (= Boolean/TYPE pz)
        (= Boolean pz))
    (= "true" (str value))

    (= String pz)
    (str value)

    :else
    (koerce pj pz value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- setProp! ""

  [^Method wm pojo k arr]

  (try
    (.invoke wm pojo arr)
  (catch Throwable e#
    (println "failed to set property: "
             k
             " for cz "
             (.getClass pojo))
    (throw e#))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- setOptions

  "Use reflection and invoke setters"

  [pj pojo options & [skips]]

  (let [arr (object-array 1)
        skips (or skips #{})
        cz (.getClass pojo)
        ps (or (get @props cz)
               (maybeListProps cz)) ]
    (doseq [[k v] options]
      (when-not (contains? skips k)
        (if-some [pd (get ps k)]
          (if-some [wm (.getWriteMethod pd)]
            ;;some cases the beaninfo is erroneous
            ;;so fall back to use *best-try*
            (let [pt (.getPropertyType pd)
                  m (.getName wm)]
              (aset arr 0 (coerce pj pt v))
              (setProp! wm pojo k arr))
            ;;else
            (let [m (str "set" (capstr (name k)))
                  rc (method? cz m)]
              (when (nil? rc)
                (throw (Exception. (str m " not-found in " pojo))))
              (aset arr 0 (coerce pj (last rc) v))
              (setProp! (first rc) pojo k arr)))
          ;;else
          (throw (Exception. (str "property "
                                  (name k)
                                  " not-found in task " cz))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn antTarFileSet

  "Configure a TarFileSet Object"

  ^Tar$TarFileSet
  [^Project pj ^Tar$TarFileSet fs & [options nested]]

  (let [options (or options {})
        nested (or nested [])]

    (setOptions pj fs options)
    (.setProject fs pj)
    (maybeCfgNested pj fs nested)
    fs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn antFileSet

  "Create a FileSet Object"

  ^FileSet
  [^Project pj & [options nested]]

  (let [fs (FileSet.)
        options (or options {})
        nested (or nested [])]

    (setOptions pj
                fs
                (merge {:errorOnMissingDir false} options))
    (.setProject fs pj)
    (maybeCfgNested pj fs nested)
    fs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn antBatchTest

  "Configure a BatchTest Object"

  ^BatchTest
  [^Project pj ^BatchTest bt & [options nested]]

  (let [options (or options {})
        nested (or nested [])]

    (setOptions pj bt options)
    (maybeCfgNested pj bt nested)
    bt))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn antJunitTest

  "Configure a single JUnit Test Object"

  ^JUnitTask
  [^Project pj & [options nested]]

  (let [jt (JUnitTest.)
        options (or options {})
        nested (or nested [])]

    (setOptions pj jt options)
    (maybeCfgNested pj jt nested)
    jt))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn antChainedMapper ""

  ^FileNameMapper
  [^Project pj & [options nested]]

  (let [cm (ChainedMapper.)]
    (doseq [n nested]
      (case (:type n)
        :glob
        (->> (doto (GlobPatternMapper.)
               (.setFrom (:from n))
               (.setTo (:to n)))
             (.add cm))
        nil))
    cm))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtr-preopts ""

  [tk options]

  (when-some [[k v] (find options :type)]
    (.setType ^FormatterElement
              tk
              (doto (FormatterElement$TypeAttribute.)
                (.setValue (str v)))))
  [options #{:type}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn antFormatter

  "Create a Formatter Object"

  ^FormatterElement
  [^Project pj & [options nested]]

  (let [fe (FormatterElement.)
        options (or options {})
        nested (or nested []) ]

    (apply setOptions pj fe (fmtr-preopts fe options))
    (.setProject fe pj)
    fe))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn setClassPath

  "Build a nested Path structure for classpath"

  [^Project pj ^Path root paths]

  (doseq [p paths]
    (case (first p)
      :location
      (doto (.createPath root)
        (.setLocation (io/file (str (last p)))))
      :refid
      (throw (Exception. "path:refid not supported."))
      ;;(doto (.createPath root) (.setRefid (last p)))
      :fileset
      (->> (antFileSet pj
                       (if (> (count p) 1)(nth p 1) {})
                       (if (> (count p) 2)(nth p 2) []))
           (.addFileset root))
      nil))
  root)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeCfgNested ""

  [pj tk nested]

  ;;(println "debug:\n" nested)
  (doseq [p nested]
    (case (first p)

      :compilerarg
      (when-some [^String line (:line (last p))]
        (-> (.createCompilerArg tk)
            (.setLine line)))

      :classpath
      (setClassPath pj (.createClasspath tk) (last p))

      :sysprops
      (doseq [[k v] (last p)]
        (->> (doto (Environment$Variable.)
                   (.setKey (name k))
                   (.setValue (str v)))
             (.addSysproperty tk)))

      :formatter
      (->> (antFormatter pj (last p))
           (.addFormatter tk))

      :include
      (-> (.createInclude tk)
          (.setName (str (last p))))

      :exclude
      (-> (.createExclude tk)
          (.setName (str (last p))))

      :fileset
      (let [s (antFileSet
                pj
                (if (> (count p) 1)(nth p 1) {})
                (if (> (count p) 2)(nth p 2) []))]
        (if (instance? BatchTest tk)
          (.addFileSet tk s)
          (.addFileset tk s)))

      :argvalues
      (doseq [v (last p)]
        (-> (.createArg tk)
            (.setValue (str v))))

      :argpaths
      (doseq [v (last p)]
        (-> (.createArg tk)
            (.setPath (Path. pj (str v)))))

      :arglines
      (doseq [v (last p)]
        (-> (.createArg tk)
            (.setLine (str v))))

      :replacefilter
      (doto (.createReplacefilter tk)
            (.setToken (:token (nth p 1)))
            (.setValue (:value (nth p 1))))

      :replacevalue
      (-> (.createReplaceValue tk)
          (.addText (:text (last p))))

      :replacetoken
      (-> (.createReplaceToken tk)
          (.addText (:text (last p))))

      :test
      (->> (antJunitTest pj
                    (if (> (count p) 1)(nth p 1) {})
                    (if (> (count p) 2)(nth p 2) []))
           (.addTest tk))

      :chainedmapper
      (->> (antChainedMapper pj
                    (if (> (count p) 1)(nth p 1) {})
                    (if (> (count p) 2)(nth p 2) []))
           (.add tk))

      :targetfile
      (.createTargetfile tk)

      :srcfile
      (.createSrcfile tk)

      :batchtest
      (antBatchTest pj
                    (.createBatchTest tk)
                    (if (> (count p) 1)(nth p 1) {})
                    (if (> (count p) 2)(nth p 2) []))

      :tarfileset
      (antTarFileSet pj
                     (.createTarFileSet tk)
                     (if (> (count p) 1)(nth p 1) {})
                     (if (> (count p) 2)(nth p 2) []))

      nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- xxx-preopts ""
  [tk options]
  [options #{} ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- delete-pre-opts ""

  [tk options]

  [(merge {:includeEmptyDirs true } options) #{}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- junit-preopts ""

  [tk options]

  (when-some [[k v] (find options :printsummary)]
    (.setPrintsummary ^JUnitTask
                tk
                (doto (JUnitTask$SummaryAttribute.)
                  (.setValue (str v)))))

  (when-some [[k v] (find options :forkMode)]
    (.setForkMode ^JUnitTask
                tk
                (doto (JUnitTask$ForkMode.)
                  (.setValue (str v)))))

  [options #{:forkMode :printsummary}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- jdoc-preopts ""

  [tk options]

  (when-some [[k v] (find options :access)]
    (.setAccess ^Javadoc
                tk
                (doto (Javadoc$AccessType.)
                  (.setValue (str v)))))
  [options #{:access}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tar-preopts ""
  [tk options]
  (when-some [[k v] (find options :compression)]
    (.setCompression ^Tar
                     tk
                     (doto (Tar$TarCompressionMethod.)
                       (.setValue (str v)))))
  [options #{:compression}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init-task

  "Reify and configure actual ant tasks"

  ^Task
  [^Project pj ^Target target tobj]

  (let [{:keys [pre-options tname
                task options nested] } tobj
        pre-options (or pre-options
                        xxx-preopts)]
    ;;(log/info "task name: %s" tname)
    (->> (doto ^Task
           task
           (.setProject pj)
           (.setOwningTarget target))
         (.addTask target))
    (->> (pre-options task options)
         (apply setOptions pj task))
    (maybeCfgNested pj task nested)
    task))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn projAntTasks

  "Bind all the tasks to a target and a project"

  ^Target
  [^String target tasks]

  {:pre [(coll? tasks)]}

  (let [^Project pj @dftprj
        tg (Target.)]
    (.setName tg (or target ""))
    (.addOrReplaceTarget pj tg)
    ;;(log/info "number of tasks ==== %d" (count tasks))
    (doseq [t tasks]
      (init-task pj tg t))
    tg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn projAntTasks*

  "Bind all the tasks to a target and a project"

  ^Target
  [target & tasks]

  (projAntTasks target tasks))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn runTarget

  "Run ant tasks"

  [target tasks]

  (-> (projAntTasks target tasks)
      (execTarget)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn runTarget*

  "Run ant tasks"

  [target & tasks]

  (runTarget target tasks))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn runTasks

  "Run ant tasks"

  [tasks]

  (runTarget "" tasks))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn runTasks*

  "Run ant tasks"

  [& tasks]

  ;;(log/info "running tasks count = %d" (count tasks))
  (runTarget "" tasks))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ^:private ant-task

  "Generate wrapper function for ant task"

  [pj sym docstr func & [preopt]]

  (let [s (str func)
        tm (cs/lower-case
             (.substring s (+ 1 (.lastIndexOf s "."))))]
    ;;(println "task---- " s)
    `(defn ~sym ~docstr [& [options# nested#]]
       (let [tk# (doto (.createTask ~pj ~s)
                     (.setTaskName ~tm))
             o# (or options# {})
             n# (or nested# [])
             r#  {:pre-options ~preopt
                  :tname ~tm
                  :task tk#
                  :options o#
                  :nested n#} ]
         (if (nil? ~preopt)
           (->> (case ~s
                  ;;certain classes need special handling of properties
                  ;;due to type mismatch or property name
                  ;;inconsistencies
                  "delete" delete-pre-opts
                  "junit" junit-preopts
                  "javadoc" jdoc-preopts
                  "tar" tar-preopts
                  nil)
                (assoc r# :pre-options))
           ;;else
           r#)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro decl-ant-tasks ""
  [pj]
  `(do ~@(map (fn [[a b]]
                `(ant-task ~pj ~a "" ~b))
              (deref tasks))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(decl-ant-tasks @dftprj)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn cleanDir

  "Clean an existing dir or create it"

  [d & {:keys [quiet]
        :or {:quiet true}}]

  (let [dir (io/file d)]
    (if (.exists dir)
      (runTasks* (antDelete
                      {:removeNotFollowedSymlinks true
                       :quiet quiet}
                      [[:fileset {:followSymlinks false
                                  :dir dir}
                                 [[:include "**/*"]]]]))
      ;;else
      (.mkdirs dir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deleteDir

  "Remove a directory"

  [d & {:keys [quiet]
        :or {:quiet true}}]

  (let [dir (io/file d)]
    (when (.exists dir)
      (runTasks*
        (antDelete {:removeNotFollowedSymlinks true
                    :quiet quiet}
                   [[:fileset {:followSymlinks false
                               :dir dir} ]])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn copyFile ""

  [file toDir]

  (.mkdirs (io/file toDir))
  (runTasks*
    (antCopy {:file file
              :todir toDir} )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn moveFile ""

  [file toDir]

  (.mkdirs (io/file toDir))
  (runTasks*
    (antMove {:file file
              :todir toDir} )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn symLink ""

  [link target]

  (runTasks*
    (antSymlink {:overwrite true
                 :action "single"
                 :link link
                 :resource target})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

