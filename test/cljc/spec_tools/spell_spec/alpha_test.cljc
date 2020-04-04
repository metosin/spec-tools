(ns spec-tools.spell-spec.alpha-test
  (:require
   [clojure.test.check]
   [clojure.test.check.generators :as gen]
   [#?(:clj clojure.test :cljs cljs.test)
    :refer [deftest is testing]]
   [#?(:clj  clojure.spec.alpha
       :cljs cljs.spec.alpha)
    :as s]
   [clojure.string :as string]
   [spec-tools.spell-spec.alpha :as spell :refer [warn-keys strict-keys warn-strict-keys]]))

(defn fetch-warning-output [thunk]
  #?(:clj (binding [*err* (java.io.StringWriter.)]
            (thunk)
            (str *err*))
     :cljs (with-out-str (thunk))))

(deftest check-misspell-test
  (let [spec (spell/keys :opt-un [::hello ::there])
        data {:there 1 :helloo 1 :barabara 1}
        {:keys [::s/problems]}
        (s/explain-data spec data)]
    (is (not (s/valid? spec data)))
    (is (= 1 (count problems)))
    (when-let [{:keys [:expound.spec.problem/type ::spell/misspelled-key ::spell/likely-misspelling-of]} (first problems)]
      (is (= ::spell/misspelled-key type))
      (is (= misspelled-key :helloo))
      (is (= '(:hello) likely-misspelling-of)))))

(deftest check-misspell-with-namespace-test
  (let [spec (spell/keys :opt [::hello ::there])
        data {::there 1 ::helloo 1 :barabara 1}
        {:keys [::s/problems]}
        (s/explain-data spec data)]
    (is (not (s/valid? spec data)))
    (is (= 1 (count problems)))
    (when-let [{:keys [:expound.spec.problem/type ::spell/misspelled-key ::spell/likely-misspelling-of]} (first problems)]
      (is (= ::spell/misspelled-key type))
      (is (= misspelled-key ::helloo))
      (is (= '(::hello) likely-misspelling-of)))))

(s/def ::hello integer?)
(s/def ::there integer?)

(deftest misspelled-errors-come-first
  (let [spec (spell/keys :opt-un [::hello ::there])
        data {:there "1" :helloo 1 :barabara 1}
        {:keys [::s/problems]} (s/explain-data spec data)]
    (is (not (s/valid? spec data)))
    (is (= 1 (count problems)))
    (when (= 1 (count problems))
      (is (= (-> problems first ::spell/misspelled-key) :helloo)))))

(deftest warning-is-valid-test
  (let [spec (warn-keys :opt-un [::hello ::there])
        data {:there 1 :helloo 1 :barabara 1}]

    (is (s/valid? spec data))
    (is (nil? (s/explain-data spec data)))

    (testing "valid prints to *err*"
      (is (= "SPEC WARNING: possible misspelled map key :helloo should probably be :hello in {:there 1, :helloo 1, :barabara 1}\n"
             (fetch-warning-output #(s/valid? spec data)))))

    (testing "other errors still show up"
      (is (not (s/valid? spec {:there "1" :hello 1})))
      (let [{:keys [::s/problems]} (s/explain-data spec {:there "1" :hello 1})]
        (is (= 1 (count problems)))
        (is (= "1" (-> problems first :val)))))))

(deftest dont-recommend-keys-if-key-already-present-in-value
  (let [spec (spell/keys :opt-un [::hello ::there])
        data {:there 1 :helloo 1 :hello 1 :barabara 1}]
    (is (s/valid? spec data))
    (is (nil? (s/explain-data spec data)))
    (testing "should not warn if key present in value already"
      (is (string/blank? (fetch-warning-output #(s/valid? spec data)))))))

(deftest strict-keys-test
  (let [spec (strict-keys :opt-un [::hello ::there])
        data {:there 1 :barabara 1}
        {:keys [::s/problems]}
        (s/explain-data spec data)]
    (is (not (s/valid? spec data)))
    (is (= 1 (count problems)))
    (when-let [{:keys [:expound.spec.problem/type ::spell/unknown-key]} (first problems)]
      (is (= ::spell/unknown-key type))
      (is (= unknown-key :barabara)))))

(deftest strict-keys-misspelled-test
  (testing "should also include misspelled errors"
    (let [spec (strict-keys :opt-un [::hello ::there])
          data {:there 1 :helloo 1 :barabara 1}
          {:keys [::s/problems]}
          (s/explain-data spec data)]
      (is (not (s/valid? spec data)))
      (is (= 2 (count problems)))
      (when-let [{:keys [:expound.spec.problem/type ::spell/misspelled-key ::spell/likely-misspelling-of]} (first problems)]
        (is (= ::spell/misspelled-key type))
        (is (= misspelled-key :helloo))
        (is (= '(:hello)  likely-misspelling-of)))
      (when-let [{:keys [:expound.spec.problem/type ::spell/unknown-key]} (second problems)]
        (is (= ::spell/unknown-key type))
        (is (= unknown-key :barabara))))))

(deftest warn-on-unknown-keys-test
  (let [spec (warn-strict-keys :opt-un [::hello ::there])
        data {:there 1 :barabara 1}
        {:keys [::s/problems]}
        (s/explain-data spec data)]
    (is (s/valid? spec data))
    (is (nil? (s/explain-data spec data)))
    (testing "should warn if unknown-key"
      (is (= "SPEC WARNING: unknown map key :barabara in {:there 1, :barabara 1}\n"
             (fetch-warning-output #(s/valid? spec data)))))))

(deftest multiple-spelling-matches
  (let [spec (spell/keys :opt-un [::hello1 ::hello2 ::hello3 ::there])
        data {:there 1 :helloo 1 :barabara 1}
        {:keys [::s/problems]}
        (s/explain-data spec data)]
    (is (not (s/valid? spec data)))
    (is (= 1 (count problems)))
    (let [{:keys [:expound.spec.problem/type ::spell/misspelled-key ::spell/likely-misspelling-of]}
          (first problems)]
      (is (= ::spell/misspelled-key type))
      (is (= misspelled-key :helloo))
      (is (= likely-misspelling-of '(:hello1 :hello2 :hello3))))))

(deftest multiple-spelling-warnings
  (let [spec (warn-keys :opt-un [::hello ::hello1 ::hello2 ::hello3 ::there])
        data {:there 1 :helloo 1 :barabara 1}]

    (is (s/valid? spec data))
    (is (nil? (s/explain-data spec data)))

    (testing "valid prints to *err*"
      (is (= "SPEC WARNING: possible misspelled map key :helloo should probably be one of (:hello :hello1 :hello2) in {:there 1, :helloo 1, :barabara 1}\n"
             (fetch-warning-output #(s/valid? spec data)))))))


(s/def ::baz string?)

;; just a smoke test to ensure generation works
(deftest generators-test
  (let [generates? (fn [spec]
                     (when-let [res (not-empty (gen/sample (s/gen spec)))]
                       (every? #(-> % :baz string?) res)))]
    (is (generates? (spell/keys :req-un [::baz])))
    (is (generates? (spell/strict-keys :req-un [::baz])))

    (is (generates? (spell/warn-keys :req-un [::baz])))
    (is (generates? (spell/warn-strict-keys :req-un [::baz])))))
