(ns doggallery.images-test
  (:require [clojure.test :refer :all]
            [buddy.core.codecs :as codecs])
  (:require [doggallery.images :refer :all]))


(deftest imgproxy-url-generation
  ; this test replicates the java upstream
  ; https://github.com/imgproxy/imgproxy/blob/master/examples/signature.java
  (testing "generate signed url for imgproxy"
    (let [imgproxy-key (codecs/hex->bytes "943b421c9eb07c830af81030552c86009268de4e532ba2ee2eab8247c6da0881")
          salt (codecs/hex->bytes "520f986b998545b4785e0defbc4f3c1203f22de2374a3d53cb7a7fe9fea309c5")
          url "http://img.example.com/pretty/image.jpg"
          resize "fill"
          width 300
          height 300
          gravity "no"
          enlarge 1
          extension "png"
          url-with-hash (signed-imgproxy-url imgproxy-key salt url resize width height gravity enlarge extension)]
      (is (= "/_PQ4ytCQMMp-1w1m_vP6g8Qb-Q7yF9mwghf6PddqxLw/fill/300/300/no/1/aHR0cDovL2ltZy5leGFtcGxlLmNvbS9wcmV0dHkvaW1hZ2UuanBn.png" url-with-hash)))))