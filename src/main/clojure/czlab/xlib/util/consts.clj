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


