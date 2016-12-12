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

(ns czlabtest.wabbit.svcs

  (:require [czlab.xlib.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io])

  (:use [czlab.convoy.netty.client]
        [czlab.convoy.net.core]
        [czlab.convoy.netty.resp]
        [czlabtest.wabbit.mock]
        [czlab.wabbit.etc.svcs]
        [czlab.wabbit.etc.core]
        [czlab.wabbit.io.core]
        [czlab.flux.wflow.core]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str]
        [clojure.test])

  (:import [java.io DataOutputStream DataInputStream BufferedInputStream]
           [czlab.wabbit.io
            EmailEvent
            SocketEvent
            FileEvent
            JmsEvent
            HttpEvent]
           [czlab.flux.wflow WorkStream Job]
           [io.netty.channel Channel]
           [czlab.wabbit.server Container]
           [javax.mail Message Message$RecipientType Multipart]
           [javax.mail.internet MimeMessage]
           [javax.jms TextMessage]
           [czlab.convoy.netty WholeResponse]
           [java.net Socket]
           [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private RESULT (atom 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn testHandler
  ""
  []
  (workStream<>
    (script<>
      (fn [_ _]
        (do->nil
          (swap! RESULT + 8))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn fileHandler
  ""
  []
  (workStream<>
    (script<>
      (fn [_ ^Job job]
        (let [^FileEvent e (.event job)
              {:keys [targetFolder recvFolder]}
              (.. e source config)
              tp (fpath targetFolder)
              rp (fpath recvFolder)
              nm (juid)
              f (.file e)
              fp (fpath f)
              s (slurpUtf8 f)
              n (convLong s 0)]
          ;;the file should be in the recv-folder
          (when (>= (.indexOf fp rp) 0)
            ;; generate a new file in target-folder
            (spitUtf8 (io/file tp nm) s)
            (swap! RESULT + n)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn sockHandler
  ""
  []
  (workStream<>
    (script<>
      #(let
         [^Job job %2
          ^SocketEvent ev (.event job)
          dis (DataInputStream. (.sockIn ev))
          dos (DataOutputStream. (.sockOut ev))
          nm (.readInt dis)]
         (swap! RESULT + nm)
         (.writeInt dos (int nm))
         (.flush dos)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn jmsHandler
  ""
  []
  (workStream<>
    (script<>
      #(let [^JmsEvent ev (.event ^Job %2)
             ^TextMessage msg (.message ev)
             s (.getText msg)]
         (assert (hgl? s))
         (swap! RESULT + 8)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn mailHandler
  ""
  []
  (workStream<>
    (script<>
      #(let [^EmailEvent ev (.event ^Job %2)
             ^MimeMessage msg (.message ev)
             _ (assert (some? msg))
             ^Multipart p (.getContent msg)]
         (assert (some? p))
         (swap! RESULT + 8)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn httpHandler
  ""
  []
  (workStream<>
    (script<>
      #(let [^HttpEvent ev (.event ^Job %2)
             soc (.socket ev)
             res (httpResult<> soc (.msgGist ev))]
         (.setContentType res "text/plain")
         (.setContent res "hello")
         (replyResult soc res)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest czlabtestwabbit-svcs

  (is (let [etype :czlab.wabbit.io.http/HTTP
            m (*emitter-defs* etype)
            c (:conf m)
            ^Container
            ctr (mock :container)
            s (service<> ctr etype "t" c)]
        (.init s
               {:handler "czlabtest.wabbit.svcs/httpHandler"
                :host "localhost"
                :port 8888})
        (.start s)
        (safeWait 1000)
        (let [res (h1get "http://localhost:8888/test/get/xxx")
              ^WholeResponse
              rc (deref res 2000 nil)
              z (some-> rc
                        (.content ) (.stringify))]
          (.stop s)
          (.dispose s)
          (.dispose ctr)
          (= "hello" z))))

  (is (let [_ (sysProp! "wabbit.mock.mail.proto" "imaps")
            etype :czlab.wabbit.io.mails/IMAP
            m (*emitter-defs* etype)
            c (:conf m)
            ^Container
            ctr (mock :container)
            s (service<> ctr etype "t" c)]
        (reset! RESULT 0)
        (.init s
               {:handler "czlabtest.wabbit.svcs/mailHandler"
                :host "localhost"
                :port 7110
                :intervalSecs 1
                :username "test1"
                :passwd "secret"})
        (.start s)
        (safeWait 3000)
        (.stop s)
        (.dispose ctr)
        (> @RESULT 8)))

  (is (let [_ (sysProp! "wabbit.mock.mail.proto" "pop3s")
            etype :czlab.wabbit.io.mails/POP3
            m (*emitter-defs* etype)
            c (:conf m)
            ^Container
            ctr (mock :container)
            s (service<> ctr etype "t" c)]
        (reset! RESULT 0)
        (.init s
               {:handler "czlabtest.wabbit.svcs/mailHandler"
                :host "localhost"
                :port 7110
                :intervalSecs 1
                :username "test1"
                :passwd "secret"})
        (.start s)
        (safeWait 3000)
        (.stop s)
        (.dispose ctr)
        (> @RESULT 8)))

  (is (let [_ (sysProp! "wabbit.mock.jms.loopsecs" "1")
            etype :czlab.wabbit.io.jms/JMS
            m (*emitter-defs* etype)
            c (assoc (:conf m)
                     :contextFactory "czlab.wabbit.mock.jms.MockContextFactory"
                     :providerUrl "java://aaa"
                     ;;:connFactory "tcf"
                     ;;:destination "topic.abc"
                     :connFactory "qcf"
                     :destination "queue.xyz"
                     :handler "czlabtest.wabbit.svcs/jmsHandler")
            ^Container
            ctr (mock :container)
            s (service<> ctr etype "t" c)]
        (reset! RESULT 0)
        (.init s
               {:jndiUser "root"
                :jndiPwd "root"
                :jmsUser "anonymous"
                :jmsPwd "anonymous"})
        (.start s)
        (safeWait 3000)
        (.stop s)
        (.dispose ctr)
        (> @RESULT 8)))

  (is (let [_ (sysProp! "wabbit.mock.jms.loopsecs" "1")
            etype :czlab.wabbit.io.jms/JMS
            m (*emitter-defs* etype)
            c (assoc (:conf m)
                     :contextFactory "czlab.wabbit.mock.jms.MockContextFactory"
                     :providerUrl "java://aaa"
                     ;;:connFactory "tcf"
                     ;;:destination "topic.abc"
                     :connFactory "qcf"
                     :destination "queue.xyz"
                     :handler "czlabtest.wabbit.svcs/jmsHandler")
            ^Container
            ctr (mock :container)
            s (service<> ctr etype "t" c)]
        (reset! RESULT 0)
        (.init s
               {:jndiUser "root"
                :jndiPwd "root"
                :jmsUser "anonymous"
                :jmsPwd "anonymous"})
        (.start s)
        (safeWait 3000)
        (.stop s)
        (.dispose ctr)
        (> @RESULT 8)))

  (is (let [etype :czlab.wabbit.io.socket/Socket
            m (*emitter-defs* etype)
            host "localhost"
            port 5555
            c (assoc (:conf m)
                     :host host
                     :port port
                     :handler "czlabtest.wabbit.svcs/sockHandler")
            ^Container
            ctr (mock :container)
            s (service<> ctr etype "t" c)]
        (.init s {})
        (.start s)
        (reset! RESULT 0)
        (dotimes [n 2]
          (safeWait 1000)
          (with-open [soc (Socket. host (int port))]
             (let [os (.getOutputStream soc)
                   is (.getInputStream soc)
                   dis (DataInputStream. is)]
               (doto (DataOutputStream. os)
                 (.writeInt (int 8))
                 (.flush))
               (let [nm (.readInt dis)]
                 (swap! RESULT + nm)))))
        (.stop s)
        (.dispose ctr)
        (== @RESULT 32)))


  (is (let [etype :czlab.wabbit.io.loops/OnceTimer
            m (*emitter-defs* etype)
            c (assoc (:conf m)
                     :delaySecs 1
                     :handler "czlabtest.wabbit.svcs/testHandler")
            ^Container
            ctr (mock :container)
            s (service<> ctr etype "t" c)]
        (reset! RESULT 0)
        (.start s)
        (safeWait 2000)
        (.stop s)
        (.dispose ctr)
        (== 8 @RESULT)))

  (is (let [etype :czlab.wabbit.io.loops/RepeatingTimer
            m (*emitter-defs* etype)
            c (assoc (:conf m)
                     :delaySecs 1
                     :intervalSecs 1
                     :handler "czlabtest.wabbit.svcs/testHandler")
            ^Container
            ctr (mock :container)
            s (service<> ctr etype "t" c)]
        (reset! RESULT 0)
        (.start s)
        (safeWait 3500)
        (.stop s)
        (.dispose ctr)
        (> @RESULT 8)))

  (is (let [etype :czlab.wabbit.io.files/FilePicker
            m (*emitter-defs* etype)
            root "/wdrive/tmp";;*TEMPFILE-REPO*
            from (str root "/from")
            to (str root "/to")
            firstfn (str from "/" (juid))
            c (assoc (:conf m)
                     :targetFolder from
                     :recvFolder to
                     :fmask ""
                     :intervalSecs 1
                     :delaySecs 0
                     :handler "czlabtest.wabbit.svcs/fileHandler")
            ^Container
            ctr (mock :container)
            s (service<> ctr etype "t" c)]
        (.init s {})
        (deleteDir from)
        (deleteDir to)
        (mkdirs from)
        (mkdirs to)
        (spitUtf8 firstfn "8")
        (reset! RESULT 0)
        (.start s)
        (safeWait 1000)
        (touch! firstfn)
        (safeWait 3000)
        (.stop s)
        (.dispose ctr)
        (deleteDir from)
        (deleteDir to)
        (> @RESULT 8)))



  (is (string? "That's all folks!")))

;;(clojure.test/run-tests 'czlabtest.wabbit.svcs)


