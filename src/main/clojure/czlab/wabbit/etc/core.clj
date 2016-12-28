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

  czlab.wabbit.etc.core

  (:require [czlab.xlib.resources :refer [rstr]]
            [czlab.xlib.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io])

  (:use [czlab.xlib.format]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str])

  (:import [org.apache.commons.lang3.text StrSubstitutor]
           [czlab.wabbit.etc Component Gist ConfigError]
           [org.apache.commons.io FileUtils]
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

(def ^String c-verprops "czlab/czlab-wabbit/version.properties")
(def ^String c-rcb "czlab.wabbit.etc/Resources")

(def ^:private ^String sys-devid-pfx "system.####")
(def ^:private ^String sys-devid-sfx "####")

(def sys-devid-regex #"system::[0-9A-Za-z_\-\.]+" )
(def shutdown-devid #"system::kill_9" )
(def ^String dft-dbid "default")

(def ^String shutdown-uri "/kill9")
(def ^String pod-protocol  "pod:" )
(def ^String meta-inf  "META-INF" )
(def ^String web-inf  "WEB-INF" )

(def ^String dn-target "target")
(def ^String dn-build "build")

(def ^String dn-classes "classes" )
(def ^String dn-bin "bin" )
(def ^String dn-conf "conf" )
(def ^String dn-lib "lib" )

(def ^String dn-cfgapp "etc/app" )
(def ^String dn-cfgweb "etc/web" )
(def ^String dn-etc "etc" )

(def ^String dn-rcprops  "Resources_en.properties" )
(def ^String dn-templates  "templates" )

(def ^String dn-logs "logs" )
(def ^String dn-tmp "tmp" )
(def ^String dn-dbs "dbs" )
(def ^String dn-dist "dist" )
(def ^String dn-views  "htmls" )
(def ^String dn-pages  "pages" )
(def ^String dn-patch "patch" )
(def ^String dn-media "media" )
(def ^String dn-scripts "scripts" )
(def ^String dn-styles "styles" )
(def ^String dn-pub "public" )

(def ^String web-classes  (str web-inf  "/" dn-classes))
(def ^String web-lib  (str web-inf  "/" dn-lib))
(def ^String web-log  (str web-inf  "/logs"))
(def ^String web-xml  (str web-inf  "/web.xml"))

(def ^String mn-rnotes (str meta-inf "/" "RELEASE-NOTES.txt"))
(def ^String mn-readme (str meta-inf "/" "README.md"))
(def ^String mn-notes (str meta-inf "/" "NOTES.txt"))
(def ^String mn-lic (str meta-inf "/" "LICENSE.txt"))

(def ^String pod-cf  "pod.conf" )
(def ^String cfg-pod-cf  (str dn-conf  "/"  pod-cf ))

(def jslot-flatline :____flatline)
(def evt-opts :____eventoptions)
(def jslot-last :____lastresult)
(def jslot-cred :credential)
(def jslot-user :principal)

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
  "Initialize component" ^Component (fn [a _] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init :default [co _]
  (log/warn "No init defined for comp: %s" co) co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getHomeDir
  ""
  ^File [] (io/file (sysProp "wabbit.home.dir")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getProcDir
  ""
  ^File [] (io/file (sysProp "wabbit.proc.dir")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn expandSysProps
  "Expand any system properties found inside the string value"
  ^String
  [^String value]
  (if (nichts? value)
    value
    (StrSubstitutor/replaceSystemProperties value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn expandEnvVars
  "Expand any env-vars found inside the string value"
  ^String
  [^String value]
  (if (nichts? value)
    value
    (.replace (StrSubstitutor. (System/getenv)) value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn expandVars
  "Replaces all system & env variables in the value"
  ^String
  [^String value]
  (if (nichts? value)
    value
    (-> (expandSysProps value)
        (expandEnvVars ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn readConf
  "Parse a edn configuration file"
  {:tag String}
  ([podDir confile]
   (readConf (io/file podDir dn-conf confile)))
  ([file]
   (doto->>
     (-> (io/file file)
         (changeContent
           #(-> (cs/replace %
                            "${pod.dir}" "${wabbit.proc.dir}")
                (expandVars ))))
     (log/debug "[%s]\n%s" file))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the directory is readable & writable.
(defn ^:no-doc precondDir
  "Assert folder(s) are read-writeable?"
  [f & dirs]
  (doseq [d (cons f dirs)]
    (test-cond (rstr (I18N/base)
                     "dir.no.rw" d)
               (dirReadWrite? d)))
  true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the file is readable
(defn ^:no-doc precondFile
  "Assert file(s) are readable?"
  [ff & files]
  (doseq [f (cons ff files)]
    (test-cond (rstr (I18N/base)
                     "file.no.r" f)
               (fileRead? f)))
  true)

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
         (-> (cs/replace s
                         "${pod.dir}"
                         "${wabbit.proc.dir}")
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
;;
(defn deleteDir
  ""
  [dir]
  (try! (FileUtils/deleteDirectory (io/file dir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn cleanDir
  ""
  [dir]
  (try! (FileUtils/cleanDirectory (io/file dir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


