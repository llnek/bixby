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

  czlabclj.tardis.etc.cmd1

  (:require [czlabclj.xlib.util.files :refer [ReadOneFile Mkdirs WriteOneFile]]
            [czlabclj.xlib.util.cmdline :refer [MakeCmdSeqQ CLIConverse]]
            [czlabclj.xlib.crypto.codec :refer [CreateStrongPwd Pwdify]]
            [czlabclj.xlib.util.guids :refer [NewUUid NewWWid]]
            [czlabclj.xlib.i18n.resources :refer [RStr]]
            [czlabclj.xlib.util.dates :refer [AddMonths MakeCal]]
            [czlabclj.xlib.util.meta :refer [GetCldr]]
            [czlabclj.xlib.util.str :refer [ucase nsb hgl? strim]]
            [czlabclj.xlib.util.core
             :refer [notnil?
             NiceFPath
             GetCwd
             IsWindows?
             Stringify
             FlattenNil
             ConvLong
             ResStr]]
            [czlabclj.xlib.util.format :refer [ReadEdn]]

            [czlabclj.xlib.crypto.core
             :refer [AES256_CBC
             AssertJce
             PEM_CERT
             ExportPublicKey
             ExportPrivateKey
             DbgProvider
             MakeKeypair
             MakeSSv1PKCS12
             MakeCsrReq]])

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.string :as cstr])

  (:use [czlabclj.tardis.etc.boot]
        [czlabclj.tardis.etc.cmd2]
        [czlabclj.xlib.util.meta]
        [czlabclj.tardis.core.consts])

  (:import  [java.util Map Calendar ResourceBundle Properties Date]
            [org.projectodd.shimdandy ClojureRuntimeShim]
            [org.apache.commons.lang3 StringUtils]
            [com.zotohlab.skaro.etc CliMain CmdHelpError]
            [com.zotohlab.frwk.crypto PasswordAPI]
            [org.apache.commons.io FileUtils]
            [org.apache.commons.codec.binary Hex]
            [com.zotohlab.wflow Job]
            [java.io File]
            [java.security KeyPair PublicKey PrivateKey]
            [com.zotohlab.frwk.io IO]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new app?
(defn OnCreate "Create a new app."

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if (> (count args) 1)
      (CreateApp (first args)
                 (second args))
      (throw (CmdHelpError.)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe build an app?
(defn OnBuild "Build the app."

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)
        tasks (if (empty? args)
                ["dev"]
                args)]
    (apply ExecBootScript
           (GetHomeDir)
           (GetCwd) tasks)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe compress and package an app?
(defn OnPodify "Package the app."

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if (> (count args) 0)
      (BundleApp (GetHomeDir)
                 (GetCwd) (first args))
      (throw (CmdHelpError.)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe run tests on an app?
(defn OnTest "Test the app."

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)
        tasks (if (empty? args)
                ["tst"]
                args)]
    (apply ExecBootScript
           (GetHomeDir)
           (GetCwd) tasks)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe start the server?
(defn OnStart "Start and run the app."

  [^Job j]

  (let [rt (ClojureRuntimeShim/newRuntime (GetCldr)
                                          (.getName (GetCwd)))
        cz "czlabclj.tardis.impl.climain"
        args (.getLastResult j)
        args (drop 1 args)
        s2 (if (> (count args) 0)
             (nth args 0)
             "")
        home (GetHomeDir)]
    (if
      ;; background job is handled differently on windows
      (and (= s2 "bg") (IsWindows?))
      (RunAppBg home)
      (doto rt
        (.require cz)
        (.invoke (str cz "/StartViaCLI") home)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe run in debug mode?
(defn OnDebug "Debug the app."

  [^Job j]

  (OnStart j))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe generate some demo apps?
(defn OnDemos "Generate demo apps."

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if (> (count args) 0)
      (PublishSamples (first args))
      (throw (CmdHelpError.)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- generatePassword "Generate a random password."

  [len]

  (println (CreateStrongPwd len)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genKeyPair "Generate a keypair."

  [^String lenStr]

  ;;(DbgProvider java.lang.System/out)
  (let [kp (MakeKeypair "RSA" (ConvLong lenStr 1024))
        pvk (.getPrivate kp)
        puk (.getPublic kp)
        pk (.getEncoded pvk)
        pu (.getEncoded puk) ]
    ;;(println "privatekey-bytes= " (Hex/encodeHexString pk))
    ;;(println "publickey-bytes = " (Hex/encodeHexString pu))
    (println "privatekey=\n" (Stringify (ExportPrivateKey pvk PEM_CERT)))
    (println "publickey=\n" (Stringify (ExportPublicKey puk PEM_CERT)))
  ))

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
(defn- make-csr-qs "Set of questions to capture the DN information."

  [^ResourceBundle rcb]

  { "fname"
    (MakeCmdSeqQ "fname" (RStr rcb "cmd.save.file")
                 "" "csr-req" true
                 #(do (.put ^Map %2 "fn" %1) ""))

    "size"
    (MakeCmdSeqQ "size" (RStr rcb "cmd.key.size")
                 "" "1024" true
                 #(do (.put ^Map %2 "size" %1) "fname"))

    "c"
    (MakeCmdSeqQ "c" (RStr rcb "cmd.dn.c")
                 "" "US" true
                 #(do (.put ^Map %2 "c" %1) "size"))

    "st"
    (MakeCmdSeqQ "st" (RStr rcb "cmd.dn.st")
                 "" "" true
                 #(do (.put ^Map %2 "st" %1) "c"))

    "loc"
    (MakeCmdSeqQ "loc" (RStr rcb "cmd.dn.loc")
                 "" "" true
                 #(do (.put ^Map %2 "l" %1) "st"))

    "o"
    (MakeCmdSeqQ "o" (RStr rcb "cmd.dn.org")
                 "" "" true
                 #(do (.put ^Map %2 "o" %1) "loc"))

    "ou"
    (MakeCmdSeqQ "ou" (RStr rcb "cmd.dn.ou")
                 "" "" true
                 #(do (.put ^Map %2 "ou" %1) "o"))

    "cn"
    (MakeCmdSeqQ "cn" (RStr rcb "cmd.dn.cn")
                 "" "" true
                 #(do (.put ^Map %2 "cn" %1) "ou"))
  })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-key-qs "Set of questions to save info to file."

  [^ResourceBundle rcb]

  {
    "fname"
    (MakeCmdSeqQ "fname" (RStr rcb "cmd.save.file")
                 "" "test.p12" true
                 #(do (.put ^Map %2 "fn" %1) ""))

    "pwd"
    (MakeCmdSeqQ "pwd" (RStr rcb "cmd.key.pwd")
                 "" "" true
                 #(do (.put ^Map %2 "pwd" %1) "fname"))

    "duration"
    (MakeCmdSeqQ "duration" (RStr rcb "cmd.key.duration")
                 "" "12" true
                 #(do (.put ^Map %2 "months" %1) "pwd"))

    "size"
    (MakeCmdSeqQ "size" (RStr rcb "cmd.key.size")
                 "" "1024" true
                 #(do (.put ^Map %2 "size" %1) "duration"))

   })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- promptQuestions ""

  [questions start]

  (when-let [rc (CLIConverse questions start)]
    (let [ssn (map #(let [v (get rc %) ]
                      (if (hgl? v)
                        (str (ucase (name %)) "=" v)))
                   [ :c :st :l :o :ou :cn ]) ]
      [(cstr/join "," (FlattenNil ssn)) rc])
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- keyfile "Maybe generate a server key file?"

  []

  (if-let [res (promptQuestions (merge (make-csr-qs @SKARO-RSBUNDLE)
                                       (make-key-qs @SKARO-RSBUNDLE))
                                "cn") ]
    (let [dn (first res)
          rc (last res)
          ff (io/file (:fn rc))
          now (Date.) ]
      (println "DN entered: " dn)
      (MakeSSv1PKCS12 dn
                      (Pwdify (:pwd rc))
                      ff
                      {:keylen (ConvLong (:size rc) 1024)
                      :start now
                      :end (-> (MakeCal now)
                               (AddMonths (ConvLong (:months rc) 12))
                               (.getTime)) })
      (println "Wrote file: " ff))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- csrfile "Maybe generate a CSR?"

  []

  (if-let [res (promptQuestions (make-csr-qs @SKARO-RSBUNDLE) "cn") ]
    (let [dn (first res)
          rc (last res)
          [req pkey]
          (MakeCsrReq (ConvLong (:size rc) 1024)
                      dn
                      PEM_CERT) ]
      (println "DN entered: " dn)
      (let [f1 (io/file (:fn rc) ".key")
            f2 (io/file (:fn rc) ".csr") ]
        (WriteOneFile f1 pkey)
        (println "Wrote file: " f1)
        (WriteOneFile f2 req)
        (println "Wrote file: " f2)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnGenerate "Generate a bunch of stuff."

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (with-local-vars [rc true]
      (condp = (first args)
        "keypair"
        (if (> (count args) 1)
          (genKeyPair (second args))
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
      (when-not @rc)
        (throw (CmdHelpError.)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genHash ""

  [text]

  (let [^PasswordAPI p (Pwdify text) ]
    (println (first (.hashed p)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnHash "Generate a hash."

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if (> (count args) 0)
      (genHash (first args))
      (throw (CmdHelpError.)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- encrypt ""

  [pkey text]

  (let [^PasswordAPI p (Pwdify text pkey) ]
    (println (.encoded p))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnEncrypt "Encrypt the data."

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if (> (count args) 1)
      (encrypt (first args) (second args))
      (throw (CmdHelpError.)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- decrypt ""

  [pkey secret]

  (let [^PasswordAPI p (Pwdify secret pkey) ]
    (println (.text p))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnDecrypt "Decrypt the cypher."

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if (> (count args) 1)
      (decrypt (first args) (second args))
      (throw (CmdHelpError.)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnTestJCE "Test if JCE (crypto) is ok."

  [j]

  (AssertJce)
  (println "JCE is OK."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnVersion "Show the version of system."

  [j]

  (println (System/getProperty "skaro.version")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- XXOnVersion "Show the version of system."

  [j]

  (let [s (ReadOneFile (io/file (GetHomeDir) "VERSION")) ]
    (if (hgl? s)
      (println s)
      (println "Unknown version."))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnHelp "Show help."

  [j]

  (throw (CmdHelpError.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanJars ""

  [^File dir ^StringBuilder out]

  (let [sep (System/getProperty "line.separator")
        fs (IO/listFiles dir "jar" false) ]
    (doseq [f (seq fs) ]
      (doto out
        (.append (str "<classpathentry  kind=\"lib\" path=\""
                      (NiceFPath f)
                      "\"/>" ))
        (.append sep)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genEclipseProj ""

  [^String app]

  (let [ec (Mkdirs (io/file (GetCwd) "eclipse.projfiles"))
        sb (StringBuilder.)
        cwd (GetCwd)
        lang "java"
        ulang (ucase lang) ]
    (FileUtils/cleanDirectory ec)
    (WriteOneFile (io/file ec ".project")
      (-> (ResStr (str "com/zotohlab/skaro/eclipse/"
                       lang
                       "/project.txt")
                  "utf-8")
          (cstr/replace "${APP.NAME}" app)
          (cstr/replace (str "${" ulang ".SRC}")
                        (NiceFPath (io/file cwd
                                            "src" "main" lang)))
          (cstr/replace "${TEST.SRC}"
                        (NiceFPath (io/file cwd
                                            "src" "test" lang)))))
    (scanJars (io/file (GetHomeDir) DN_DIST) sb)
    (scanJars (io/file (GetHomeDir) DN_LIB) sb)
    (scanJars (io/file cwd POD_CLASSES) sb)
    (scanJars (io/file cwd POD_LIB) sb)
    (WriteOneFile (io/file ec ".classpath")
      (-> (ResStr (str "com/zotohlab/skaro/eclipse/"
                       lang
                       "/classpath.txt")
                  "utf-8")
          (cstr/replace "${CLASS.PATH.ENTRIES}" (.toString sb))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnIDE "Generate IDE project files."

  [^Job j]

  (let [args (.getLastResult j)
        args (drop 1 args)]
    (if (and (> (count args) 1)
             (= "eclipse" (first args)))
      (genEclipseProj (second args))
      (throw (CmdHelpError.)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

