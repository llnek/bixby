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
;; Copyright © 2013-2024, Kenneth Leung. All rights reserved.

(ns czlab.bixby.plugs.files

  "Implementation for FilePicker."

  (:require [clojure.java.io :as io]
            [clojure.string :as cs]
            [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [czlab.bixby.core :as b]
            [czlab.bixby.plugs.loops :as l]
            [czlab.basal.core :as c :refer [n#]])

  (:import [java.util Timer Properties ResourceBundle]
           [java.io FileFilter File IOException]
           [clojure.lang APersistentMap]
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
           [org.apache.commons.io FileUtils]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord FileMsg []
  c/Idable
  (id [_] (:id _))
  c/Hierarchical
  (parent [me] (:source me)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- evt<>

  [co fname fp action]

  (c/object<> FileMsg
              :source co
              :file (io/file fp)
              :original-fname fname
              :id (str "FileMsg#" (u/seqint2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- post-poll

  "Only look for new files."
  [{{:keys [recv-folder]} :conf :as plug} f action]

  (let [orig (i/fname f)]
    (when-some
      [cf (if (and recv-folder
                   (not= action :FP-DELETED))
            (c/try! (c/doto->> (io/file recv-folder orig)
                               (FileUtils/moveFile ^File f))))]
      (b/dispatch (evt<> plug orig cf action)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- init2

  [conf]

  (letfn
    [(to-fmask [mask]
       (cond (cs/starts-with? mask "*.")
             (SuffixFileFilter. (subs mask 1))
             (cs/ends-with? mask "*")
             (PrefixFileFilter.
               (subs mask 0 (- (n# mask) 1)))
             (not-empty mask)
             (RegexFileFilter. ^String mask)
             :else
             FileFileFilter/FILE))]
    (let [{:keys [fmask]
           dest :recv-folder
           root :target-folder} conf]
      (assert (c/hgl? root)
              (c/fmt "Bad root-folder %s." root))
      (c/info (str "source dir: %s\n"
                   "receiving dir: %s") root dest)
      (assoc conf
             :fmask (to-fmask (str fmask))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- file-mon<>

  [plug]

  (let [{:keys [target-folder
                interval-secs
                ^FileFilter fmask]} (:conf plug)]
    [(FileAlterationMonitor. (b/s2ms interval-secs))
     (-> (io/file target-folder) (FileAlterationObserver. fmask))]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord FilePickerPlugin [server _id info conf]
  c/Hierarchical
  (parent [_] server)
  c/Idable
  (id [_] _id)
  c/Initable
  (init [me arg]
    (update-in me
               [:conf]
               #(-> (c/merge+ % arg)
                    b/expand-vars* b/prevar-cfg init2)))
  c/Finzable
  (finz [_] (c/stop _))
  c/Startable
  (start [_]
    (c/start _ nil))
  (start [me arg]
    (let [[^FileAlterationMonitor mon
           ^FileAlterationObserver obs] (file-mon<> me)
          plug (assoc me :monitor mon)]
      (.addListener obs (proxy [FileAlterationListenerAdaptor][]
                          (onFileCreate [f]
                            (post-poll plug f :FP-CREATED))
                          (onFileChange [f]
                            (post-poll plug f :FP-CHANGED))
                          (onFileDelete [f]
                            (post-poll plug f :FP-DELETED))))
      (.addObserver mon obs)
      (l/cfg-timer (Timer. true)
                   #(do (c/info "apache io monitor starting...")
                        (.start ^FileAlterationMonitor mon)) conf false)
      plug))
  (stop [me]
    (c/info "apache io monitor stopping...")
    (some-> ^FileAlterationMonitor (:monitor me) .stop)
    (assoc me :monitor nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def
  ^{:doc ""}

  FilePickerSpec

  {:info {:name "File Picker"
          :version "1.0.0"}
   :conf {:$pluggable ::file-picker<>
          :$action nil
          :$error nil
          :interval-secs 300
          :delay-secs 0
          :fmask ""
          :recv-folder "/home/joe"
          :target-folder "/home/dropbox"}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn file-picker<>

  "Create a File Picker Plugin."
  {:arglists '([server id]
               [server id options])}

  ([_ id]
   (file-picker<> _ id FilePickerSpec))

  ([server id {:keys [info conf]}]
   (FilePickerPlugin. server id info conf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

