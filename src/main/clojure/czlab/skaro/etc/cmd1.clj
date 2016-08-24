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

(ns ^{:doc "" :author "Kenneth Leung" }

  czlab.skaro.etc.cmd1

  ;;(:refer-clojure :rename {first fst second snd last lst})
  (:require
    [czlab.xlib.str :refer [addDelim! strbf<> ucase hgl? strim]]
    [czlab.xlib.format :refer [writeEdnString readEdn]]
    [czlab.crypto.codec :refer [strongPwd passwd<>]]
    [czlab.xlib.resources :refer [rstr]]
    [czlab.xlib.files :refer [spitUTF8]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [clojure.string :as cs]
    [boot.core :as bcore]
    [czlab.xlib.files
     :refer [readFile
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
             resStr]]
    [czlab.crypto.core
     :refer [exportPrivateKey
             exportPublicKey
             asymKeyPair<>
             assertJce
             dbgProvider
             ssv1PKCS12
             csreq<>]])

  (:use [czlab.skaro.etc.cmd2]
        [czlab.tpcl.boot]
        [czlab.xlib.guids]
        [czlab.xlib.meta]
        [czlab.skaro.etc.svcs]
        [czlab.skaro.sys.core])

  (:import
    [czlab.skaro.loaders CljClassLoader]
    [org.apache.commons.io FileUtils]
    [czlab.skaro.etc CmdHelpError]
    [czlab.skaro.server Cljshim ]
    [czlab.crypto PasswordAPI]
    [boot App]
    [java.util
     ResourceBundle
     Properties
     Calendar
     Map
     Date]
    [czlab.xlib I18N]
    [java.io File]
    [java.security KeyPair PublicKey PrivateKey]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- execBootScript

  "Call into boot/clj code"
  [^File homeDir ^File appDir & args]

  (sysProp! "skaro.home.dir" (.getCanonicalPath homeDir))
  (sysProp! "skaro.proc.dir" (.getCanonicalPath appDir))
  (App/main (into-array String args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Create

  ""
  []

  (let [rcb (I18N/base)]
    (printf "%s\n" (rstr rcb "usage.new.d1"))
    (printf "%s\n" (rstr rcb "usage.new.d2"))
    (printf "%s\n" (rstr rcb "usage.new.d3"))
    (printf "%s\n" (rstr rcb "usage.new.d4"))
    (printf "%s\n" (rstr rcb "usage.new.d5"))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onCreate

  "Create a new app"
  [args]
  {:pre [(vector? args)]}

  (if (> (count args) 1)
    (createApp (args 0) (args 1))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Build

  ""
  []

  (let [rcb (I18N/base)]
    (printf "%s\n" (rstr rcb "usage.build.d1"))
    (printf "%s\n" (rstr rcb "usage.build.d2"))
    (printf "%s\n" (rstr rcb "usage.build.d3"))
    (printf "%s\n" (rstr rcb "usage.build.d4"))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe build an app?
(defn onBuild

  "Build the app"
  [args]
  {:pre [(vector? args)]}

  (->> (if (empty? args) ["dev"] args)
       (apply execBootScript (getHomeDir) (getCwd))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Podify

  ""
  []

  (let [rcb (I18N/base)]
    (printf "%s\n" (rstr rcb "usage.podify.d1"))
    (printf "%s\n" (rstr rcb "usage.podify.d2"))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onPodify

  "Package the app"
  [args]
  {:pre [(vector? args)]}

  (if-not (empty? args)
    (bundleApp (getHomeDir)
               (getCwd) (args 0))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Test

  ""
  []

  (let [rcb (I18N/base)]
    (printf "%s\n" (rstr rcb "usage.test.d1"))
    (printf "%s\n" (rstr rcb "usage.test.d2"))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onTest

  "Test the app"
  [args]
  {:pre [(vector? args)]}

  (->> (if (empty? args) ["tst"] args)
       (apply execBootScript (getHomeDir) (getCwd) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Start

  ""
  []

  (let [rcb (I18N/base)]
    (printf "%s\n" (rstr rcb "usage.start.d1"))
    (printf "%s\n" (rstr rcb "usage.start.d2"))
    (printf "%s\n" (rstr rcb "usage.start.d3"))
    (printf "%s\n" (rstr rcb "usage.start.d4"))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onStart

  "Start and run the app"
  [args]
  {:pre [(vector? args)]}

  (let [func "czlab.skaro.sys.climain/startViaCLI"
        home (getHomeDir)
        cwd (getCwd)
        rt (-> (CljClassLoader/newLoader home cwd)
               (Cljshim/newrt (.getName cwd)))
        s2 (first args)]
    ;; background job is handled differently on windows
    (if (and (contains? #{"-bg" "--background"} s2)
             (isWindows?))
      (runAppBg home cwd)
      (try!
        (.callEx rt func (object-array [home cwd]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Debug

  ""
  []

  (let [rcb (I18N/base)]
    (printf "%s\n" (rstr rcb "usage.debug.d1"))
    (printf "%s\n" (rstr rcb "usage.debug.d2"))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onDebug "Debug the app" [args] (onStart args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Demos

  ""
  []

  (let [rcb (I18N/base)]
    (printf "%s\n" (rstr rcb "usage.demo.d1"))
    (printf "%s\n" (rstr rcb "usage.demo.d2"))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onDemos

  "Generate demo apps"
  [args]
  {:pre [(vector? args)]}

  (if-not (empty? args)
    (publishSamples (args 0))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genPwd

  ""
  [args]

  (let [c (first args)
        n (convLong (str c) 12)]
    (if (and (>= n 8)
             (<= n 32))
      (println (strongPwd n))
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
  {:pre [(vector? args)]}

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
  {:pre [(vector? args)]}

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
  {:pre [(vector? args)]}

  (if (> (count args) 1)
    (->> (passwd<> (args 1) (args 0))
         (.text )
         (println ))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Generate

  ""
  []

  (let [rcb (I18N/base)]
    (printf "%s\n" (rstr rcb "usage.gen.d1"))
    (printf "%s\n" (rstr rcb "usage.gen.d2"))
    (printf "%s\n" (rstr rcb "usage.gen.d3"))
    (printf "%s\n" (rstr rcb "usage.gen.d4"))
    (printf "%s\n" (rstr rcb "usage.gen.d5"))
    (printf "%s\n" (rstr rcb "usage.gen.d6"))
    (printf "%s\n" (rstr rcb "usage.gen.d7"))
    (printf "%s\n" (rstr rcb "usage.gen.d8"))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onGenerate

  "Generate a bunch of stuff"
  [args]
  {:pre [(vector? args)]}

  (let [c (first args)]
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
(defn onHelp-TestJCE

  ""
  []

  (let [rcb (I18N/base)]
    (printf "%s\n" (rstr rcb "usage.testjce.d1"))
    (printf "%s\n" (rstr rcb "usage.testjce.d2"))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onTestJCE

  "Test if JCE (crypto) is ok"
  [args]
  {:pre [(vector? args)]}

  (let [rcb (I18N/base)]
    (assertJce)
    (println (rstr rcb "usage.testjce.ok"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Version

  ""
  []

  (let [rcb (I18N/base)]
    (printf "%s\n" (rstr rcb "usage.version.d1"))
    (printf "%s\n" (rstr rcb "usage.version.d2"))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onVersion

  "Show the version of system"
  [args]
  {:pre [(vector? args)]}

  (let [rcb (I18N/base)]
    (printf "%s\n" (rstr rcb "usage.version.o1" (sysProp "skaro.version")))
    (printf "%s\n" (rstr rcb "usage.version.o2" (sysProp "java.version")))
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
      (-> (resStr (str "czlab/skaro/eclipse/"
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
      (-> (resStr (str "czlab/skaro/eclipse/"
                       "java"
                       "/classpath.txt"))
          (cs/replace "${CLASS.PATH.ENTRIES}" (str sb))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-IDE

  ""
  []

  (let [rcb (I18N/base)]
    (printf "%s\n" (rstr rcb "usage.ide.d1"))
    (printf "%s\n" (rstr rcb "usage.ide.d2"))
    (printf "%s\n" (rstr rcb "usage.ide.d3"))
    (printf "%s\n" (rstr rcb "usage.ide.d4"))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onIDE

  "Generate IDE project files"
  [args]
  {:pre [(vector? args)]}

  (if (and (> (count args) 0)
           (contains? #{"-e" "--eclipse"} (args 0)))
    (genEclipseProj (getCwd))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Service

  ""
  []

  (let [rcb (I18N/base)]
    (printf "%s\n" (rstr rcb "usage.svc.d1"))
    (printf "%s\n" (rstr rcb "usage.svc.d2"))
    (printf "%s\n" (rstr rcb "usage.svc.d3"))
    (printf "%s\n" (rstr rcb "usage.svc.d4"))
    (printf "%s\n" (rstr rcb "usage.svc.d5"))
    (printf "%s\n" (rstr rcb "usage.svc.d6"))
    (printf "%s\n" (rstr rcb "usage.svc.d7"))
    (printf "%s\n" (rstr rcb "usage.svc.d8"))
    (println)))

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
           (spitUTF8 fp)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onService

  ""
  [args]
  {:pre [(vector? args)]}

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
            :repeat :czlab.skaro.io.loops/RepeatingTimer
            :once :czlab.skaro.io.loops/OnceTimer
            :files :czlab.skaro.io.files/FilePicker
            :http :czlab.skaro.io.netty/NettyMVC
            :pop3 :czlab.skaro.io.mails/POP3
            :imap :czlab.skaro.io.mails/IMAP
            :tcp :czlab.skaro.io.socket/Socket
            :jms :czlab.skaro.io.jms/JMS
            :? nil
            (trap! CmdHelpError))]
    (onSvc id hint t)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Help

  ""
  []

  (trap! CmdHelpError))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(declare getTasks)
(defn onHelp

  "Show help"
  [args]
  {:pre [(vector? args)]}

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
  *skaro-tasks*
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
(defn- getTasks "" [] *skaro-tasks*)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


