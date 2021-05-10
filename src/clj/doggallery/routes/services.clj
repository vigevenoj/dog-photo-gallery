(ns doggallery.routes.services
  (:require
    [clojure.tools.logging :as log]
    [org.httpkit.client :as http]
    [ring.util.http-response :as r]
    [ring.util.response]
    [reitit.swagger :as swagger]
    [reitit.swagger-ui :as swagger-ui]
    [reitit.ring.coercion :as coercion]
    [reitit.coercion.spec :as spec-coercion]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.multipart :as multipart]
    [reitit.ring.middleware.parameters :as parameters]
    [doggallery.middleware.formats :as formats]
    [ring.util.http-response :refer :all]
    [doggallery.config :refer [env]]
    [doggallery.db.core :as db]
    [doggallery.images :as images]
    ;[pantomime.mime :refer [mime-type-of]]
    [clj-uuid :as uuid]
    [clojure.java.io :as io])
  (:import (java.io ByteArrayInputStream)))

(defn handle-image-upload [file]
  (log/warn "uploaded a file")
  (let [tempfile (:tempfile file)]
    ; todo strip metadata from image
    ; todo generate thumbnails at appropriate sizes?
    ; todo use conman/with-transaction to wrap saving the image metadata into the db and the image file into the object storage
    (if (images/is-image-file? tempfile)
        (try
         (let [meta (images/single-image-full-metadata tempfile)
               ; make the :name a uuid
               ; use with-transaction to wrap saving this and saving it to object storage
               userid 1
               photo-id (db/add-dog-photo! {:name (:filename file)
                                            :userid userid
                                            :taken (:date-time-original meta)
                                            :metadata meta
                                            :photo (images/file->bytes tempfile)})]
           {:status 200
            :body   {:name          (:filename file)
                     :photo-id      photo-id
                     :size          (:size file)
                     :exif-metadata (images/single-image-full-metadata tempfile)}})

         (catch Exception e
           (do
             (log/error (.printStackTrace e))
             {:status 500
              :body "Error saving uploaded image"})))
      (do ; If the file is not an image file
        (log/warn "is not image")
        {:status 400
         :body   {:error "File was not image"}}))))

;; make-file-stream and fetch-remote-image are adapted from
;; https://stackoverflow.com/questions/33375826/how-can-i-stream-gridfs-files-to-web-clients-in-clojure-monger
;; and
;; https://github.com/luminus-framework/examples/blob/master/reporting-example/src/clj/reporting_example/routes/home.clj
(defn make-file-stream
  [file]
  (ring.util.io/piped-input-stream
    (fn [output-stream]
      (.writeTo file output-stream))))

(defn remote-image-url [image-url]
  (let [imgproxy-base (env :imageproxy-base-url)
        resize "fit"
        width 600
        height 400
        gravity "no"
        enlarge 0
        extension "png"
        signed-url (images/signed-imgproxy-url image-url resize width height gravity enlarge extension)]
    (str imgproxy-base signed-url)))

