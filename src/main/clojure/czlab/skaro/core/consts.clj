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

  czlab.skaro.core.consts)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^String ^:private SYS_DEVID_PFX "system.####")
(defonce ^String ^:private SYS_DEVID_SFX "####")

(defonce ^String SYS_DEVID_REGEX #"system::[0-9A-Za-z_\-\.]+" )
(defonce ^String SHUTDOWN_DEVID #"system::kill_9" )
(defonce ^String DEF_DBID "default")

(defonce ^String SHUTDOWN_URI "/kill9")
(defonce ^String POD_PROTOCOL  "pod:" )
(defonce ^String META_INF  "META-INF" )
(defonce ^String WEB_INF  "WEB-INF" )

(defonce ^String DN_TARGET "target")
(defonce ^String DN_BUILD "build")

(defonce ^String DN_CLASSES "classes" )
(defonce ^String DN_BIN "bin" )
(defonce ^String DN_BLOCKS  "ems" )
(defonce ^String DN_BOOT "boot" )
(defonce ^String DN_EXEC "exec" )
(defonce ^String DN_CONF "conf" )
(defonce ^String DN_LIB "lib" )

(defonce ^String DN_CFGAPP "etc/app" )
(defonce ^String DN_CFGWEB "etc/web" )
(defonce ^String DN_CFG "etc" )

(defonce ^String DN_RCPROPS  "Resources_en.properties" )
(defonce ^String DN_TEMPLATES  "templates" )

(defonce ^String DN_BOXX "apps" )
(defonce ^String DN_PODS  "pods" )
(defonce ^String DN_LOGS "logs" )
(defonce ^String DN_TMP "tmp" )
(defonce ^String DN_DBS "dbs" )
(defonce ^String DN_DIST "dist" )
(defonce ^String DN_VIEWS  "htmls" )
(defonce ^String DN_PAGES  "pages" )
(defonce ^String DN_PATCH "patch" )
(defonce ^String DN_MEDIA "media" )
(defonce ^String DN_SCRIPTS "scripts" )
(defonce ^String DN_STYLES "styles" )
(defonce ^String DN_PUBLIC "public" )

(defonce ^String ENV_CF  "env.conf" )
(defonce ^String APP_CF  "app.conf" )

(defonce ^String WEB_CLASSES  (str WEB_INF  "/" DN_CLASSES))
(defonce ^String WEB_LIB  (str WEB_INF  "/" DN_LIB))
(defonce ^String WEB_LOG  (str WEB_INF  "/logs"))
(defonce ^String WEB_XML  (str WEB_INF  "/web.xml"))

(defonce ^String MN_RNOTES (str META_INF "/" "RELEASE-NOTES.txt"))
(defonce ^String MN_README (str META_INF "/" "README.md"))
(defonce ^String MN_NOTES (str META_INF "/" "NOTES.txt"))
(defonce ^String MN_LIC (str META_INF "/" "LICENSE.txt"))

(defonce ^String CFG_ENV_CF  (str DN_CONF  "/"  ENV_CF ))
(defonce ^String CFG_APP_CF  (str DN_CONF  "/"  APP_CF ))


(defonce K_SKARO_APPDOMAIN :skaro-app-domain )
(defonce K_SKARO_APPID :skaro-appid )
(defonce K_SKARO_APPTASK :skaro-app-task )
(defonce K_JMXMGM :jmx-management )
(defonce K_HOMEDIR :skaro-home )
(defonce K_PROPS :skaro.conf )
(defonce K_ROUTE_INFO :route-info )
(defonce K_CLISH :cli-shell )
(defonce K_COMPS :components )
;;(defonce K_ENDORSED :endorsed )
(defonce K_REGS :registries )
(defonce K_KERNEL :kernel )
(defonce K_EXECV :execvisor )
(defonce K_DEPLOYER :deployer )
(defonce K_JCTOR :job-creator )
(defonce K_EBUS :event-bus)
(defonce K_SCHEDULER :scheduler )
(defonce K_CONTAINERS :containers)
(defonce K_BLOCKS :blocks )
(defonce K_JMXSVR :jmxsvr )
(defonce K_MCACHE :meta-cache)
(defonce K_PLUGINS :plugins)
(defonce K_APPS :apps )
(defonce K_PODS :pods )
(defonce K_SVCS :services )
;;(defonce K_ROOT :root-rego )

(defonce K_ROOT_CZLR :root-loader )
(defonce K_APP_CZLR :app-loader )
(defonce K_EXEC_CZLR :exec-loader )
(defonce K_DBPS :db-pools )

(defonce K_BASEDIR :base-dir )
(defonce K_PODSDIR :pods-dir )
(defonce K_CFGDIR :cfg-dir )
(defonce K_APPDIR :app-dir )
(defonce K_PLAYDIR :play-dir )
(defonce K_LOGDIR :log-dir )
(defonce K_TMPDIR :tmp-dir )
(defonce K_DBSDIR :dbs-dir )
(defonce K_BKSDIR :blocks-dir )

(defonce K_COUNTRY :country )
(defonce K_LOCALE :locale )
(defonce K_L10N :l10n )
(defonce K_LANG :lang )
(defonce K_RCBUNDLE :str-bundle )

(defonce K_PIDFILE :pid-file )
(defonce K_APPCONF_FP :app-conf-file)
(defonce K_APPCONF :app-conf)
(defonce K_ENVCONF_FP :env-conf-file)
(defonce K_ENVCONF :env-conf)
(defonce K_MFPROPS :mf-props)
(defonce K_META :meta )

(defonce K_KILLPORT :discarder)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


