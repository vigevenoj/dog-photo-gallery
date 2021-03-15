(ns doggallery.handler-test
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :refer :all]
    [doggallery.handler :refer :all]
    [doggallery.middleware.formats :as formats]
    [muuntaja.core :as m]
    [mount.core :as mount]))

(defn parse-json [body]
  (m/decode formats/instance "application/json" body))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'doggallery.config/env
                 #'doggallery.handler/app-routes)
    (f)))

(deftest test-app
  (testing "main route"
    (let [response ((app) (request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "not-found route"
    (let [response ((app) (request :get "/invalid"))]
      (is (= 404 (:status response)))))

  (testing "services"

    (testing "success"
      (let [response ((app) (request :get "/api/ping"))]
        (is (= 200 (:status response)))
        (is (= {:message "pong"} (m/decode-response-body response)))))))
