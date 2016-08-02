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

  czlab.skaro.io.files

  (:require
    [czlab.xlib.files :refer [mkdirs moveFileToDir]]
    [czlab.xlib.core
     :refer [test-nestr
             seqint2
             muble<>
             try!]]
    [czlab.skaro.io.loops
     :refer [loopableSchedule
             loopableOneLoop
             cfgLoopable]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.str :refer [hgl? nsn]])

  (:use
    [czlab.skaro.sys.core]
    [czlab.skaro.io.core])

  (:import
    [java.io FileFilter File IOException]
    [java.util Properties ResourceBundle]
    [org.apache.commons.io.filefilter
     SuffixFileFilter
     PrefixFileFilter
     RegexFileFilter
     FileFileFilter]
    [org.apache.commons.io.monitor
     FileAlterationListener
     FileAlterationMonitor
     FileAlterationObserver
     FileAlterationListenerAdaptor]
    [czlab.server Emitter]
    [czlab.skaro.io FileEvent]
    [czlab.xlib Muble Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Order of args must match
(defmethod ioevent<>

  ::FilePicker
  [^Service co & args]

  (let
    [fnm (first args)
     f (nth args 1)
     eeid (seqint2)
     impl (muble<>)]
    (with-meta
      (reify FileEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] )
        (session [_] )
        (id [_] eeid)
        (emitter [_] co)
        (originalFileName [_] fnm)
        (file [_] f)
        (id [_] eeid))

      {:typeid ::FileEvent})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postPoll

  "Only look for new files"
  [^Service co ^File f action]

  (let
    [{:keys [recvFolder]}
     (.config co)
     orig (.getName f)
     cf (if (and (= action :FP-CREATED)
                 (some? recvFolder))
          (trylet!
            [r (subsVar recvFolder)]
            (moveFileToDir f r false)
            (io/file r orig)))]
    (when (some? cf)
      (->> (ioevent<> co orig cf action)
           (.dispatch co)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- toFMask

  ""
  ^FileFilter
  [^String mask]

  (cond
    (.startsWith mask "*.")
    (SuffixFileFilter. (.substring mask 1))
    (.endsWith mask "*")
    (PrefixFileFilter.
      (.substring mask
                  0 (dec (.length mask))))
    (> (.length mask) 0)
    (RegexFileFilter. mask)
    :else
    FileFileFilter/FILE))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::FilePicker
  [^Service co & xs]

  (log/info "comp->initialize: FilePicker: %s" (.id co))
  (let
    [{:keys [recvFolder
             fmask
             targetFolder]}
     (.config co)
     root (subsVar targetFolder)
     dest (subsVar recvFolder)
     ff (toFMask (str fmask))
     c2 (cfgLoopable co)]
    (log/info "monitoring folder: %s" root)
    (log/info "rcv folder: %s" (nsn dest))
    (test-nestr "file-root-folder" root)
    (let
      [mon (FileAlterationMonitor. (:intervalMillis c2))
       obs (FileAlterationObserver. (io/file root) ff)]
      (->>
        (proxy [FileAlterationListenerAdaptor][]
          (onFileCreate [f]
            (postPoll co f :FP-CREATED))
          (onFileChange [f]
            (postPoll co f :FP-CHANGED))
          (onFileDelete [f]
            (postPoll co f :FP-DELETED)))
        (.addListener obs ))
      (.addObserver mon obs)
      (doto (.getx co)
        (.setv :monitor mon)))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableSchedule

  ::FilePicker
  [^Service co & args]

  (when-some
    [mon (.getv (.getx co) :monitor)]
    (log/info "apache io monitor starting...")
    (.start ^FileAlterationMonitor mon)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


