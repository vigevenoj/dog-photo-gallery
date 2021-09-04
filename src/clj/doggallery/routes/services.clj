(ns doggallery.routes.services
  (:require
    [clojure.tools.logging :as log]
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
    [doggallery.images :as images]))

(defn handle-image-upload [file]
  (log/warn "uploaded a file")
  (let [tempfile (:tempfile file)]
    ; todo strip metadata from image
    ; todo generate thumbnails at appropriate sizes?
    (if (images/is-image-file? tempfile)
        (try
         (let [meta (images/single-image-full-metadata tempfile)
               userid 1
               photo-uuid (images/photo-file->uuid tempfile)]
           (db/upload-photo! meta userid photo-uuid tempfile)
           {:status 200
            :body   {:name          photo-uuid
                     :size          (:size file)
                     :exif-metadata meta}})

         (catch Exception e
           (do
             (log/error (.printStackTrace e))
             {:status 500
              :body "Error saving uploaded image"})))
      (do ; If the file is not an image file
        (log/warn "File is not an image")
        {:status 400
         :body   {:error "File was not an image"}}))))




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
                         :description "xn--d08h7b.xn--9q8h2cq1m.ws"}}}

    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
             {:url "/api/swagger.json"
              :config {:validator-url nil}})}]]

   ["/ping"
    {:get (constantly (ok {:message "pong"}))}]

   ["/photos"
    {:swagger {:tags ["photos"]
               :tryItOutEnabled true}}
    ;; should really think about how to fetch a set of images to display them,
    ;; like "most recent" or something else that makes sense?
    ;; perhaps "today's photos", "this week's photos", "the x most-recent"
    ["" {:get {:summary "Get recent photos, maximum 24"
               :responses {:200 {:description "a list of photos"}
                           :body {:photos map?}}
               :handler (fn [{{{:keys [limit]} :query} :parameters}]
                          (try
                            {:status 200
                             :body {:photos (map images/scrub-metadata (db/get-recent-photos {:limit (min (or limit 24) 24)}))}}))}}]



    ; todo add authn+authz to this endpoint (or stick it behind a forward proxy)
    ; if behind forward proxy, read the userid out of a header or something
    ; used these as helpful guides:
    ; https://github.com/knutesten/upload/blob/main/src/no/neksa/upload/keycloak.clj
    ; https://github.com/SekibOmazic/file-upload-server/blob/master/src/sekibomazic/file_upload_server.clj
    ; https://github.com/ring-clojure/ring/wiki/File-Uploads
    ["/secure/upload"
     {:post {:summary "upload a photo. jpg only"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :responses {200 {:body {:name uuid?, :size int?, :exif-metadata map?}}
                         400 {:description "Bad request"}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        (handle-image-upload file))}}]

    ["/memories/:month/:day"
     {:get {:summary "Get photos from the same day of the year"
            :swagger {:produces ["application/json"]}
            :parameters {:path {:month int?
                                :day int?}}
            :responses {:200 {:description "A list of photos"}
                        :body {:photos map?}}
            :handler (fn [{{{:keys [month day]} :path } :parameters}]
                       (try
                         {:status 200
                          :body {:photos (map images/scrub-metadata (db/get-previous-years-photos {:month month :day day}))}}))}}]

    ["/:photo-id"
     {:get {:summary "Get data about a single photo by its ID"
            :swagger {:produces ["application/json"]}
            :parameters {:path {:photo-id uuid?}}
            :responses {:200 {:body {:id int? :name uuid? :exif-metadata map?}}
                        :400 {:description "Bad request"}
                        :404 {:description "Not found"}}
            :handler (fn [{{{:keys [photo-id]} :path } :parameters}]
                       (if-let [result (db/get-dog-photo-by-uuid {:name photo-id})]
                         {:status 200
                          :body (images/scrub-metadata result)}
                         {:status 404
                          :body {:error "Not found"}}))}}]

    ["/:photo-id/older"
     {:get {:summary "Get the next older image than this one"
            :swagger {:produces ["application/json"]}
            :parameters {:path {:photo-id uuid?}}
            :responses {:200 {:body {:id int? :name uuid? :exif-metadata map?}}
                        :400 {:description "Bad request"}
                        :404 {:description "Not found"}}
            :handler (fn [{{{:keys [photo-id]} :path } :parameters}]
                       (if-let [result (db/get-next-older-photo {:name photo-id})]
                         {:status 200
                          :body result}
                         {:status 404
                          :body {:error "Not found"}}))}}]

    [":/photo-id/newer"
     {:get {:summary "Get the next newer image than this one"
            :swagger {:produces ["application/json"]}
            :parameters {:path {:photo-id uuid?}}
            :responses {:200 {:body {:id int? :name uuid? :exif-metadata map?}}
                        :400 {:description "Bad request"}
                        :404 {:description "Not found"}}
            :handler (fn [{{{:keys [photo-id]} :path } :parameters}]
                       (if-let [result (db/get-next-more-recent-photo {:name photo-id})]
                         {:status 200
                          :body result}
                         {:status 404
                          :body {:error "Not found"}}))}}]

    ["/:photo-id/image"
     {:get {:summary    "Get a photo by its ID"
            :swagger    {:produces ["image/jpg"]}
            :parameters {:path {:photo-id uuid?}}
            :responses  {:200 {:description "An image"}
                         :404 {:description "Not found"}}
            :handler    (fn [{{{:keys [photo-id]} :path} :parameters}]
                          (let [image-uuid photo-id]
                            (images/fetch-dog-image image-uuid)))}}]
    ["/:photo-id/thumbnail"
     {:get {:summary    "Get a thumbnail for a photo by its ID"
            :swagger    {:produces ["image/jpg"]}
            :parameters {:path {:photo-id uuid?}}
            :responses  {:200 {:description "An image"}
                         :404 {:description "Not found"}}
            :handler    (fn [{{{:keys [photo-id]} :path} :parameters}]
                          (let [image-uuid photo-id]
                            (images/fetch-dog-image-thumbnail image-uuid 300)))}}]
    ["/info/"
     {:get {:summary "Get info about the system"
            :swagger {:produces ["application/json"]}
            :responses {:200 {:description "informational details"}}
            :handler (fn [_]
                       (let [total-count (:count (db/count-total-photos))
                             processed-count (:count (db/count-processed-photos))]
                         {:status 200
                          :body {:total-db-photos total-count
                                 :processed-db-photos processed-count}}))}}]]])
