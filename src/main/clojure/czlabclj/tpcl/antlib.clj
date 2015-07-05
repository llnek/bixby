;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tpcl.antlib

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.string :as cstr])

  (:import [org.apache.commons.exec CommandLine DefaultExecutor]
           [org.apache.commons.io FileUtils]
           [java.util Map HashMap Stack]
           [java.lang.reflect Method]
           [java.io File]
           [org.apache.tools.ant.taskdefs Javadoc Java Copy
            Chmod Concat Move Mkdir Tar Replace ExecuteOn
            Delete Jar Zip ExecTask Javac]
           [org.apache.tools.ant.listener TimestampedLogger]
           [org.apache.tools.ant.types Reference
            Commandline$Argument
            Commandline$Marker
            PatternSet$NameEntry
            Environment$Variable FileSet Path DirSet]
           [org.apache.tools.ant Project Target Task]
           [org.apache.tools.ant.taskdefs Javadoc$AccessType
            Replace$Replacefilter Replace$NestedString
            Tar$TarFileSet Tar$TarCompressionMethod
            Javac$ImplementationSpecificArgument]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def primitiveBool
(.getReturnType (.getMethod java.lang.String "isEmpty"
                            (make-array java.lang.Class 0)
                            )))
(def primitiveLong
(.getReturnType (.getMethod java.lang.Long "longValue"
                            (make-array java.lang.Class 0)
                            )))
(def primitiveInt
(.getReturnType (.getMethod java.lang.String "length"
                            (make-array java.lang.Class 0)
                            )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- capstr "Just capitalize the 1st character."
  [^String s]
  (str (.toUpperCase (.substring s 0 1))
       (.substring s 1)))

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
       primitiveBool
       java.lang.Boolean
       primitiveInt
       java.lang.Integer
       primitiveLong
       java.lang.Long
       org.apache.tools.ant.types.Path ])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^:private koerce "Best attempt to convert a value." (fn [_ a b] [a (class b)]))

(defmethod koerce [primitiveInt String] [_ _ ^String v] (Integer/parseInt v (int 10)))
(defmethod koerce [Integer String] [_ _ ^String v] (Integer/parseInt v (int 10)))

(defmethod koerce [primitiveInt Long] [_ _ ^Long v] (.intValue v))
(defmethod koerce [Integer Long] [_ _ ^Long v] (.intValue v))

(defmethod koerce [primitiveInt Integer] [_ _ ^Integer v] v)
(defmethod koerce [Integer Integer] [_ _ ^Integer v] v)

(defmethod koerce [primitiveLong String] [_ _ ^String v] (Long/parseLong v (int 10)))
(defmethod koerce [Long String] [_ _ ^String v] (Long/parseLong v (int 10)))

(defmethod koerce [primitiveLong Long] [_ _ ^Long v] v)
(defmethod koerce [Long Long] [_ _ ^Long v] v)

(defmethod koerce [Path File] [^Project pj _ ^File v] (Path. pj (.getCanonicalPath v)))
(defmethod koerce [Path String] [^Project pj _ ^String v] (Path. pj v))

(defmethod koerce [File String] [_ _ ^String v] (io/file v))
(defmethod koerce [File File] [_ _ v] v)

