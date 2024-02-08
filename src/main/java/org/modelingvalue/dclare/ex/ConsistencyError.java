//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2024 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.dclare.ex;

import org.modelingvalue.dclare.Feature;

@SuppressWarnings({"serial", "unused"})
public abstract class ConsistencyError extends RuntimeException implements Comparable<ConsistencyError> {
    private final Object  object;
    private final Feature feature;
    private final int     severity;

    protected ConsistencyError(Object object, Feature feature, int severity, String message) {
        super(message);
        this.object   = object;
        this.feature  = feature;
        this.severity = severity;
    }

    protected ConsistencyError(Object object, Feature feature, int severity, Throwable t) {
        super(t);
        this.object   = object;
        this.feature  = feature;
        this.severity = severity;
    }

    protected ConsistencyError(Object object, Feature feature, int severity) {
        this.object   = object;
        this.feature  = feature;
        this.severity = severity;
    }

    public Object getObject() {
        return object;
    }

    public int getSeverity() {
        return severity;
    }

    public Feature getFeature() {
        return feature;
    }

    @Override
    public int compareTo(ConsistencyError o) {
        return Integer.compare(severity, o.severity);
    }
}
