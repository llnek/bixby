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
      :author "kenl" }

  czlab.skaro.io.files

  (:require
    [czlab.xlib.files :refer [mkdirs moveFileToDir]]
    [czlab.xlib.core
     :refer [nextLong
             mubleObj!
             test-nestr
             tryc
             subsVar]]
    [czlab.skaro.io.loops
     :refer [loopableSchedule
             loopableOneLoop
             cfgLoopable]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.str :refer [toKW hgl? nsn]])

  (:use
    [czlab.skaro.core.sys]
    [czlab.skaro.io.core])

  (:import
    [java.io FileFilter File FilenameFilter IOException]
    [org.apache.commons.lang3 StringUtils]
    [java.util Properties ResourceBundle]
    [org.apache.commons.io.filefilter SuffixFileFilter
     PrefixFileFilter
     RegexFileFilter
     FileFileFilter]
    [org.apache.commons.io.monitor FileAlterationListener
     FileAlterationListenerAdaptor
     FileAlterationMonitor
     FileAlterationObserver]
    [czlab.wflow.server Emitter]
    [czlab.skaro.io FileEvent]
    [czlab.xlib Muble Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Order of args must match
(defmethod ioReifyEvent :czc.skaro.io/FilePicker

  [co & args]

  (let
    [fnm (first args)
     f (nth args 1)
     eeid (nextLong)
     impl (mubleObj!) ]
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

      {:typeid (toKW "czc.skaro.io" "FileEvent") })))

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
                 (moveFileToDir f recvFolder false)
                 (io/file recvFolder origFname))
               f)
             nil)]
    (when (some? cf)
      (.dispatch src (ioReifyEvent co origFname cf action) {}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compConfigure :czc.skaro.io/FilePicker

  [^Muble co cfg0]

  (log/info "compConfigure: FilePicker: %s" (.id ^Identifiable co))
  (let [{:keys [targetFolder recvFolder fmask]
         :as cfg}
        (merge (.getv co :dftOptions) cfg0)
        root (subsVar targetFolder)
        dest (subsVar recvFolder)
        mask (str fmask)
        c2 (cfgLoopable co cfg) ]
    (log/info "monitoring folder: %s" root)
    (log/info "rcv folder: %s" (nsn dest))
    (test-nestr "file-root-folder" root)
    (.setv co :emcfg
      (-> c2
        (assoc :targetFolder (mkdirs (io/file root)))
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
                 (mkdirs (io/file dest))))))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compInitialize :czc.skaro.io/FilePicker

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
(defmethod loopableSchedule :czc.skaro.io/FilePicker

  [^Muble co & args]

  (when-some [mon (.getv co :monitor) ]
    (log/info "filePicker's apache io monitor starting...")
    (.start ^FileAlterationMonitor mon)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


