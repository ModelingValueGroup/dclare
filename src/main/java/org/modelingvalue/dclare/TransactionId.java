//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

package org.modelingvalue.dclare;

public class TransactionId {

    public static final TransactionId of(long number) {
        return new TransactionId(null, number);
    }

    public static final TransactionId of(TransactionId superTransactionId, long number) {
        return new TransactionId(superTransactionId, number);
    }

    private final TransactionId superTransactionId;
    private final long          number;

    private TransactionId(TransactionId superTransactionId, long number) {
        this.superTransactionId = superTransactionId;
        this.number = number;
    }

    public TransactionId superTransactionId() {
        return superTransactionId;
    }

    public boolean isSuper() {
        return superTransactionId == null;
    }

    public boolean isSub() {
        return superTransactionId != null;
    }

    public long number() {
        return number;
    }

    @Override
    public String toString() {
        return (isSub() ? Long.toString(superTransactionId.number) + "." : "") + Long.toString(number);
    }

}
