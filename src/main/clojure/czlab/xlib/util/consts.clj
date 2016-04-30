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

  czlab.xlib.util.consts)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(defonce ^String TS_REGEX "^\\d\\d\\d\\d-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])\\s\\d\\d:\\d\\d:\\d\\d")
(defonce ^String DT_REGEX "^\\d\\d\\d\\d-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])$")

(defonce ^String TS_FMT_NANO "yyyy-MM-dd HH:mm:ss.fffffffff" )
(defonce ^String TS_FMT "yyyy-MM-dd HH:mm:ss")

(defonce ^String DT_FMT_MICRO "yyyy-MM-dd'T'HH:mm:ss.SSS" )
(defonce ^String DT_FMT "yyyy-MM-dd'T'HH:mm:ss" )
(defonce ^String DATE_FMT "yyyy-MM-dd" )

(defonce ^String ISO8601_FMT "yyyy-MM-dd'T'HH:mm:ss.SSSZ" )

(defonce ^String USASCII "ISO-8859-1" )
(defonce ^String UTF16 "UTF-16" )
(defonce ^String UTF8 "UTF-8" )
(defonce ^String SLASH   "/" )
(defonce ^String PATHSEP   SLASH )

(defonce EV_OPTS :____eventoptions)
(defonce JS_LAST :____lastresult)
(defonce JS_CRED :credential)
(defonce JS_USER :principal)
(defonce JS_FLATLINE :____flatline)

(defonce BOOLS #{ "true", "yes", "on", "ok", "active", "1"} )

(defonce MONTHS ["JAN" "FEB" "MAR" "APR" "MAY" "JUN"
             "JUL" "AUG" "SEP" "OCT" "NOV" "DEC" ] )

(defonce ^String COPYRIGHT "Copyright (c) 2008-2015, Ken Leung. All rights reserved.")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


