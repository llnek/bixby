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

(ns ^{:doc "Date related utilities"
      :author "kenl" }

  czlab.xlib.util.dates

  (:require
    [czlab.xlib.util.str :refer [Has? HasAny? nichts? nsb]]
    [czlab.xlib.util.core :refer [try!]])

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs])

  (:use [czlab.xlib.util.consts])

  (:import
    [java.text ParsePosition SimpleDateFormat]
    [java.util Locale TimeZone SimpleTimeZone
     Date Calendar GregorianCalendar]
    [java.sql Timestamp]
    [org.apache.commons.lang3 StringUtils]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn LeapYear?

  "true if this is a leap year"

  [year]

  (cond
    (zero? (mod year 400)) true
    (zero? (mod year 100)) false
    :else (zero? (mod year 4))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- hastzpart? ""

  [^String s]

  (let [pos (StringUtils/indexOf s ",; \t\r\n\f")
        ss (if (> pos 0)
             (.substring s (inc pos))
             "") ]
    (or (HasAny? ss ["+" "-"])
        (.matches ss "\\s*[a-zA-Z]+\\s*"))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- hastz?

  "true if this datetime string contains some timezone info"

  [^String dateStr]

  (let [p1 (.lastIndexOf dateStr (int \.))
        p2 (.lastIndexOf dateStr (int \:))
        p3 (.lastIndexOf dateStr (int \-))
        p4 (.lastIndexOf dateStr (int \/)) ]
    (cond
      (> p1 0)
      (hastzpart? (.substring dateStr (inc p1)))

      (> p2 0)
      (hastzpart? (.substring dateStr (inc p2)))

      (> p3 0)
      (hastzpart? (.substring dateStr (inc p3)))

      (> p4 0)
      (hastzpart? (.substring dateStr (inc p4)))

      :else false)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ParseTimestamp

  "Convert string into a valid Timestamp object
  *tstr* conforming to the format \"yyyy-mm-dd hh:mm:ss.[fff...]\""

  ^Timestamp
  [^String tstr]

  (try! (Timestamp/valueOf tstr) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ParseDate

  "Convert string into a Date object"

  ^Date
  [^String tstr ^String fmt]

  (when-not (or (empty? tstr)
                (empty? fmt))
    (.parse (SimpleDateFormat. fmt) tstr)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- parseIso8601

  "Parses datetime in ISO8601 format"

  ^Date
  [^String tstr]

  (when-not (empty? tstr)
    (let [fmt (if (Has? tstr \:)
                (if (Has? tstr \.) DT_FMT_MICRO DT_FMT )
                DATE_FMT ) ]
      (ParseDate tstr (if (hastz? tstr) (str fmt "Z") fmt)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FmtTimestamp

  "Convert Timestamp into a string value"

  ^String
  [^Timestamp ts]

  (nsb ts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FmtDate

  "Convert Date into string value"

  (^String
    [^Date dt]
    (FmtDate dt DT_FMT_MICRO nil))

  (^String
    [^Date dt fmt]
    (FmtDate dt fmt nil))

  (^String
    [^Date dt fmt ^TimeZone tz]
    (if (or (nil? dt) (empty? fmt))
      ""
      (let [df (SimpleDateFormat. fmt) ]
        (when (some? tz) (.setTimeZone df tz))
        (.format df dt)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FmtGMT

  "Convert Date object into a string - GMT timezone"

  ^String
  [^Date dt]

  (FmtDate dt DT_FMT_MICRO (SimpleTimeZone. 0 "GMT")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- add ""

  ^Calendar
  [^Calendar cal calendarField amount]

  (when (some? cal)
    (doto (GregorianCalendar. (.getTimeZone cal))
      (.setTime (.getTime cal))
      (.add (int calendarField) ^long amount))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GCal* ""

  ^Calendar
  [date]

  (doto (GregorianCalendar.) (.setTime date)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddYears

  "Add n more years to the calendar"

  ^Calendar
  [^Calendar cal yrs]

  (add cal Calendar/YEAR yrs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddMonths

  "Add n more months to the calendar"

  ^Calendar
  [^Calendar cal mts]

  (add cal Calendar/MONTH mts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddDays

  "Add n more days to the calendar"

  ^Calendar
  [^Calendar cal days]

  (add cal Calendar/DAY_OF_YEAR days))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PlusMonths

  "Add n months"

  ^Date
  [months]

  (let [now (GCal* (Date.)) ]
    (-> (AddMonths now months)
        (.getTime))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PlusYears

  "Add n years"

  ^Date
  [years]

  (let [now (GCal* (Date.)) ]
    (-> (AddYears now years)
        (.getTime))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PlusDays

  "Add n days"

  ^Date
  [days]

  (let [now (GCal* (Date.)) ]
    (-> (AddDays now days)
        (.getTime))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FmtCal

  "Formats time to yyyy-MM-ddThh:mm:ss"

  ^String
  [^Calendar cal]

  (java.lang.String/format
    (Locale/getDefault)
    "%1$04d-%2$02d-%3$02dT%4$02d:%5$02d:%6$02d"
    (into-array Object
                [(.get cal Calendar/YEAR)
                 (+ 1 (.get cal Calendar/MONTH))
                 (.get cal Calendar/DAY_OF_MONTH)
                 (.get cal Calendar/HOUR_OF_DAY)
                 (.get cal Calendar/MINUTE)
                 (.get cal Calendar/SECOND) ] )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GmtCal ""

  ^GregorianCalendar
  []

  (GregorianCalendar. (TimeZone/getTimeZone "GMT")) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DebugCal

  "Debug show a calendar's internal data"

  ^String
  [^Calendar cal]

  (cs/join ""
           ["{" (.. cal (getTimeZone) (getDisplayName) )  "} "
            "{" (.. cal (getTimeZone) (getID)) "} "
            "[" (.getTimeInMillis cal) "] "
            (FmtCal cal) ]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

