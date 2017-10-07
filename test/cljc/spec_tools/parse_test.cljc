(ns spec-tools.parse-test
  (:require [clojure.test :refer [deftest is]]
            [spec-tools.parse :as parse]
            [clojure.spec.alpha :as s]))

(s/def ::a string?)
(s/def ::b string?)
(s/def ::c string?)
(s/def ::d string?)

(s/def ::keys (s/keys :opt [::a]
                      :opt-un [::a]
                      :req [::a (or ::b (and ::c ::d))]
                      :req-un [::a (or ::b (and ::c ::d))]))

(deftest parse-test
  (is (= {:type :map
          :keys #{:a :b :c :d ::a ::b ::c ::d}
          :keys/req #{:a :b :c :d ::a ::b ::c ::d}
          :keys/opt #{:a ::a}}
         (parse/parse-spec ::keys))))
