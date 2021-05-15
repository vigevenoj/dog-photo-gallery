(ns doggallery.routes.services
  (:require
    [clojure.tools.logging :as log]
    [org.httpkit.client :as http]
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
               photo-uuid (clj-uuid/v5 (env :uuid-namespace) tempfile)]
           (db/upload-photo meta userid photo-uuid tempfile)
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
         signed-url (images/signed-imgproxy-url image-url resize width height gravity enlarge extension)]
     (str imgproxy-base signed-url))))


(defn fetch-remote-image
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
    (log/warn "Image data fetched from proxy")
    (log/warn (type image-data))
    (log/warn image-response-headers)
    (-> (ring.util.response/response image-data)
        ;make-file-stream
        (header "Content-Disposition" (str "inline; filename=\"" image-uuid "\""))
        (header "Content-Type" (:content-type image-response-headers))
        (header "Content-Length" (:content-length image-response-headers)))))

(defn fetch-dog-image-thumbnail [image-uuid size]
  (let [image-response @(http/get (remote-image-url (str "s3://" (env :bucket-name) "/" image-uuid "-" size)))
        image-data (.bytes (:body image-response))
        image-response-headers (:headers image-response)]
    (-> (ring.util.response/response image-data)
        (header "Content-Disposition" (str "inline; filename=\"" image-uuid "\""))
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
                      (let [image-name "242d756e-b9d1-531d-9b17-5db885e4fd61"]
                        (log/warn "Using " image-name " as image-name")
                        (fetch-dog-image image-name)))}}]



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
                             :body {:photos (db/get-recent-photos {:limit limit})}}))}}]



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
                            (log/warn "Using " image-uuid " as image-name")
                            (fetch-dog-image image-uuid)))}}]]])