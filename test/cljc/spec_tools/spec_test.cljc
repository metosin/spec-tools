(ns spec-tools.spec-test
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.spec.alpha :as s]
            [#?(:clj  clojure.spec.gen.alpha
                :cljs cljs.spec.gen.alpha) :as gen]
            [spec-tools.core :as st]
            [spec-tools.spec :as spec]))

(deftest specs-test
  (are [spec pred]
    (= (:spec spec) pred)
    spec/any? clojure.core/any?
    spec/some? clojure.core/some?
    spec/number? clojure.core/number?
    spec/pos? clojure.core/pos?
    spec/neg? clojure.core/neg?
    spec/integer? clojure.core/integer?
    spec/int? clojure.core/int?
    spec/pos-int? clojure.core/pos-int?
    spec/neg-int? clojure.core/neg-int?
    spec/nat-int? clojure.core/nat-int?
    spec/float? clojure.core/float?
    spec/double? clojure.core/double?
    spec/boolean? clojure.core/boolean?
    spec/string? clojure.core/string?
    spec/ident? clojure.core/ident?
    spec/simple-ident? clojure.core/simple-ident?
    spec/qualified-ident? clojure.core/qualified-ident?
    spec/keyword? clojure.core/keyword?
    spec/simple-keyword? clojure.core/simple-keyword?
    spec/qualified-keyword? clojure.core/qualified-keyword?
    spec/symbol? clojure.core/symbol?
    spec/simple-symbol? clojure.core/simple-symbol?
    spec/qualified-symbol? clojure.core/qualified-symbol?
    spec/uuid? clojure.core/uuid?
    #?@(:clj [spec/uri? clojure.core/uri?])
    #?@(:clj [spec/decimal? clojure.core/decimal?])
    spec/inst? clojure.core/inst?
    spec/seqable? clojure.core/seqable?
    spec/indexed? clojure.core/indexed?
    spec/map? clojure.core/map?
    spec/vector? clojure.core/vector?
    spec/list? clojure.core/list?
    spec/seq? clojure.core/seq?
    spec/char? clojure.core/char?
    spec/set? clojure.core/set?
    spec/nil? clojure.core/nil?
    spec/false? clojure.core/false?
    spec/true? clojure.core/true?
    spec/zero? clojure.core/zero?
    #?@(:clj [spec/rational? clojure.core/rational?])
    spec/coll? clojure.core/coll?
    spec/empty? clojure.core/empty?
    spec/associative? clojure.core/associative?
    spec/sequential? clojure.core/sequential?
    #?@(:clj [spec/ratio? clojure.core/ratio?])
    #?@(:clj [spec/bytes? clojure.core/bytes?])))

(s/def ::kw1 spec/keyword?)
(s/def ::m1 (s/keys :req-un [::kw1]))
(s/def ::kw2 spec/keyword?)
(s/def ::map (spec/merge ::m1
                         (s/keys :opt-un [::kw2])))
(s/def ::core-map (s/merge ::m1
                           (s/keys :opt-un [::kw2])))

(s/def ::or (s/or :int int? :string string?))
(s/def ::or-map (spec/merge (s/keys :req-un [::or])
                            (s/keys :opt-un [::kw2])))

(deftest merge-test
  (let [input {:kw1 "kw1"
               :kw2 "kw2"}
        bad-input {:kw2 :kw2}
        output {:kw1 :kw1
                :kw2 :kw2}]
    (testing "clojure.spec.alpha/merge"
      (testing "fails to conform all values with spec-tools.core/conform"
        (is (= {:kw1 "kw1"
                :kw2 :kw2}
               (st/conform ::core-map input st/json-conforming)))))
    (testing "spec-tools.spec/merge"
      (testing "creates a conformer that conforms maps inside merge with spec-tools.core/conform"
        (is (= output (st/conform ::map input st/json-conforming)))
        (testing "also for non-spectools specs"
          (is (= {:or [:int 1]} (st/conform ::or-map {:or 1})))
          (is (= {:or [:string "1"]} (st/conform ::or-map {:or "1"}))))
        (testing "also for nested spec-tools.spec/merge"
          (is (= output (st/conform (spec/merge ::map) input st/json-conforming)))))
      (testing "fails with bad input"
        (is (not (s/valid? ::map bad-input))))
      (testing "doesn't strip extra keys from input"
        (is (= (assoc output :foo true)
               (st/conform ::map (assoc input :foo true) st/json-conforming))))
      (testing "works with strip-extra-keys-conforming"
        (is (= output
               (st/conform ::map (assoc output :foo true) st/strip-extra-keys-conforming))))
      (testing "has proper unform"
        (is (= output (s/conform ::map (s/unform ::map (st/conform ::map input st/json-conforming)))))
        (testing "also for non-spectools specs"
          (is (= {:or 1} (s/unform ::or-map (st/conform ::or-map {:or 1} st/json-conforming))))
          (is (= {:or "1"} (s/unform ::or-map (st/conform ::or-map {:or "1"} st/json-conforming))))))
      (testing "has a working generator"
        (is (s/valid? ::map (gen/generate (s/gen ::map)))))
      (testing "has a working with-gen"
        (let [new-spec (s/with-gen ::map #(gen/return output))]
          (testing "that creates a conformer that conforms maps inside merge"
            (is (= output (st/conform new-spec input st/json-conforming))))
          (testing "that uses the given generator"
            (is (= output (gen/generate (s/gen new-spec)))))))
      (testing "has the same explain as clojure.spec.alpha/merge"
        (let [expected-explanation (s/explain-data ::core-map bad-input)
              actual-explanation (s/explain-data ::map bad-input)]
          (is (= (select-keys (first (::s/problems expected-explanation))
                              [:path :pred :val :in])
                 (select-keys (first (::s/problems actual-explanation))
                              [:path :pred :val :in])))))
      (testing "has a working describe"
        (is (= (s/describe ::core-map)
               (:spec (second (s/describe ::map)))))))))
