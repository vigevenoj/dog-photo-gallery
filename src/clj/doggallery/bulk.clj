(ns doggallery.bulk
  (:require
    [amazonica.aws.s3 :as s3]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
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

(defn handle-existing-object-file [photo-key]
  ; if key is not uuid, g
  ; if key exists in database; exit
  ; if uuid not present in database,
  ; fetch the object from storage,
  ; parse the exif data
  ; save that to the database
  (if (db/get-dog-photo-by-uuid {:name #uuid photo-uuid})
    (log/info "Found " photo-uuid " in database, skipping")
    (do
      (log/info photo-uuid " not found in database")
      (try
        (let [cred {:access-key (env :object-storage-access-key)
                    :secret-key (env :object-storage-secret-key)
                    :endpoint (env :object-storage-endpoint)
                    :client-config {:path-style-access-enabled true}}
              photo-data (with-open [file (s3/get-object cred {:bucket (env :bucket-name)
                                                               :key (images/photo-uuid->key photo-uuid)})])
              meta (images/single-image-full-metadata photo-data)
              userid 1]
          (db/add-dog-photo! {:name photo-uuid
                              :userid userid
                              :taken (:date-time-original meta)
                              :metadata meta})
          (log/info "Successfully saved entry for " photo-uuid))
        (catch Exception e
          (log/error "Error saving entry for " photo-uuid))))))


(defn list-all-photos
  [opts]
  (let [cred {:access-key (env :object-storage-access-key)
              :secret-key (env :object-storage-secret-key)
              :endpoint (env :object-storage-endpoint)
              :client-config {:path-style-access-enabled true}}
        result (s3/list-objects-v2 cred
                 {:bucket-name (env :bucket-name)
                  :prefix ""})]
    (cons result (when (:truncated? result)
                   (lazy-seq
                     (list-all-photos
                       (assoc opts :continuation-token (:next-continuation-token result))))))))
