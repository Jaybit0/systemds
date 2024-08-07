#-------------------------------------------------------------
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
#-------------------------------------------------------------


args <- commandArgs(TRUE)
options(digits=22)
library("Matrix")

A = matrix(1, nrow=10, ncol=2);
B = matrix(2, nrow=2, ncol=1);
C = matrix(3, nrow=10, ncol=10);
D = matrix(4, nrow=10, ncol=10);
E = matrix(5, nrow=10, ncol=20);
F = matrix(6, nrow=20, ncol=1);

RESULT = t(A %*% B) %*% C %*% D %*% (E %*% F);

writeMM(as(RESULT, "CsparseMatrix"), paste(args[1], "R", sep=""));
