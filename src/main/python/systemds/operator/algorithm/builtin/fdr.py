# -------------------------------------------------------------
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
# -------------------------------------------------------------

# Autogenerated By   : src/main/python/generator/generator.py
# Autogenerated From : scripts/builtin/fdr.dml

from typing import Dict, Iterable

from systemds.operator import OperationNode, Matrix, Frame, List, MultiReturn, Scalar
from systemds.utils.consts import VALID_INPUT_TYPES


def fdr(P: Matrix,
        Y: Matrix):
    """
     This built-in function computes the false discovery rate
     for all classes as false-predictions / all-predictions.
    
    
    
    :param P: vector of predictions (1-based, recoded), shape: [N x 1]
    :param Y: vector of actual labels (1-based, recoded), shape: [N x 1]
    :return: vector of false discovery rate per class, shape: [M x 1]
    """

    params_dict = {'P': P, 'Y': Y}
    return Matrix(P.sds_context,
        'fdr',
        named_input_nodes=params_dict)