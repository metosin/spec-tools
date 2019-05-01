(ns spec-tools.spell-test
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.spec.alpha :as s]
            [spec-tools.spell :as spell]
            [clojure.string :as str]
            [spec-tools.parse :as parse]
            [spec-tools.data-spec :as ds]))

(s/def ::name string?)
(s/def ::use-history boolean?)
(s/def ::config (spell/closed (s/keys :opt-un [::name ::use-history])))

(def invalid-options {:configz {:name "John" :use-history false}})
(def invalid-config {:config {:name "John" :use-hisory false :countr 1}})
(def valid {:config {:name "John" :use-history false}})

(deftest spell-test
  (testing "explain-data"
    (testing "simple specs"
      (is (not (s/explain-data
                 (s/keys :opt-un [::config])
                 invalid-options)))
      (is (s/explain-data
            (spell/closed (s/keys :opt-un [::config]))
            invalid-options))
      (is (s/explain-data
            (spell/closed (s/keys :opt-un [::config]))
            invalid-config))
      (is (not (s/explain-data
                 (spell/closed (s/keys :opt-un [::config]))
                 valid))))
    (testing "composite specs"
      (is (not (s/explain-data
                 (spell/closed
                   (s/merge
                     (s/keys :opt-un [::config])
                     (s/keys :opt-un [::name])))
                 valid)))
      (is (s/explain-data
            (spell/closed
              (s/merge
                (s/keys :opt-un [::config])
                (s/keys :opt-un [::name])))
            {:namez "kikka"})))
    (testing "explain-str"
      (is (str/includes?
            (spell/explain-str
              (spell/closed (s/keys :opt-un [::config]))
              invalid-options)
            "Misspelled map key")))
    (testing "explain"
      (is (str/includes?
            (with-out-str
              (spell/explain
                (spell/closed (s/keys :opt-un [::config]))
                invalid-options))
            "Misspelled map key")))
    (testing "errors"
      (is (thrown-with-msg?
            #?(:clj Exception, :cljs js/Error)
            #"Can't read keys from spec"
            (spell/closed int?))))
    (testing "parsing"
      (is (= {:type :map,
              :spec-tools.parse/key->spec {:config ::config},
              :spec-tools.parse/keys #{:config},
              :spec-tools.parse/keys-opt #{:config}}
             (parse/parse-spec (spell/closed (s/keys :opt-un [::config])))))
      (is (= {:type :map,
              :spec-tools.parse/key->spec {:config ::config, :name ::name},
              :spec-tools.parse/keys #{:config :name},
              :spec-tools.parse/keys-opt #{:config :name}}
             (parse/parse-spec
               (spell/closed
                 (s/merge
                   (s/keys :opt-un [::config])
                   (s/keys :opt-un [::name])))))))
    (testing "data-specs"
      (let [spec (ds/spec
                   {:spec {:config {:name string? :use-history boolean?}}
                    :name ::spec
                    :keys-spec spell/closed-keys})]
        (is (s/explain-data spec invalid-options))
        (is (s/explain-data spec invalid-config))
        (is (not (s/explain-data spec valid)))))))
