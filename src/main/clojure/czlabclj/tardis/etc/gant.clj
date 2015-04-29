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

  czlabclj.tardis.etc.gant

  (:import  [org.codehaus.gant GantState]
            [gant Gant]
            [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ExecGantScript ""

  [^File homeDir ^String appDir ^String target]

  (System/setProperty "user.dir"
                      (-> (File. homeDir (str "/apps/" appDir))
                          (.getCanonicalPath)))
  (let [fp (-> (File. homeDir (str "/apps/" appDir "/build.gant"))
               (.getCanonicalPath))
        g (Gant.)
        args (make-array String 4)]
    (aset #^"[Ljava.lang.String;" args 0 "--quiet")
    (aset #^"[Ljava.lang.String;" args 1 "--file")
    (aset #^"[Ljava.lang.String;" args 2 "build.gant")
    (aset #^"[Ljava.lang.String;" args 3 target)
    (.processArgs g args)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ExecGantScriptXXX ""

  [^File homeDir ^String appDir ^String target]

  (doto (Gant.)
    (.loadScript (File. homeDir (str "/apps/" appDir "/build.gant")))
    (.processTargets target)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private task-eof nil)

