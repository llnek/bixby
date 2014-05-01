;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.


(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.tardis.etc.task

  (:import (org.apache.tools.ant.taskdefs Ant Zip ExecTask Javac))
  (:import (org.apache.tools.ant.listener TimestampedLogger))
  (:import (org.apache.tools.ant.types FileSet Path DirSet))
  (:import (org.apache.tools.ant Project Target Task))
  (:import (java.io File)))

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

  (let [ pj (doto (Project.)
              (.setName "hhh-project")
              (.init))
         lg (doto (TimestampedLogger.)
                  (.setOutputPrintStream System/out)
                  (.setErrorPrintStream System/err)
                  (.setMessageOutputLevel Project/MSG_INFO))
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

  (let [ tk (Ant.)
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

  (let [ tk (ExecTask.)
         pj (ProjAntTask tk) ]
    (doto tk
          (.setTaskName "hhh-exec-task")
          (.setExecutable execProg)
          (.setDir workDir))
    (doseq [ v (seq args) ]
      (-> (.createArg tk)(.setValue v)))
    pj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeZipTask ""

  [^File srcDir ^File zipFile includes excludes]

  (let [ tk (Zip.)
         pj (ProjAntTask tk)
         fs (doto (FileSet.)
                  (.setDir srcDir)) ]
    (doseq [ s (seq excludes) ]
      (-> (.createExclude fs) (.setName s)))
    (doseq [ s (seq includes) ]
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

  (let [ ct (Javac.)
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

