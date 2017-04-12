(ns spec-tools.visitor
  "Tools for walking spec definitions."
  (:require [clojure.spec :as s]
            [spec-tools.core :as st]
            [spec-tools.type :as type]))

(defn strip-fn-if-needed [form]
  (let [head (first form)]
    ;; Deal with the form (clojure.core/fn [%] (foo ... %))
    ;; We should just use core.match...
    (if (and (= (count form) 3) (= head #?(:clj 'clojure.core/fn :cljs 'cljs.core/fn)))
      (nth form 2)
      form)))

(defn- normalize-symbol [kw]
  (case (and (symbol? kw) (namespace kw))
    "cljs.core" (symbol "clojure.core" (name kw))
    "cljs.spec" (symbol "clojure.spec" (name kw))
    kw))

(defn- formize [spec] (if (seq? spec) spec (s/form spec)))
(defn- de-spec [{:keys [form spec]}] (if (seq? form) spec form))

(defn- spec-dispatch
  [spec accept]
  (cond
    (or (s/spec? spec) (s/regex? spec) (keyword? spec))
    (let [form (s/form spec)]
      (if (not= form ::s/unknown)
        (if (seq? form)
          (normalize-symbol (first form))
          (spec-dispatch form accept))
        spec))
    (set? spec) ::set
    (seq? spec) (normalize-symbol (first (strip-fn-if-needed spec)))
    :else (normalize-symbol spec)))

(defn- expand-spec-ns [x]
  (if-not (namespace x) (symbol "clojure.core" (name x)) x))

(defn- ++expand-every-cljs-spec-bug++ [x]
  (if (seq? x)
    (let [[k & rest] x]
      (cons k (if (= k 'cljs.spec/tuple)
                (map expand-spec-ns rest)
                rest)))
    (expand-spec-ns x)))

(defmulti visit
  "Walk a spec definition. Takes two arguments, the spec and the accept
  function, and returns the result of calling the accept function.

  The accept function is called with three arguments: the dispatch term for the
  spec (see below), the spec itself and the vector with the results of
  recursively walking the children of the spec.

  The dispatch term is one of the following
  * if the spec is a function call: a fully qualified symbol for the function
    with the following exceptions:
    - cljs.core symbols are converted to clojure.core symbols
    - cljs.spec symbols are converted to clojure.spec symbols
  * if the spec is a set: :spec-tools.visitor/set
  * otherwise: the spec itself"
  spec-dispatch :default ::default)

(defmethod visit ::set [spec accept]
  (accept ::set spec (vec (if (keyword? spec) (s/form spec) spec))))

(defmethod visit 'clojure.spec/keys [spec accept]
  (let [[_ & {:keys [req req-un opt opt-un]}] (s/form spec)]
    (accept 'clojure.spec/keys spec (mapv #(visit % accept) (concat req req-un opt opt-un)))))

(defmethod visit 'clojure.spec/or [spec accept]
  (let [[_ & {:as inner-spec-map}] (s/form spec)]
    (accept 'clojure.spec/or spec (mapv #(visit % accept) (vals inner-spec-map)))))

(defmethod visit 'clojure.spec/and [spec accept]
  (let [[_ & inner-specs] (s/form spec)]
    (accept 'clojure.spec/and spec (mapv #(visit % accept) inner-specs))))

;(defmethod visit 'clojure.spec/merge [spec accept])

(defmethod visit 'clojure.spec/every [spec accept]
  (let [[_ inner-spec & kwargs] (formize spec)]
    (accept 'clojure.spec/every spec [(visit (++expand-every-cljs-spec-bug++ inner-spec) accept)])))

;(defmethod visit 'clojure.spec/every-ks [spec accept])

(defmethod visit 'clojure.spec/coll-of [spec accept]
  (let [form (s/form spec)
        pred (second form)
        type (type/resolve-type form)
        dispatch (case type
                   :map ::map-of
                   :set ::set-of
                   :vector ::vector-of)]
    (accept dispatch spec [(visit pred accept)])))

(defmethod visit 'clojure.spec/map-of [spec accept]
  (let [[_ k v] (s/form spec)]
    (accept ::map-of spec (mapv #(visit % accept) [k v]))))

(defmethod visit 'clojure.spec/* [spec accept]
  (let [[_ inner-spec] (s/form spec)]
    (accept 'clojure.spec/* spec [(visit inner-spec accept)])))

(defmethod visit 'clojure.spec/+ [spec accept]
  (let [[_ inner-spec] (s/form spec)]
    (accept 'clojure.spec/+ spec [(visit inner-spec accept)])))

(defmethod visit 'clojure.spec/? [spec accept]
  (let [[_ inner-spec] (s/form spec)]
    (accept 'clojure.spec/? spec [(visit inner-spec accept)])))

(defmethod visit 'clojure.spec/alt [spec accept]
  (let [[_ & {:as inner-spec-map}] (s/form spec)]
    (accept 'clojure.spec/alt spec (mapv #(visit % accept) (vals inner-spec-map)))))

;(defmethod visit 'clojure.spec/cat [spec accept])

;(defmethod visit 'clojure.spec/& [spec accept])

(defmethod visit 'clojure.spec/tuple [spec accept]
  (let [[_ & inner-specs] (formize spec)]
    (accept 'clojure.spec/tuple spec (mapv #(visit % accept) inner-specs))))

;(defmethod visit 'clojure.spec/keys* [spec accept])

;(defmethod visit 'clojure.spec/nilable [spec accept])

(defmethod visit 'clojure.spec/nilable [spec accept]
  (let [[_ inner-spec] (s/form spec)]
    (accept 'clojure.spec/nilable spec [(visit inner-spec accept)])))

(defmethod visit 'spec-tools.core/spec [spec accept]
  (let [real-spec (st/coerce-spec spec)]
    (accept ::spec spec (visit (de-spec real-spec) accept))))

(defmethod visit ::default [spec accept]
  (accept (spec-dispatch spec accept) spec nil))

;;
;; sample visitor
;;

(defn collect-specs
  "a visitor that collects all registered specs. Returns
  a map of spec-name => specs"
  []
  (let [specs (atom {})]
    (fn [_ spec _]
      (if-let [s (s/get-spec spec)]
        (swap! specs assoc spec s)
        @specs))))

(defn convert-specs!
  "Collects all registered subspecs from a spec and
  transforms their registry values into Spec Records."
  [spec]
  (let [specs (visit spec (collect-specs))
        report (atom #{})]
    (doseq [[k v] specs]
      (if (keyword? v)
        (swap! report into (convert-specs! v))
        (when-not (st/spec? v)
          (eval `(s/def ~k (st/create-spec {:spec ~v})))
          (swap! report conj k))))
    @report))
