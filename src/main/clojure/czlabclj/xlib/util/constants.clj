;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.xlib.util.constants)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^String TS_REGEX "^\\d\\d\\d\\d-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])\\s\\d\\d:\\d\\d:\\d\\d")
(def ^String DT_REGEX "^\\d\\d\\d\\d-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])$")

(def ^String TS_FMT_NANO "yyyy-MM-dd HH:mm:ss.fffffffff" )
(def ^String TS_FMT "yyyy-MM-dd HH:mm:ss")

(def ^String DT_FMT_MICRO "yyyy-MM-dd'T'HH:mm:ss.SSS" )
(def ^String DT_FMT "yyyy-MM-dd'T'HH:mm:ss" )
(def ^String DATE_FMT "yyyy-MM-dd" )

(def ^String ISO8601_FMT "yyyy-MM-dd'T'HH:mm:ss.SSSZ" )

(def ^String USASCII "ISO-8859-1" )
(def ^String UTF16 "UTF-16" )
(def ^String UTF8 "UTF-8" )
(def ^String SLASH   "/" )
(def ^String PATHSEP   SLASH )

(def BOOLS #{ "true", "yes", "on", "ok", "active", "1"} )

(def MONTHS [ "JAN" "FEB" "MAR" "APR" "MAY" "JUN" "JUL" "AUG" "SEP" "OCT" "NOV" "DEC" ] )


(def ^String COPYRIGHT "Copyright (c) 2008-2015, Ken Leung. All rights reserved.")




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private constants-eof nil)


