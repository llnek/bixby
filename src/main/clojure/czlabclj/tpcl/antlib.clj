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

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tpcl.antlib

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.string :as cstr])

  (:import [java.beans Introspector PropertyDescriptor]
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
            Javac$ImplementationSpecificArgument]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
(declare maybeCfgNested)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- capstr "Just capitalize the 1st character."

  ^String
  [^String s]

  (str (.toUpperCase (.substring s 0 1))
       (.substring s 1)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntProject "Create a new ant project."

  ^Project
  []

  (let [lg (doto
             ;;(TimestampedLogger.)
             (AnsiColorLogger.)
             ;;(NoBannerLogger.)
             (.setOutputPrintStream System/out)
             (.setErrorPrintStream System/err)
             (.setMessageOutputLevel Project/MSG_INFO)) ]
    (doto (Project.)
      (.init)
      (.setName "project-x")
      (.addBuildListener lg))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ExecTarget "Run and execute a target."

  [^Target target]

  (.executeTarget (.getProject target) (.getName target)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;create a default project.
(def ^:private dftprj (atom (AntProject)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;cache ant task names as symbols
(def ^:private tasks
  (atom (let [arr (atom [])]
          (doseq [[k v] (.getTaskDefinitions @dftprj)]
            (when (.isAssignableFrom Task v)
              (let [n (str "Ant" (capstr k))]
                (reset! arr (conj @arr n k)))))
          (partition 2 (map #(symbol %) @arr)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;cache task bean info property descriptors.
(def ^:private props
  (atom (let [m {"tarfileset" Tar$TarFileSet
                 "formatter" FormatterElement
                 "batchtest" BatchTest
                 "junittest" JUnitTest
                 "fileset" FileSet }
              arr (atom {})]
          (doseq [[k v] (merge m (.getTaskDefinitions @dftprj))]
            (when (or (.isAssignableFrom Task v)
                      (contains? m k))
              (->> (-> (Introspector/getBeanInfo v)
                       (.getPropertyDescriptors))
                   (reduce (fn [memo pd]
                             (assoc memo
                                    (keyword (.getName pd)) pd))
                           {})
                   (swap! arr assoc v))))
          @arr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- method? "Find this setter method via best match."

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
(defn- coerce "Best attempt to convert a given value."

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
(defn- setOptions "Use reflection and invoke setters."

  [pj pojo options & [skips]]

  (let [arr (object-array 1)
        skips (or skips #{})
        cz (.getClass pojo)
        ps (get @props cz) ]
    (doseq [[k v] options]
      (when-not (contains? skips k)
        (if-let [pd (get ps k)]
          (if-let [wm (.getWriteMethod pd)]
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
                                  " not-found in task " cz))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntTarFileSet "Configure a TarFileSet Object."

  ^Tar$TarFileSet
  [^Project pj ^Tar$TarFileSet fs & [options nested]]

  (let [options (or options {})
        nested (or nested []) ]

    (setOptions pj fs options)
    (.setProject fs pj)
    (maybeCfgNested pj fs nested)
    fs
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntFileSet "Create a FileSet Object."

  ^FileSet
  [^Project pj & [options nested]]

  (let [fs (FileSet.)
        options (or options {})
        nested (or nested []) ]

    (setOptions pj
                fs
                (merge {:errorOnMissingDir false} options))
    (.setProject fs pj)
    (maybeCfgNested pj fs nested)
    fs
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntBatchTest "Configure a BatchTest Object."

  ^BatchTest
  [^Project pj ^BatchTest bt & [options nested]]

  (let [options (or options {})
        nested (or nested []) ]

    (setOptions pj bt options)
    (maybeCfgNested pj bt nested)
    bt
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntJunitTest "Configure a single JUnit Test Object."

  ^JUnitTask
  [^Project pj & [options nested]]

  (let [jt (JUnitTest.)
        options (or options {})
        nested (or nested []) ]

    (setOptions pj jt options)
    (maybeCfgNested pj jt nested)
    jt
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntChainedMapper ""

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
    cm
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtr-preopts ""

  [tk options]

  (when-let [[k v] (find options :type)]
    (.setType ^FormatterElement
                tk
                (doto (FormatterElement$TypeAttribute.)
                  (.setValue (str v)))))

  [options #{:type}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntFormatter "Create a Formatter Object."

  ^FormatterElement
  [^Project pj & [options nested]]

  (let [fe (FormatterElement.)
        options (or options {})
        nested (or nested []) ]

    (apply setOptions pj fe (fmtr-preopts fe options))
    (.setProject fe pj)
    fe
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SetClassPath "Build a nested Path structure for classpath."

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
      (->> (AntFileSet pj
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
      (when-let [^String line (:line (last p))]
        (-> (.createCompilerArg tk)
            (.setLine line)))

      :classpath
      (SetClassPath pj (.createClasspath tk) (last p))

      :sysprops
      (doseq [[k v] (last p)]
        (->> (doto (Environment$Variable.)
                   (.setKey (name k))
                   (.setValue (str v)))
             (.addSysproperty tk)))

      :formatter
      (->> (AntFormatter pj (last p))
           (.addFormatter tk))

      :include
      (-> (.createInclude tk)
          (.setName (str (last p))))

      :exclude
      (-> (.createExclude tk)
          (.setName (str (last p))))

      :fileset
      (let [s (AntFileSet
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
      (->> (AntJunitTest pj
                    (if (> (count p) 1)(nth p 1) {})
                    (if (> (count p) 2)(nth p 2) []))
           (.addTest tk))

      :chainedmapper
      (->> (AntChainedMapper pj
                    (if (> (count p) 1)(nth p 1) {})
                    (if (> (count p) 2)(nth p 2) []))
           (.add tk))

      :targetfile
      (.createTargetfile tk)

      :srcfile
      (.createSrcfile tk)

      :batchtest
      (AntBatchTest pj
                    (.createBatchTest tk)
                    (if (> (count p) 1)(nth p 1) {})
                    (if (> (count p) 2)(nth p 2) []))

      :tarfileset
      (AntTarFileSet pj
                     (.createTarFileSet tk)
                     (if (> (count p) 1)(nth p 1) {})
                     (if (> (count p) 2)(nth p 2) []))

      nil)
  ))

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

  (when-let [[k v] (find options :printsummary)]
    (.setPrintsummary ^JUnitTask
                tk
                (doto (JUnitTask$SummaryAttribute.)
                  (.setValue (str v)))))

  (when-let [[k v] (find options :forkMode)]
    (.setForkMode ^JUnitTask
                tk
                (doto (JUnitTask$ForkMode.)
                  (.setValue (str v)))))

  [options #{:forkMode :printsummary}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- jdoc-preopts ""

  [tk options]

  (when-let [[k v] (find options :access)]
    (.setAccess ^Javadoc
                tk
                (doto (Javadoc$AccessType.)
                  (.setValue (str v)))))
  [options #{:access}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tar-preopts ""
  [tk options]
  (when-let [[k v] (find options :compression)]
    (.setCompression ^Tar
                     tk
                     (doto (Tar$TarCompressionMethod.)
                       (.setValue (str v)))))
  [options #{:compression}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init-task ""

  ^Task
  [^Project pj ^Target target tobj]

  (let [{:keys [pre-options tname
                task options nested] } tobj
        pre-options (or pre-options
                        xxx-preopts)]
    (->> (doto ^Task
           task
           (.setProject pj)
           (.setOwningTarget target))
         (.addTask target))
    (->> (pre-options task options)
         (apply setOptions pj task))
    (maybeCfgNested pj task nested)
    task
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ProjAntTasks ""

  ^Target
  [^String target tasks]

  (let [^Project pj @dftprj
        tg (Target.)]
    (.setName tg (or target ""))
    (.addOrReplaceTarget pj tg)
    (doseq [t tasks]
      (init-task pj tg t))
    tg
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ProjAntTasks* ""

  ^Target
  [target & tasks]

  (ProjAntTasks target tasks))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RunTarget "Run ant tasks."

  [target tasks]

  (-> (ProjAntTasks target tasks)
      (ExecTarget)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RunTarget* "Run ant tasks."

  [target & tasks]

  (RunTarget target tasks))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RunTasks "Run ant tasks."

  [tasks]

  (RunTarget "" tasks))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RunTasks* "Run ant tasks."

  [& tasks]

  (RunTarget "" tasks))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ^:private ant-task

  ""
  [pj sym docstr func & [preopt]]

  (let [s (str func)
        tm (cstr/lower-case
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
                  "delete" delete-pre-opts
                  "junit" junit-preopts
                  "javadoc" jdoc-preopts
                  "tar" tar-preopts
                  nil)
                (assoc r# :pre-options))
           ;;else
           r#)))
  ))

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
(defn CleanDir "Clean an existing dir or create it."

  [d & {:keys [quiet]
        :or {:quiet true}}]

  (let [dir (io/file d)]
    (if (.exists dir)
      (RunTasks* (AntDelete
                      {:quiet quiet}
                      [[:fileset {:followSymlinks false
                                  :dir dir}
                                 [[:include "**/*"]]]]))
      ;;else
      (.mkdirs dir))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DeleteDir "Remove a directory."

  [d & {:keys [quiet]
        :or {:quiet true}}]

  (let [dir (io/file d)]
    (when (.exists dir)
      (RunTasks*
        (AntDelete {:quiet quiet}
                   [[:fileset {:followSymlinks false
                               :dir dir} ]])))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CopyFile ""

  [file toDir]

  (RunTasks*
    (AntCopy {:file file
              :todir toDir} )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MoveFile ""

  [file toDir]

  (RunTasks*
    (AntMove {:file file
              :todir toDir} )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

