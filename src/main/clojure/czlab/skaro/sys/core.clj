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

  czlab.skaro.sys.core

  (:require
    [czlab.xlib.io :refer [changeContent readAsStr]]
    [czlab.xlib.logging :as log]
    [clojure.string :as cs]
    [clojure.java.io :as io])

  (:use [czlab.xlib.core]
        [czlab.xlib.str])

  (:import
    [org.apache.commons.lang3.text StrSubstitutor]
    [czlab.skaro.server Component]
    [czlab.xlib
     Hierarchial
     Muble
     Identifiable
     Versioned]
    [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^String C_VERPROPS "czlab/czlab-skaro/version.properties")
(def ^String C_RCB "czlab.skaro.etc/Resources")

(def ^:private ^String SYS_DEVID_PFX "system.####")
(def ^:private ^String SYS_DEVID_SFX "####")

(def ^String SYS_DEVID_REGEX #"system::[0-9A-Za-z_\-\.]+" )
(def ^String SHUTDOWN_DEVID #"system::kill_9" )
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

(def ^String DN_BOXX "apps" )
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

(def ^String APP_CF  "appconfig.conf" )
(def ^String ENV_CF  "env.conf" )

(def ^String WEB_CLASSES  (str WEB_INF  "/" DN_CLASSES))
(def ^String WEB_LIB  (str WEB_INF  "/" DN_LIB))
(def ^String WEB_LOG  (str WEB_INF  "/logs"))
(def ^String WEB_XML  (str WEB_INF  "/web.xml"))

(def ^String MN_RNOTES (str META_INF "/" "RELEASE-NOTES.txt"))
(def ^String MN_README (str META_INF "/" "README.md"))
(def ^String MN_NOTES (str META_INF "/" "NOTES.txt"))
(def ^String MN_LIC (str META_INF "/" "LICENSE.txt"))

(def ^String CFG_APP_CF  (str DN_CONF  "/"  APP_CF ))
(def ^String CFG_ENV_CF  (str DN_CONF  "/"  ENV_CF ))

(def K_SKARO_APPDOMAIN :skaro-app-domain )
(def K_SKARO_APPID :skaro-appid )
(def K_SKARO_APPTASK :skaro-app-task )
(def K_JMXMGM :jmx-management )
(def K_HOMEDIR :skaro-home )
(def K_ROUTE_INFO :route-info )
(def K_CLISH :cli-shell )
(def K_COMPS :components )
;;(def K_ENDORSED :endorsed )
(def K_REGS :registries )
(def K_KERNEL :kernel )
(def K_EXECV :execvisor )
(def K_DEPLOYER :deployer )
(def K_JCTOR :job-creator )
(def K_EBUS :event-bus)
(def K_SCHEDULER :scheduler )
(def K_CONTAINERS :containers)
(def K_BLOCKS :blocks )
(def K_JMXSVR :jmxsvr )
(def K_MCACHE :meta-cache)
(def K_PLUGINS :plugins)
(def K_APPS :apps )
(def K_PODS :pods )
(def K_SVCS :services )
;;(def K_ROOT :root-rego )

(def K_ROOT_CZLR :root-loader )
(def K_APP_CZLR :app-loader )
(def K_EXEC_CZLR :exec-loader )
(def K_DBPS :db-pools )

(def K_BASEDIR :base-dir )
(def K_PODSDIR :pods-dir )
(def K_CFGDIR :cfg-dir )
(def K_APPDIR :app-dir )
(def K_PLAYDIR :play-dir )
(def K_LOGDIR :log-dir )
(def K_TMPDIR :tmp-dir )
(def K_DBSDIR :dbs-dir )
(def K_BKSDIR :blocks-dir )

(def K_COUNTRY :country )
(def K_LOCALE :locale )
(def K_L10N :l10n )
(def K_LANG :lang )
(def K_RCBUNDLE :str-bundle )

(def K_PIDFILE :pid-file )
(def K_APPCONF_FP :app-conf-file)
(def K_APPCONF :app-conf)
(def K_ENVCONF_FP :env-conf-file)
(def K_ENVCONF :env-conf)
(def K_MFPROPS :mf-props)
(def K_META :meta )
(def K_KILLPORT :discarder)

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
  "Init component" ^Component (fn [a arg] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init :default [co arg] co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn readConf

  "Parse a edn configuration file"
  ^String
  [^File appDir ^String confile]

  (let [rc (-> (io/file appDir DN_CONF confile)
               (changeContent
                 #(cs/replace %
                              "${appdir}"
                              (fpath appDir))))]
    (log/debug "[%s]\n%s" confile rc)
    rc))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


