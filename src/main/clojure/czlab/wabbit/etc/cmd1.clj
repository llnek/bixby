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
      :author "Kenneth Leung" }

  czlab.wabbit.etc.cmd1

  (:require
    [czlab.xlib.str :refer [addDelim! strbf<> ucase hgl? strim]]
    [czlab.xlib.format :refer [writeEdnString readEdn]]
    [czlab.crypto.core :refer [assertJce dbgProvider]]
    [czlab.crypto.codec :refer [strongPwd<> passwd<>]]
    [czlab.wabbit.sys.main :refer [startViaCLI]]
    [czlab.xlib.resources :refer [rstr]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [clojure.string :as cs]
    [boot.core :as bcore]
    [czlab.xlib.io
     :refer [readAsStr
             spitUtf8
             mkdirs
             writeFile
             listFiles]]
    [czlab.xlib.core
     :refer [isWindows?
             when-some+
             stringify
             sysProp!
             sysProp
             fpath
             spos?
             getCwd
             trap!
             exp!
             try!
             flatnil
             convLong
             resStr]])

  (:use [czlab.wabbit.etc.cmd2]
        [czlab.tpcl.boot]
        [czlab.xlib.guids]
        [czlab.xlib.meta]
        [czlab.wabbit.etc.svcs]
        [czlab.wabbit.sys.core])

  (:import
    [czlab.wabbit.etc AppMain CmdHelpError]
    [org.apache.commons.io FileUtils]
    [czlab.crypto PasswordAPI]
    ;;[boot App]
    [java.util
     ResourceBundle
     Properties
     Calendar
     Map
     Date]
    [czlab.xlib I18N]
    [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- execBootScript

  "Call into boot/clj code"
  [^File homeDir ^File appDir & args]

  (sysProp! "wabbit.home.dir" (.getCanonicalPath homeDir))
  (sysProp! "wabbit.proc.dir" (.getCanonicalPath appDir))
  (AppMain/invokeStatic (into-array String args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelpXXX

  ""
  [pfx end]

  (let [rcb (I18N/base)]
    (dotimes [n end]
      (printf "%s\n" (rstr rcb (str pfx (+ n 1)))))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Create "" [] (onHelpXXX "usage.new.d" 5))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onCreate

  "Create a new app"
  [args]

  (if (> (count args) 1)
    (createApp (args 0) (args 1))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Build "" [] (onHelpXXX "usage.build.d" 4))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe build an app?
(defn- onBuild

  "Build the app"
  [args]

  (->> (if (empty? args) ["dev"] args)
       (apply execBootScript (getHomeDir) (getCwd))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Podify "" [] (onHelpXXX "usage.podify.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onPodify

  "Package the app"
  [args]

  (if-not (empty? args)
    (bundleApp (getHomeDir)
               (getCwd) (args 0))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Test "" [] (onHelpXXX "usage.test.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onTest

  "Test the app"
  [args]

  (->> (if (empty? args) ["tst"] args)
       (apply execBootScript (getHomeDir) (getCwd) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Start "" [] (onHelpXXX "usage.start.d" 4))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onStart

  "Start and run the app"
  [args]

  (let [home (getHomeDir)
        cwd (getCwd)
        s2 (first args)]
    ;; background job is handled differently on windows
    (if (and (contains? #{"-bg" "--background"} s2)
             (isWindows?))
      (runAppBg home cwd)
      (startViaCLI home cwd))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Debug "" [] (onHelpXXX "usage.debug.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onDebug "Debug the app" [args] (onStart args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Demos "" [] (onHelpXXX "usage.demo.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onDemos

  "Generate demo apps"
  [args]

  (if-not (empty? args)
    (publishSamples (args 0))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genPwd

  ""
  [args]

  (let [c (first args)
        n (convLong (str c) 16)]
    (if (and (>= n 8)
             (<= n 32))
      (println (strongPwd<> n))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genWwid "" [] (println (wwid<>)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genGuid "" [] (println (uuid<>)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHash

  "Generate a hash"
  [args]

  (if-not (empty? args)
    (->> (passwd<> (first args))
         (.hashed )
         (:hash )
         (println ))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onEncrypt

  "Encrypt the data"
  [args]

  (if (> (count args) 1)
    (->> (passwd<> (args 1) (args 0))
         (.encoded )
         (println ))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onDecrypt

  "Decrypt the cypher"
  [args]

  (if (> (count args) 1)
    (->> (passwd<> (args 1) (args 0))
         (.text )
         (println ))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Generate "" [] (onHelpXXX "usage.gen.d" 8))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onGenerate

  "Generate a bunch of stuff"
  [args]

  (let [c (first args)
        args (vec (drop 1 args))]
    (cond
      (contains? #{"-p" "--password"} c)
      (genPwd args)
      (contains? #{"-h" "--hash"} c)
      (onHash args)
      (contains? #{"-u" "--uuid"} c)
      (genGuid)
      (contains? #{"-w" "--wwid"} c)
      (genWwid)
      (contains? #{"-e" "--encrypt"} c)
      (onEncrypt args)
      (contains? #{"-d" "--decrypt"} c)
      (onDecrypt args)
      :else (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-TestJCE "" [] (onHelpXXX "usage.testjce.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onTestJCE

  "Test if JCE (crypto) is ok"
  [args]

  (let [rcb (I18N/base)]
    (assertJce)
    (println (rstr rcb "usage.testjce.ok"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Version "" [] (onHelpXXX "usage.version.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onVersion

  "Show the version of system"
  [args]

  (let [rcb (I18N/base)]
    (->> (sysProp "wabbit.version")
         (rstr rcb "usage.version.o1")
         (printf "%s\n" ))
    (->> (sysProp "java.version")
         (rstr rcb "usage.version.o2")
         (printf "%s\n" ))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanJars

  ""
  [^StringBuilder out ^File dir]

  (let [sep (System/getProperty "line.separator")]
    (reduce
      (fn [^StringBuilder b f]
         (.append b
                  (str "<classpathentry  "
                       "kind=\"lib\""
                       " path=\"" (fpath f) "\"/>"))
         (.append b sep))
      out
      (listFiles dir "jar"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genEclipseProj

  ""
  [^File appdir]

  (let [ec (mkdirs (io/file appdir "eclipse.projfiles"))
        app (.getName appdir)
        sb (strbf<>)]
    (FileUtils/cleanDirectory ec)
    (writeFile
      (io/file ec ".project")
      (-> (resStr (str "czlab/wabbit/eclipse/"
                       "java"
                       "/project.txt"))
          (cs/replace "${APP.NAME}" app)
          (cs/replace "${JAVA.TEST}"
                      (fpath (io/file appdir
                                      "src/test/java")))
          (cs/replace "${JAVA.SRC}"
                      (fpath (io/file appdir
                                      "src/main/java")))
          (cs/replace "${CLJ.TEST}"
                      (fpath (io/file appdir
                                      "src/test/clojure")))
          (cs/replace "${CLJ.SRC}"
                      (fpath (io/file appdir
                                      "src/main/clojure")))))
    (mkdirs (io/file appdir DN_BUILD "classes"))
    (doall
      (map (partial scanJars sb)
           [(io/file (getHomeDir) DN_DIST)
            (io/file (getHomeDir) DN_LIB)
            (io/file appdir DN_TARGET)]))
    (writeFile
      (io/file ec ".classpath")
      (-> (resStr (str "czlab/wabbit/eclipse/"
                       "java"
                       "/classpath.txt"))
          (cs/replace "${CLASS.PATH.ENTRIES}" (str sb))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-IDE "" [] (onHelpXXX "usage.ide.d" 4))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onIDE

  "Generate IDE project files"
  [args]

  (if (and (not-empty args)
           (contains? #{"-e" "--eclipse"} (args 0)))
    (genEclipseProj (getCwd))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Service "" [] (onHelpXXX "usage.svc.d" 8))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onSvc

  ""
  [id hint & [svc]]

  (let
    [fp (io/file (getCwd) CFG_APP_CF)
     cf (readEdn fp)
     root (:services cf)
     nw
     (if (< hint 0)
       (dissoc root id)
       (when-some
         [gist (:conf (*emitter-defs* svc))]
         (when (contains? root id) (trap! CmdHelpError))
         (assoc root id (assoc gist :service svc))))]
    (when (some? nw)
      (->> (assoc cf :services nw)
           (writeEdnString)
           (spitUtf8 fp)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onService

  ""
  [args]

  (when (< (count args) 2) (trap! CmdHelpError))
  (let [cmd (args 0)
        id (keyword (args 1))
        [hint svc]
        (cond
          (contains? #{"-r" "--remove"} cmd)
          [-1 "?"]
          (contains? #{"-a" "--add"} cmd)
          (if (< (count args) 3)
            (trap! CmdHelpError)
            [1 (args 2)])
          :else (trap! CmdHelpError))
        t (case (keyword svc)
            :repeat :czlab.wabbit.io.loops/RepeatingTimer
            :once :czlab.wabbit.io.loops/OnceTimer
            :files :czlab.wabbit.io.files/FilePicker
            :http :czlab.wabbit.io.netty/NettyMVC
            :pop3 :czlab.wabbit.io.mails/POP3
            :imap :czlab.wabbit.io.mails/IMAP
            :tcp :czlab.wabbit.io.socket/Socket
            :jms :czlab.wabbit.io.jms/JMS
            :? nil
            (trap! CmdHelpError))]
    (onSvc id hint t)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Help "" [] (trap! CmdHelpError))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(declare getTasks)
(defn onHelp

  "Show help"
  [args]

  (let
    [c (keyword (first args))
     [f h] ((getTasks) c)]
    (if (fn? h)
      (h)
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def
  ^:dynamic
  *wabbit-tasks*
  {:service [onService onHelp-Service]
   :new [onCreate onHelp-Create]
   :ide [onIDE onHelp-IDE]
   :make [onBuild onHelp-Build]
   :podify [onPodify onHelp-Podify]
   :test [onTest onHelp-Test]
   :debug [onDebug onHelp-Debug]
   :help [onHelp onHelp-Help]
   :start [onStart onHelp-Start]
   :demos [onDemos onHelp-Demos]
   :generate [onGenerate onHelp-Generate]
   :testjce [onTestJCE onHelp-TestJCE]
   :version [onVersion onHelp-Version] })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getTasks "" [] *wabbit-tasks*)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


