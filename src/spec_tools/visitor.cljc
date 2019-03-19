(ns spec-tools.visitor
  "Tools for walking spec definitions."
  (:require [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [spec-tools.parse :as parse]
            [spec-tools.impl :as impl]
            [spec-tools.form :as form]))

(defn- spec-dispatch
  [spec accept options]
  (cond
    (or (s/spec? spec) (s/regex? spec) (keyword? spec))
    (let [form (s/form spec)]
      (if (not= form ::s/unknown)
        (if (seq? form)
          (impl/normalize-symbol (first form))
          (spec-dispatch form accept options))
        spec))
    (set? spec) ::set
    (seq? spec) (impl/normalize-symbol (first (impl/strip-fn-if-needed spec)))
    (symbol? spec) (impl/normalize-symbol spec)
    :else (impl/normalize-symbol (form/resolve-form spec))))

(defmulti visit-spec spec-dispatch :default ::default)

(defn visit
  "Walk a spec definition. Takes 2-3 arguments, the spec and the accept
  function, and optionally a options map, and returns the result of
  calling the accept function. Options map can be used to pass in context-
  specific information to to sub-visits & accepts.

  The accept function is called with 4 arguments: the dispatch term for the
  spec (see below), the spec itself, vector with the results of
  recursively walking the children of the spec and the options map.

  The dispatch term is one of the following
  * if the spec is a function call: a fully qualified symbol for the function
    with the following exceptions:
    - cljs.core symbols are converted to clojure.core symbols
    - cljs.spec.alpha symbols are converted to clojure.spec.alpha symbols
  * if the spec is a set: :spec-tools.visitor/set
  * otherwise: the spec itself"
  ([spec accept]
   (visit spec accept nil))
  ([spec accept {:keys [::visited] :as options}]
    ;; quick fix for #75: don't rewalk on recursive specs
    ;; correct solution would be walk on a reference of a spec?
   (if-not (get visited spec)
     (let [options (if (keyword? spec)
                     (update options ::visited (fnil conj #{}) spec)
                     options)]
       (visit-spec spec accept options))
     (visit-spec nil accept options))))

(defmethod visit-spec ::set [spec accept options]
  (accept ::set spec (vec (if (keyword? spec) (impl/extract-form spec) spec)) options))

(defmethod visit-spec 'clojure.spec.alpha/keys [spec accept options]
  (let [keys (impl/extract-keys (impl/extract-form spec))]
    (accept 'clojure.spec.alpha/keys spec (mapv #(visit % accept options) keys) options)))

(defmethod visit-spec 'clojure.spec.alpha/or [spec accept options]
  (let [[_ & {:as inner-spec-map}] (impl/extract-form spec)]
    (accept 'clojure.spec.alpha/or spec (mapv #(visit % accept options) (vals inner-spec-map)) options)))

(defmethod visit-spec 'clojure.spec.alpha/and [spec accept options]
  (let [[_ & inner-specs] (impl/extract-form spec)]
    (accept 'clojure.spec.alpha/and spec (mapv #(visit % accept options) inner-specs) options)))

(defn- visit-merge [spec accept options]
  (let [[_ & inner-specs] (impl/extract-form spec)]
    (accept 'clojure.spec.alpha/merge spec (mapv #(visit % accept options) inner-specs) options)))

(defmethod visit-spec 'clojure.spec.alpha/merge [spec accept options]
  (visit-merge spec accept options))

(defmethod visit-spec 'spec-tools.core/merge [spec accept options]
  (visit-merge spec accept options))

(defmethod visit-spec 'clojure.spec.alpha/every [spec accept options]
  (let [[_ inner-spec] (impl/extract-form spec)]
    (accept 'clojure.spec.alpha/every spec [(visit inner-spec accept options)] options)))

(defmethod visit-spec 'clojure.spec.alpha/every-kv [spec accept options]
  (let [[_ inner-spec1 inner-spec2] (impl/extract-form spec)]
    (accept 'clojure.spec.alpha/every-kv spec (mapv
                                                #(visit % accept options)
                                                [inner-spec1 inner-spec2]) options)))

(defmethod visit-spec 'clojure.spec.alpha/coll-of [spec accept options]
  (let [form (impl/extract-form spec)
        pred (second form)
        {:keys [type]} (parse/parse-spec form)
        dispatch (case type
                   :map-of ::map-of
                   :set ::set-of
                   :vector ::vector-of)]
    (accept dispatch spec [(visit pred accept options)] options)))

(defmethod visit-spec 'clojure.spec.alpha/map-of [spec accept options]
  (let [[_ k v] (impl/extract-form spec)]
    (accept ::map-of spec (mapv #(visit % accept options) [k v]) options)))

(defmethod visit-spec 'clojure.spec.alpha/* [spec accept options]
  (let [[_ inner-spec] (impl/extract-form spec)]
    (accept 'clojure.spec.alpha/* spec [(visit inner-spec accept options)] options)))

(defmethod visit-spec 'clojure.spec.alpha/+ [spec accept options]
  (let [[_ inner-spec] (impl/extract-form spec)]
    (accept 'clojure.spec.alpha/+ spec [(visit inner-spec accept options)] options)))

(defmethod visit-spec 'clojure.spec.alpha/? [spec accept options]
  (let [[_ inner-spec] (impl/extract-form spec)]
    (accept 'clojure.spec.alpha/? spec [(visit inner-spec accept options)] options)))

(defmethod visit-spec 'clojure.spec.alpha/alt [spec accept options]
  (let [[_ & {:as inner-spec-map}] (impl/extract-form spec)]
    (accept 'clojure.spec.alpha/alt spec (mapv #(visit % accept options) (vals inner-spec-map)) options)))

(defmethod visit-spec 'clojure.spec.alpha/cat [spec accept options]
  (let [[_ & {:as inner-spec-map}] (impl/extract-form spec)]
    (accept 'clojure.spec.alpha/cat spec (mapv #(visit % accept options) (vals inner-spec-map)) options)))

(defmethod visit-spec 'clojure.spec.alpha/& [spec accept options]
  (let [[_ inner-spec] (impl/extract-form spec)]
    (accept 'clojure.spec.alpha/& spec [(visit inner-spec accept options)] options)))

(defmethod visit-spec 'clojure.spec.alpha/tuple [spec accept options]
  (let [[_ & inner-specs] (impl/extract-form spec)]
    (accept 'clojure.spec.alpha/tuple spec (mapv #(visit % accept options) inner-specs) options)))

;; TODO: broken: http://dev.clojure.org/jira/browse/CLJ-2147
(defmethod visit-spec 'clojure.spec.alpha/keys* [spec accept options]
  (let [keys (impl/extract-keys (impl/extract-form spec))]
    (accept 'clojure.spec.alpha/keys* spec (mapv #(visit % accept options) keys) options)))

(defmethod visit-spec 'clojure.spec.alpha/nilable [spec accept options]
  (let [[_ inner-spec] (impl/extract-form spec)]
    (accept 'clojure.spec.alpha/nilable spec [(visit inner-spec accept options)] options)))

(defmethod visit-spec 'spec-tools.core/spec [spec accept options]
  (let [[_ {inner-spec :spec}] (impl/extract-form spec)]
    (accept ::spec spec [(visit inner-spec accept options)] options)))

(defmethod visit-spec ::default [spec accept options]
  (accept (spec-dispatch spec accept options) spec nil options))

;;
;; sample visitor
;;

(defn spec-collector
  "a visitor that collects all registered specs. Returns
  a map of spec-name => spec."
  []
  (let [specs (atom {})]
    (fn [_ spec _ _]
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
