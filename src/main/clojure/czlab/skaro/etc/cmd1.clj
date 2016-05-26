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
      :author "kenl" }

  czlab.skaro.etc.cmd1

  (:require
    [czlab.crypto.codec :refer [strongPwd pwdify]]
    [czlab.xlib.files
     :refer [readOneFile
             cleanDir mkdirs writeOneFile listFiles]]
    [czlab.xlib.cmdline :refer [cliConverse]]
    [czlab.xlib.guids :refer [newUUid newWWid]]
    [czlab.xlib.resources :refer [rstr]]
    [czlab.xlib.dates :refer [addMonths gcal]]
    [czlab.xlib.meta :refer [getCldr]]
    [czlab.xlib.str :refer [ucase hgl? strim]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [clojure.string :as cs]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.xlib.core
     :refer [fpath
             getCwd
             isWindows?
             trap!
             exp!
             tryc
             try!
             stringify
             flattenNil
             convLong
             resStr]]
    [czlab.crypto.core
     :refer [AES256_CBC
             assertJce
             PEM_CERT
             exportPublicKey
             exportPrivateKey
             dbgProvider
             asymKeyPair
             ssv1PKCS12
             csrReQ]])

  (:refer-clojure :rename {first fst second snd last lst})

  (:use [czlab.skaro.etc.boot]
        [czlab.skaro.etc.cmd2]
        [czlab.xlib.meta]
        [czlab.skaro.core.consts])

  (:import
    [org.apache.commons.lang3.tuple ImmutablePair]
    [java.util Map
     Calendar
     ResourceBundle
     Properties
     Date]
    [czlab.skaro.loaders AppClassLoader]
    [czlab.skaro.server CLJShim ]
    [czlab.skaro.etc CmdHelpError]
    [czlab.crypto PasswordAPI]
    [czlab.wflow.dsl Job]
    [java.io File]
    [java.security KeyPair PublicKey PrivateKey]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new app?
(defn onCreate

  "Create a new app"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if (> (count args) 1)
      (createApp (fst args) (snd args))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe build an app?
(defn onBuild

  "Build the app"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (->> (if (empty? args) ["dev"] args)
         (apply execBootScript (getHomeDir) (getCwd) ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe compress and package an app?
(defn onPodify

  "Package the app"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if-not (empty? args)
      (bundleApp (getHomeDir)
                 (getCwd) (fst args))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe run tests on an app?
(defn onTest

  "Test the app"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (->> (if (empty? args) ["testjava" "testclj"] args)
         (apply execBootScript (getHomeDir) (getCwd) ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe start the server?
(defn onStart

  "Start and run the app"

  [^Job j]

  (let [func (str "czlab.skaro.impl.climain/StartViaCLI")
        cwd (getCwd)
        rt (-> (doto
                 (AppClassLoader. (getCldr))
                 (.configure cwd))
               (CLJShim/newrt (.getName cwd)))
        args (.getLastResult j)
        args (drop 1 args)
        s2 (fst args)
        home (getHomeDir)]
    ;; background job is handled differently on windows
    (if (and (= s2 "bg")
             (isWindows?))
      (runAppBg home)
      (tryc
        (.callEx rt func (object-array [home]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe run in debug mode?
(defn onDebug

  "Debug the app"

  [^Job j]

  (onStart j))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe generate some demo apps?
(defn onDemos

  "Generate demo apps"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if-not (empty? args)
      (publishSamples (fst args))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- generatePassword

  "Generate a random password"

  [len]

  (println (str (strongPwd len))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genKeyPair

  "Generate a keypair"

  [^String lenStr]

  ;;(DbgProvider java.lang.System/out)
  (let [kp (asymKeyPair "RSA" (convLong lenStr 1024))
        pvk (.getPrivate kp)
        puk (.getPublic kp)]
    (println "privatekey=\n"
             (stringify (exportPrivateKey pvk PEM_CERT)))
    (println "publickey=\n"
             (stringify (exportPublicKey puk PEM_CERT)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genWwid ""

  []

  (println (newWWid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genGuid ""

  []

  (println (newUUid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeCsrQs

  "Set of questions to capture the DN information"

  [^ResourceBundle rcb]

  {:fname {:question (rstr rcb "cmd.save.file")
           :default "csr"
           :required true
           :next :end
           :result :fn}

   :size {:question (rstr rcb "cmd.key.size")
          :default "1024"
          :required true
          :next :fname
          :result :size}

   :c {:question (rstr rcb "cmd.dn.c")
       :default "US"
       :required true
       :next :size
       :result :c}

   :st {:question (rstr rcb "cmd.dn.st")
        :required true
        :next :c
        :result :st}

   :loc {:question (rstr rcb "cmd.dn.loc")
         :required true
         :next :st
         :result :l }

   :o {:question (rstr rcb "cmd.dn.org")
       :required true
       :next :loc
       :result :o }

   :ou {:question (rstr rcb "cmd.dn.ou")
        :required true
        :next :o
        :result :ou }

   :cn {:question (rstr rcb "cmd.dn.cn")
        :required true
        :next :ou
        :result :cn } })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeKeyQs

  "Set of questions to save info to file"

  [^ResourceBundle rcb]

  {:fname {:question (rstr rcb "cmd.save.file")
           :default "test.p12"
           :required true
           :next :end
           :result :fn }

   :pwd {:question (rstr rcb "cmd.key.pwd")
         :required true
         :next :fname
         :result :pwd }

   :duration {:question (rstr rcb "cmd.key.duration")
              :default "12"
              :required true
              :next :pwd
              :result :months }

   :size {:question (rstr rcb "cmd.key.size")
          :default "1024"
          :required true
          :next :duration
          :result :size } })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- promptQs ""

  [questions start]

  (when-some [rc (cliConverse questions start)]
    (let [ssn (map #(let [v (get rc %) ]
                      (if (hgl? v)
                        (str (ucase (name %)) "=" v)))
                   [ :c :st :l :o :ou :cn ]) ]
      [(cs/join "," (flattenNil ssn)) rc])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- keyfile

  "Maybe generate a server key file?"
  []

  (if-some
    [res (promptQs (merge (makeCsrQs (resBdl))
                          (makeKeyQs (resBdl))) :cn) ]
    (let [dn (fst res)
          rc (lst res)
          now (Date.)
          ff (io/file (:fn rc))]
      (println "DN entered: " dn)
      (ssv1PKCS12
        dn
        (pwdify (:pwd rc))
        ff
        {:keylen (convLong (:size rc) 1024)
         :start now
         :end (-> (gcal now)
                  (addMonths (convLong (:months rc) 12))
                  (.getTime)) })
      (println "Wrote file: " ff))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- csrfile

  "Maybe generate a CSR?"
  []

  (if-some
    [res (promptQs (makeCsrQs (resBdl)) :cn) ]
    (let [dn (fst res)
          rc (lst res)
          [req pkey]
          (csrReQ (convLong (:size rc) 1024)
                  dn
                  PEM_CERT)]
      (println "DN entered: " dn)
      (let [f1 (io/file (:fn rc) ".key")
            f2 (io/file (:fn rc) ".csr") ]
        (writeOneFile f1 pkey)
        (println "Wrote file: " f1)
        (writeOneFile f2 req)
        (println "Wrote file: " f2)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onGenerate

  "Generate a bunch of stuff"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (with-local-vars [rc true]
      (condp = (fst args)
        "keypair"
        (if (> (count args) 1)
          (genKeyPair (snd args))
          (var-set rc false))
        "password"
        (generatePassword 12)
        "serverkey"
        (keyfile)
        "guid"
        (genGuid)
        "wwid"
        (genWwid)
        "csr"
        (csrfile)
        (var-set rc false))
      (when-not @rc
        (trap! CmdHelpError)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genHash ""

  [text]

  (->> ^PasswordAPI
       (pwdify text)
       (.hashed )
       (.getLeft )
       (println )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHash

  "Generate a hash"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if-not (empty? args)
      (genHash (fst args))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- encrypt ""

  [pkey text]

  (->> ^PasswordAPI
       (pwdify text pkey)
       (.encoded )
       (println )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onEncrypt

  "Encrypt the data"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if (> (count args) 1)
      (encrypt (fst args) (snd args))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- decrypt ""

  [pkey secret]

  (->> ^PasswordAPI
       (pwdify secret pkey)
       (.text )
       (println )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onDecrypt

  "Decrypt the cypher"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if (> (count args) 1)
      (decrypt (fst args) (snd args))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onTestJCE

  "Test if JCE (crypto) is ok"

  [j]

  (assertJce)
  (println "JCE is OK."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onVersion

  "Show the version of system"

  [j]

  (println "skaro version : "  (System/getProperty "skaro.version"))
  (println "java version  : "  (System/getProperty "java.version")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp

  "Show help"

  [j]

  (trap! CmdHelpError))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanJars ""

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
(defn- genEclipseProj ""

  [^File appdir]

  (let [ec (mkdirs (io/file appdir "eclipse.projfiles"))
        app (.getName appdir)
        sb (StringBuilder.)]
    (cleanDir ec)
    (writeOneFile
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
    (writeOneFile
      (io/file ec ".classpath")
      (-> (resStr (str "czlab/skaro/eclipse/"
                       "java"
                       "/classpath.txt"))
          (cs/replace "${CLASS.PATH.ENTRIES}" (str sb))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onIDE

  "Generate IDE project files"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if (and (> (count args) 0)
             (= "eclipse" (fst args)))
      (genEclipseProj (getCwd))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


