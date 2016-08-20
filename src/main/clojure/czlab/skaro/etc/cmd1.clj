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

  czlab.skaro.etc.cmd1

  ;;(:refer-clojure :rename {first fst second snd last lst})

  (:require
    [czlab.xlib.str :refer [addDelim! strbf<> ucase hgl? strim]]
    [czlab.crypto.codec :refer [strongPwd passwd<>]]
    [czlab.xlib.resources :refer [rstr]]
    [czlab.xlib.dates :refer [+months gcal<>]]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.xlib.meta :refer [getCldr]]
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
             stringify
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
        [czlab.skaro.sys.core])

  (:import
    [czlab.skaro.loaders CljClassLoader]
    [org.apache.commons.io FileUtils]
    [czlab.skaro.etc CmdHelpError]
    [czlab.skaro.server Cljshim ]
    [boot App]
    [java.util
     ResourceBundle
     Properties
     Calendar
     Map
     Date]
    [czlab.crypto PasswordAPI]
    [java.io File]
    [java.security KeyPair PublicKey PrivateKey]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(defn- execBootScript

  "Call into boot/clj code"
  [^File homeDir ^File appDir & args]

  (System/setProperty "skaro.home.dir" (.getCanonicalPath homeDir))
  (System/setProperty "skaro.proc.dir" (.getCanonicalPath appDir))
  (App/main (into-array String args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new app?
(defn onCreate

  "Create a new app"
  [args]

  (if (> (count args) 0)
    (createApp (args 0))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe build an app?
(defn onBuild

  "Build the app"
  [args]

  (->> (if (empty? args) ["dev"] args)
       (apply execBootScript (getHomeDir) (getCwd))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe compress and package an app?
(defn onPodify

  "Package the app"
  [args]

  (if-not (empty? args)
    (bundleApp (getHomeDir)
               (getCwd) (args 0))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe run tests on an app?
(defn onTest

  "Test the app"
  [args]

  (->> (if (empty? args) ["tst"] args)
       (apply execBootScript (getHomeDir) (getCwd) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe start the server?
(defn onStart

  "Start and run the app"
  [args]

  (let [func "czlab.skaro.sys.climain/startViaCLI"
        home (getHomeDir)
        cwd (getCwd)
        rt (-> (CljClassLoader/newLoader home cwd)
               (Cljshim/newrt (.getName cwd)))
        s2 (first args)]
    ;; background job is handled differently on windows
    (if (and (= s2 "bg")
             (isWindows?))
      (runAppBg home)
      (try!
        (.callEx rt func (object-array [home]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe run in debug mode?
(defn onDebug "Debug the app" [args] (onStart args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe generate some demo apps?
(defn onDemos

  "Generate demo apps"
  [args]

  (if-not (empty? args)
    (publishSamples (args 0))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro generatePassword

  "Generate a random password"
  {:private true}
  [len]

  `(println (str (strongPwd ~len))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genKeyPair

  "Generate a keypair"
  [args]

  ;;(DbgProvider java.lang.System/out)
  (let [kp (asymKeyPair<> "RSA" 1024)
        pvk (.getPrivate kp)
        puk (.getPublic kp)]
    (println "privatekey=\n"
             (stringify (exportPrivateKey pvk )))
    (println "publickey=\n"
             (stringify (exportPublicKey puk )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genWwid "" [] (println (wwid<>)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genGuid "" [] (println (uuid<>)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onGenerate

  "Generate a bunch of stuff"
  [args]

  (condp = (first args)
    "keypair"
    (if (> (count args) 1)
      (genKeyPair args)
      (trap! CmdHelpError))
    "password"
    (generatePassword 12)
    "serverkey"
    nil;;(keyfile args)
    "guid"
    (genGuid)
    "wwid"
    (genWwid)
    "csr"
    nil;;(csrfile args)
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genHash

  ""
  [text]

  (->> (passwd<> text)
       (.hashed )
       (:hash )
       (println )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHash

  "Generate a hash"
  [args]

  (if-not (empty? args)
    (genHash (args 0))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- encrypt

  ""
  [pkey text]

  (->> (passwd<> text pkey)
       (.encoded )
       (println )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onEncrypt

  "Encrypt the data"
  [args]

  (if (> (count args) 1)
    (encrypt (args 0) (args 1))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- decrypt

  ""
  [pkey secret]

  (->> (passwd<> secret pkey)
       (.text )
       (println )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onDecrypt

  "Decrypt the cypher"
  [args]

  (if (> (count args) 1)
    (decrypt (args 0) (args 1))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onTestJCE

  "Test if JCE (crypto) is ok"
  [args]

  (assertJce)
  (println "JCE is OK."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onVersion

  "Show the version of system"
  [args]

  (println "skaro version : "  (System/getProperty "skaro.version"))
  (println "java version  : "  (System/getProperty "java.version")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp "Show help" [args] (trap! CmdHelpError))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanJars

  ""
  [^StringBuilder out ^File dir]

  (let [sep (System/getProperty "line.separator")
        fs (listFiles dir "jar") ]
    (doseq [f fs]
      (doto out
        (.append (str "<classpathentry  kind=\"lib\" path=\""
                      (fpath f)
                      "\"/>" ))
        (.append sep)))))

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
    (.mkdirs (io/file appdir DN_BUILD "classes"))
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
(defn onIDE

  "Generate IDE project files"
  [args]

  (if (and (> (count args) 0)
           (= "eclipse" (args 0)))
    (genEclipseProj (getCwd))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


