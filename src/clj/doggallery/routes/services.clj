(ns doggallery.routes.services
  (:require
    [clojure.tools.logging :as log]
    [reitit.swagger :as swagger]
    [reitit.swagger-ui :as swagger-ui]
    [reitit.ring.coercion :as coercion]
    [reitit.coercion.spec :as spec-coercion]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.multipart :as multipart]
    [reitit.ring.middleware.parameters :as parameters]
    [doggallery.middleware.formats :as formats]
    [ring.util.http-response :refer :all]
    [doggallery.db.core :as db]
    [doggallery.images :as images]
    [pantomime.mime :refer [mime-type-of]]
    [clojure.java.io :as io]))

(defn is-image-file? [upload-file]
  (= "image/jpeg"  (mime-type-of (:tempfile upload-file))))


(defn handle-image-upload [file]
  (log/warn "uploaded a file")
  (let [tempfile (:tempfile file)]
    (if (is-image-file? file)
        (try
         (let [meta (images/single-image-full-metadata tempfile)
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


   ["/files"
    {:swagger {:tags ["files"]}}

    ; todo add authn+authz to this endpoint (or stick it behind a forward proxy)
    ["/upload"
     {:post {:summary "upload a file"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :responses {200 {:body {:name string?, :size int?, :exif-metadata map?}}
                         400 {:description "Bad request"}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        (handle-image-upload file))}}]

    ["/download"
     {:get {:summary "downloads a file"
            :swagger {:produces ["image/png"]}
            :handler (fn [_]
                       {:status 200
                        :headers {"Content-Type" "image/png"}
                        :body (-> "public/img/warning_clojure.png"
                                  (io/resource)
                                  (io/input-stream))})}}]]])
