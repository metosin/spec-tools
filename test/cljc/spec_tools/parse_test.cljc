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
          :keys/key->spec {:a :spec-tools.parse-test/a
                           :b :spec-tools.parse-test/b
                           :c :spec-tools.parse-test/c
                           :d :spec-tools.parse-test/d
                           :e :spec-tools.parse-test/e
                           :spec-tools.parse-test/a :spec-tools.parse-test/a
                           :spec-tools.parse-test/b :spec-tools.parse-test/b
                           :spec-tools.parse-test/c :spec-tools.parse-test/c
                           :spec-tools.parse-test/d :spec-tools.parse-test/d
                           :spec-tools.parse-test/e :spec-tools.parse-test/e}
          :keys/req #{:a :b :c :d ::a ::b ::c ::d}
          :keys/opt #{:e ::e}}
         (parse/parse-spec ::keys)))

  (is (= {:type :map
          :keys #{:a :b :c :d :e :f :g ::a ::b ::c ::d ::e ::f ::g}
          :keys/key->spec {:a :spec-tools.parse-test/a
                           :b :spec-tools.parse-test/b
                           :c :spec-tools.parse-test/c
                           :d :spec-tools.parse-test/d
                           :e :spec-tools.parse-test/e
                           :f :spec-tools.parse-test/f
                           :g :spec-tools.parse-test/g
                           :spec-tools.parse-test/a :spec-tools.parse-test/a
                           :spec-tools.parse-test/b :spec-tools.parse-test/b
                           :spec-tools.parse-test/c :spec-tools.parse-test/c
                           :spec-tools.parse-test/d :spec-tools.parse-test/d
                           :spec-tools.parse-test/e :spec-tools.parse-test/e
                           :spec-tools.parse-test/f :spec-tools.parse-test/f
                           :spec-tools.parse-test/g :spec-tools.parse-test/g}
          :keys/req #{:a :b :c :d :g ::a ::b ::c ::d ::g}
          :keys/opt #{:e :f ::e ::f}}
         (parse/parse-spec ::merged))))
