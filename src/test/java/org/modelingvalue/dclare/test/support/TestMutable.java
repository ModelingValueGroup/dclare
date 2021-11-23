//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2021 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
//                                                                                                                     ~
// Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in      ~
// compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0  ~
// Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on ~
// an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the  ~
// specific language governing permissions and limitations under the License.                                          ~
//                                                                                                                     ~
// Maintainers:                                                                                                        ~
//     Wim Bast, Tom Brus, Ronald Krijgsheld                                                                           ~
// Contributors:                                                                                                       ~
//     Arjan Kok, Carel Bast                                                                                           ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.dclare.test.support;

import org.modelingvalue.collections.util.StringUtil;
import org.modelingvalue.dclare.Mutable;

public class TestMutable implements Mutable {
    private final Object           id;
    private final TestMutableClass clazz;

    public static TestMutable of(Object id, TestMutableClass clazz) {
        return new TestMutable(id, clazz);
    }

    protected TestMutable(Object id, TestMutableClass clazz) {
        this.id    = id;
        this.clazz = clazz;
    }

    @Override
    public TestMutableClass dClass() {
        return clazz;
    }

    public Object id() {
        return id;
    }

    @Override
    public String toString() {
        return dClass() + "@" + StringUtil.toString(id());
    }

    @Override
    public int hashCode() {
        return dClass().hashCode() ^ id().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (getClass() != obj.getClass()) {
            return false;
        } else {
            TestMutable c = (TestMutable) obj;
            return id().equals(c.id()) && dClass().equals(c.dClass());
        }
    }

    public String serialize() {
        return id.toString();
    }
}
