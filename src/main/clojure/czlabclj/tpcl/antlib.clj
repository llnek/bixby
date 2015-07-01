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
            Chmod Concat Move Mkdir Tar
            Delete Jar Zip ExecTask Javac]
           [org.apache.tools.ant.listener TimestampedLogger]
           [org.apache.tools.ant.types Reference
            Commandline$Argument
            PatternSet$NameEntry
            Environment$Variable FileSet Path DirSet]
           [org.apache.tools.ant Project Target Task]
           [org.apache.tools.ant.taskdefs Javadoc$AccessType
            Tar$TarFileSet
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
        (aset  #^"[Ljava.lang.Class;" arr 0 z)
        (try
          [(.getMethod cz m arr) z]
          (catch Exception _)))
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
(defmulti koerce "Best attempt to convert a value." (fn [_ a b] [a (class b)]))

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

  ([^Project pj ^Object pojo options]
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
(defn AntTarFileSet "Create a TarFileSet Object."

  ^Tar$TarFileSet
  [^Project pj ^Tar$TarFileSet fs options nested]

  (let []
    (setOptions pj fs options)
    (doseq [p nested]
      (case (first p)
        :include (-> ^PatternSet$NameEntry
                     (.createInclude fs)
                     (.setName (str (last p))))
        :exclude (-> ^PatternSet$NameEntry
                     (.createExclude fs)
                     (.setName (str (last p))))
        nil))
    fs
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntFileSet "Create a FileSet Object."

  ^FileSet
  [^Project pj options nested]

  (let [fs (FileSet.)]
    (setOptions pj fs options)
    (doseq [p nested]
      (case (first p)
        :include (-> ^PatternSet$NameEntry
                     (.createInclude fs)
                     (.setName (str (last p))))
        :exclude (-> ^PatternSet$NameEntry
                     (.createExclude fs)
                     (.setName (str (last p))))
        nil))
    fs
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
  [^Project pj ^String target & tasks]

  (let [lg (doto (TimestampedLogger.)
             (.setOutputPrintStream System/out)
             (.setErrorPrintStream System/err)
             (.setMessageOutputLevel Project/MSG_INFO))
        tg (doto (Target.)
             (.setName (or target "mi6"))) ]
    (doto pj
      (.addOrReplaceTarget tg))
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntExec "Ant exec task."

  ^Task
  [^Project pj options nested]

  (let [tk (doto (ExecTask.)
                 (.setProject pj)
                 (.setTaskName "exec"))]
    (setOptions pj tk options)
    (doseq [p nested]
      (case (first p)
        :args
        (doseq [v (last p)]
          (-> (.createArg tk)
              (.setValue (str v))))
        nil))
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntConcat "Ant concat task."

  ^Task
  [^Project pj options nested]

  (let [tk (doto (Concat.)
                 (.setProject pj)
                 (.setTaskName "concat"))]
    (setOptions pj tk options)
    (doseq [p nested]
      (case (first p)
        :fileset
        (->> (AntFileSet pj (nth p 1) (nth p 2))
             (.addFileset tk))
        nil))
    tk
  ))

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
(defn AntChmod "Ant chmod task."

  ^Task
  [^Project pj options nested]

  (let [tk (doto (Chmod.)
                 (.setProject pj)
                 (.setTaskName "chmod"))]
    (setOptions pj tk options)
    (doseq [p nested]
      (case (first p)
        :fileset
        (->> (AntFileSet pj (nth p 1) (nth p 2))
             (.addFileset tk))
        nil))
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntJavac "Ant javac task."

  ^Task
  [^Project pj options nested]

  (let [tk (doto (Javac.)
                 (.setProject pj)
                 (.setTaskName "javac"))]
    (setOptions pj tk options)
    (doseq [p nested]
      (case (first p)
        :compilerarg
        (when-let [^String line (:line (last p))]
          (-> (.createCompilerArg tk)
              (.setLine line)))
        :classpath
        (SetClassPath pj (.createClasspath tk) (last p))
        :files
        (doseq [n (last p)]
          (case (first n)
            :include
            (-> (.createInclude tk)
                (.setName (str (last n))))
            :exclude
            (-> (.createExclude tk)
                (.setName (str (last n))))
            nil))
        nil))
    tk
  ))

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
    (doseq [p nested]
      (case (first p)
        :classpath
        (SetClassPath pj (.createClasspath tk) (last p))
        :fileset
        (->> (AntFileSet pj (nth p 1) (nth p 2))
             (.addFileset tk))
        nil))
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntDelete "Ant delete task."

  [^Project pj options nested]

  (let [tk (doto (Delete.)
                 (.setProject pj)
                 (.setTaskName "delete"))]
    (setOptions pj tk options)
    (doseq [p nested]
      (case (first p)
        :fileset
        (->> (AntFileSet pj (nth p 1) (nth p 2))
             (.addFileset tk))
        nil))
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntCopy "Ant copy task."

  ^Task
  [^Project pj options nested]

  (let [tk (doto (Copy.)
                 (.setProject pj)
                 (.setTaskName "copy"))]
    (setOptions pj tk options)
    (doseq [p nested]
      (case (first p)
        :fileset
        (->> (AntFileSet pj (nth p 1) (nth p 2))
             (.addFileset tk))
        nil))
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntMove "Ant move task."

  ^Task
  [^Project pj options nested]

  (let [tk (doto (Move.)
                 (.setProject pj)
                 (.setTaskName "move"))]
    (setOptions pj tk options)
    (doseq [p nested]
      (case (first p)
        :fileset
        (->> (AntFileSet pj (nth p 1) (nth p 2))
             (.addFileset tk))
        nil))
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntTar "Ant tar task."

  ^Task
  [^Project pj options nested]

  (let [tk (doto (Tar.)
                 (.setProject pj)
                 (.setTaskName "tar"))]
    (setOptions pj tk options)
    (doseq [p nested]
      (case (first p)
        :tarfileset
        (AntTarFileSet pj (.createTarFileSet tk) (nth p 1) (nth p 2))
        nil))
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntJar "Ant jar task."

  ^Task
  [^Project pj options nested]

  (let [tk (doto (Jar.)
                 (.setProject pj)
                 (.setTaskName "jar"))]
    (setOptions pj tk options)
    (doseq [p nested]
      (case (first p)
        :fileset
        (->> (AntFileSet pj (nth p 1) (nth p 2))
             (.addFileset tk))
        nil))
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntJava "Ant java task."

  ^Task
  [^Project pj options nested]

  (let [tk (doto (Java.)
                 (.setProject pj)
                 (.setTaskName "java"))]
    (setOptions pj tk options)
    (doseq [p nested]
      (case (first p)
        :sysprops
        (doseq [[k v] (last p)]
          (->> (doto (Environment$Variable.)
                     (.setKey (name k))
                     (.setValue (str v)))
               (.addSysproperty tk)))
        :classpath
        (SetClassPath pj (.createClasspath tk) (last p))
        :args
        (doseq [a (last p)]
          (-> (.createArg tk)
              (.setValue (str a))))
        nil))
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntProject "Create a new ant project."
  []
  (let [lg (doto (TimestampedLogger.)
               (.setOutputPrintStream System/out)
               (.setErrorPrintStream System/err)
               (.setMessageOutputLevel Project/MSG_INFO))]
    (doto (Project.)
      (.setName "project-x")
      (.addBuildListener lg))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF
