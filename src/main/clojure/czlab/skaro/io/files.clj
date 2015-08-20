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

  (:require
    [czlab.xlib.util.files :refer [Mkdirs MoveFileToDir]]
    [czlab.xlib.util.core
    :refer [NextLong MubleObj! test-nestr tryc SubsVar]]
    [czlab.skaro.io.loops
    :refer [LoopableSchedule
    LoopableOneLoop CfgLoopable]]
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.util.str :refer [ToKW hgl? nsn]])

  (:use
    [czlab.skaro.core.sys]
    [czlab.skaro.io.core])

  (:import
    [java.io FileFilter File FilenameFilter IOException]
    [org.apache.commons.lang3 StringUtils]
    [java.util Properties ResourceBundle]
    [org.apache.commons.io.filefilter SuffixFileFilter
    PrefixFileFilter
    RegexFileFilter FileFileFilter]
    [org.apache.commons.io.monitor FileAlterationListener
    FileAlterationListenerAdaptor
    FileAlterationMonitor
    FileAlterationObserver]
    [com.zotohlab.frwk.server Emitter]
    [com.zotohlab.skaro.core Muble]
    [com.zotohlab.skaro.io FileEvent]
    [com.zotohlab.frwk.core Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Order of args must match
(defmethod IOESReifyEvent :czc.skaro.io/FilePicker

  [co & args]

  (let
    [fnm (first args)
     f (nth args 1)
     eeid (NextLong)
     impl (MubleObj!) ]
    (with-meta
      (reify

        Identifiable

        (id [_] eeid)

        FileEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] )
        (getSession [_] )
        (getId [_] eeid)
        (emitter [_] co)
        (getOriginalFileName [_] fnm)
        (getFile [_] f))

      {:typeid (ToKW "czc.skaro.io" "FileEvent") })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postPoll

  "Only look for new files"

  [^Muble co ^File f action]

  (let [^Emitter src co
        {:keys [recvFolder]}
        (.getv co :emcfg)
        origFname (.getName f)
        cf (case action
             :FP-CREATED
             (if (some? recvFolder)
               (tryc
                 (MoveFileToDir f recvFolder false)
                 (io/file recvFolder origFname))
               f)
             nil)]
    (when (some? cf)
      (.dispatch src (IOESReifyEvent co origFname cf action) {}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.skaro.io/FilePicker

  [^Muble co cfg0]

  (log/info "compConfigure: FilePicker: %s" (.id ^Identifiable co))
  (let [{:keys [targetFolder recvFolder fmask]
         :as cfg}
        (merge (.getv co :dftOptions) cfg0)
        root (SubsVar targetFolder)
        dest (SubsVar recvFolder)
        mask (str fmask)
        c2 (CfgLoopable co cfg) ]
    (log/info "monitoring folder: %s" root)
    (log/info "rcv folder: %s" (nsn dest))
    (test-nestr "file-root-folder" root)
    (.setv co :emcfg
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
                 (Mkdirs (io/file dest))))))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.io/FilePicker

  [^Muble co]

  (log/info "compInitialize FilePicker: %s" (.id ^Identifiable co))
  (let [{:keys [targetFolder fmask intervalMillis]}
        (.getv co :emcfg)
        obs (FileAlterationObserver. ^File targetFolder
                                     ^FileFilter fmask)
        mon (FileAlterationMonitor. intervalMillis)
        lnr (proxy [FileAlterationListenerAdaptor][]
              (onFileCreate [f]
                (postPoll co f :FP-CREATED))
              (onFileChange [f]
                (postPoll co f :FP-CHANGED))
              (onFileDelete [f]
                (postPoll co f :FP-DELETED))) ]
    (.addListener obs lnr)
    (.addObserver mon obs)
    (.setv co :monitor mon)
    (log/info "filePicker's apache io monitor created - ok")
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableSchedule :czc.skaro.io/FilePicker

  [^Muble co & args]

  (when-some [mon (.getv co :monitor) ]
    (log/info "filePicker's apache io monitor starting...")
    (.start ^FileAlterationMonitor mon)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

