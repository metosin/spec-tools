(ns spec-tools.spell-spec.expound
  (:require
    [clojure.string :as string]
    [expound.alpha :as exp]
    [expound.ansi :as ansi]
    [expound.printer :as printer]
    [expound.problems :as problems]))

(defn format-correction-list [options]
  (str (when (> (count options) 1) " one of")
       (let [joined (string/join ", " (map #(ansi/color (pr-str %) :good) options))]
         (if (> (count options) 3)
           (str "\n\n" (printer/indent joined))
           (str ": " joined)))))

(defn exp-formated [header _type spec-name val path problems opts]
  (printer/format
    "%s\n\n%s\n\n%s"
    (#'exp/header-label header)
    (printer/indent (#'exp/*value-str-fn* spec-name val path (problems/value-in val path)))
    (exp/expected-str _type spec-name val path problems opts)))

(defmethod exp/problem-group-str :spec-tools.spell-spec.alpha/misspelled-key [_type spec-name val path problems opts]
  (exp-formated "Misspelled map key" _type spec-name val path problems opts))

(defmethod exp/expected-str :spec-tools.spell-spec.alpha/misspelled-key [_type spec-name val path problems opts]
  (let [{:keys [:spec-tools.spell-spec.alpha/likely-misspelling-of]} (first problems)]
    (str "should probably be" (format-correction-list likely-misspelling-of))))

(defmethod exp/problem-group-str :spec-tools.spell-spec.alpha/unknown-key [_type spec-name val path problems opts]
  (exp-formated "Unknown map key" _type spec-name val path problems opts))

(defmethod exp/expected-str :spec-tools.spell-spec.alpha/unknown-key [_type spec-name val path problems opts]
  (str "should be" (format-correction-list (-> problems first :pred))))
