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

  czlab.wabbit.etc.svcs)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:dynamic *emitter-defs*
  {:czlab.wabbit.io.files/FilePicker
   {:info {:version "1.0"
           :name "FILEPicker" }
    :conf {:targetFolder "/home/dropbox"
           :recvFolder "/home/joe"
           ;; reg-ex, blank => all
           :fmask ""
           ;; 5mins
           :intervalSecs 300
           :delaySecs 0
           :handler "" }}
   :czlab.wabbit.io.mails/IMAP
   {:info {:version "1.0"
           :name "IMAP" }
    :conf {:host "imap.gmail.com"
           :port 993
           :deleteMsg? false
           :ssl? true
           :username "joe"
           :passwd "secret"
           :intervalSecs 300
           :delaySecs 0
           :handler "" } }
   :czlab.wabbit.io.jms/JMS
   {:info {:version "1.0"
           :name "JMS" }
    :conf {:contextFactory "czlab.wabbit.mock.jms.MockContextFactory"
           :providerUrl "java://aaa"
           :connFactory "tcf"
           :destination "topic.abc"
           :jndiUser "root"
           :jndiPwd "root"
           :jmsUser "anonymous"
           :jmsPwd "anonymous"
           :intervalSecs 5
           :handler "" } }
   :czlab.wabbit.io.http/HTTP
   {:info {:version "1.0"
           :name "HTTP" }
    :conf {:waitMillis (* 1000 300)
           :limitKB (* 1024 4)
           :sockTimeOut 0
           :host ""
           :port 8080
           :serverKey ""
           :passwd ""
           :handler "" } }
   :czlab.wabbit.io.http/WebMVC
   {:info {:version "1.0"
           :name "WebMVC" }
    :conf {:waitMillis (* 1000 300)
           :limitKB (* 1024 4)
           :sockTimeOut 0
           :host ""
           :port 8080
           :serverKey ""
           :passwd ""
           :sessionAgeSecs 2592000
           :maxIdleSecs 0
           :hidden true
           :domain ""
           :domainPath "/"
           :cacheAssets? false
           :maxAgeSecs 3600
           :useETags? false
           :errorHandler ""
           :handler "" } }
   :czlab.wabbit.io.loops/OnceTimer
   {:info {:version "1.0"
           :name "OnceTimer" }
    :conf {:delaySecs 0
           :handler "" } }
   :czlab.wabbit.io.mails/POP3
   {:info {:version "1.0"
           :name "POP3" }
    :conf {:host "pop.gmail.com"
           :port 995
           :deleteMsg? false
           :username "joe"
           :passwd "secret"
           :intervalSecs 300
           :delaySecs 0
           :ssl? true
           :handler "" } }
   :czlab.wabbit.io.loops/RepeatingTimer
   {:info {:version "1.0"
           :name "RepeatTimer" }
    :conf {:intervalSecs 300
           :delaySecs 0
           :handler "" } }
   :czlab.wabbit.io.socket/Socket
   {:info {:version "1.0"
           :name "SocketIO" }
    :conf {:host ""
           :port 8080
           :handler "" } } })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


