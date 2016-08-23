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
    [czlab.xlib.format :refer [writeEdnString readEdn]]
    [czlab.crypto.codec :refer [strongPwd passwd<>]]
    [czlab.xlib.resources :refer [rstr]]
    [czlab.xlib.dates :refer [+months gcal<>]]
    [czlab.xlib.files :refer [spitUTF8]]
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
             when-some+
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
        [czlab.skaro.etc.svcs]
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
;;
(defn onHelp-Create

  ""
  []

  (printf "%s\n" "Create a new application in the current directory.")
  (printf "%s\n" "skaro new [options] <app-name>")
  (printf "%s\n" "options:")
  (printf "%s\n" "-web, --webapp   Create a web application")
  (printf "%s\n" "-soa, --soaapp   Create a service oriented application")
  (println))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onCreate

  "Create a new app"
  [args]

  (if (> (count args) 1)
    (createApp (args 0) (args 1))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Build

  ""
  []

  (printf "%s\n" "Make the application, optionally run a build target.")
  (printf "%s\n" "make [optional-target]")
  (printf "%s\n" "e.g.")
  (printf "%s\n" "make compile")
  (println))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe build an app?
(defn onBuild

  "Build the app"
  [args]

  (->> (if (empty? args) ["dev"] args)
       (apply execBootScript (getHomeDir) (getCwd))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Podify

  ""
  []

  (printf "%s\n" "Package this application as a zipped archive.")
  (printf "%s\n" "skaro podify <output-dir>")
  (println))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onPodify

  "Package the app"
  [args]

  (if-not (empty? args)
    (bundleApp (getHomeDir)
               (getCwd) (args 0))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Test

  ""
  []

  (printf "Compiles and run test cases."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onTest

  "Test the app"
  [args]

  (->> (if (empty? args) ["tst"] args)
       (apply execBootScript (getHomeDir) (getCwd) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Start

  ""
  []

  (printf "%s\n" "Start the application, optionally run in background.")
  (printf "%s\n" "start [options]")
  (printf "%s\n" "options:")
  (printf "%s\n" "-bg, --background   Run app in background.")
  (println))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
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
;;
(defn onHelp-Debug

  ""
  []

  (printf "%s" "Start the application in debug mode.")
  (println))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onDebug "Debug the app" [args] (onStart args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Demos

  ""
  []

  (printf "%s\n" "Generate a set of samples in the output directory.")
  (printf "%s\n" "skaro demos <output-dir>")
  (println))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
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
(defn onHelp-Generate

  ""
  []

  (printf "%s\n" "Generate useful crypto related artifacts.")
  (printf "%s\n" "skaro generate [options]")
  (printf "%s\n" "options:")
  (printf "%s\n" "-sc --selfsignedcert   x")
  (printf "%s\n" "-cr --certreq          x")
  (printf "%s\n" "-kp --keypair          x")
  (printf "%s\n" "-pw --password         x")
  (printf "%s\n" "-hh --hash             x")
  (printf "%s\n" "-uu --uuid             x")
  (printf "%s\n" "-ec --encrypt          x")
  (printf "%s\n" "-dc --decrypt          x")
  (println))

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
(defn onHelp-TestJCE

  ""
  []

  (printf "%s" "Check for the installation of unlimited JCE files.")
  (println))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onTestJCE

  "Test if JCE (crypto) is ok"
  [args]

  (assertJce)
  (println "JCE is OK."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Version

  ""
  []

  (printf "%s" "Show the skaro version")
  (println))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onVersion

  "Show the version of system"
  [args]

  (println "skaro version : "  (System/getProperty "skaro.version"))
  (println "java version  : "  (System/getProperty "java.version")))

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
(defn onHelp-IDE

  ""
  []

  (printf "%s\n" "Generate IDE project files.")
  (printf "%s\n" "skaro ide [options]")
  (printf "%s\n" "options:")
  (printf "%s\n" "-e, --eclipse   Generate eclipse project files.")
  (println))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onIDE

  "Generate IDE project files"
  [args]

  (if (and (> (count args) 0)
           (= "eclipse" (args 0)))
    (genEclipseProj (getCwd))
    (trap! CmdHelpError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Service

  ""
  []

  (printf "%s\n" "Manage system services for this application.")
  (printf "%s\n" "skaro service [options]")
  (printf "%s\n" "options:")
  (printf "%s\n" "-t, --type <http|web|tcp|mail|repeat|once|files>   Service type.")
  (printf "%s\n" "-a, --add <id>                                     Name of service.")
  (printf "%s\n" "-r, --remove <id>                                  Name of service.")
  (println))

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

  (when (< (count args) 2) (trap! CmdHelpError))
  (let [cmd (args 0)
        id (keyword (args 1))
        [hint svc]
        (case (keyword cmd)
          :remove [-1 "?"]
          :add (if (< (count args) 3)
                 (trap! CmdHelpError)
                 [1 (args 2)])
          (trap! CmdHelpError))
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


