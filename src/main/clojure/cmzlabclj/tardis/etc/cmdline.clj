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

(ns ^{ :doc ""
       :author "kenl" }

  cmzlabclj.tardis.etc.cmdline

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.i18n.resources :only [GetString] ]
        [cmzlabclj.tardis.etc.climain :only [StartMain] ]
        [cmzlabclj.nucleus.util.guids :only [NewUUid NewWWid] ]
        [cmzlabclj.tardis.etc.cli]
        [cmzlabclj.nucleus.util.core
         :only [notnil? NiceFPath IsWindows? FlattenNil ConvLong ResStr] ]
        [cmzlabclj.nucleus.util.dates :only [AddMonths MakeCal] ]
        [cmzlabclj.nucleus.util.meta]
        [cmzlabclj.nucleus.util.str :only [nsb hgl? strim] ]
        [cmzlabclj.nucleus.util.cmdline :only [MakeCmdSeqQ CLIConverse] ]
        [cmzlabclj.nucleus.crypto.codec :only [CreateStrongPwd Pwdify] ]
        [cmzlabclj.nucleus.crypto.core
         :only [AES256_CBC AssertJce PEM_CERT
                DbgProvider MakeKeypair
                MakeSSv1PKCS12 MakeCsrReq] ]
        [cmzlabclj.tardis.core.constants]
        [cmzlabclj.nucleus.util.ini :only [ParseInifile] ])

  (:import  [java.util Map Calendar ResourceBundle Properties Date]
            [org.apache.commons.lang3 StringUtils]
            [com.zotohlab.gallifrey.etc CmdHelpError]
            [com.zotohlab.frwk.crypto PasswordAPI]
            [org.apache.commons.io FileUtils]
            [org.apache.commons.codec.binary Hex]
            [java.io File]
            [java.security KeyPair]
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
;;
(defn- onCreateApp ""

  [& args]

  (let [hhh (getHomeDir) hf (ParseInifile (File. hhh
                                                  (str DN_CONF
                                                       "/"
                                                       (name K_PROPS))))
        wlg (.optString hf "webdev" "lang" "js")
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
;;
(defn- onCreate ""

  [& args]

  (if (< (count args) 3)
    (throw (CmdHelpError.))
    (apply onCreateApp args)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
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
;;
(defn- onPodify ""

  [& args]

  (if (> (count args) 1)
    (BundleApp (getHomeDir) (nth args 1))
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onTest ""

  [& args]

  (if (> (count args) 1)
    (AntBuildApp (getHomeDir) (nth args 1) "test")
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onStart ""

  [& args]

  (let [s2 (if (> (count args) 1) (nth args 1) "") ]
    (cond
      (and (= s2 "bg") (IsWindows?))
      (RunAppBg (getHomeDir) true)

      :else
      (StartMain (NiceFPath (getHomeDir))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onDebug ""

  [& args]

  (onStart args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
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
(defn- generatePassword ""

  [len]

  (println (nsb (CreateStrongPwd len))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genKeyPair ""

  [^String lenStr]

  ;;(DbgProvider java.lang.System/out)
  (let [kp (MakeKeypair "RSA" (ConvLong lenStr 256))
        pk (.getEncoded (.getPrivate kp))
        pu (.getEncoded (.getPublic kp)) ]
    (println "privatekey-bytes= " (Hex/encodeHexString pk))
    (println "publickey-bytes = " (Hex/encodeHexString pu))
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
(defn- make-csr-qs ""

  [^ResourceBundle rcb]

  { "fname"
    (MakeCmdSeqQ "fname" (GetString rcb "cmd.save.file")
                   "" "csr-req" true
                   (fn [a ^Map ps] (do (.put ps "fn" a) "")))

    "size"
    (MakeCmdSeqQ "size" (GetString rcb "cmd.key.size")
                   "" "1024" true
                   (fn [a ^Map ps] (do (.put ps "size" a) "fname")))
    "c"
    (MakeCmdSeqQ "c" (GetString rcb "cmd.dn.c")
                   "" "US" true
                   (fn [a ^Map ps] (do (.put ps "c" a) "size")))

    "st"
    (MakeCmdSeqQ "st" (GetString rcb "cmd.dn.st")
                   "" "" true
                   (fn [a ^Map ps] (do (.put ps "st" a) "c")))

    "loc"
    (MakeCmdSeqQ "loc" (GetString rcb "cmd.dn.loc")
                   "" "" true
                   (fn [a ^Map ps] (do (.put ps "l" a) "st")))

    "o"
    (MakeCmdSeqQ "o" (GetString rcb "cmd.dn.org")
                   "" "" true
                   (fn [a ^Map ps] (do (.put ps "o" a) "loc")))

    "ou"
    (MakeCmdSeqQ "ou" (GetString rcb "cmd.dn.ou")
                   "" "" true
                   (fn [a ^Map ps] (do (.put ps "ou" a) "o")))

    "cn"
    (MakeCmdSeqQ "cn" (GetString rcb "cmd.dn.cn")
                   "" "" true
                   (fn [a ^Map ps] (do (.put ps "cn" a) "ou")))
  } )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-key-qs ""

  [^ResourceBundle rcb]

  {
    "fname"
    (MakeCmdSeqQ "fname" (GetString rcb "cmd.save.file")
                     "" "test.p12" true
                     (fn [a ^Map ps] (do (.put ps "fn" a) "")))

    "pwd"
    (MakeCmdSeqQ "pwd" (GetString rcb "cmd.key.pwd")
                     "" "" true
                     (fn [a ^Map ps] (do (.put ps "pwd" a) "fname")))

    "duration"
    (MakeCmdSeqQ "duration" (GetString rcb "cmd.key.duration")
                     "" "12" true
                     (fn [a ^Map ps] (do (.put ps "months" a) "pwd")))

    "size"
    (MakeCmdSeqQ "size" (GetString rcb "cmd.key.size")
                   "" "1024" true
                   (fn [a ^Map ps] (do (.put ps "size" a) "duration")))

   } )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- keyfile ""

  []

  (let [k (merge (make-csr-qs *SKARO-RSBUNDLE*)
                  (make-key-qs *SKARO-RSBUNDLE*))
        rc (CLIConverse k "cn") ]
    (when-not (nil? rc)
      (let [ssn (map #(let [v (get rc %) ]
                        (if (hgl? v)
                          (str (cstr/upper-case (name %))
                               "="
                               v)))
                     [ :c :st :l :o :ou :cn ])
             dn (cstr/join "," (FlattenNil ssn))
             ff (File. ^String (:fn rc))
             now (Date.) ]
        (println (str "DN entered: " dn))
        (MakeSSv1PKCS12
          dn
          (Pwdify (:pwd rc))
          ff
          {:keylen (ConvLong (:size rc) 1024)
           :start now
           :end (-> (MakeCal now)
                    (AddMonths (ConvLong (:months rc) 12))
                    (.getTime))
          })
        (println (str "Wrote file: " ff))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- csrfile ""

  []

  (let [rc (CLIConverse (make-csr-qs *SKARO-RSBUNDLE*) "cn") ]
    (when-not (nil? rc)
      (let [ssn (map #(let [v (get rc %) ]
                        (if (hgl? v)
                          (str (cstr/upper-case (name %))
                               "=" v)))
                     [ :c :st :l :o :ou :cn ])
            dn (cstr/join "," (FlattenNil ssn))
            [req pkey]
            (MakeCsrReq (ConvLong (:size rc) 1024)
                        dn
                        PEM_CERT) ]
        (println (str "DN entered: " dn))
        (let [ff (File. (str (:fn rc) ".key")) ]
          (FileUtils/writeByteArrayToFile ff pkey)
          (println (str "Wrote file: " ff)))
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

  (let [s (FileUtils/readFileToString (File. (getHomeDir) "VERSION") "utf-8") ]
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

  [app]

  (let [cwd (File. (getHomeDir) (str DN_BOXX "/" app))
        sb (StringBuilder.)
        ec (doto (File. cwd "eclipse.projfiles")
             (.mkdirs))
         ;;lang "scala"
        lang "java"
        ulang (cstr/upper-case lang) ]
    (FileUtils/cleanDirectory ec)
    (FileUtils/writeStringToFile (File. ec ".project")
      (-> (ResStr (str "com/zotohlab/gallifrey/eclipse/" lang "/project.txt") "utf-8")
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
;;
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

  (let [v (get _ARGS (keyword (first args))) ]
    (when (nil? v)
      (throw (CmdHelpError.)))
    (binding [*SKARO-HOME-DIR* home
              *SKARO-RSBUNDLE* rcb]
      (apply v args))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetCommands ""

  []

  (set (keys _ARGS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private cmdline-eof nil)

