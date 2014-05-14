;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.


(ns ^{ :doc "This is a utility class that provides various MIME related functionality."
       :author "kenl" }

  cmzlabsclj.nucleus.util.mime

  (:require [clojure.tools.logging :as log  :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.nucleus.util.core :only [Bytesify Try! IntoMap] ])
  (:use [cmzlabsclj.nucleus.util.meta :only [BytesClass] ])
  (:use [cmzlabsclj.nucleus.util.str :only [nsb hgl?] ])
  (:use [cmzlabsclj.nucleus.util.io :only [Streamify] ])

  (:import (org.apache.commons.lang3 StringUtils))
  (:import (java.net URLDecoder URLEncoder))
  (:import (java.io IOException InputStream File))
  (:import (java.net URL))
  (:import (java.util.regex Pattern Matcher))
  (:import (java.util Properties))
  (:import (javax.mail Message))
  (:import (com.zotohlabs.frwk.mime MimeFileTypes)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def CTE_QUOTED "quoted-printable")
(def CTE_7BIT "7bit")
(def CTE_8BIT "8bit")
(def CTE_BINARY "binary")
(def CTE_BASE64 "base64")

(def MIME_USER_PROP  "mime.rfc2822.user")
(def MIME_USER_JAVAMAIL   "javamail")
(def DEF_USER  "popeye")
(def MIME_USER_PREFIX   "zotohlabs")
(def DEF_HOST  "localhost")
(def MIME_HEADER_MSGID  "Message-ID")
(def MIME_MULTIPART_BOUNDARY  "boundary")
(def DOT   ".")
(def AT  "@")
(def CH_DOT   \. )
(def CH_AT  \@)
(def STR_LT   "<")
(def STR_GT  ">")
(def ALL   -1)
(def ALL_ASCII   1)
(def MOSTLY_ASCII   2)
(def MOSTLY_NONASCII   3)

;; Capitalized MIME constants to use when generating MIME headers)
;; for messages to be transmitted.)
(def AS2_VER_ID    "1.1")
(def UA  "user-agent")
(def TO   "to")
(def FROM  "from")
(def AS2_VERSION    "as2-version")
(def AS2_TO   "as2-to")
(def AS2_FROM  "as2-from")
(def SUBJECT    "subject")
(def CONTENT_TYPE  "content-type")
(def CONTENT     "content")
(def CONTENT_NAME   "content-name")
(def CONTENT_LENGTH  "content-length")
(def CONTENT_LOC  "content-Location")
(def CONTENT_ID    "content-id")
(def CONTENT_TRANSFER_ENCODING  "content-transfer-encoding")
(def CONTENT_DISPOSITION   "content-disposition")
(def DISPOSITION_NOTIFICATION_TO  "disposition-notification-to")
(def DISPOSITION_NOTIFICATION_OPTIONS  "disposition-notification-options")
(def SIGNED_REC_MICALG "signed-receipt-micalg")
(def MESSAGE_ID   "message-id")
(def ORIGINAL_MESSAGE_ID   "original-message-id")
(def RECEIPT_DELIVERY_OPTION   "receipt-delivery-option")
(def DISPOSITION  "disposition")
(def DATE    "date")
(def MIME_VERSION   "mime-version")
(def FINAL_RECIPIENT   "final-recipient")
(def ORIGINAL_RECIPIENT   "original-recipient")
(def RECV_CONTENT_MIC   "received-content-mic")

(def RFC822 "rfc822")
(def RFC822_PFX (str RFC822 "; "))

(def APP_XML "application/xml")
(def TEXT_PLAIN "text/plain")
(def APP_OCTET "application/octet-stream")
(def PKCS7SIG "pkcs7-signature")
(def TEXT_HTML "text/html")
(def TEXT_XML "text/xml")
(def MSG_DISP "message/disposition-notification")

(def ERROR   "error")
(def FAILURE "failure")
(def WARNING  "warning")
(def HEADERS  "headers")

