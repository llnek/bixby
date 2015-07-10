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

  czlabclj.tardis.etc.cmd1

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.cmdline :only [MakeCmdSeqQ CLIConverse]]
        [czlabclj.xlib.crypto.codec :only [CreateStrongPwd Pwdify]]
        [czlabclj.xlib.util.guids :only [NewUUid NewWWid]]
        [czlabclj.xlib.i18n.resources :only [RStr]]

        [czlabclj.xlib.util.dates :only [AddMonths MakeCal]]
        [czlabclj.xlib.util.str :only [ucase nsb hgl? strim]]
        [czlabclj.tardis.etc.boot]
        [czlabclj.tardis.etc.cmd2]
        [czlabclj.xlib.util.meta]

        [czlabclj.xlib.util.core
        :only
        [notnil?
         NiceFPath
         IsWindows?
         Stringify
         FlattenNil
         ConvLong
         ResStr]]
        [czlabclj.xlib.util.format :only [ReadEdn]]
        [czlabclj.xlib.util.files
        :only
        [ReadOneFile Mkdirs WriteOneFile]]

        [czlabclj.xlib.crypto.core
        :only
        [AES256_CBC
         AssertJce
         PEM_CERT
         ExportPublicKey
         ExportPrivateKey
         DbgProvider
         MakeKeypair
         MakeSSv1PKCS12
         MakeCsrReq]]

        [czlabclj.tardis.core.consts])

  (:import  [java.util Map Calendar ResourceBundle Properties Date]
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
;; some globals
(def SKARO-RSBUNDLE (atom nil))
(def SKARO-HOME-DIR (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResBdl "Return the system resource bundle."

  ^ResourceBundle
  []

  @SKARO-RSBUNDLE)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetHomeDir "Return the home directory."

  ^File
  []

  @SKARO-HOME-DIR)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;not used
(defn- getBuildFilePath ""

  ^String
  []

  (NiceFPath (io/file (GetHomeDir) DN_CFG "app" "build.xml")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Create a new app template.
(defn- onCreateApp ""

  [& args]

  (let [t (re-matches #"^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)*"
                      (nth args 2))
        hhh (GetHomeDir)
        hf (ReadEdn (io/file hhh DN_CONF (name K_PROPS)))
        wlg (or (:lang (:webdev hf)) "js")
        ;; treat as domain e.g com.acme => app = acme
        ;; regex gives ["com.acme" ".acme"]
        id (when-not (nil? t)
             (if-let [tkn (last t) ]
               (.substring ^String tkn 1)
               (first t))) ]
    (when (nil? id) (throw (CmdHelpError.)))
    (binding [*SKARO-WEBLANG* wlg]
      (let [app (nth args 2)]
        (case (nth args 1)
          ("mvc" "web")
          (CreateNetty hhh id app)

          "jetty"
          (CreateJetty hhh id app)

          "basic"
          (CreateBasic hhh id app)

          (throw (CmdHelpError.)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new app?
(defn OnCreate "Create a new app."

  [^Job j]

  (when-let [args (.getLastResult j)]
    (if (< (count args) 3)
      (throw (CmdHelpError.))
      (apply onCreateApp args))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe build an app?
(defn OnBuild "Build the app."

  [^Job j]

  (when-let [args (.getLastResult j)]
    (if (>= (count args) 2)
      (let [appId (nth args 1)
            taskId (if (> (count args) 2)
                     (nth args 2)
                     "dev") ]
        (ExecBootScript (GetHomeDir) appId taskId))
      (throw (CmdHelpError.)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe compress and package an app?
(defn OnPodify "Package the app."

  [^Job j]

  (when-let [args (.getLastResult j)]
    (if (> (count args) 1)
      (BundleApp (GetHomeDir) (nth args 1))
      (throw (CmdHelpError.)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe run tests on an app?
(defn OnTest "Test the app."

  [^Job j]

  (when-let [args (.getLastResult j)]
    (if (> (count args) 1)
      (ExecBootScript (GetHomeDir) (nth args 1) "tst")
      (throw (CmdHelpError.)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe start the server?
(defn OnStart "Start and run the app."

  [^Job j]

  (let [cz "czlabclj.tardis.impl.climain.StartViaCLI"
        args (.getLastResult j)
        s2 (if (> (count args) 1)
             (nth args 1)
             "")
        home (GetHomeDir)]
    (if
      ;; background job is handled differently on windows
      (and (= s2 "bg") (IsWindows?))
      (RunAppBg home true)
      ;;else
      (when-let [^CliMain m (MakeObj cz)]
        (.run m (object-array [ home ]))))
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe run in debug mode?
(defn OnDebug "Debug the app."

  [j]

  (OnStart j))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe generate some demo apps?
(defn OnDemo "Generate demo apps."

  [^Job j]

  (when-let [args (.getLastResult j)]
    (if (and (> (count args) 1)
             (= "samples" (nth args 1)))
      (PublishSamples (GetHomeDir))
      (throw (CmdHelpError.)))
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
      (println (str "Wrote file: " ff))
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- csrfile "Maybe generate a CSR?"

  []

  (if-let [res (promptQuestions (make-csr-qs @SKARO-RSBUNDLE) "cn") ]
    (let [^String dn (first res)
          rc (last res)
          [req pkey]
          (MakeCsrReq (ConvLong (:size rc) 1024)
                      dn
                      PEM_CERT) ]
      (println (str "DN entered: " dn))
      (let [f1 (File. (str (:fn rc) ".key"))
            f2 (File. (str (:fn rc) ".csr")) ]
        (WriteOneFile f1 pkey)
        (println (str "Wrote file: " f1))
        (WriteOneFile f2 req)
        (println (str "Wrote file: " f2))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnGenerate "Generate a bunch of stuff."

  [^Job j]

  (when-let [args (.getLastResult j)]
    (when-not (if (> (count args) 1)
                (condp = (nth args 1)
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

  (when-let [args (.getLastResult j)]
    (if (> (count args) 1)
      (genHash (nth args 1))
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

  (when-let [args (.getLastResult j)]
    (if (> (count args) 2)
      (encrypt (nth args 1) (nth args 2))
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

  (when-let [args (.getLastResult j)]
    (if (> (count args) 2)
      (decrypt (nth args 1) (nth args 2))
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

  ;;(log/debug "HomeDir = " (GetHomeDir))
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

  (let [cwd (io/file (GetHomeDir) DN_BOXX app)
        sb (StringBuilder.)
        ec (Mkdirs (io/file cwd "eclipse.projfiles"))
         ;;lang "scala"
        lang "java"
        ulang (ucase lang) ]
    (FileUtils/cleanDirectory ec)
    (WriteOneFile (io/file ec ".project")
      (-> (ResStr (str "com/zotohlab/skaro/eclipse/"
                       lang
                       "/project.txt")
                  "utf-8")
          (.replace "${APP.NAME}" app)
          (.replace (str "${" ulang ".SRC}")
                    (NiceFPath (io/file cwd "src/main/" lang)))
          (.replace "${TEST.SRC}"
                    (NiceFPath (io/file cwd "src" "test" lang)))))
    (scanJars (io/file (GetHomeDir) DN_DIST) sb)
    (scanJars (io/file (GetHomeDir) DN_LIB) sb)
    (scanJars (io/file cwd POD_CLASSES) sb)
    (scanJars (io/file cwd POD_LIB) sb)
    (WriteOneFile (io/file ec ".classpath")
      (-> (ResStr (str "com/zotohlab/skaro/eclipse/"
                       lang
                       "/classpath.txt")
                  "utf-8")
          (.replace "${CLASS.PATH.ENTRIES}" (.toString sb))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnIDE "Generate IDE project files."

  [^Job j]

  (when-let [args (.getLastResult j)]
    (if (and (> (count args) 2)
             (= "eclipse" (nth args 1)))
      (genEclipseProj (nth args 2))
      (throw (CmdHelpError.)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private cmd1-eof nil)

