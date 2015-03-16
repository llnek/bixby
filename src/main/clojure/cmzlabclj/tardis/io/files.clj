;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  cmzlabclj.tardis.io.files

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.tardis.core.sys :rename {seq* rego-seq*
                                            has? rego-has? } ]
        [cmzlabclj.tardis.io.loops
         :only [LoopableSchedule LoopableOneLoop CfgLoopable] ]
        [cmzlabclj.nucleus.util.seqnum :only [NextLong] ]
        [cmzlabclj.tardis.io.core]
        [cmzlabclj.nucleus.util.core
         :only [MakeMMap notnil?  test-nestr TryC SubsVar] ]
        [cmzlabclj.nucleus.util.str :only [nsb hgl? nsn] ])

  (:import  [java.io FileFilter File FilenameFilter IOException]
            [org.apache.commons.lang3 StringUtils]
            [java.util Properties ResourceBundle]
            [org.apache.commons.io.filefilter SuffixFileFilter PrefixFileFilter
                                             RegexFileFilter FileFileFilter]
            [org.apache.commons.io FileUtils]
            [org.apache.commons.io.monitor FileAlterationListener
                                           FileAlterationListenerAdaptor
                                           FileAlterationMonitor
                                           FileAlterationObserver]
            [com.zotohlab.gallifrey.io FileEvent]
            [com.zotohlab.frwk.core Identifiable]))

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
;; Order of args must match.
(defmethod IOESReifyEvent :czc.tardis.io/FilePicker

  [co & args]

  (let [^File f (nth args 1)
        fnm (first args)
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
        (checkAuthenticity [_] false)
        (emitter [_] co)
        (getOriginalFileName [_] fnm)
        (getFile [_] f))

      { :typeid :czc.tardis.io/FileEvent }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postPoll ""

  [^cmzlabclj.tardis.core.sys.Element
   co
   ^File f action]

  (let [^cmzlabclj.tardis.io.core.EmitterAPI src co
        cfg (.getAttr co :emcfg)
        ^File des (:recvFolder cfg)
        ;; original file name
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

  [^cmzlabclj.tardis.core.sys.Element co cfg0]

  (let [cfg (merge (.getAttr co :dftOptions) cfg0)
        root (SubsVar (nsb (:targetFolder cfg)))
        dest (SubsVar (nsb (:recvFolder cfg)))
        mask (nsb (:fmask cfg))
        c2 (CfgLoopable co cfg) ]
    (test-nestr "file-root-folder" root)
    (log/info "Monitoring folder: " root)
    (log/info "Rcv folder: " (nsn dest))
    (.setAttr! co :emcfg
    (-> c2
        (assoc :targetFolder (doto (File. root) (.mkdirs)))
        (assoc :fmask
               (cond
                 (.startsWith mask "*.")
                 (SuffixFileFilter. (.substring mask 1))
                 (.endsWith mask "*")
                 (PrefixFileFilter. (.substring mask 0
                                                (dec (.length mask))))
                 (> (.length mask) 0)
                 (RegexFileFilter. mask) ;;WildcardFileFilter(mask)
                 :else
                 FileFileFilter/FILE))
        (assoc :recvFolder
               (if (hgl? dest)
                 (doto (File. dest) (.mkdirs))
                 nil))))
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.io/FilePicker

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [cfg (.getAttr co :emcfg)
        obs (FileAlterationObserver. ^File (:targetFolder cfg)
                                     ^FileFilter (:fmask cfg))
        intv (:intervalMillis cfg)
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

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [^FileAlterationMonitor mon (.getAttr co :monitor) ]
    (log/info "FilePicker's apache io monitor starting...")
    (.start mon)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private files-eof nil)

