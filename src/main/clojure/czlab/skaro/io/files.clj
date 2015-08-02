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

  czlab.skaro.io.files

  (:require [czlab.skaro.io.loops
             :refer
             [LoopableSchedule
              LoopableOneLoop
              CfgLoopable]]
            [czlab.xlib.util.files :refer [Mkdirs]]
            [czlab.xlib.util.core
             :refer
             [NextLong
              MakeMMap
              notnil?
              test-nestr
              tryc
              SubsVar]]
            [czlab.xlib.util.str :refer [nsb hgl? nsn]])

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.skaro.core.sys
         :rename
         {seq* rego-seq* has? rego-has? } ]
        [czlab.skaro.io.core])

  (:import  [java.io FileFilter File FilenameFilter IOException]
            [org.apache.commons.lang3 StringUtils]
            [java.util Properties ResourceBundle]
            [org.apache.commons.io.filefilter SuffixFileFilter
             PrefixFileFilter
             RegexFileFilter FileFileFilter]
            [org.apache.commons.io FileUtils]
            [org.apache.commons.io.monitor FileAlterationListener
             FileAlterationListenerAdaptor
             FileAlterationMonitor
             FileAlterationObserver]
            [com.zotohlab.skaro.io FileEvent]
            [com.zotohlab.frwk.core Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Order of args must match.
(defmethod IOESReifyEvent :czc.skaro.io/FilePicker

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

      { :typeid :czc.skaro.io/FileEvent }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postPoll "Only look for new files."

  [^czlab.skaro.core.sys.Elmt
   co
   ^File f action]

  (let [^czlab.skaro.io.core.EmitAPI src co
        cfg (.getAttr co :emcfg)
        ^File des (:recvFolder cfg)
        origFname (.getName f)
        cf (case action
             :FP-CREATED
             (if-not (nil? des)
               (tryc
                 (FileUtils/moveFileToDirectory f des false)
                 (io/file des origFname))
               f)
             nil)]
    (when-not (nil? cf)
      (.dispatch src (IOESReifyEvent co origFname cf action) {}))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.skaro.io/FilePicker

  [^czlab.skaro.core.sys.Elmt co cfg0]

  (log/info "ComConfigure: FilePicker: " (.id ^Identifiable co))
  (let [cfg (merge (.getAttr co :dftOptions) cfg0)
        root (SubsVar (nsb (:targetFolder cfg)))
        dest (SubsVar (nsb (:recvFolder cfg)))
        mask (nsb (:fmask cfg))
        c2 (CfgLoopable co cfg) ]
    (log/info "Monitoring folder: " root)
    (log/info "Rcv folder: " (nsn dest))
    (test-nestr "file-root-folder" root)
    (.setAttr! co :emcfg
      (-> c2
        (assoc :targetFolder (Mkdirs (io/file root)))
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
                 (Mkdirs (io/file dest))
                 nil))))
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.io/FilePicker

  [^czlab.skaro.core.sys.Elmt co]

  (log/info "ComInitialize FilePicker: " (.id ^Identifiable co))
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
(defmethod LoopableSchedule :czc.skaro.io/FilePicker

  [^czlab.skaro.core.sys.Elmt co]

  (when-let [mon (.getAttr co :monitor) ]
    (log/info "FilePicker's apache io monitor starting...")
    (.start ^FileAlterationMonitor mon)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

