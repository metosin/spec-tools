(ns spec-tools.parse-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.spec.alpha :as s]
            [spec-tools.parse :as parse]
            [spec-tools.core :as st]))

(s/def ::a string?)
(s/def ::b string?)
(s/def ::c string?)
(s/def ::d string?)
(s/def ::e string?)

(s/def ::f string?)
(s/def ::g string?)

(s/def ::keys (s/keys :opt [::e]
                      :opt-un [::e]
                      :req [::a (or ::b (and ::c ::d))]
                      :req-un [::a (or ::b (and ::c ::d))]))

(s/def ::keys2 (st/spec (s/keys :opt [::f]
                                :opt-un [::f]
                                :req [::g]
                                :req-un [::g])))

(s/def ::merged (s/merge ::keys ::keys2))

(deftest parse-test
  (is (= {:type :map
          :keys #{:a :b :c :d :e ::a ::b ::c ::d ::e}
          :keys/req #{:a :b :c :d ::a ::b ::c ::d}
          :keys/opt #{:e ::e}}
         (parse/parse-spec ::keys)))

  (is (= {:type :map
          :keys #{:a :b :c :d :e :f :g ::a ::b ::c ::d ::e ::f ::g}
          :keys/req #{:a :b :c :d :g ::a ::b ::c ::d ::g}
          :keys/opt #{:e :f ::e ::f}}
         (parse/parse-spec ::merged))))
