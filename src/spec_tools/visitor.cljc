(ns spec-tools.visitor
  "Tools for walking spec definitions."
  (:require [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [spec-tools.type :as type]
            [spec-tools.impl :as impl]))

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
    "cljs.spec" (symbol "clojure.spec.alpha" (name kw))
    kw))

(defn extract-spec [spec]
  (let [[_ form opts] (if (seq? spec) spec (s/form spec))]
    (assoc opts :form form)))

(defn extract-form [spec]
  (if (seq? spec) spec (s/form spec)))

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

(defn- ++expand-symbol-cljs-spec-bug++ [x]
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
  (accept ::set spec (vec (if (keyword? spec) (extract-form spec) spec))))

(defmethod visit 'clojure.spec.alpha/keys [spec accept]
  (let [keys (impl/extract-keys (extract-form spec))]
    (accept 'clojure.spec.alpha/keys spec (mapv #(visit % accept) keys))))

(defmethod visit 'clojure.spec.alpha/or [spec accept]
  (let [[_ & {:as inner-spec-map}] (extract-form spec)]
    (accept 'clojure.spec.alpha/or spec (mapv #(visit % accept) (vals inner-spec-map)))))

(defmethod visit 'clojure.spec.alpha/and [spec accept]
  (let [[_ & inner-specs] (extract-form spec)]
    (accept 'clojure.spec.alpha/and spec (mapv #(visit % accept) inner-specs))))

(defmethod visit 'clojure.spec.alpha/merge [spec accept]
  (let [[_ & inner-specs] (extract-form spec)]
    (accept 'clojure.spec.alpha/merge spec (mapv #(visit % accept) inner-specs))))

(defmethod visit 'clojure.spec.alpha/every [spec accept]
  (let [[_ inner-spec] (extract-form spec)]
    (accept 'clojure.spec.alpha/every spec [(visit (++expand-symbol-cljs-spec-bug++ inner-spec) accept)])))

(defmethod visit 'clojure.spec.alpha/every-kv [spec accept]
  (let [[_ inner-spec1 inner-spec2] (extract-form spec)]
    (accept 'clojure.spec.alpha/every-kv spec (mapv
                                          #(visit (++expand-symbol-cljs-spec-bug++ %) accept)
                                          [inner-spec1 inner-spec2]))))

(defmethod visit 'clojure.spec.alpha/coll-of [spec accept]
  (let [form (extract-form spec)
        pred (second form)
        type (type/resolve-type form)
        dispatch (case type
                   :map ::map-of
                   :set ::set-of
                   :vector ::vector-of)]
    (accept dispatch spec [(visit pred accept)])))

(defmethod visit 'clojure.spec.alpha/map-of [spec accept]
  (let [[_ k v] (extract-form spec)]
    (accept ::map-of spec (mapv #(visit % accept) [k v]))))

(defmethod visit 'clojure.spec.alpha/* [spec accept]
  (let [[_ inner-spec] (extract-form spec)]
    (accept 'clojure.spec.alpha/* spec [(visit inner-spec accept)])))

(defmethod visit 'clojure.spec.alpha/+ [spec accept]
  (let [[_ inner-spec] (extract-form spec)]
    (accept 'clojure.spec.alpha/+ spec [(visit inner-spec accept)])))

(defmethod visit 'clojure.spec.alpha/? [spec accept]
  (let [[_ inner-spec] (extract-form spec)]
    (accept 'clojure.spec.alpha/? spec [(visit (++expand-symbol-cljs-spec-bug++ inner-spec) accept)])))

(defmethod visit 'clojure.spec.alpha/alt [spec accept]
  (let [[_ & {:as inner-spec-map}] (extract-form spec)]
    (accept 'clojure.spec.alpha/alt spec (mapv #(visit % accept) (vals inner-spec-map)))))

(defmethod visit 'clojure.spec.alpha/cat [spec accept]
  (let [[_ & {:as inner-spec-map}] (extract-form spec)]
    (accept 'clojure.spec.alpha/cat spec (mapv #(visit % accept) (vals inner-spec-map)))))

(defmethod visit 'clojure.spec.alpha/& [spec accept]
  (let [[_ inner-spec] (extract-form spec)]
    (accept 'clojure.spec.alpha/& spec [(visit inner-spec accept)])))

(defmethod visit 'clojure.spec.alpha/tuple [spec accept]
  (let [[_ & inner-specs] (extract-form spec)]
    (accept 'clojure.spec.alpha/tuple spec (mapv #(visit % accept) inner-specs))))

;; TODO: broken: http://dev.clojure.org/jira/browse/CLJ-2147
(defmethod visit 'clojure.spec.alpha/keys* [spec accept]
  (let [keys (impl/extract-keys (extract-form spec))]
    (accept 'clojure.spec.alpha/keys* spec (mapv #(visit % accept) keys))))

(defmethod visit 'clojure.spec.alpha/nilable [spec accept]
  (let [[_ inner-spec] (extract-form spec)]
    (accept 'clojure.spec.alpha/nilable spec [(visit inner-spec accept)])))

(defmethod visit 'spec-tools.core/spec [spec accept]
  (let [[_ inner-spec] (extract-form spec)]
    (accept ::spec spec [(visit inner-spec accept)])))

(defmethod visit ::default [spec accept]
  (accept (spec-dispatch spec accept) spec nil))

;;
;; sample visitor
;;

(defn spec-collector
  "a visitor that collects all registered specs. Returns
  a map of spec-name => specs"
  []
  (let [specs (atom {})]
    (fn [_ spec _]
      (if-let [s (s/get-spec spec)]
        (swap! specs assoc spec s)
        @specs))))

;; TODO: uses ^:skip-wiki functions from clojure.spec
(comment
  (defn convert-specs!
    "Collects all registered subspecs from a spec and
    transforms their registry values into Spec Records.
    Does not convert clojure.spec.alpha regex ops."
    [spec]
    (let [specs (visit spec (spec-collector))
          report (atom #{})]
      (doseq [[k v] specs]
        (if (keyword? v)
          (swap! report into (convert-specs! v))
          (when-not (or (s/regex? v) (st/spec? v))
            (let [s (st/create-spec {:spec v})]
              (impl/register-spec! k s)
              (swap! report conj k)))))
      @report)))
