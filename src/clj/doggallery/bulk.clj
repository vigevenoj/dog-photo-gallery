(ns doggallery.bulk
  (:require
    [amazonica.aws.s3 :as s3]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [pantomime.mime :refer [mime-type-of]]
    [doggallery.config :refer [env]]
    [doggallery.db.core :as db]
    [doggallery.images :as images]))

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
  (with-open [photo-stream (:input-stream (s3/get-object (creds)
                                                         {:bucket (env :bucket-name)
                                                          :key file-key}))]
    (boolean (some #{(mime-type-of photo-stream)} images/image-file-types))))

(defn metadata-from-photo-object [object-key]
  (with-open [photo-stream (:input-stream (s3/get-object (creds) {:bucket (env :bucket-name)
                                                                  :key object-key}))]
    (images/single-image-full-metadata photo-stream)))


(defn update-db-with-object-file-info [object-key]
  "Update the database with info from an image in the object storage"
  ; assumes we have an object-key that is a uuid
  ; assumes the uuid is not present in the database
  [object-key]
  (try
    (let [metadata (metadata-from-photo-object object-key)
          userid 1]
      (db/add-dog-photo! {:name object-key
                          :userid userid
                          :taken {:date-time-original metadata}
                          :metadata metadata}))
    (catch Exception e
      (log/error "Unable to save " object-key " to database:" (.getMessage e)))))

(defn handle-not-uuid-object-file
  "Handle a file in s3 where the key is not a uuid"
  ; assumes no prefix
  [object-key]
  (if (is-object-file-photo? object-key)
    (do
      (log/warn object-key "is an image file. Generating uuid key and checking the database")
      (with-open [photo-stream (:input-stream (s3/get-object (creds) {:bucket (env :bucket-name)
                                                                      :key object-key}))
                  photo-uuid (images/photo-file->uuid photo-stream)
                  metadata (images/single-image-full-metadata photo-stream)]
        (db/add-dog-photo! {:name photo-uuid
                            :userid 1
                            :taken (:date-time-original metadata)
                            :metadata metadata})
        (s3/copy-object (creds) (env :bucket-name) object-key (env :bucket-name) (images/photo-uuid->key photo-uuid))))
    (log/error "Object is not an image file: " object-key)))

(defn handle-existing-object-file [photo-key]
  ; if key is not uuid, figure something out
  ; if key is uuid, continue
  ; if key exists in database; exit, this file has been handled
  ; if uuid not present in database,
  ; fetch the object from storage,
  ; parse the exif data
  ; save that to the database
  (if (uuid? photo-key)
    (log/warn photo-key "is a uuid, we should handle it")
    (log/error photo-key "is not uuid"))
  (if (db/get-dog-photo-by-uuid {:name photo-key})
    (log/info "Found " photo-key " in database, skipping")
    (do
      (log/info photo-key " not found in database")
      (update-db-with-object-file-info photo-key))))


(defn list-all-photos
  [opts]
  (let [cred (creds)
        result (s3/list-objects-v2 cred
                 {:bucket-name (env :bucket-name)
                  :prefix ""})]
    (cons result (when (:truncated? result)
                   (lazy-seq
                     (list-all-photos
                       (assoc opts :continuation-token (:next-continuation-token result))))))))