(def ISO_8859_1 "iso-8859-1")
(def US_ASCII "us-ascii")

(def CRLF "\r\n")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^Pattern ^:private _extRegex (Pattern/compile "^.*\\.([^.]+)$"))
(def ^:private _mime_cache (atom {}))
(def ^:private _mime_types (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MimeCache "Cache of most MIME types."

  []

  @_mime_cache)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- is-pkcs7mime? ""

  [^String s]

  (>= (.indexOf s "application/x-pkcs7-mime") 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetCharset "Get charset from this content-type string."

  ^String
  [^String cType]

  (let [ pos (-> (nsb cType) (cstr/lower-case )
                 (.indexOf "charset="))
         rc "utf-8" ]
         ;;rc "ISO-8859-1" ]
    (if (> pos 0)
        (let [ s (.substring cType (+ pos 8))
               p (StringUtils/indexOfAny s "; \t\r\n") ]
          (if (> p 0) (.substring s 0 p) s))
        rc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsSigned? "Returns true if this content-type indicates signed."

  [^String cType]

  (let [ ct (cstr/lower-case (nsb cType)) ]
    (or (>= (.indexOf ct "multipart/signed") 0)
        (and (is-pkcs7mime? ct) (>= (.indexOf ct "signed-data") 0)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsEncrypted? "Returns true if this content-type indicates encrypted."

  [^String cType]

  (let [ ct (cstr/lower-case (nsb cType)) ]
    (and (is-pkcs7mime? ct) (>= (.indexOf ct "enveloped-data") 0))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsCompressed? "Returns true if this content-type indicates compressed."

  [^String cType]

  (let [ ct (cstr/lower-case (nsb cType)) ]
    (and (>= (.indexOf ct "application/pkcs7-mime") 0)
         (>= (.indexOf ct "compressed-data") 0))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsMDN? "Returns true if this content-type indicates MDN."

  [^String cType]

  (let [ ct (cstr/lower-case (nsb cType)) ]
    (and (>= (.indexOf ct "multipart/report") 0)
         (>= (.indexOf ct "disposition-notification") 0))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeStream "Turn this object into some form of stream, if possible."

  ^InputStream
  [^Object obj]

  (cond
    (instance? InputStream obj)
    obj

    (instance? (BytesClass) obj)
    (Streamify obj)

    (instance? String obj)
    (Streamify (Bytesify obj))

    :else
    nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn UrlDecode "URL decode this string."

  [^String u]

  (if (nil? u)
      nil
      (Try!  (URLDecoder/decode u "utf-8") )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn UrlEncode "URL encode this string."

  [^String u]

  (if (nil? u)
      nil
      (Try!  (URLEncoder/encode u "utf-8") )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GuessMimeType "Guess the MIME type of file."

  (^String
    [^File file]
    (GuessMimeType file ""))

  (^String
    [^File file ^String dft]
    (let [ ^Matcher mc (.matcher _extRegex (cstr/lower-case (.getName file)))
           ex (if (.matches mc) (.group mc 1) "")
           p (if (hgl? ex) ((keyword ex) (MimeCache))) ]
      (if (hgl? p) p dft))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GuessContentType "Guess the content-type of file."

  (^String [^File file ^String enc ^String dft]
    (let [ mt (GuessMimeType file)
           ct (if (hgl? mt) mt dft) ]
      (if (not (.startsWith ct "text/"))
          ct
          (str ct "; charset=" enc))))

  (^String [^File file]
    (GuessContentType file "utf-8" "application/octet-stream" )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SetupCache "Load file mime-types as a map."

  [^URL fileUrl]

  (with-open [ inp (.openStream fileUrl) ]
    (let [ ps (Properties.) ]
      (.load ps inp)
      (reset! _mime_types (MimeFileTypes/makeMimeFileTypes ps))
      (reset! _mime_cache (IntoMap ps)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private mime-eof nil)
