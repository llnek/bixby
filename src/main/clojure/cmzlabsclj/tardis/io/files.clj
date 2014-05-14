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

  cmzlabsclj.tardis.io.files

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:use [cmzlabsclj.tardis.core.sys :rename { seq* rego-seq*
                                                    has? rego-has? } ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.tardis.io.loops
         :only [LoopableSchedule LoopableOneLoop CfgLoopable] ])
  (:use [cmzlabsclj.nucleus.util.seqnum :only [NextLong] ])
  (:use [cmzlabsclj.tardis.io.core])
  (:use [cmzlabsclj.nucleus.util.core
         :only [MakeMMap notnil?  test-nestr TryC SubsVar] ])
  (:use [cmzlabsclj.nucleus.util.str :only [nsb hgl? nsn] ])

  (:import (java.io FileFilter File FilenameFilter IOException))
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (java.util Properties ResourceBundle))
  (:import (org.apache.commons.io.filefilter SuffixFileFilter PrefixFileFilter
                                             RegexFileFilter FileFileFilter))
  (:import (org.apache.commons.io FileUtils))
  (:import (org.apache.commons.io.monitor FileAlterationListener
                                          FileAlterationListenerAdaptor
                                          FileAlterationMonitor
                                          FileAlterationObserver))
  (:import (com.zotohlabs.gallifrey.io FileEvent))
  (:import (com.zotohlabs.frwk.core Identifiable)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def FP_CREATED :FP-CREATED )
(def FP_CHANGED :FP-CHANGED )
(def FP_DELETED :FP-DELETED )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FilePicker
(defn MakeFilePicker

  [container]

  (MakeEmitter container :czc.tardis.io/FilePicker))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/FilePicker

  [co & args]

  (let [ ^File f (nth args 1)
         eeid (NextLong)
         impl (MakeMMap) ]
    (with-meta
      (reify

        Identifiable

        (id [_] eeid)

        FileEvent

        (bindSession [_ s] (.setf! impl :ios s))
        (getSession [_] (.getf impl :ios))
        (getId [_] eeid)
        (emitter [_] co)
        (getFile [_] f))

      { :typeid :czc.tardis.io/FileEvent }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postPoll ""

  [^cmzlabsclj.tardis.core.sys.Element co ^File f action]

  (let [ ^cmzlabsclj.tardis.io.core.EmitterAPI src co
         ^File des (.getAttr co :dest)
         fname (.getName f)
         cf (cond
              (= action :FP-CREATED)
              (if (notnil? des)
                (TryC
                  (FileUtils/moveFileToDirectory f des false)
                  (File. des fname) )
                f)
              :else nil) ]
    (when-not (nil? cf)
      (.dispatch src (IOESReifyEvent co fname cf action) {} ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/FilePicker

  [^cmzlabsclj.tardis.core.sys.Element co cfg]

  (let [ ^String root (SubsVar (nsb (:target-folder cfg)))
         ^String dest (SubsVar (nsb (:recv-folder cfg)))
         ^String mask (nsb (:fmask cfg)) ]
    (test-nestr "file-root-folder" root)
    (CfgLoopable co cfg)
    (.setAttr! co :target (doto (File. root) (.mkdirs)))
    (.setAttr! co :mask
      (cond
        (.startsWith mask "*.")
        (SuffixFileFilter. (.substring mask 1))

        (.endsWith mask "*")
        (PrefixFileFilter. (.substring mask 0 (dec (.length mask))))

        (> (.length mask) 0)
        (RegexFileFilter. mask) ;;WildcardFileFilter(mask)

        :else
        FileFileFilter/FILE ) )
    (when (hgl? dest)
      (.setAttr! co :dest (doto (File. dest) (.mkdirs))))

    (log/info "Monitoring folder: " root)
    (log/info "Recv folder: " (nsn dest))
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.io/FilePicker

  [^cmzlabsclj.tardis.core.sys.Element co]

  (let [ obs (FileAlterationObserver.  ^File (.getAttr co :target)
                                       ^FileFilter (.getAttr co :mask))
         ^long intv (.getAttr co :intervalMillis)
         mon (FileAlterationMonitor. intv)
         lnr (proxy [FileAlterationListenerAdaptor][]
               (onFileCreate [f]
                 (postPoll co f :FP-CREATED))
               (onFileChange [f]
                 (postPoll co f :FP-CHANGED))
               (onFileDelete [f]
                 (postPoll co f :FP-DELETED))) ]
    (.addListener obs lnr)
    (.addObserver mon obs)
    (.setAttr! co :monitor mon)
    (log/info "FilePicker's apache io monitor created - OK.")
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableSchedule :czc.tardis.io/FilePicker

  [^cmzlabsclj.tardis.core.sys.Element co]

  (let [ ^FileAlterationMonitor mon (.getAttr co :monitor) ]
    (log/info "FilePicker's apache io monitor starting...")
    (.start mon)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private files-eof nil)

