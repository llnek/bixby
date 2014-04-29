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

  comzotohlabscljc.tardis.impl.sys

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.tardis.core.constants])
  (:use [comzotohlabscljc.tardis.core.sys])
  (:use [comzotohlabscljc.tardis.impl.ext])
  (:use [comzotohlabscljc.tardis.impl.defaults :rename {Enabled? blockmeta-enabled?
                                                        Start kernel-start
                                                        Stop kernel-stop}])
  (:use [ comzotohlabscljc.util.core :only [MakeMMap TryC NiceFPath notnil? NewRandom] ])
  (:use [ comzotohlabscljc.util.str :only [strim] ])
  (:use [ comzotohlabscljc.util.process :only [SafeWait] ])
  (:use [ comzotohlabscljc.util.files :only [Unzip] ])
  (:use [ comzotohlabscljc.util.mime :only [SetupCache] ])
  (:use [ comzotohlabscljc.util.seqnum :only [NextLong] ] )
  (:import (org.apache.commons.io FilenameUtils FileUtils))
  (:import (org.apache.commons.lang3 StringUtils))

  (:import (com.zotohlabs.frwk.core Disposable Identifiable Hierarchial Versioned Startable))
  (:import (com.zotohlabs.gallifrey.loaders AppClassLoader))
  (:import (com.zotohlabs.frwk.server Component ComponentRegistry))
  (:import (java.net URL))
  (:import (java.io File))
  (:import (java.security SecureRandom))
  (:import (java.util.zip ZipFile))
  (:import (com.zotohlabs.frwk.io IOUtils)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Deployer
(defn MakeDeployer ""

  []

  (let [ impl (MakeMMap) ]
    (with-meta
      (reify

        Element

        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )
        (setCtx! [_ x] (.setf! impl :ctx x) )
        (getCtx [_] (.getf impl :ctx) )

        Component
        (id [_] K_DEPLOYER )
        (version [_] "1.0" )

        Hierarchial
        (parent [_] nil)

        Deployer

        (undeploy [this app]
          (let [ ^comzotohlabscljc.util.core.MubleAPI ctx (getCtx this)
                 dir (File. ^File (.getf ctx K_PLAYDIR) ^String app) ]
            (when (.exists dir)
                (FileUtils/deleteDirectory dir))))

        (deploy [this src]
          (let [ app (FilenameUtils/getBaseName (NiceFPath src))
                 ^comzotohlabscljc.util.core.MubleAPI ctx (getCtx this)
                 des (File. ^File (.getf ctx K_PLAYDIR) ^String app) ]
            (when-not (.exists des)
              (Unzip src des)))) )

      { :typeid (keyword "czc.tardis.impl/Deployer") }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompContextualize :czc.tardis.impl/Deployer

  [co ctx]

  (do
    (PrecondDir (MaybeDir ctx K_BASEDIR))
    ;;(precondDir (maybeDir ctx K_PODSDIR))
    (PrecondDir (MaybeDir ctx K_PLAYDIR))
    (CompCloneContext co ctx)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.impl/Deployer

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ^comzotohlabscljc.util.core.MubleAPI ctx (.getCtx co)
         ^File py (.getf ctx K_PLAYDIR)
         ^File pd (.getf ctx K_PODSDIR) ]
    (when (.isDirectory pd)
      (doseq [ ^File f (seq (IOUtils/listFiles pd "pod" false)) ]
        (.deploy ^comzotohlabscljc.tardis.impl.defaults.Deployer co f)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Kernel
(defn- maybe-start-pod

  [^comzotohlabscljc.tardis.core.sys.Element knl
   cset
   ^comzotohlabscljc.tardis.core.sys.Element pod]

  (TryC
    (let [ cache (.getAttr knl K_CONTAINERS)
           cid (.id ^Identifiable pod)
           app (.moniker ^comzotohlabscljc.tardis.impl.defaults.PODMeta pod)
           ctr (if (and (not (empty? cset))
                        (not (contains? cset app)))
                 nil
                 (MakeContainer pod)) ]
      (log/debug "start-pod? cid = " cid ", app = " app " !! cset = " cset)
      (if (notnil? ctr)
        (do
          (.setAttr! knl K_CONTAINERS (assoc cache cid ctr))
        ;;_jmx.register(ctr,"", c.name)
          true)
        (do
          (log/info "kernel: container " cid " disabled.")
          false) ) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeKernel ""

  []

  (let [ impl (MakeMMap) ]
    (.setf! impl K_CONTAINERS {} )
    (with-meta
      (reify

        Element

        (setCtx! [_ x] (.setf! impl :ctx x))
        (getCtx [_] (.getf impl :ctx))
        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )

        Component
        (version [_] "1.0")
        (id [_] K_KERNEL )

        Hierarchial
        (parent [_] nil)

        Kernel

        Startable
        (start [this]
          (let [ ^comzotohlabscljc.util.core.MubleAPI ctx (getCtx this)
                 ^ComponentRegistry root (.getf ctx K_COMPS)
                 ^comzotohlabscljc.util.ini.IWin32Conf
                 wc (.getf ctx K_PROPS)
                 endorsed (strim (.optString wc K_APPS "endorsed" ""))
                 ^comzotohlabscljc.tardis.core.sys.Registry
                 apps (.lookup root K_APPS)
                 cs (if (= "*" endorsed)
                      #{}
                      (into #{} (filter (fn [^String s] (> (.length s) 0))
                                        (map #(strim %)
                                             (seq (StringUtils/split endorsed ",;"))) ) )) ]
            ;; need this to prevent deadlocks amongst pods
            ;; when there are dependencies
            ;; TODO: need to handle this better
            (doseq [ [k v] (seq* apps) ]
              (let [ r (-> (NewRandom) (.nextInt 6)) ]
                (if (maybe-start-pod this cs v)
                  (SafeWait (* 1000 (Math/max (int 1) r))))))) )

        (stop [this]
          (let [ cs (.getf impl K_CONTAINERS) ]
            (doseq [ [k v] (seq cs) ]
              (.stop ^Startable v))
            (doseq [ [k v] (seq cs) ]
              (.dispose ^Disposable v))
            (.setf! impl K_CONTAINERS {}))) )

      { :typeid (keyword "czc.tardis.impl/Kernel") }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakePodMeta ""

  [app ver parObj podType appid pathToPOD]

  (let [ pid (str podType "#" (NextLong))
         impl (MakeMMap) ]
    (log/info "PODMeta: " app ", " ver ", " podType ", " appid ", " pathToPOD )
    (with-meta
      (reify

        Element

        (setCtx! [_ x] (.setf! impl :ctx x))
        (getCtx [_] (.getf impl :ctx))
        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )

        Component
        (version [_] ver)
        (id [_] pid )

        Hierarchial
        (parent [_] parObj)


        PODMeta

        (srcUrl [_] pathToPOD)
        (moniker [_] app)
        (appKey [_] appid)
        (typeof [_] podType))

      { :typeid (keyword "czc.tardis.impl/PODMeta") }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.impl/PODMeta

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ^comzotohlabscljc.util.core.MubleAPI ctx (.getCtx co)
         rcl (.getf ctx K_ROOT_CZLR)
         ^URL url (.srcUrl ^comzotohlabscljc.tardis.impl.defaults.PODMeta co)
         cl  (AppClassLoader. rcl) ]
    (.configure cl (NiceFPath (File. (.toURI  url))) )
    (.setf! ctx K_APP_CZLR cl)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompCompose :czc.tardis.impl/Kernel

  [co rego]

  ;; get the jmx server from root
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompContextualize :czc.tardis.impl/Kernel

  [co ctx]

  (let [ base (MaybeDir ctx K_BASEDIR) ]
    (PrecondDir base)
    ;;(precondDir (maybeDir ctx K_PODSDIR))
    (PrecondDir (MaybeDir ctx K_PLAYDIR))
    (SetupCache (-> (File. base (str DN_CFG "/app/mime.properties"))
                      (.toURI)(.toURL )))
    (CompCloneContext co ctx)
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private sys-eof nil)

