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

  cmzlabclj.tardis.core.constants)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^String ^:private SYS_DEVID_PFX "system.####")
(def ^String ^:private SYS_DEVID_SFX "####")

(def ^String SYS_DEVID_REGEX #"system::[0-9A-Za-z_\-\.]+" )
(def ^String SHUTDOWN_DEVID #"system::kill_9" )
(def ^String DEF_DBID "default")

(def ^String SHUTDOWN_URI "/kill9")
(def ^String POD_PROTOCOL  "pod:" )
(def ^String META_INF  "META-INF" )
(def ^String POD_INF  "POD-INF" )
(def ^String WEB_INF  "WEB-INF" )

(def ^String DN_BLOCKS  "blocks" )
(def ^String DN_BOOT "boot" )
(def ^String DN_EXEC "exec" )
(def ^String DN_CONF "conf" )
(def ^String DN_CLASSES "classes" )
(def ^String DN_LIB "lib" )
(def ^String DN_CFG "etc" )
(def ^String DN_BOXX "apps" )
(def ^String DN_PODS  "pods" )
(def ^String DN_LOGS "logs" )
(def ^String DN_TMP "tmp" )
(def ^String DN_DBS "dbs" )
(def ^String DN_DIST "dist" )
(def ^String DN_TEMPLATES  "templates" )
(def ^String DN_VIEWS  "htmls" )
(def ^String DN_PAGES  "pages" )
(def ^String DN_PATCH "patch" )
(def ^String DN_MEDIA "media" )
(def ^String DN_SCRIPTS "scripts" )
(def ^String DN_STYLES "styles" )
(def ^String DN_PUBLIC "public" )

(def ^String ENV_CF  "env.conf.edn" )
(def ^String APP_CF  "app.conf.edn" )

(def ^String MN_FILE (str META_INF "/" "MANIFEST.MF"))
(def ^String POD_CLASSES  (str POD_INF "/" DN_CLASSES))
(def ^String POD_PATCH  (str POD_INF "/" DN_PATCH))
(def ^String POD_LIB  (str POD_INF "/" DN_LIB))

(def ^String WEB_CLASSES  (str WEB_INF  "/" DN_CLASSES))
(def ^String WEB_LIB  (str WEB_INF  "/" DN_LIB))
(def ^String WEB_LOG  (str WEB_INF  "/logs"))
(def ^String WEB_XML  (str WEB_INF  "/web.xml"))

(def ^String MN_RNOTES (str META_INF "/" "RELEASE-NOTES.txt"))
(def ^String MN_README (str META_INF "/" "README.md"))
(def ^String MN_NOTES (str META_INF "/" "NOTES.txt"))
(def ^String MN_LIC (str META_INF "/" "LICENSE.txt"))

(def ^String CFG_ENV_CF  (str DN_CONF  "/"  ENV_CF ))
(def ^String CFG_APP_CF  (str DN_CONF  "/"  APP_CF ))


(def K_SKARO_APPDOMAIN :skaro-app-domain )
(def K_SKARO_APPID :skaro-appid )
(def K_SKARO_APPTASK :skaro-app-task )
(def K_JMXMGM :jmx-management )
(def K_HOMEDIR :skaro-home )
(def K_PROPS :skaro.conf.edn )
(def K_ROUTE_INFO :route-info )
(def K_CLISH :cli-shell )
(def K_COMPS :components )
;;(def K_ENDORSED :endorsed )
(def K_REGS :registries )
(def K_KERNEL :kernel )
(def K_EXECV :execvisor )
(def K_DEPLOYER :deployer )
(def K_JCTOR :job-creator )
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

(def ^String EV_OPTS "____eventoptions")
(def ^String JS_LAST "____lastresult")
(def ^String JS_CRED "credential")
(def ^String JS_USER "principal")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private constants-eof nil)


