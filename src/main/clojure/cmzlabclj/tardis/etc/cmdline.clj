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

(ns ^{:doc ""
      :author "kenl" }

  cmzlabclj.tardis.etc.cmdline

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.i18n.resources :only [GetString] ]
        [cmzlabclj.tardis.etc.climain :only [StartMain] ]
        [cmzlabclj.nucleus.util.guids :only [NewUUid NewWWid] ]
        [cmzlabclj.tardis.etc.cli]
        [cmzlabclj.nucleus.util.core
         :only [notnil? NiceFPath IsWindows? Stringify
                ternary FlattenNil ConvLong ResStr] ]
        [cmzlabclj.nucleus.util.dates :only [AddMonths MakeCal] ]
        [cmzlabclj.nucleus.util.meta]
        [cmzlabclj.nucleus.util.files :only [ReadEdn ReadOneFile] ]
        [cmzlabclj.nucleus.util.str :only [nsb hgl? strim] ]
        [cmzlabclj.nucleus.util.cmdline :only [MakeCmdSeqQ CLIConverse] ]
        [cmzlabclj.nucleus.crypto.codec :only [CreateStrongPwd Pwdify] ]
        [cmzlabclj.nucleus.crypto.core
         :only [AES256_CBC AssertJce PEM_CERT
                ExportPublicKey ExportPrivateKey
                DbgProvider MakeKeypair
                MakeSSv1PKCS12 MakeCsrReq] ]
        [cmzlabclj.tardis.core.constants])

  (:import  [java.util Map Calendar ResourceBundle Properties Date]
            [org.apache.commons.lang3 StringUtils]
            [com.zotohlab.gallifrey.etc CmdHelpError]
            [com.zotohlab.frwk.crypto PasswordAPI]
            [org.apache.commons.io FileUtils]
            [org.apache.commons.codec.binary Hex]
            [java.io File]
            [java.security KeyPair PublicKey PrivateKey]
            [com.zotohlab.frwk.io IOUtils]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:dynamic *SKARO-RSBUNDLE* nil)
