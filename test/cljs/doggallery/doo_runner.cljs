(ns doggallery.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [doggallery.core-test]))

(doo-tests 'doggallery.core-test)

