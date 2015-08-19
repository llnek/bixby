;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlab.skaro.etc.cmd1

  (:require
    [czlab.xlib.crypto.codec :refer [StrongPwd* Pwdify]]
    [czlab.xlib.util.files
    :refer [ReadOneFile
    CleanDir Mkdirs WriteOneFile ListFiles]]
    [czlab.xlib.util.cmdline :refer [CLIConverse]]
    [czlab.xlib.util.guids :refer [NewUUid NewWWid]]
    [czlab.xlib.i18n.resources :refer [RStr]]
    [czlab.xlib.util.dates :refer [AddMonths GCal*]]
    [czlab.xlib.util.meta :refer [GetCldr]]
    [czlab.xlib.util.str :refer [ucase hgl? strim]]
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io]
    [clojure.string :as cs]
    [czlab.xlib.util.core
    :refer [FPath GetCwd IsWindows?
    trap! ex*
    tryc try! Stringify FlattenNil ConvLong ResStr]]
    [czlab.xlib.util.format :refer [ReadEdn]]
    [czlab.xlib.crypto.core
    :refer [AES256_CBC AssertJce PEM_CERT ExportPublicKey
    ExportPrivateKey DbgProvider AsymKeyPair* SSv1PKCS12* CsrReQ*]])

  (:refer-clojure :rename {first fst second snd last lst})

  (:use [czlab.skaro.etc.boot]
        [czlab.skaro.etc.cmd2]
        [czlab.xlib.util.meta]
        [czlab.skaro.core.consts])

  (:import
    [java.util Map Calendar ResourceBundle Properties Date]
    [org.apache.commons.lang3.tuple ImmutablePair]
    [com.zotohlab.skaro.loaders AppClassLoader]
    [com.zotohlab.skaro.core CLJShim ]
    [com.zotohlab.skaro.etc CmdHelpError]
    [com.zotohlab.frwk.crypto PasswordAPI]
    [com.zotohlab.wflow Job]
    [java.io File]
    [java.security KeyPair PublicKey PrivateKey]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new app?
(defn OnCreate

  "Create a new app"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if (> (count args) 1)
      (CreateApp (fst args) (snd args))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe build an app?
(defn OnBuild

  "Build the app"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (->> (if (empty? args) ["dev"] args)
         (apply ExecBootScript (GetHomeDir) (GetCwd) ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe compress and package an app?
(defn OnPodify

  "Package the app"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if-not (empty? args)
      (BundleApp (GetHomeDir)
                 (GetCwd) (fst args))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe run tests on an app?
(defn OnTest

  "Test the app"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (->> (if (empty? args) ["tst"] args)
         (apply ExecBootScript (GetHomeDir) (GetCwd) ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe start the server?
(defn OnStart

  "Start and run the app"

  [^Job j]

  (let [func (str "czlab.skaro.impl.climain/StartViaCLI")
        cwd (GetCwd)
        rt (-> (doto
                 (AppClassLoader. (GetCldr))
                 (.configure cwd))
               (CLJShim/newrt (.getName cwd)))
        args (.getLastResult j)
        args (drop 1 args)
        s2 (fst args)
        home (GetHomeDir)]
    ;; background job is handled differently on windows
    (if (and (= s2 "bg")
             (IsWindows?))
      (RunAppBg home)
      (tryc
        (.callEx rt func (object-array [home]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe run in debug mode?
(defn OnDebug

  "Debug the app"

  [^Job j]

  (OnStart j))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe generate some demo apps?
(defn OnDemos

  "Generate demo apps"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if-not (empty? args)
      (PublishSamples (fst args))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- generatePassword

  "Generate a random password"

  [len]

  (println (str (StrongPwd* len))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genKeyPair

  "Generate a keypair"

  [^String lenStr]

  ;;(DbgProvider java.lang.System/out)
  (let [kp (AsymKeyPair* "RSA" (ConvLong lenStr 1024))
        pvk (.getPrivate kp)
        puk (.getPublic kp)]
    (println "privatekey=\n" (Stringify (ExportPrivateKey pvk PEM_CERT)))
    (println "publickey=\n" (Stringify (ExportPublicKey puk PEM_CERT)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genWwid ""

  []

  (println (NewWWid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genGuid ""

  []

  (println (NewUUid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeCsrQs

  "Set of questions to capture the DN information"

  [^ResourceBundle rcb]

  {:fname {:question (RStr rcb "cmd.save.file")
           :default "csr"
           :required true
           :next :end
           :result :fn}

   :size {:question (RStr rcb "cmd.key.size")
          :default "1024"
          :required true
          :next :fname
          :result :size}

   :c {:question (RStr rcb "cmd.dn.c")
       :default "US"
       :required true
       :next :size
       :result :c}

   :st {:question (RStr rcb "cmd.dn.st")
        :required true
        :next :c
        :result :st}

   :loc {:question (RStr rcb "cmd.dn.loc")
         :required true
         :next :st
         :result :l }

   :o {:question (RStr rcb "cmd.dn.org")
       :required true
       :next :loc
       :result :o }

   :ou {:question (RStr rcb "cmd.dn.ou")
        :required true
        :next :o
        :result :ou }

   :cn {:question (RStr rcb "cmd.dn.cn")
        :required true
        :next :ou
        :result :cn } })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeKeyQs

  "Set of questions to save info to file"

  [^ResourceBundle rcb]

  {:fname {:question (RStr rcb "cmd.save.file")
           :default "test.p12"
           :required true
           :next :end
           :result :fn }

   :pwd {:question (RStr rcb "cmd.key.pwd")
         :required true
         :next :fname
         :result :pwd }

   :duration {:question (RStr rcb "cmd.key.duration")
              :default "12"
              :required true
              :next :pwd
              :result :months }

   :size {:question (RStr rcb "cmd.key.size")
          :default "1024"
          :required true
          :next :duration
          :result :size } })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- promptQs ""

  [questions start]

  (when-some [rc (CLIConverse questions start)]
    (let [ssn (map #(let [v (get rc %) ]
                      (if (hgl? v)
                        (str (ucase (name %)) "=" v)))
                   [ :c :st :l :o :ou :cn ]) ]
      [(cs/join "," (FlattenNil ssn)) rc])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- keyfile

  "Maybe generate a server key file?"
  []

  (if-some
    [res (promptQs (merge (makeCsrQs (ResBdl))
                          (makeKeyQs (ResBdl))) :cn) ]
    (let [dn (fst res)
          rc (lst res)
          now (Date.)
          ff (io/file (:fn rc))]
      (println "DN entered: " dn)
      (SSv1PKCS12*
        dn
        (Pwdify (:pwd rc))
        ff
        {:keylen (ConvLong (:size rc) 1024)
         :start now
         :end (-> (GCal* now)
                  (AddMonths (ConvLong (:months rc) 12))
                  (.getTime)) })
      (println "Wrote file: " ff))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- csrfile

  "Maybe generate a CSR?"
  []

  (if-some
    [res (promptQs (makeCsrQs (ResBdl)) :cn) ]
    (let [dn (fst res)
          rc (lst res)
          [req pkey]
          (CsrReQ* (ConvLong (:size rc) 1024)
                      dn
                      PEM_CERT) ]
      (println "DN entered: " dn)
      (let [f1 (io/file (:fn rc) ".key")
            f2 (io/file (:fn rc) ".csr") ]
        (WriteOneFile f1 pkey)
        (println "Wrote file: " f1)
        (WriteOneFile f2 req)
        (println "Wrote file: " f2)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnGenerate

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
       (Pwdify text)
       (.hashed )
       (.getLeft )
       (println )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnHash

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
       (Pwdify text pkey)
       (.encoded )
       (println )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnEncrypt

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
       (Pwdify secret pkey)
       (.text )
       (println )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnDecrypt

  "Decrypt the cypher"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if (> (count args) 1)
      (decrypt (fst args) (snd args))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnTestJCE

  "Test if JCE (crypto) is ok"

  [j]

  (AssertJce)
  (println "JCE is OK."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnVersion

  "Show the version of system"

  [j]

  (println (System/getProperty "skaro.version")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnHelp

  "Show help"

  [j]

  (trap! CmdHelpError))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanJars ""

  [^StringBuilder out ^File dir]

  (let [sep (System/getProperty "line.separator")
        fs (ListFiles dir "jar") ]
    (doseq [f fs]
      (doto out
        (.append (str "<classpathentry  kind=\"lib\" path=\""
                      (FPath f)
                      "\"/>" ))
        (.append sep)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genEclipseProj ""

  [^String app]

  (let [ec (Mkdirs (io/file (GetCwd) "eclipse.projfiles"))
        sb (StringBuilder.)
        cwd (GetCwd)
        lang "java"
        ulang (ucase lang) ]
    (CleanDir ec)
    (WriteOneFile
      (io/file ec ".project")
      (-> (ResStr (str "com/zotohlab/skaro/eclipse/"
                       lang
                       "/project.txt"))
          (cs/replace "${APP.NAME}" app)
          (cs/replace (str "${" ulang ".SRC}")
                      (FPath (io/file cwd
                                      "src/main" lang)))
          (cs/replace "${TEST.SRC}"
                      (FPath (io/file cwd
                                      "src/test" lang)))))
    (.mkdirs (io/file cwd DN_BUILD DN_CLASSES))
    (map (partial scanJars sb)
         [(io/file (GetHomeDir) DN_DIST)
          (io/file (GetHomeDir) DN_LIB)
          (io/file cwd DN_BUILD DN_CLASSES)
          (io/file cwd DN_TARGET)])
    (WriteOneFile
      (io/file ec ".classpath")
      (-> (ResStr (str "com/zotohlab/skaro/eclipse/"
                       lang
                       "/classpath.txt"))
          (cs/replace "${CLASS.PATH.ENTRIES}" (str sb))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnIDE

  "Generate IDE project files"

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if (and (> (count args) 1)
             (= "eclipse" (fst args)))
      (genEclipseProj (snd args))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

