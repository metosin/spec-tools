(ns spec-tools.visitor
  (:require [clojure.spec :as s]
            [clojure.set :as set]))

(defn- strip-fn-if-needed [form]
  (let [head (first form)]
    ;; Deal with the form (clojure.core/fn [%] (foo ... %))
    ;; We should just use core.match...
    (if (and (= (count form) 3) (= head 'clojure.core/fn))
      (nth form 2)
      form)))

(defn- formize [spec] (if (seq? spec) spec (s/form spec)))

(defn- spec-dispatch
  [spec accept]
  (cond
    (or (s/spec? spec) (s/regex? spec) (keyword? spec))
    (let [form (s/form spec)]
      (if (not= form :clojure.spec/unknown)
        (if (seq? form)
          (first form)
          (spec-dispatch form accept))
        spec))
    (set? spec) ::set
    (seq? spec) (first (strip-fn-if-needed spec))
    :else spec))

(defmulti visit spec-dispatch :default ::default)

(defmethod visit ::set [spec accept]
  (accept ::set (vec spec)))

(defmethod visit 'clojure.spec/every [spec accept]
  (let [[_ inner-spec & kwargs] (formize spec)]
    (accept 'clojure.spec/every spec [(visit inner-spec accept)])))

(defmethod visit 'clojure.spec/tuple [spec accept]
  (let [[_ & inner-specs] (formize spec)]
    (accept 'clojure.spec/tuple spec (mapv #(visit % accept) inner-specs))))

(defmethod visit 'clojure.spec/* [spec accept]
  (let [[_ inner-spec] (s/form spec)]
    (accept 'clojure.spec/* spec [(visit inner-spec accept)])))

(defmethod visit 'clojure.spec/+ [spec accept]
  (let [[_ inner-spec] (s/form spec)]
    (accept 'clojure.spec/+ spec [(visit inner-spec accept)])))

(defmethod visit 'clojure.spec/keys [spec accept]
  (let [[_ & {:keys [req req-un opt opt-un]}] (s/form spec)]
    (accept 'clojure.spec/keys spec (mapv #(visit % accept) (concat req req-un opt opt-un)))))

(defmethod visit 'clojure.spec/or [spec accept]
  (let [[_ & {:as inner-spec-map}] (s/form spec)]
    (accept 'clojure.spec/or spec (mapv #(visit % accept) (vals inner-spec-map)))))

(defmethod visit 'clojure.spec/and [spec accept]
  (let [[_ & inner-specs] (s/form spec)]
    (accept 'clojure.spec/and spec (mapv #(visit % accept) inner-specs))))

(defmethod visit 'clojure.spec/nilable [spec accept]
  (let [[_ inner-spec] (s/form spec)]
    (accept 'clojure.spec/nilable spec [(visit inner-spec accept)])))

(defmethod visit ::default [spec accept]
  (accept (spec-dispatch spec accept) spec nil))

(s/def ::a string?)
(s/def ::b integer?)
(s/def ::f integer?)
(s/def ::e ::f)
(s/def ::c (s/keys :req [::a]))
(s/def ::d (s/tuple ::e integer?))
(s/def ::foo (s/keys :req [::b ::c ::d]))

(prn (visit ::foo (fn [x y z]
                    (case x
                      clojure.spec/keys
                      (let [[_ & {:keys [req req-un opt opt-un]}] (s/form y)]
                        {:type "object"
                         :properties (zipmap (map name (concat req req-un opt opt-un)) z)
                         :required (mapv name (concat req req-un))})
                      clojure.core/string?
                      {:type "string"}
                      clojure.core/integer?
                      {:type "number"}
                      {}
                      ))))

(prn (visit ::foo (fn [dispatch spec children]
                    (case dispatch
                      clojure.spec/keys
                      (let [[_ & {:keys [req req-un opt opt-un]}] (s/form spec)]
                        (apply set/union (set (concat req req-un opt opt-un)) children))
                      (apply set/union (when (keyword? spec) #{spec}) children)))))
