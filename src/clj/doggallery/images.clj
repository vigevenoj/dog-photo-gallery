(ns doggallery.images
  (:require
    [clojure.java.io :as io]
    [pantomime.mime :refer [mime-type-of]]
    [remworks.exif-reader :as exif]
    [buddy.core.codecs :as codecs]
    [doggallery.config :refer [env]])
  (:import java.util.Base64
           javax.crypto.Mac
           javax.crypto.spec.SecretKeySpec
           (java.io ByteArrayOutputStream)))

; These keys are the ones used for basic data persistence
; we use date-time-original for the "taken" field
(def core-exif-keys [:date-time :date-time-original
                     :gps-altitude :gps-date-stamp
                     :gps-latitude :gps-latitude-ref
                     :gps-longitude :gps-longitude-ref])

; should use pantomime.media/image? instead
(def image-file-types '("image/jpeg" "image/png" "image/heif" "image/heic"))

(defn is-image-file?
  "Return true if this file is an image type we can handle"
  [file]
  (boolean (some #{(mime-type-of (io/as-file file))} image-file-types)))

(defn single-image-full-metadata
      "Return the full map of EXIF metadata from an image"
      [image]
      (exif/from-jpeg (io/as-file image)))

(defn single-image-core-metadata
      "Return date and location from EXIF metadata in an image"
      [image]
      (select-keys
        (exif/from-jpeg (io/as-file image))
        core-exif-keys))

(defn file->bytes
  "Convert a file to byte array, in order to store it in postgres"
  [file]
  (with-open [xin (io/input-stream file)
              xout (ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(defn signed-imgproxy-url
  "Generate signed URL for imgproxy"
  ; adapted from https://github.com/imgproxy/imgproxy/blob/master/examples/signature.java
  ; this isn't very idiomatic clojure but once tests pass it can be fixed
  ([url resize width height gravity enlarge extension]
   (signed-imgproxy-url (codecs/hex->bytes (env :imageproxy-key)) (codecs/hex->bytes (env :imageproxy-salt)) url resize width height gravity enlarge extension))
  ([imgproxy-key salt url resize width height gravity enlarge extension]
   (let [hmacsha256 "HmacSHA256"
         encodedUrl (-> (Base64/getUrlEncoder) .withoutPadding (.encodeToString (.getBytes url)))
         path (str "/" resize "/" width "/" height "/" gravity "/" enlarge "/" encodedUrl "." extension)
         secret-key-spec (SecretKeySpec. imgproxy-key hmacsha256)
         sha256hmac (Mac/getInstance hmacsha256)
         hash (-> (Base64/getUrlEncoder) .withoutPadding (.encodeToString
                                                           (.doFinal
                                                             (doto sha256hmac
                                                               (.init secret-key-spec)
                                                               (.update salt))
                                                             (.getBytes path))))]
     (str "/" hash path))))