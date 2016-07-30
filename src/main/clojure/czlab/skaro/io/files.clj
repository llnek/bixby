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
    [czlab.skaro.core.sys]
    [czlab.skaro.io.core])

  (:import
    [java.io FileFilter File FilenameFilter IOException]
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
    [czlab.server EventEmitter]
    [czlab.skaro.io FileEvent]
    [czlab.xlib Muble Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Order of args must match
(defmethod ioevent<>

  ::FilePicker
  [^EventEmitter co & args]

  (let
    [fnm (first args)
     f (nth args 1)
     eeid (seqint2)
     impl (muble<>)]
    (with-meta
      (reify

        Identifiable

        (id [_] eeid)

        FileEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] )
        (getSession [_] )
        (id [_] eeid)
        (emitter [_] co)
        (getOriginalFileName [_] fnm)
        (getFile [_] f))

      {:typeid ::FileEvent})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postPoll

  "Only look for new files"
  [^Context co ^File f action]

  (let
    [^EventEmitter src co
     {:keys [recvFolder]}
     (.getv (.getx co) :emcfg)
     orig (.getName f)
     cf (if (and (= action :FP-CREATED)
                 (some? recvFolder))
          (try!
            (moveFileToDir f recvFolder false)
            (io/file recvFolder orig))
          nil)]
    (when (some? cf)
      (.dispatch ^EventEmitter src
                 (ioevent<> co orig cf action) {}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->configure

  ::FilePicker
  [^Context co cfg0]

  (log/info "comp->configure: FilePicker: %s" (.id ^Identifiable co))
  (let
    [{:keys [recvFolder
             fmask
             targetFolder]
      :as cfg}
      (merge (.getv (.getx co)
                    :dftOptions) cfg0)
      root (subsVar targetFolder)
      dest (subsVar recvFolder)
      mask (str fmask)
      ff
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
        FileFileFilter/FILE)
      c2 (cfgLoopable co cfg)]
    (log/info "monitoring folder: %s" root)
    (log/info "rcv folder: %s" (nsn dest))
    (test-nestr "file-root-folder" root)
    (->>
      (-> (assoc c2 :targetFolder
                 (mkdirs (io/file root)))
          (assoc :fmask ff)
          (assoc :recvFolder
                 (if (hgl? dest)
                   (mkdirs (io/file dest)))))
      (.setv (.getx co) :emcfg))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::FilePicker
  [^Context co]

  (log/info "comp->initialize FilePicker: %s" (.id ^Identifiable co))
  (let
    [{:keys [targetFolder fmask intervalMillis]}
     (.getv (.getx co) :emcfg)
     obs (FileAlterationObserver.
           (io/file targetFolder) ^FileFilter fmask)
     mon (FileAlterationMonitor. intervalMillis)
     lnr (proxy [FileAlterationListenerAdaptor][]
           (onFileCreate [f]
             (postPoll co f :FP-CREATED))
           (onFileChange [f]
             (postPoll co f :FP-CHANGED))
           (onFileDelete [f]
             (postPoll co f :FP-DELETED)))]
    (.addListener obs lnr)
    (.addObserver mon obs)
    (.setv (.getx co) :monitor mon)
    (log/info "filePicker's apache io monitor created - ok")
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableSchedule

  ::FilePicker
  [^Context co & args]

  (when-some [mon (.getv (.getx co) :monitor)]
    (log/info "filePicker's apache io monitor starting...")
    (.start ^FileAlterationMonitor mon)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


