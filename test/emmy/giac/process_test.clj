#_"SPDX-License-Identifier: GPL-3.0"

(ns emmy.giac.process-test
  "Runs Giac for real through the process engine. Skipped unless the server
  module (`wasm/giac/build.sh`, or `EMMY_GIAC_WASM`) and the zwasm runtime are
  both present."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.test :refer [deftest is testing]]
            [emmy.generic :as g]
            [emmy.giac :as giac]
            [emmy.giac.process :as gp]))

(defn- runtime-available? []
  (and (.exists (io/file gp/default-wasm))
       (try (zero? (:exit (sh/sh "zwasm" "--version")))
            ;; sh throws when the zwasm binary is not on PATH: not available.
            (catch java.io.IOException _zwasm-not-installed false))))

(deftest round-trip-test
  (if-not (runtime-available?)
    (println "skipping emmy.giac.process-test: no giac-cli.wasm or zwasm")
    (let [e (gp/start!)]
      (try
        (testing "Emmy expressions out, Emmy expressions back"
          (is (= 1 (giac/limit e '(/ (sin x) x) 'x 0)))
          (is (= 9 (giac/integrate e '(expt x 2) 'x 0 3)))
          (is (= [1 2] (giac/solve e '(+ (expt x 2) (* -3 x) 2) 'x)))
          (is (= '(* (+ x -1) (+ x 1) (+ (expt x 2) 1))
                 (g/freeze (giac/factor e '(- (expt x 4) 1))))))

        (testing "derivatives of unknown functions use Emmy's partial"
          (is (= '(((partial 0) f) x y)
                 (g/freeze (giac/call e 'diff '(f x y) 'x)))))

        (testing "an Emmy variable named i is not the imaginary unit"
          (is (= '(+ (expt i 2) (* 2 i) 1)
                 (g/freeze (giac/expand e '(expt (+ i 1) 2))))))

        (testing "settings change the session"
          (giac/configure! e {:assume ['(> x 0)]})
          (is (= 'x (giac/simplify e '(sqrt (expt x 2))))))

        (testing "Giac's own LaTeX"
          (is (string? (giac/latex e '(/ 1 x)))))
        (finally (gp/stop! e))))))