(defmethod koerce :default [_ pz _] (Exception. (str "expected class " pz)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- coerce "Best attempt to convert a given value."

  [^Project pj ^Class pz value]

  (cond
    (or (= primitiveBool pz)
        (= Boolean pz))
    (= "true" (str value))

    (= String pz)
    (str value)

    :else
    (koerce pj pz value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- setOptions "Use reflection and invoke setters."

  ([pj pojo options]
  (setOptions pj pojo options #{}))

  ([^Project pj ^Object pojo options skips]
  (let [arr (object-array 1)
        cz (.getClass pojo)]
    (doseq [[k v] options]
      (when-not (contains? skips k)
        (let [m (str "set" (capstr (name k)))
              rc (method? cz m)]
          (when (nil? rc)
            (throw (Exception. (str m " not found in class " pojo))))
          (aset arr 0 (coerce pj (last rc) v))
          (.invoke ^Method (first rc) pojo arr)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(declare maybeCfgNested)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntTarFileSet "Configure a TarFileSet Object."

  ^Tar$TarFileSet
  [^Project pj ^Tar$TarFileSet fs options nested]

  (let []
    (setOptions pj fs options)
    (.setProject fs pj)
    (maybeCfgNested pj fs nested)
    fs
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntFileSet "Create a FileSet Object."

  ^FileSet
  [^Project pj options nested]

  (let [fs (FileSet.)]
    (setOptions pj
                fs
                (merge {:errorOnMissingDir false} options))
    (.setProject fs pj)
    (maybeCfgNested pj fs nested)
    fs
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
      (->> (AntFileSet pj (nth p 1) (nth p 2))
           (.addFileset root))
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeCfgNested ""

  [^Project pj tk nested]

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

      :include
      (-> (.createInclude tk)
          (.setName (str (last p))))

      :exclude
      (-> (.createExclude tk)
          (.setName (str (last p))))

      :fileset
      (->> (AntFileSet pj (nth p 1) (nth p 2))
           (.addFileset tk))

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

      :tarfileset
      (AntTarFileSet pj (.createTarFileSet tk) (nth p 1) (nth p 2))

      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntProject "Create a new ant project."
  []
  (let [lg (doto
             (TimestampedLogger.)
             (.setOutputPrintStream System/out)
             (.setErrorPrintStream System/err)
             (.setMessageOutputLevel Project/MSG_INFO)) ]
    (doto
      (Project.)
      (.setName "project-x")
      (.addBuildListener lg))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ExecTarget "Run and execute a target."

  [^Target target]

  (.executeTarget (.getProject target) (.getName target)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ProjAntTasks "Bootstrap ant tasks with a target & project."

  ^Target
  [^Project pj ^String target tasks]

  (let [lg (doto (TimestampedLogger.)
             (.setOutputPrintStream System/out)
             (.setErrorPrintStream System/err)
             (.setMessageOutputLevel Project/MSG_INFO))
        tg (doto (Target.)
             (.setName (or target "mi6"))) ]
    (doto pj (.addOrReplaceTarget tg))
        ;;(.addBuildListener lg))
    (doseq [t tasks]
      (doto ^Task
        t
        (.setProject pj)
        (.setOwningTarget tg))
      (.addTask tg t))
    tg
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ProjAntTasks* "Bootstrap ant tasks with a target & project."

  ^Target
  [pj target & tasks]

  (ProjAntTasks pj target tasks))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RunAntTasks "Run ant tasks."

  ^Target
  [pj target tasks]

  (-> (ProjAntTasks pj target tasks)
      (ExecTarget)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RunAntTasks* "Run ant tasks."

  ^Target
  [pj target & tasks]

  (RunAntTasks pj target tasks))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ^:private ant-task

  ""
  [sym docstr func]

  (let [s (str func)
        tm (cstr/lower-case
             (.substring s (+ 1 (.lastIndexOf s "."))))]

    `(defn ~sym ~docstr [pj# options# nested#]
       (let [tk# (doto (new ~func)
                     (.setProject pj#)
                     (.setTaskName ~tm))]
        (setOptions pj# tk# options#)
        (maybeCfgNested pj# tk# nested#)
        tk#
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntDelete "Ant delete task."

  [^Project pj options nested]

  (let [tk (doto (Delete.)
                 (.setProject pj)
                 (.setTaskName "delete"))]
    (->> (merge {:includeEmptyDirs true} options)
         (setOptions pj tk))
    (maybeCfgNested pj tk nested)
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CleanDir "Clean an existing dir or create it."

  [^File dir & {:keys [quiet]
                :or {:quiet true}}]

  (let [pj (AntProject)]
    (if (.exists dir)
      (RunAntTasks* pj
                    ""
                    (AntDelete
                      pj
                      {:quiet quiet}
                      [[:fileset {:dir dir}
                                 [[:include "**/*"]]]]))
      (.mkdirs dir))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DeleteDir "Remove a directory."

  [^File dir & {:keys [quiet]
                :or {:quiet true}}]

  (when (.exists dir)
    (let [pj (AntProject)]
      (RunAntTasks*
        pj
        ""
        (AntDelete pj
                   {:quiet quiet}
                   [[:fileset {:dir dir} []]])))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(ant-task AntExec "Ant Exec task." ExecTask)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(ant-task AntConcat "Ant concat task." Concat)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntMkdir "Ant mkdir task."

  ^Task
  [^Project pj options]

  (let [tk (doto (Mkdir.)
                 (.setProject pj)
                 (.setTaskName "mkdir"))]
    (setOptions pj tk options)
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(ant-task AntReplace "Ant replace task." Replace)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(ant-task AntChmod "Ant chmod task." Chmod)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(ant-task AntJavac "Ant javac task." Javac)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntJavadoc "Ant javadoc task."

  ^Task
  [^Project pj options nested]

  (let [tk (doto (Javadoc.)
                 (.setProject pj)
                 (.setTaskName "javadoc"))]

    (when-let [[k v] (find options :access)]
      (.setAccess tk (doto (Javadoc$AccessType.)
                           (.setValue (str v)))))
    (setOptions pj tk options #{:access})
    (maybeCfgNested pj tk nested)
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(ant-task AntCopy "Ant copy task." Copy)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(ant-task AntMove "Ant move task." Move)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CopyFile ""

  [file toDir]

  (let [pj (AntProject)]
    (.mkdirs (io/file toDir))
    (RunAntTasks*
      pj
      ""
      (AntCopy pj {:file file
                   :todir toDir} []))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MoveFile ""

  [file toDir]

  (let [pj (AntProject)]
    (.mkdirs (io/file toDir))
    (RunAntTasks*
      pj
      ""
      (AntMove pj {:file file
                   :todir toDir} []))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntTar "Ant tar task."

  ^Task
  [^Project pj options nested]

  (let [tk (doto (Tar.)
                 (.setProject pj)
                 (.setTaskName "tar"))]

    (when-let [[k v] (find options :compression)]
          (.setCompression tk (doto (Tar$TarCompressionMethod.)
                               (.setValue (str v)))))
    (setOptions pj tk options #{:compression})
    (maybeCfgNested pj tk nested)
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(ant-task AntJar "Ant jar task." Jar)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(ant-task AntJava "Ant java task." Java)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(ant-task AntApply "Ant apply task." ExecuteOn)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF
