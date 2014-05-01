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

  comzotohlabscljc.tardis.etc.cmdline

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.tardis.core.climain :only [StartMain] ])
  (:use [comzotohlabscljc.tardis.etc.cli
         :only [CreateWeb CreateJetty CreateBasic
                AntBuildApp BundleApp RunAppBg
                *SKARO-WEBLANG*
                CreateSamples CreateDemo] ])
  (:use [comzotohlabscljc.i18n.resources :only [GetString] ])
  (:use [comzotohlabscljc.util.core
         :only [NiceFPath IsWindows? FlattenNil ConvLong ResStr] ])
  (:use [comzotohlabscljc.util.dates :only [AddMonths MakeCal] ])
  (:use [comzotohlabscljc.util.meta :only [] ])
  (:use [comzotohlabscljc.util.str :only [nsb hgl? strim] ])
  (:use [comzotohlabscljc.util.cmdline :only [MakeCmdSeqQ CliConverse] ])
  (:use [comzotohlabscljc.crypto.codec :only [CreateStrongPwd Pwdify] ])
  (:use [comzotohlabscljc.crypto.core
         :only [AssertJce PEM_CERT MakeSSv1PKCS12 MakeCsrReq] ])
  (:use [comzotohlabscljc.tardis.core.constants])
  (:use [comzotohlabscljc.util.ini :only [ParseInifile] ])
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (com.zotohlabs.gallifrey.etc CmdHelpError))
  (:import (org.apache.commons.io FileUtils))
  (:import (java.util Calendar ResourceBundle Properties Date))
  (:import (java.io File))
  (:import (com.zotohlabs.frwk.io IOUtils)))

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
(defn- getHomeDir ""

  ^File
  []

  *SKARO-HOME-DIR*)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getBuildFilePath ""

  ^String
  []

  (NiceFPath (File. (File. (getHomeDir) (str DN_CFG "/app")) "ant.xml")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onCreateApp ""

  [ & args]

  (let [ hhh (getHomeDir)
         hf (ParseInifile (File. hhh (str DN_CONF "/" (name K_PROPS))))
         wlg (.optString hf "webdev" "lang" "js")
         ;; treat as domain e.g com.acme => app = acme
         app (nth args 2)
         t (re-matches #"^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)*" app)
         id (if (nil? t)
              nil
              (if (nil? (last t))
                (first t)
                (.substring ^String (last t) 1))) ]
    (binding [ *SKARO-WEBLANG* wlg]
      (if (nil? id)
        (throw (CmdHelpError.))
        (case (nth args 1)
          ("mvc" "web") (CreateWeb hhh id app)
          "jetty" (CreateJetty hhh id app)
          "basic" (CreateBasic hhh id app)
          (throw (CmdHelpError.)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onCreate ""

  [ & args]

  (if (< (count args) 3)
    (throw (CmdHelpError.))
    (apply onCreateApp args)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onBuild ""

  [ & args]

  (if (>= (count args) 2)
    (let [ appId (nth args 1)
           taskId (if (> (count args) 2) (nth args 2) "devmode") ]
      (AntBuildApp (getHomeDir) appId taskId))
    (throw (CmdHelpError.))

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onPodify ""

  [ & args]

  (if (> (count args) 1)
    (BundleApp (getHomeDir) (nth args 1))
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onTest ""

  [ & args]

  (if (> (count args) 1)
    (AntBuildApp (getHomeDir) (nth args 1) "test")
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onStart ""

  [ & args]

  (let [ s2 (if (> (count args) 1) (nth args 1) "") ]
    (cond
      (and (= s2 "bg") (IsWindows?))
      (RunAppBg (getHomeDir) true)

      :else
      (StartMain (NiceFPath (getHomeDir))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onDebug ""

  [ & args]

  (onStart args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onDemo ""

  [ & args]

  (if (> (count args) 1)
    (let [ s (nth args 1) h (getHomeDir) ]
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

  (let [ csr (make-csr-qs *SKARO-RSBUNDLE*)
         k (merge csr (make-key-qs *SKARO-RSBUNDLE*))
         rc (CliConverse k "cn") ]
    (when-not (nil? rc)
      (let [ dn (cstr/join "," (FlattenNil (map (fn [k]
                                   (let [ v (get rc k) ]
                                     (if (hgl? v)
                                      (str (cstr/upper-case (name k)) "=" v)
                                     nil)))
                                   [ :c :st :l :o :ou :cn ])) )
             ff (File. ^String (:fn rc))
             now (Date.) ]
        (println (str "DN entered: " dn))
        (MakeSSv1PKCS12
          now
          (.getTime (AddMonths (MakeCal now) (ConvLong (:months rc) 12)))
          dn
          (Pwdify (:pwd rc))
          (ConvLong (:size rc) 1024)
          ff)
        (println (str "Wrote file: " ff))))
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- csrfile ""

  []

  (let [ csr (make-csr-qs *SKARO-RSBUNDLE*)
         rc (CliConverse csr "cn") ]
    (when-not (nil? rc)
      (let [ dn (cstr/join "," (FlattenNil (map (fn [k]
                                   (let [ v (get rc k) ]
                                     (if (hgl? v)
                                      (str (cstr/upper-case (name k)) "=" v)
                                     nil)))
                                   [ :c :st :l :o :ou :cn ])) )
             [req pkey] (MakeCsrReq
                          (ConvLong (:size rc) 1024)
                          dn
                          PEM_CERT ) ]
        (println (str "DN entered: " dn))
        (let [ ff (File. (str (:fn rc) ".key")) ]
          (FileUtils/writeByteArrayToFile ff pkey)
          (println (str "Wrote file: " ff)))
        (let [ ff (File. (str (:fn rc) ".csr")) ]
          (FileUtils/writeByteArrayToFile ff req)
          (println (str "Wrote file: " ff))) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onGenerate ""

  [ & args]

  (let [ ok (if (> (count args) 1)
              (case (nth args 1)
                "password" (do (GeneratePassword 12) true)
                "serverkey" (do (keyfile) true)
                "csr" (do (csrfile) true)
                false)
              false) ]
    (when-not ok
      (throw (CmdHelpError.)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genHash ""

  [text]

  (let [ ^comzotohlabscljc.crypto.codec.Password p (Pwdify text) ]
    (println (.hashed p))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHash ""

  [ & args]

  (if (> (count args) 1)
    (genHash (nth args 1))
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- encrypt ""

  [pkey text]

  (let [ ^comzotohlabscljc.crypto.codec.Password p (Pwdify text pkey) ]
    (println (.encoded p))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onEncrypt ""

  [ & args]

  (if (> (count args) 2)
    (encrypt  (nth args 1) (nth args 2))
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- decrypt ""

  [pkey secret]

  (let [ ^comzotohlabscljc.crypto.codec.Password p (Pwdify secret pkey) ]
    (println (.text p))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onDecrypt ""

  [ & args]

  (if (> (count args) 2)
    (decrypt (nth args 1) (nth args 2))
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onTestJCE ""

  [ & args]

  (AssertJce)
  (println "JCE is OK."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onVersion ""

  [ & args]

  (let [ s (FileUtils/readFileToString (File. (getHomeDir) "VERSION") "utf-8") ]
    (if (hgl? s)
      (println s)
      (println "Unknown version."))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp ""

  [ & args]

  (throw (CmdHelpError.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanJars ""

  [^File dir ^StringBuilder out]

  (let [ sep (System/getProperty "line.separator")
         fs (IOUtils/listFiles dir "jar" false) ]
    (doseq [ f (seq fs) ]
      (doto out
        (.append (str "<classpathentry  kind=\"lib\" path=\""
                      (NiceFPath f) "\"/>" ))
        (.append sep)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genEclipseProj ""

  [app]

  (let [ cwd (File. (getHomeDir) (str DN_BOXX "/" app))
         ec (File. cwd "eclipse.projfiles")
         sb (StringBuilder.)
         ;;lang "scala"
         lang "java"
         ulang (cstr/upper-case lang) ]
    (.mkdirs ec)
    (FileUtils/cleanDirectory ec)
    (FileUtils/writeStringToFile (File. ec ".project")
      (-> (rc-str (str "com/zotohlabs/gallifrey/eclipse/" lang "/project.txt") "utf-8")
          (StringUtils/replace "${APP.NAME}" app)
          (StringUtils/replace (str "${" ulang ".SRC}")
               (nice-fpath (File. cwd (str "src/main/" lang))))
          (StringUtils/replace "${TEST.SRC}"
               (nice-fpath (File. cwd (str "src/test/" lang)))))
      "utf-8")
    (scanJars (File. (getHomeDir) ^String DN_DIST) sb)
    (scanJars (File. (getHomeDir) ^String DN_LIB) sb)
    (scanJars (File. cwd ^String POD_CLASSES) sb)
    (scanJars (File. cwd ^String POD_LIB) sb)
    (FileUtils/writeStringToFile (File. ec ".classpath")
      (-> (rc-str (str "com/zotohlabs/gallifrey/eclipse/" lang "/classpath.txt") "utf-8")
          (StringUtils/replace "${CLASS.PATH.ENTRIES}" (.toString sb)))
      "utf-8")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onIDE ""

  [ & args]

  (if (> (count args) 2)
    (case (nth args 1)
      "eclipse" (genEclipseProj (nth args 2))
      (throw (CmdHelpError.)))
    (throw (CmdHelpError.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private _ARGS {
  :new #'onCreate
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
  :help #'onHelp
  })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn eval-command ""

  [home rcb & args]

  (let [ v (get _ARGS (keyword (first args))) ]
    (when (nil? v)
      (throw (CmdHelpError.)))
    (binding [ *SKARO-HOME-DIR* home
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


