(ns doggallery.images
  (:require
    [buddy.core.codecs :as codecs]
    [clojure.java.io :as io]
    [org.httpkit.client :as http]
    [pantomime.mime :refer [mime-type-of]]
    [remworks.exif-reader :as exif]
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

;; make-file-stream and fetch-remote-image are adapted from
;; https://stackoverflow.com/questions/33375826/how-can-i-stream-gridfs-files-to-web-clients-in-clojure-monger
;; and
;; https://github.com/luminus-framework/examples/blob/master/reporting-example/src/clj/reporting_example/routes/home.clj
(defn make-file-stream
  [file]
  (ring.util.io/piped-input-stream
    (fn [output-stream]
      (.writeTo file output-stream))))

(defn remote-image-url
  "Build complete imgproxy url for a remote image"
  ([image-url]
   (remote-image-url image-url 600 400))
  ([image-url width height]
   (let [imgproxy-base (env :imageproxy-base-url)
         resize "fit"
         width width
         height height
         gravity "no"
         enlarge 0
         extension "png"
         signed-url (signed-imgproxy-url image-url resize width height gravity enlarge extension)]
     (str imgproxy-base signed-url))))


(defn fetch-remote-image
  ; todo either refactor fetch-dog-image and fetch-dog-image-thumbnail to use this code
  ; or refactor this function to handle any remote image, not just images in our bucket
  "Fetch a remote image, specifying the height and width"
  [image-uuid height width]
  (let [image-response @(http/get (remote-image-url (str "s3://" (env :bucket-name) "/" image-uuid)))
        image-data  (.bytes (:body image-response))
        image-response-headers (:headers image-response)]
    (-> (ring.util.response/response image-data)
        ;make-file-stream
        (header "Content-Disposition" (str "inline; filename=\"" image-uuid "\""))
        (header "Content-Type" (:content-type image-response-headers))
        (header "Content-Length" (:content-length image-response-headers)))))


(defn fetch-dog-image [image-uuid]
  ; https://github.com/http-kit/http-kit/issues/90#issuecomment-191052170
  ; says I should switch to clj-http if I want to stream the data via a
  ; piped-input-stream to output stream sort of thing (so make-file-stream is
  ; currently unused because we need to buffer the whole image in this service before we can
  ; send it to the client. It should only be a few hundred kb to some mb of data so
  ; I don't think this is a huge deal immediately
  ; our image names are in the format s3://%bucket_name/%file_key
  ; so (str "s3://" (env :bucket-name) "/" image-name)
  ; will give us our file in production (image-name will be a uuid)
  (let [image-response @(http/get (remote-image-url (str "s3://" (env :bucket-name) "/" image-uuid)))
        image-data  (.bytes (:body image-response))
        image-response-headers (:headers image-response)]
    (-> (ring.util.response/response image-data)
        ;make-file-stream
        (header "Content-Disposition" (str "inline; filename=\"" image-uuid "\""))
        (header "Content-Type" (:content-type image-response-headers))
        (header "Content-Length" (:content-length image-response-headers)))))

(defn fetch-dog-image-thumbnail [image-uuid size]
  (let [image-response @(http/get (remote-image-url (str "s3://" (env :bucket-name) "/" image-uuid) 150 100))
        image-data (.bytes (:body image-response))
        image-response-headers (:headers image-response)]
    (-> (ring.util.response/response image-data)
        (header "Content-Disposition" (str "inline; filename=\"" image-uuid "\""))
        (header "Content-Type" (:content-type image-response-headers))
        (header "Content-Length" (:content-length image-response-headers)))))