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
      :author "Kenneth Leung"}

  czlab.wabbit.sys.core

  (:require [czlab.xlib.resources :refer [rstr]]
            [czlab.xlib.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io])

  (:use [czlab.xlib.format]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str])

  (:import [czlab.wabbit.server ConfigError PodGist Component]
           [org.apache.commons.lang3.text StrSubstitutor]
           [czlab.xlib
            Versioned
            Muble
            I18N
            Hierarchial
            Identifiable]
           [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^String C_VERPROPS "czlab/czlab-wabbit/version.properties")
(def ^String C_RCB "czlab.wabbit.etc/Resources")

(def ^:private ^String SYS_DEVID_PFX "system.####")
(def ^:private ^String SYS_DEVID_SFX "####")

(def SYS_DEVID_REGEX #"system::[0-9A-Za-z_\-\.]+" )
(def SHUTDOWN_DEVID #"system::kill_9" )
(def ^String DEF_DBID "default")

(def ^String SHUTDOWN_URI "/kill9")
(def ^String POD_PROTOCOL  "pod:" )
(def ^String META_INF  "META-INF" )
(def ^String WEB_INF  "WEB-INF" )

(def ^String DN_TARGET "target")
(def ^String DN_BUILD "build")

(def ^String DN_CLASSES "classes" )
(def ^String DN_BIN "bin" )
(def ^String DN_CONF "conf" )
(def ^String DN_LIB "lib" )

(def ^String DN_CFGAPP "etc/app" )
(def ^String DN_CFGWEB "etc/web" )
(def ^String DN_ETC "etc" )

(def ^String DN_RCPROPS  "Resources_en.properties" )
(def ^String DN_TEMPLATES  "templates" )

(def ^String DN_LOGS "logs" )
(def ^String DN_TMP "tmp" )
(def ^String DN_DBS "dbs" )
(def ^String DN_DIST "dist" )
(def ^String DN_VIEWS  "htmls" )
(def ^String DN_PAGES  "pages" )
(def ^String DN_PATCH "patch" )
(def ^String DN_MEDIA "media" )
(def ^String DN_SCRIPTS "scripts" )
(def ^String DN_STYLES "styles" )
(def ^String DN_PUB "public" )

(def ^String POD_CF  "pod.conf" )
(def ^String ENV_CF  "env.conf" )
(def ^String WEB_CF  "web.conf" )

(def ^String WEB_CLASSES  (str WEB_INF  "/" DN_CLASSES))
(def ^String WEB_LIB  (str WEB_INF  "/" DN_LIB))
(def ^String WEB_LOG  (str WEB_INF  "/logs"))
(def ^String WEB_XML  (str WEB_INF  "/web.xml"))

(def ^String MN_RNOTES (str META_INF "/" "RELEASE-NOTES.txt"))
(def ^String MN_README (str META_INF "/" "README.md"))
(def ^String MN_NOTES (str META_INF "/" "NOTES.txt"))
(def ^String MN_LIC (str META_INF "/" "LICENSE.txt"))

(def ^String CFG_POD_CF  (str DN_CONF  "/"  POD_CF ))
(def ^String CFG_ENV_CF  (str DN_CONF  "/"  ENV_CF ))
(def ^String CFG_WEB_CF  (str DN_CONF  "/"  WEB_CF ))

(def K_PLUGINS :plugins)

(def JS_FLATLINE :____flatline)
(def EV_OPTS :____eventoptions)
(def JS_LAST :____lastresult)
(def JS_CRED :credential)
(def JS_USER :principal)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro gtid "typeid of component" [obj] `(:typeid (meta ~obj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro logcomp
  ""
  [pfx co]
  `(log/info "%s: '%s'# '%s'" ~pfx (gtid ~co) (.id ~co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti comp->init
  "Initialize component" ^Component (fn [a arg] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init :default [co arg] co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn readConf
  "Parse a edn configuration file"
  ^String
  [^File podDir ^String confile]
  (doto->>
    (-> (io/file podDir DN_CONF confile)
        (changeContent
          #(cs/replace %
                       "${appdir}"
                       (fpath podDir))))
    (log/debug "[%s]\n%s" confile)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn expandSysProps
  "Expand any system properties found inside the string value"
  ^String
  [^String value]
  (if-not (hgl? value)
    value
    (StrSubstitutor/replaceSystemProperties value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn expandEnvVars
  "Expand any env-vars found inside the string value"
  ^String
  [^String value]
  (if-not (hgl? value)
    value
    (.replace (StrSubstitutor. (System/getenv)) value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn expandVars
  "Replaces all system & env variables in the value"
  ^String
  [^String value]
  (if-not (hgl? value)
    value
    (-> (expandSysProps value)
        (expandEnvVars ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the directory is readable & writable.
(defn ^:no-doc precondDir
  "Assert folder(s) are read-writeable?"
  [f & dirs]
  (doseq [d (cons f dirs)]
    (test-cond (rstr (I18N/base)
                     "dir.no.rw" d)
               (dirReadWrite? d))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the file is readable
(defn ^:no-doc precondFile
  "Assert file(s) are readable?"
  [ff & files]
  (doseq [f (cons ff files)]
    (test-cond (rstr (I18N/base)
                     "file.no.r" f)
               (fileRead? f))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ^:no-doc maybeDir
  "If the key maps to a File"
  ^File
  [^Muble m kn]
  (let [v (.getv m kn)]
    (condp instance? v
      String (io/file v)
      File v
      (trap! ConfigError (rstr (I18N/base)
                               "wabbit.no.dir" kn)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn podMeta
  "Create metadata for an application bundle"
  ^PodGist
  [^String pod conf urlToPod]
  {:pre [(map? conf)]}
  (let [info
        (merge {:version "1.0"
                :name pod
                :main ""}
               (:info conf)
               {:path urlToPod})
        pid (str (:name info)
                 "#" (seqint2))
        impl (muble<> info)]
    (log/info "pod-meta:\n%s" (.impl impl))
    (with-meta
      (reify
        PodGist
        (version [_] (:version info))
        (id [_] pid)
        (getx [_] impl))
      {:typeid  ::PodGist})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn slurpXXXConf
  "Parse config file"
  ([podDir conf] (slurpXXXConf podDir conf false))
  ([podDir conf expVars?]
   (let [f (io/file podDir conf)
         s (str "{\n"
                (slurpUtf8 f) "\n}")]
     (->
       (if expVars?
         (-> (cs/replace s "${appdir}" (fpath podDir))
             (expandVars))
         s)
       (readEdn )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn spitXXXConf
  "Write out config file"
  [podDir conf cfgObj]
  (let [f (io/file podDir conf)
        s (strim (writeEdnStr cfgObj))]
    (->>
      (if (and (.startsWith s "{")
               (.endsWith s "}"))
        (-> (drophead s 1)
            (droptail 1))
        s)
      (spitUtf8 f))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