(def ^:dynamic *SKARO-HOME-DIR* "")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- rcb ""

  ^ResourceBundle
  []

  *SKARO-RSBUNDLE*)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getHomeDir ""

  ^File
  []

  *SKARO-HOME-DIR*)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getBuildFilePath ""

  ^String
  []

  (NiceFPath (File. (File. (getHomeDir)
                           (str DN_CFG "/app"))
                    "build.xml")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Create a new app template.
(defn- onCreateApp ""

  [& args]

  (let [hhh (getHomeDir)
        hf (ReadEdn (File. hhh
                           (str DN_CONF "/" (name K_PROPS))))
        wlg (ternary (:lang (:webdev hf)) "js")
        app (nth args 2)
        t (re-matches #"^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)*" app)
         ;; treat as domain e.g com.acme => app = acme
         ;; regex gives ["com.acme" ".acme"]
        id (when (notnil? t)
             (if-let [tkn (last t) ]
               (.substring ^String tkn 1)
               (first t))) ]
    (binding [*SKARO-WEBLANG* wlg]
      (when (nil? id) (throw (CmdHelpError.)))
      (case (nth args 1)
        ("mvc" "web")
        (CreateWeb hhh id app)

        "jetty"
        (CreateJetty hhh id app)

        "basic"
        (CreateBasic hhh id app)

        (throw (CmdHelpError.))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new app?
(defn- onCreate ""

  [& args]

  (if (< (count args) 3)
    (throw (CmdHelpError.))
    (apply onCreateApp args)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe build an app?
(defn- onBuild ""

  [& args]

  (if (>= (count args) 2)
    (let [appId (nth args 1)
          taskId (if (> (count args) 2)
                   (nth args 2)
                   "devmode") ]
      (AntBuildApp (getHomeDir) appId taskId))
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe compress and package an app?
(defn- onPodify ""

  [& args]

  (if (> (count args) 1)
    (BundleApp (getHomeDir) (nth args 1))
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe run tests on an app?
(defn- onTest ""

  [& args]

  (if (> (count args) 1)
    (AntBuildApp (getHomeDir) (nth args 1) "test")
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe start the server?
(defn- onStart ""

  [& args]

  (let [s2 (if (> (count args) 1) (nth args 1) "") ]
    (cond
      ;; background job is handled differently on windows
      (and (= s2 "bg") (IsWindows?))
      (RunAppBg (getHomeDir) true)

      :else
      (StartMain (NiceFPath (getHomeDir))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe run in debug mode?
(defn- onDebug ""

  [& args]

  (onStart args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe generate some demo apps?
(defn- onDemo ""

  [& args]

  (if (> (count args) 1)
    (let [s (nth args 1) h (getHomeDir) ]
      (if (= "samples" s)
        (CreateSamples h)
        (CreateDemo h s)))
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- generatePassword "Generate a random password."

  [len]

  (println (nsb (CreateStrongPwd len))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genKeyPair "Generate a keypair."

  [^String lenStr]

  ;;(DbgProvider java.lang.System/out)
  (let [kp (MakeKeypair "RSA" (ConvLong lenStr 256))
        pvk (.getPrivate kp)
        puk (.getPublic kp)
        pk (.getEncoded pvk)
        pu (.getEncoded puk) ]
    ;;(println "privatekey-bytes= " (Hex/encodeHexString pk))
    ;;(println "publickey-bytes = " (Hex/encodeHexString pu))
    (println "privatekey=\n" (ExportPrivateKey pvk PEM_CERT))
    (println "publickey=\n" (ExportPublicKey puk PEM_CERT))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genWwid ""

  []

  (println (nsb (NewWWid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genGuid ""

  []

  (println (nsb (NewUUid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-csr-qs "Set of questions to capture the DN information."

  [^ResourceBundle rcb]

  { "fname"
    (MakeCmdSeqQ "fname" (GetString rcb "cmd.save.file")
                 "" "csr-req" true
                 #(do (.put ^Map %2 "fn" %1) ""))

    "size"
    (MakeCmdSeqQ "size" (GetString rcb "cmd.key.size")
                 "" "1024" true
                 #(do (.put ^Map %2 "size" %1) "fname"))

    "c"
    (MakeCmdSeqQ "c" (GetString rcb "cmd.dn.c")
                 "" "US" true
                 #(do (.put ^Map %2 "c" %1) "size"))

    "st"
    (MakeCmdSeqQ "st" (GetString rcb "cmd.dn.st")
                 "" "" true
                 #(do (.put ^Map %2 "st" %1) "c"))

    "loc"
    (MakeCmdSeqQ "loc" (GetString rcb "cmd.dn.loc")
                 "" "" true
                 #(do (.put ^Map %2 "l" %1) "st"))

    "o"
    (MakeCmdSeqQ "o" (GetString rcb "cmd.dn.org")
                 "" "" true
                 #(do (.put ^Map %2 "o" %1) "loc"))

    "ou"
    (MakeCmdSeqQ "ou" (GetString rcb "cmd.dn.ou")
                 "" "" true
                 #(do (.put ^Map %2 "ou" %1) "o"))

    "cn"
    (MakeCmdSeqQ "cn" (GetString rcb "cmd.dn.cn")
                 "" "" true
                 #(do (.put ^Map %2 "cn" %1) "ou"))
  })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-key-qs "Set of questions to save info to file."

  [^ResourceBundle rcb]

  {
    "fname"
    (MakeCmdSeqQ "fname" (GetString rcb "cmd.save.file")
                 "" "test.p12" true
                 #(do (.put ^Map %2 "fn" %1) ""))

    "pwd"
    (MakeCmdSeqQ "pwd" (GetString rcb "cmd.key.pwd")
                 "" "" true
                 #(do (.put ^Map %2 "pwd" %1) "fname"))

    "duration"
    (MakeCmdSeqQ "duration" (GetString rcb "cmd.key.duration")
                 "" "12" true
                 #(do (.put ^Map %2 "months" %1) "pwd"))

    "size"
    (MakeCmdSeqQ "size" (GetString rcb "cmd.key.size")
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
                        (str (cstr/upper-case (name %)) "=" v)))
                   [ :c :st :l :o :ou :cn ]) ]
      [(cstr/join "," (FlattenNil ssn)) rc])
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- keyfile "Maybe generate a server key file?"

  []

  (if-let [res (promptQuestions (merge (make-csr-qs *SKARO-RSBUNDLE*)
                                       (make-key-qs *SKARO-RSBUNDLE*))
                                "cn") ]
    (let [^String dn (first res)
          rc (last res)
          ff (File. ^String (:fn rc))
          now (Date.) ]
      (println (str "DN entered: " dn))
      (MakeSSv1PKCS12 dn
                      (Pwdify (:pwd rc))
                      ff
                      {:keylen (ConvLong (:size rc) 1024)
                      :start now
                      :end (-> (MakeCal now)
                               (AddMonths (ConvLong (:months rc) 12))
                               (.getTime)) })
      (println (str "Wrote file: " ff)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- csrfile "Maybe generate a CSR?"

  []

  (if-let [res (promptQuestions (make-csr-qs *SKARO-RSBUNDLE*) "cn") ]
    (let [^String dn (first res)
          rc (last res)
          [req pkey]
          (MakeCsrReq (ConvLong (:size rc) 1024)
                      dn
                      PEM_CERT) ]
      (println (str "DN entered: " dn))
      (let [ff (File. (str (:fn rc) ".key")) ]
        (FileUtils/writeByteArrayToFile ff pkey)
        (println (str "Wrote file: " ff))
        (let [ff (File. (str (:fn rc) ".csr")) ]
          (FileUtils/writeByteArrayToFile ff req)
          (println (str "Wrote file: " ff))) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onGenerate ""

  [& args]

  (when-not (if (> (count args) 1)
              (case (nth args 1)
                "keypair" (if (> (count args) 2)
                            (do (genKeyPair (nth args 2)) true)
                            false)
                "password" (do (generatePassword 12) true)
                "serverkey" (do (keyfile) true)
                "guid" (do (genGuid) true)
                "wwid" (do (genWwid) true)
                "csr" (do (csrfile) true)
                false)
              false)
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genHash ""

  [text]

  (let [^PasswordAPI p (Pwdify text) ]
    (println (.hashed p))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHash ""

  [& args]

  (if (> (count args) 1)
    (genHash (nth args 1))
    (throw (CmdHelpError.))
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
(defn- onEncrypt ""

  [& args]

  (if (> (count args) 2)
    (encrypt (nth args 1) (nth args 2))
    (throw (CmdHelpError.))
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
(defn- onDecrypt ""

  [& args]

  (if (> (count args) 2)
    (decrypt (nth args 1) (nth args 2))
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onTestJCE ""

  [& args]

  (AssertJce)
  (println "JCE is OK."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onVersion ""

  [& args]

  (let [s (ReadOneFile (File. (getHomeDir) "VERSION")) ]
    (if (hgl? s)
      (println s)
      (println "Unknown version."))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp ""

  [& args]

  (throw (CmdHelpError.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanJars ""

  [^File dir ^StringBuilder out]

  (let [sep (System/getProperty "line.separator")
        fs (IOUtils/listFiles dir "jar" false) ]
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

  (let [cwd (File. (getHomeDir) (str DN_BOXX "/" app))
        sb (StringBuilder.)
        ec (doto (File. cwd "eclipse.projfiles")
             (.mkdirs))
         ;;lang "scala"
        lang "java"
        ulang (cstr/upper-case lang) ]
    (FileUtils/cleanDirectory ec)
    (FileUtils/writeStringToFile (File. ec ".project")
      (-> (ResStr (str "com/zotohlab/gallifrey/eclipse/"
                       lang
                       "/project.txt")
                  "utf-8")
          (StringUtils/replace "${APP.NAME}" app)
          (StringUtils/replace (str "${" ulang ".SRC}")
                               (NiceFPath (File. cwd
                                                 (str "src/main/" lang))))
          (StringUtils/replace "${TEST.SRC}"
                               (NiceFPath (File. cwd
                                                 (str "src/test/" lang)))))
      "utf-8")
    (scanJars (File. (getHomeDir) ^String DN_DIST) sb)
    (scanJars (File. (getHomeDir) ^String DN_LIB) sb)
    (scanJars (File. cwd ^String POD_CLASSES) sb)
    (scanJars (File. cwd ^String POD_LIB) sb)
    (FileUtils/writeStringToFile (File. ec ".classpath")
      (-> (ResStr (str "com/zotohlab/gallifrey/eclipse/"
                       lang
                       "/classpath.txt")
                  "utf-8")
          (StringUtils/replace "${CLASS.PATH.ENTRIES}" (.toString sb)))
      "utf-8")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onIDE ""

  [& args]

  (if (and (> (count args) 2)
           (= "eclipse" (nth args 1)))
    (genEclipseProj (nth args 2))
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Like a map of function pointers.
(def ^:private _ARGS {:new #'onCreate
                      :ide #'onIDE
                      :build #'onBuild
                      :podify #'onPodify
                      :test #'onTest
                      :debug #'onDebug
                      :start #'onStart
                      :demo #'onDemo
                      :generate #'onGenerate
                      :encrypt #'onEncrypt
                      :decrypt #'onDecrypt
                      :hash #'onHash
                      :testjce #'onTestJCE
                      :version #'onVersion
                      :help #'onHelp})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn EvalCommand ""

  [home rcb & args]

  (if-let [v (get _ARGS (keyword (first args))) ]
    ;; set the global values for skaro-home and system resource bundle
    ;; for messages.
    (binding [*SKARO-HOME-DIR* home
              *SKARO-RSBUNDLE* rcb]
      (apply v args))
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetCommands ""

  []

  (set (keys _ARGS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private cmdline-eof nil)