(defn fetch-remote-image [image-name]
  ; https://github.com/http-kit/http-kit/issues/90#issuecomment-191052170
  ; says I should switch to clj-http if I want to stream the data via a
  ; piped-input-stream to output stream sort of thing (so make-file-stream is
  ; currently unused because we need to buffer the whole image in this service before we can
  ; send it to the client. It should only be a few hundred kb to some mb of data so
  ; I don't think this is a huge deal immediately
  ; our image names are in the format s3://%bucket_name/%file_key
  ; so (str "s3://" (env :bucket-name) "/" image-name)
  ; will give us our file in production (image-name will be a uuid)
  (let [image-response @(http/get (remote-image-url image-name))
        image-data  (.bytes (:body image-response))
        image-response-headers (:headers image-response)]
    (log/warn "Image data fetched from proxy")
    (log/warn (type image-data))
    (log/warn image-response-headers)
    (-> (ring.util.response/response image-data)
        ;make-file-stream
        (header "Content-Disposition" (str "inline; filename=\"" image-name "\""))
        (header "Content-Type" (:content-type image-response-headers))
        (header "Content-Length" (:content-length image-response-headers)))))




(defn service-routes []
  ["/api"
   {:coercion spec-coercion/coercion
    :muuntaja formats/instance
    :swagger {:id ::api}
    :middleware [;; query-params & form-params
                 parameters/parameters-middleware
                 ;; content-negotiation
                 muuntaja/format-negotiate-middleware
                 ;; encoding response body
                 muuntaja/format-response-middleware
                 ;; exception handling
                 coercion/coerce-exceptions-middleware
                 ;; decoding request body
                 muuntaja/format-request-middleware
                 ;; coercing response bodys
                 coercion/coerce-response-middleware
                 ;; coercing request parameters
                 coercion/coerce-request-middleware
                 ;; multipart
                 multipart/multipart-middleware]}

   ;; swagger documentation
   ["" {:no-doc true
        :swagger {:info {:title "Dog Photo Gallery"
                         :description "https://cljdoc.org/d/metosin/reitit"}}}

    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
             {:url "/api/swagger.json"
              :config {:validator-url nil}})}]]

   ["/ping"
    {:get (constantly (ok {:message "pong"}))}]

   ["/proxy-test"
    {:get {:summary "get a single photo from imgproxy"
           :handler (fn [_]
                      (fetch-remote-image (str "s3://" (env :bucket-name) "/" "someuuidfilename")))}}]


   ["/photos"
    {:swagger {:tags ["photos"]
               :tryItOutEnabled true}}
    ;; should really think about how to fetch a set of images to display them,
    ;; like "most recent" or something else that makes sense?
    ;; perhaps "today's photos", "this week's photos", "the x most-recent"
    ["" {:get {:summary "Get recent photos"
               :responses {:200 {:description "a list of photos"}
                           :body {:photos map?}}
               :handler (fn [{{{:keys [limit]} :query} :parameters}]
                          (try
                            {:status 200
                             :body {:photos (db/get-recent-photos {:limit 10})}}))}}]



    ; todo add authn+authz to this endpoint (or stick it behind a forward proxy)
    ; if behind forward proxy, read the userid out of a header or something
    ; used these as helpful guides:
    ; https://github.com/knutesten/upload/blob/main/src/no/neksa/upload/keycloak.clj
    ; https://github.com/SekibOmazic/file-upload-server/blob/master/src/sekibomazic/file_upload_server.clj
    ; https://github.com/ring-clojure/ring/wiki/File-Uploads
    ["/secure/upload"
     {:post {:summary "upload a photo. jpg only"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :responses {200 {:body {:name string?, :size int?, :exif-metadata map?}}
                         400 {:description "Bad request"}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        (handle-image-upload file))}}]

    ["/:photo-id"
     {:get {:summary "Get data about a single photo by its ID"
            :swagger {:produces ["application/json"]}
            :parameters {:path {:photo-id int?}}
            :responses {:200 {:body {:id int? :name string? :exif-metadata map?}}
                        :400 {:description "Bad request"}
                        :404 {:description "Not found"}}
            :handler (fn [{{{:keys [photo-id]} :path } :parameters}]
                       (if-let [result (db/get-dog-photo-no-binary {:id photo-id})]
                         {:status 200
                          :body result}
                         {:status 404
                          :body {:error "Not found"}}))}}]

    ["/:photo-id/image"
     {:get {:summary    "Get a photo by its ID"
            :swagger    {:produces ["image/jpg"]}
            :parameters {:path {:photo-id int?}}
            :responses  {:200 {:description "An image"}
                         :404 {:description "Not found"}}
            :handler    (fn [{{{:keys [photo-id]} :path} :parameters}]
                          (if-let [result (db/get-dog-photo {:id photo-id})]
                            {:status  200
                             :headers {"Content-Type" "image/jpg"}
                             :body    (ByteArrayInputStream. (:photo result))}
                            {:status 404
                             :body   {:error "Not found"}}))}}]]])