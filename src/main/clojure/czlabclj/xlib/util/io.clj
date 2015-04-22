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

(ns ^{:doc "Util functions related to stream/io."
      :author "kenl" }

  czlabclj.xlib.util.io

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.core :only [Try!]])

  (:import  [java.util.zip GZIPInputStream GZIPOutputStream]
            [java.io ByteArrayInputStream
             ByteArrayOutputStream DataInputStream
             DataInputStream DataOutputStream
             FileInputStream FileOutputStream
             CharArrayWriter OutputStreamWriter
             File InputStream InputStreamReader
             OutputStream Reader Writer]
            [java.nio ByteBuffer CharBuffer]
            [java.nio.charset Charset]
            [com.zotohlab.frwk.io XData XStream]
            [org.apache.commons.codec.binary Base64]
            [org.apache.commons.lang3 StringUtils]
            [org.apache.commons.io IOUtils]
            [org.xml.sax InputSource]
            [java.nio.charset Charset]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;(def ^:private HEX_CHS [ \0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \A \B \C \D \E \F ])
(def ^:private ^chars HEX_CHS (.toCharArray "0123456789ABCDEF"))
(def ^:private SZ_10MEG (* 1024 1024 10))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti WriteBytes "Write this long value out as byte[]." class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ToBytes "Convert char[] to byte[]."

  ^bytes
  [^chars chArray ^String encoding]

  (.array (.encode (Charset/forName encoding)
                   (CharBuffer/wrap chArray)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ToChars "Convert byte[] to char[]."

  ^chars
  [^bytes byteArray ^String encoding]

  (.array (.decode (Charset/forName encoding)
                   (ByteBuffer/wrap byteArray)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadLong "Return a long by scanning the byte[]."

  [^bytes byteArray]

  (.readLong (DataInputStream. (ByteArrayInputStream. byteArray))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadInt "Return an int by scanning the byte[]."

  [^bytes byteArray]

  (.readInt (DataInputStream. (ByteArrayInputStream. byteArray)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod WriteBytes Integer

  ^bytes
  [nnum]

  (with-open [baos (ByteArrayOutputStream. (int 4096)) ]
    (doto (DataOutputStream. baos)
      (.writeInt (int nnum))
      (.flush))
    (.toByteArray baos)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod WriteBytes Long

  ^bytes
  [nnum]

  (with-open [baos (ByteArrayOutputStream. (int 4096)) ]
    (doto (DataOutputStream. baos)
      (.writeLong ^long nnum)
      (.flush))
    (.toByteArray baos)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeTmpfile "Create a temp file in the temp dir."

  (^File
    []
    (MakeTmpfile "" ""))

  (^File
    [^String pfx ^String sux]
    (File/createTempFile (if (cstr/blank? pfx) "tmp-" pfx)
                         (if (cstr/blank? sux) ".dat" sux)
                         (com.zotohlab.frwk.io.IOUtils/workDir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NewlyTmpfile "Create a new temp file, optionally open it for write as stream."

  ([]
   (NewlyTmpfile false))

  ([open]
   (let [f (MakeTmpfile) ]
     (if open
       [f (FileOutputStream. f) ]
       [ f nil ]))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Streamify "Wrapped these bytes in an input-stream."

  ^InputStream
  [^bytes bits]

  (when-not (nil? bits)
    (ByteArrayInputStream. bits)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeBitOS "Make a byte array output stream."

  ^ByteArrayOutputStream
  []

  (ByteArrayOutputStream. (int 4096)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HexifyChars "Turn bytes into hex chars."

  ^chars
  [^bytes bits]

  (let [len (* 2 (if (nil? bits) 0 (alength bits)))
        out (char-array len)]
    (loop [k 0 pos 0]
      (when-not (>= pos len)
        (let [n (bit-and (aget ^bytes bits k) 0xff) ]
          (aset-char out pos
                     (aget ^chars HEX_CHS (bit-shift-right n 4))) ;; high 4 bits
          (aset-char out (+ pos 1)
                     (aget ^chars HEX_CHS (bit-and n 0xf))) ;; low 4 bits
          (recur (inc k) (+ 2 pos)) )))
    out
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HexifyString "Turn bytes into hex string."

  ^String
  [^bytes bits]

  (when-not (nil? bits)
    (String. (HexifyChars bits))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti OpenFile "Open this file path." class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Gzip "Gzip these bytes."

  ^bytes
  [^bytes bits]

  (when-not (nil? bits)
    (let [baos (MakeBitOS)]
      (with-open [g (GZIPOutputStream. baos)]
        (.write g bits, 0, (alength bits)))
      (.toByteArray baos))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Gunzip "Gunzip these bytes."

  ^bytes
  [^bytes bits]

  (when-not (nil? bits)
    (IOUtils/toByteArray (GZIPInputStream. (Streamify bits)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResetStream! "Call reset on this input stream."

  [^InputStream inp]

  (Try! (when-not (nil? inp) (.reset inp)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod OpenFile String

  ^XStream
  [^String fp]

  (when-not (nil? fp)
    (XStream. (File. fp))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod OpenFile File

  ^XStream
  [^File f]

  (when-not (nil? f)
    (XStream. f)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FromGZB64 "Unzip content which is base64 encoded + gziped."

  ^bytes
  [^String gzb64]

  (when-not (nil? gzb64)
    (Gunzip (Base64/decodeBase64 gzb64))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ToGZB64 "Zip content and then base64 encode it."

  ^String
  [^bytes bits]

  (when-not (nil? bits)
    (Base64/encodeBase64String (Gzip bits))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Available "Get the available bytes in this stream."

  ;; int
  [^InputStream inp]

  (if (nil? inp)
    0
    (.available inp)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CopyStream "Copy content from this input-stream to a temp file."

  ^File
  [^InputStream inp]

  (let [[^File fp ^OutputStream os]
        (NewlyTmpfile true) ]
    (try
      (IOUtils/copy inp os)
      (finally
        (IOUtils/closeQuietly os)))
    fp
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CopyBytes "Copy x number of bytes from the source input-stream."

  [^InputStream src ^OutputStream out bytesToCopy]

  (when (> bytesToCopy 0)
    (IOUtils/copyLarge src out 0 ^long bytesToCopy)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResetSource! "Reset an input source."

  [^InputSource inpsrc]

  (when-not (nil? inpsrc)
    (let [rdr (.getCharacterStream inpsrc)
          ism (.getByteStream inpsrc) ]
      (Try! (when-not (nil? ism) (.reset ism)) )
      (Try! (when-not (nil? rdr) (.reset rdr)) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeXData "Return a newly created XData."

  (^XData [] (MakeXData false))

  (^XData [usefile]
          (if usefile
            (XData. (MakeTmpfile))
            (XData.)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- swap-bytes ""

  [^ByteArrayOutputStream baos]

  (let [[^File fp ^OutputStream os]
        (NewlyTmpfile true) ]
    (doto os
      (.write (.toByteArray baos))
      (.flush))
    (.close baos)
    [fp os]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- swap-read-bytes ""

  [^InputStream inp ^ByteArrayOutputStream baos]

  (let [[^File fp ^OutputStream os]
        (swap-bytes baos)
        bits (byte-array 4096) ]
    (try
      (loop [c (.read inp bits) ]
        (if (< c 0)
          (XData. fp)
          (if (= c 0)
            (recur (.read inp bits))
            (do
              (.write os bits 0 c)
              (recur (.read inp bits))))))
      (finally
        (IOUtils/closeQuietly os)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- slurp-bytes ""

  ^XData
  [^InputStream inp lmt]

  (let [bits (byte-array 4096)
        baos (MakeBitOS) ]
    (loop [c (.read inp bits)
           cnt 0 ]
      (if (< c 0)
        (XData. baos)
        (if (= c 0)
          (recur (.read inp bits) cnt)
          (do ;; some data
            (.write baos bits 0 c)
            (if (> (+ c cnt) lmt)
              (swap-read-bytes inp baos)
              (recur (.read inp bits) (+ c cnt)) )))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- swap-chars ""

  [^CharArrayWriter wtr]

  (let [[^File fp ^OutputStream out]
        (NewlyTmpfile true)
        bits (.toCharArray wtr)
        os (OutputStreamWriter. out "utf-8") ]
    (doto os
      (.write bits)
      (.flush))
    (IOUtils/closeQuietly wtr)
    [fp os]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- swap-read-chars ""

  ^XData
  [^Reader inp ^CharArrayWriter wtr]

  (let [[^File fp ^Writer os]
        (swap-chars wtr)
        bits (char-array 4096) ]
    (try
      (loop [c (.read inp bits) ]
        (if (< c 0)
          (XData. fp)
          (if (= c 0)
            (recur (.read inp bits))
            (do
              (.write os bits 0 c)
              (recur (.read inp bits))))
        ))
      (finally
        (IOUtils/closeQuietly os)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- slurp-chars ""

  ^XData
  [^Reader inp lmt]

  (let [wtr (CharArrayWriter. (int 10000))
        bits (char-array 4096) ]
    (loop [c (.read inp bits) cnt 0 ]
      (if (< c 0)
        (XData. wtr)
        (if (= c 0)
          (recur (.read inp bits) cnt)
          (do
            (.write wtr bits 0 c)
            (if (> (+ c cnt) lmt)
              (swap-read-chars inp wtr)
              (recur (.read inp bits) (+ c cnt)))))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadBytes "Read bytes and return a XData."

  (^XData
    [^InputStream inp usefile]
    (slurp-bytes inp (if usefile
                       1
                       (com.zotohlab.frwk.io.IOUtils/streamLimit))))

  (^XData
    [^InputStream inp]
    (slurp-bytes inp (com.zotohlab.frwk.io.IOUtils/streamLimit))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadChars "Read chars and return a XData."

  (^XData
    [^Reader rdr]
    (slurp-chars rdr
                 (com.zotohlab.frwk.io.IOUtils/streamLimit)))

  (^XData
    [^Reader rdr usefile]
    (slurp-chars rdr (if usefile
                       1
                       (com.zotohlab.frwk.io.IOUtils/streamLimit)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MorphChars "Convert these bytes to chars."

  (^chars
    [^bytes bits]
    (MorphChars bits (Charset/forName "utf-8")) )

  (^chars
    [^bytes bits ^Charset charSet]
;;    (1 to min(b.length, count)).foreach { (i) =>
;;      val b1 = b(i-1)
;;      ch(i-1) = (if (b1 < 0) { 256 + b1 } else b1 ).asInstanceOf[Char]
;;    }
    (when-not (nil? bits)
      (IOUtils/toCharArray (Streamify bits) charSet))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Diff? "Tests if both streams are the same or different at byte level."

  [^InputStream inp1 ^InputStream inp2]

  (cond
    (and (nil? inp1) (nil? inp2)) false
    (or (nil? inp1) (nil? inp2)) true
    :else (not (IOUtils/contentEquals inp1 inp2))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private io-eof nil)

