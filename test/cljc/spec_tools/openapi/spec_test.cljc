(ns spec-tools.openapi.spec-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [spec-tools.openapi.spec :as openapi-spec]))

(deftest openapi-spec-test
  (doseq [spec [::openapi-spec/contact
                ::openapi-spec/license
                ::openapi-spec/info
                ::openapi-spec/server-variable
                ::openapi-spec/server
                ::openapi-spec/external-documentation
                ::openapi-spec/schema
                ::openapi-spec/example
                ::openapi-spec/header
                ::openapi-spec/encoding
                ::openapi-spec/media-object
                ::openapi-spec/parameter-path
                ::openapi-spec/parameter-other
                ::openapi-spec/parameter
                ::openapi-spec/request-body
                ::openapi-spec/link
                ::openapi-spec/response-code
                ::openapi-spec/response
                ::openapi-spec/tag
                ::openapi-spec/security-scheme-api-key
                ::openapi-spec/security-scheme-http
                ::openapi-spec/security-scheme-oauth2
                ::openapi-spec/security-scheme]]
    (testing "Exercise works"
      (is (= 10 (count (s/exercise spec)))))))
