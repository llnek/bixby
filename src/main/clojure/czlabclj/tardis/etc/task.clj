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

  czlabclj.tardis.etc.task

  (:import  [org.apache.tools.ant.taskdefs Ant Zip ExecTask Javac]
            [org.apache.tools.ant.listener TimestampedLogger]
            [org.apache.tools.ant.types FileSet Path DirSet]
            [org.apache.tools.ant Project Target Task]
            [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ExecProj ""

  [^Project pj]

  (.executeTarget pj "mi6"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ProjAntTask ""

  ^Project
  [^Task taskObj]

  (let [lg (doto (TimestampedLogger.)
             (.setOutputPrintStream System/out)
             (.setErrorPrintStream System/err)
             (.setMessageOutputLevel Project/MSG_INFO))
        pj (doto (Project.)
             (.setName "skaro-project")
             (.init))
        tg (doto (Target.)
             (.setName "mi6")) ]
    (doto pj
      (.addTarget tg)
      (.addBuildListener lg))
    (doto taskObj
      (.setProject pj)
      (.setOwningTarget tg))
    (.addTask tg taskObj)
    pj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeAntTask ""

  [^File hhhHome appId taskId]

  (let [tk (Ant.)
        pj (ProjAntTask tk) ]
    (doto tk
      (.setDir (File. hhhHome (str "apps/" appId)))
      (.setAntfile "build.xml")
      (.setTarget taskId)
          ;;(.setOutput "/tmp/out.txt")
      (.setUseNativeBasedir true)
      (.setInheritAll false))
    pj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeExecTask ""

  [^String execProg ^File workDir args]

  (let [tk (ExecTask.)
        pj (ProjAntTask tk) ]
    (doto tk
      (.setTaskName "skaro-exec-task")
      (.setExecutable execProg)
      (.setDir workDir))
    (doseq [v (seq args) ]
      (-> (.createArg tk)(.setValue v)))
    pj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeZipTask ""

  [^File srcDir ^File zipFile includes excludes]

  (let [tk (Zip.)
        pj (ProjAntTask tk)
        fs (doto (FileSet.)
             (.setDir srcDir)) ]
    (doseq [s (seq excludes) ]
      (-> (.createExclude fs) (.setName s)))
    (doseq [s (seq includes) ]
      (-> (.createInclude fs) (.setName s)))
    (doto tk
      (.add fs)
      (.setDestFile zipFile))
    pj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeAntJavac ""

  [^File srcPath ^File destDir]

  (let [ct (Javac.)
        pj (ProjAntTask ct) ]
    (doto ct
      (.setTaskName "compile")
      (.setFork true)
      (.setDestdir destDir))

    ;;(.setClassPath ct (Path. pj))

    (-> (.createSrc ct)
        (.addDirset (doto (DirSet.) (.setDir srcPath))))

    pj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private task-eof nil)

