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


(ns ^{:doc "This is a utility class that provides
           various MIME related functionality"
      :author "kenl" }

  czlab.xlib.util.mime

  (:require
    [czlab.xlib.util.core :refer [Bytesify try! IntoMap]]
    [czlab.xlib.util.meta :refer [BytesClass]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.util.str :refer [lcase ucase hgl?]]
    [czlab.xlib.util.io :refer [Streamify]])

  (:import
    [org.apache.commons.lang3 StringUtils]
    [java.io IOException InputStream File]
    [java.net URL]
    [org.apache.commons.codec.net URLCodec]
    [java.util.regex Pattern Matcher]
    [java.util Properties]
    [javax.mail Message]
    [com.zotohlab.frwk.mime MimeFileTypes]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(defonce CTE_QUOTED "quoted-printable")
(defonce CTE_7BIT "7bit")
(defonce CTE_8BIT "8bit")
(defonce CTE_BINARY "binary")
(defonce CTE_BASE64 "base64")

(defonce MIME_USER_PROP  "mime.rfc2822.user")
(defonce MIME_USER_JAVAMAIL   "javamail")
(defonce DEF_USER  "popeye")
(defonce MIME_USER_PREFIX   "zotohlab")
(defonce DEF_HOST  "localhost")
(defonce MIME_HEADER_MSGID  "Message-ID")
(defonce MIME_MULTIPART_BOUNDARY  "boundary")
(defonce DOT   ".")
(defonce AT  "@")
(defonce CH_DOT   \. )
(defonce CH_AT  \@)
(defonce STR_LT   "<")
(defonce STR_GT  ">")
(defonce ALL   -1)
(defonce ALL_ASCII   1)
(defonce MOSTLY_ASCII   2)
(defonce MOSTLY_NONASCII   3)

;; Capitalized MIME constants to use when generating MIME headers)
;; for messages to be transmitted.)
(defonce AS2_VER_ID    "1.1")
(defonce UA  "user-agent")
(defonce TO   "to")
(defonce FROM  "from")
(defonce AS2_VERSION    "as2-version")
(defonce AS2_TO   "as2-to")
(defonce AS2_FROM  "as2-from")
(defonce SUBJECT    "subject")
(defonce CONTENT_TYPE  "content-type")
(defonce CONTENT     "content")
(defonce CONTENT_NAME   "content-name")
(defonce CONTENT_LENGTH  "content-length")
(defonce CONTENT_LOC  "content-Location")
(defonce CONTENT_ID    "content-id")
(defonce CONTENT_TRANSFER_ENCODING  "content-transfer-encoding")
(defonce CONTENT_DISPOSITION   "content-disposition")
(defonce DISPOSITION_NOTIFICATION_TO  "disposition-notification-to")
(defonce DISPOSITION_NOTIFICATION_OPTIONS  "disposition-notification-options")
(defonce SIGNED_REC_MICALG "signed-receipt-micalg")
(defonce MESSAGE_ID   "message-id")
(defonce ORIGINAL_MESSAGE_ID   "original-message-id")
(defonce RECEIPT_DELIVERY_OPTION   "receipt-delivery-option")
(defonce DISPOSITION  "disposition")
(defonce DATE    "date")
(defonce MIME_VERSION   "mime-version")
(defonce FINAL_RECIPIENT   "final-recipient")
(defonce ORIGINAL_RECIPIENT   "original-recipient")
(defonce RECV_CONTENT_MIC   "received-content-mic")

(defonce RFC822 "rfc822")
(defonce RFC822_PFX (str RFC822 "; "))

(defonce APP_XML "application/xml")
(defonce TEXT_PLAIN "text/plain")
(defonce APP_OCTET "application/octet-stream")
(defonce PKCS7SIG "pkcs7-signature")
(defonce TEXT_HTML "text/html")
(defonce TEXT_XML "text/xml")
(defonce MSG_DISP "message/disposition-notification")

(defonce ERROR   "error")
(defonce FAILURE "failure")
(defonce WARNING  "warning")
(defonce HEADERS  "headers")

(defonce ISO_8859_1 "iso-8859-1")
(defonce US_ASCII "us-ascii")

(defonce CRLF "\r\n")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^Pattern _extRegex (Pattern/compile "^.*\\.([^.]+)$"))
(def ^:private _mime_cache (atom {}))
(def ^:private _mime_types (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MimeCache* "Cache of most MIME types" [] @_mime_cache)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isPkcs7Mime? ""

  [^String s]

  (>= (.indexOf s "application/x-pkcs7-mime") 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetCharset

  "charset from this content-type string"

  ^String
  [^String cType]

  (let [pos (-> (str cType)
                (lcase )
                (.indexOf "charset="))
        rc "utf-8" ]
         ;;rc "ISO-8859-1" ]
    (if (> pos 0)
      (let [s (.substring cType (+ pos 8))
            p (StringUtils/indexOfAny s "; \t\r\n") ]
        (if (> p 0) (.substring s 0 p) s))
      rc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsSigned?

  "true if this content-type indicates signed"

  [^String cType]

  (let [ct (lcase (str cType)) ]
    (or (>= (.indexOf ct "multipart/signed") 0)
        (and (isPkcs7Mime? ct) (>= (.indexOf ct "signed-data") 0)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsEncrypted?

  "true if this content-type indicates encrypted"

  [^String cType]

  (let [ct (lcase (str cType)) ]
    (and (isPkcs7Mime? ct) (>= (.indexOf ct "enveloped-data") 0))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsCompressed?

  "true if this content-type indicates compressed"

  [^String cType]

  (let [ct (lcase (str cType)) ]
    (and (>= (.indexOf ct "application/pkcs7-mime") 0)
         (>= (.indexOf ct "compressed-data") 0))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsMDN?

  "true if this content-type indicates MDN"

  [^String cType]

  (let [ct (lcase (str cType)) ]
    (and (>= (.indexOf ct "multipart/report") 0)
         (>= (.indexOf ct "disposition-notification") 0))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeStream

  "Turn this object into some form of stream, if possible"

  ^InputStream
  [^Object obj]

  (condp instance? obj
    String (Streamify (Bytesify obj))
    InputStream obj
    (BytesClass) (Streamify obj)
    nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn UrlDecode

  "URL decode this string"

  ^String
  [^String u]

  (when-not (empty? u)
    (try! (-> (URLCodec. "utf-8")(.decode u)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn UrlEncode

  "URL encode this string"

  ^String
  [^String u]

  (when-not (empty? u)
    (try! (-> (URLCodec. "utf-8")(.encode u)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GuessMimeType

  "Guess the MIME type of file"

  ^String
  [^File file & [dft]]

  (let [^Matcher
        mc (->> (lcase (.getName file))
                (.matcher _extRegex))
        ex (if
             (.matches mc)
             (.group mc 1) "")
        p (if (hgl? ex)
            (get (MimeCache*) (keyword ex) )) ]
   (if (hgl? p) p (str dft))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GuessContentType

  "Guess the content-type of file"

  ^String
  [^File file & [enc dft]]

  (let [dft (or dft "application/octet-stream" )
        enc (or enc "utf-8")
        mt (GuessMimeType file)
        ^String ct (if (hgl? mt) mt dft) ]
    (if-not (.startsWith ct "text/")
      ct
      (str ct "; charset=" enc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SetupCache

  "Load file mime-types as a map"

  [^URL fileUrl]

  (with-open [inp (.openStream fileUrl) ]
    (let [ps (Properties.) ]
      (.load ps inp)
      (reset! _mime_types (MimeFileTypes/makeMimeFileTypes ps))
      (reset! _mime_cache (IntoMap ps)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

