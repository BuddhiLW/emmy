#_"SPDX-License-Identifier: GPL-3.0"

(ns emmy.giac-test
  (:require [clojure.test :refer [deftest is testing]]
            [emmy.generic :as g]
            [emmy.giac :as giac]
            [emmy.giac.render :refer [->giac]]))

(deftest render-test
  (testing "arithmetic and precedence"
    (is (= "3*x^2 + 1" (->giac '(+ (* 3 (expt x 2)) 1))))
    (is (= "(x + 1)^2" (->giac '(expt (+ x 1) 2))))
    (is (= "x = 0" (->giac '(= x 0)))))

  (testing "function names follow Giac"
    (is (= "ln(x)" (->giac '(log x))))
    (is (= "atan2(y, x)" (->giac '(atan y x))))
    (is (= "atan(x)" (->giac '(atan x)))))

  (testing "tuples become lists"
    (is (= "[x, y]" (->giac '(up x y)))))

  (testing "Giac's constants are not captured by Emmy variables"
    (is (= "sin(emmy_i) + emmy_e" (->giac '(+ (sin i) e))))))

(defn- printed
  "A string as Giac's caseval prints it: quoted, with inner quotes doubled."
  [s]
  (str "\"" (.replace ^String s "\"" "\"\"") "\""))

(deftest read-result-test
  (testing "numbers, rationals and lists"
    (is (= 10 (giac/read-result (printed "10"))))
    (is (= (g/div 3 4) (giac/read-result (printed "(\"/\" 3 4)"))))
    (is (= [1 2] (giac/read-result (printed "[ 1 2]")))))

  (testing "expressions come back through Emmy's generic arithmetic"
    (is (= '(* (+ x -1) (+ x 1))
           (g/freeze
            (giac/read-result
             (printed "(\"'*'\" (\"'+'\" (\"id\" \"x\") -1) (\"'+'\" (\"id\" \"x\") 1))"))))))

  (testing "unknown functions and relations"
    (is (= '(f x y)
           (g/freeze
            (giac/read-result
             (printed "(\"'of'\" (\"id\" \"f\") [ (\"id\" \"x\") (\"id\" \"y\")])")))))
    (is (= '(>= x y)
           (g/freeze
            (giac/read-result
             (printed "(\"'>='\" (\"id\" \"x\") (\"id\" \"y\"))"))))))

  (testing "prefixed variables lose their prefix"
    (is (= 'i (giac/read-result (printed "(\"id\" \"emmy_i\")")))))

  (testing "a Giac error is reported, not parsed"
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (giac/read-result "syntax error line 1")))))

(deftest settings-test
  (is (= ["complex_mode:=0" "angle_radian:=1" "Digits:=30" "assume(x > 0)"]
         (vec (giac/settings->commands
               {:complex? false :angle :radian :digits 30
                :assume ['(> x 0)]})))))
