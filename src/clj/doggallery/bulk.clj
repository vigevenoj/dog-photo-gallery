(ns doggallery.bulk
  (:require
    [amazonica.aws.s3 :as s3]
    [clj-uuid :refer [uuidable?]]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [pantomime.mime :refer [mime-type-of]]
    [remworks.exif-reader :as exif]
    [doggallery.config :refer [env]]
    [doggallery.db.core :as db]
    [doggallery.images :as images])
  (:import (java.io ByteArrayOutputStream)))

; todo implement bulk-add functionality:
; * read a directory of files and add images to database
; * read a zip file or tarball and add images from it to the database

; todo handle errors in saving images
(defn save-image
  "Save an image to the database, given an image file and a user ID"
  [image userid]
  (let [meta (images/single-image-full-metadata image)
        photo-id (db/add-dog-photo! {:name (.getName image)
                                     :userid userid
                                     :taken (:date-time-original meta)
                                     :metadata meta
                                     :photo (images/file->bytes image)})]
    photo-id))

; todo handle accumulating success/failure for each file
; todo handle errors reading files (that .DS_Store file did something weird)
; todo handle user ID--do we apply a partial or something
(defn load-directory
  [directory userid]
  (let [image-paths (filter images/is-image-file? (file-seq (io/file directory)))]
    (map save-image image-paths)))


(defn- creds []
  {:access-key (env :object-storage-access-key)
   :secret-key (env :object-storage-secret-key)
   :endpoint (env :object-storage-endpoint)
   :client-config {:path-style-access-enabled true}})


(defn is-object-file-photo?
  "Check if a file in object storage is an image file"
  ; this sort of duplicates images/is-image-file?
  ; with same caveat: it should probably use pantomime.media/image? instead
  [file-key]
  (with-open [photo-stream (-> (s3/get-object (creds)
                                              {:bucket-name (env :bucket-name)
                                               :key file-key})
                               :object-content
                               io/input-stream)]
    (boolean (some #{(mime-type-of photo-stream)} images/image-file-types))))

(defn movie-filter
  "Filter out movies"
  [key]
  (not (clojure.string/ends-with? key ".MOV")))

(defn metadata-from-photo-object [object-key]
  (with-open [xin (-> (s3/get-object (creds) {:bucket-name (env :bucket-name)
                                              :key object-key})
                      :object-content
                      io/input-stream)
              xout (ByteArrayOutputStream.)]
    (case (mime-type-of xin)
      "image/jpeg" (do
                     (io/copy xin xout)
                     (exif/from-jpeg (.toByteArray xout)))
      ("image/heic" "image/heif") (images/metadata-from-heif xin))))


(defn update-db-with-object-file-info [object-key]
  "Update the database with info from an image in the object storage"
  ; assumes we have an object-key that is a uuid
  ; assumes the uuid is not present in the database
  [object-key]
  (try
    (let [metadata (metadata-from-photo-object object-key)
          userid 1]
      (log/warn "Metadata for key " object-key " has taken " (:date-time-original metadata))
      (db/add-dog-photo! {:name object-key
                          :userid userid
                          :taken (:date-time-original metadata)
                          :metadata metadata}))
    (catch Exception e
      (log/error "Unable to save " object-key " to database:" (.getMessage e)))))

(defn handle-not-uuid-object-file
  "Handle a file in s3 where the key is not a uuid"
  ; assumes no prefix
  [object-key]
  (if (is-object-file-photo? object-key)
    (do
      (log/warn object-key "is an image file. Generating uuid key and adding to the database")
      (let [photo-stream (-> (s3/get-object (creds) {:bucket-name (env :bucket-name)
                                                     :key object-key})
                             :input-stream
                             slurp)
            photo-uuid (images/photo-file->uuid photo-stream)
            metadata (metadata-from-photo-object object-key)]
        (when  (nil? (:date-time-original metadata))
          ; this warning usually means it is actually a HEIF with XMP metadata
          ; remworks.exif-reader doesn't handle that sort of file
          (log/warn "No date-time-original in metadata for " object-key "(" photo-uuid ")"))
        (db/add-dog-photo! {:name photo-uuid
                            :userid 1
                            :taken (:date-time-original metadata)
                            :metadata metadata})
        (s3/copy-object (creds) (env :bucket-name) object-key (env :bucket-name) (images/photo-uuid->key photo-uuid))
        (s3/delete-object (creds) (env :bucket-name) object-key)))
    (log/error "Object is not an image file: " object-key)))

(defn handle-existing-object-file [photo-key]
  (if (uuidable? photo-key)
    (do
      (log/warn "Checking database for presence of " photo-key)
      (if (db/get-dog-photo-by-uuid {:name (clj-uuid/as-uuid photo-key)})
        (log/info photo-key "already present in database")
        (do
          (log/info photo-key " not found in database")
          ; We should look up pictures with the same :taken instant
          ; and images with the same :metadata (duplicate upload detection since metadata should match)
          (log/info "Not handling " photo-key " because metadata might match another object"))))
    (do
      (log/warn photo-key "is not uuid")
      (handle-not-uuid-object-file photo-key))))



(defn list-all-photos
  [opts]
  (let [result (s3/list-objects-v2 (creds)
                 {:bucket-name (env :bucket-name)
                  :prefix ""})]
    (concat (:object-summaries result) (when (:truncated? result)
                                         (lazy-seq
                                           (list-all-photos
                                             (assoc opts :continuation-token (:next-continuation-token result))))))))

(defn list-some-photos
  [opts]
  (let [cred creds
        bucket-response (s3/list-objects-v2 cred {:bucket-name (env :bucket-name)
                                                  :prefix ""})
        result (map :key (:object-summaries
                           bucket-response))]
    (concat result (when (:truncated? bucket-response)
                     (lazy-seq
                       (list-some-photos
                         (assoc opts
                           :continuation-token (:next-continuation-token bucket-response))))))))

(defn update-database-with-object-metadata
  "Update the database with metadata from object storage object"
  ; Assumes that the uuid is already present in the database
  ; call it like this
  ; (map update-database-with-object-metadata (map :name (hugsql/db-run doggallery.db.core/*db* "select name from photos where metadata is null")))
  [object-key]
  (try
    (let [metadata (metadata-from-photo-object object-key)]
      (log/info "Metadata for " object-key " includes " (:date-time-original metadata))
      (db/update-photo-by-name! {:name (clj-uuid/as-uuid object-key)
                                 :taken (:date-time-original metadata)
                                 :metadata metadata}))))

; This can be used to parse existing files 
;(map bulk/handle-existing-object-file
;     (filter movie-filter
;             (map :key
;                  (:object-summaries
;                    (s3/list-objects-v2
;                      (creds)
;                      {:bucket-name (env :bucket-name)
;                       :prefix "IMG_"})))))

;(defn missing-metadata-photos []
;  (let [unprocessed (hugsql/db-run doggallery.db.core/*db* "select name from photos where metadata is null")]
;    (map update-database-with-object-metadata unprocessed)))

; there's probably an easier way to do this in SQL than in clojure, honestly
;(defn fix-null-taken
;  "Update a null taken date with a better date"
;  [name]
;  (let [photo (db/get-dog-photo-by-uuid name)
;        metadata (:metadata photo)]
;    ;(assoc image-metadata :date-time-original ((keyword exif:DateTimeOriginal) metadata))
;    (db/update-photo-by-name {:name name
;                              :taken (:date-time-original metadata)
;                              :metadata metadata})))
