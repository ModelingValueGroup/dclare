//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2023 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import java.util.Objects;

import org.modelingvalue.collections.DefaultMap;
import org.modelingvalue.collections.Set;

@SuppressWarnings({"unused", "rawtypes"})
public class UniverseStatistics {
    private final UniverseTransaction  tx;

    private boolean                    debugging;
    private long                       runCount;
    private long                       forwardCount;
    private int                        totalChanges;
    private long                       totalChangesEver;
    private long                       mostTotalChangesEver;
    private int                        mostObservers;
    private long                       mostObserversEver;
    private ChampionObserver           championObservers;
    private int                        mostObserved;
    private long                       mostObservedEver;
    private ChampionObserved           championObserved;
    private int                        mostChangesPerInstance;
    private long                       mostChangesPerInstanceEver;
    private ChampionChangesPerInstance championChangesPerInstance;

    public static class ChampionObserver {
        public final Observed                           observed;
        public final Mutable                            mutable;
        public final DefaultMap<Observer, Set<Mutable>> observers;

        public ChampionObserver(Observed observed, Mutable mutable, DefaultMap<Observer, Set<Mutable>> observers) {
            this.observed = observed;
            this.mutable = mutable;
            this.observers = observers;
        }

        @Override
        public String toString() {
            return String.format("{observed=%s, mutable=%s, observers=%s}", observed, mutable, LeafTransaction.condenseForConsistencyTrace(observers));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ChampionObserver that = (ChampionObserver) o;
            return Objects.equals(observed, that.observed) && Objects.equals(mutable, that.mutable) && Objects.equals(observers, that.observers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(observed, mutable, observers);
        }
    }

    public static class ChampionObserved {
        public final Observer<?>                        observer;
        public final Mutable                            mutable;
        public final DefaultMap<Observed, Set<Mutable>> observeds;

        public ChampionObserved(Observer<?> observer, Mutable mutable, DefaultMap<Observed, Set<Mutable>> observeds) {
            this.observer = observer;
            this.mutable = mutable;
            this.observeds = observeds;
        }

        @Override
        public String toString() {
            return String.format("{observer=%s, mutable=%s, observeds=%s}", observer, mutable, LeafTransaction.condenseForConsistencyTrace(observeds));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ChampionObserved that = (ChampionObserved) o;
            return Objects.equals(observer, that.observer) && Objects.equals(mutable, that.mutable) && Objects.equals(observeds, that.observeds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(observer, mutable, observeds);
        }
    }

    public static class ChampionChangesPerInstance {
        public final Observer<?> observer;
        public final Mutable     mutable;

        public ChampionChangesPerInstance(Observer<?> observer, Mutable mutable) {
            this.observer = observer;
            this.mutable = mutable;
        }

        @Override
        public String toString() {
            return String.format("{observer=%s, mutable=%s}", observer, mutable);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ChampionChangesPerInstance that = (ChampionChangesPerInstance) o;
            return Objects.equals(observer, that.observer) && Objects.equals(mutable, that.mutable);
        }

        @Override
        public int hashCode() {
            return Objects.hash(observer, mutable);
        }
    }

    public UniverseStatistics(UniverseTransaction tx) {
        this.tx = tx;
    }

    public UniverseStatistics(UniverseStatistics o) {
        this(o.tx);
        this.debugging = o.debugging;
        this.runCount = o.runCount;
        this.forwardCount = o.forwardCount;
        this.totalChanges = o.totalChanges;
        this.totalChangesEver = o.totalChangesEver;
        this.mostTotalChangesEver = o.mostTotalChangesEver;
        this.mostObservers = o.mostObservers;
        this.mostObserversEver = o.mostObserversEver;
        this.championObservers = o.championObservers;
        this.mostObserved = o.mostObserved;
        this.mostObservedEver = o.mostObservedEver;
        this.championObserved = o.championObserved;
        this.mostChangesPerInstance = o.mostChangesPerInstance;
        this.mostChangesPerInstanceEver = o.mostChangesPerInstanceEver;
        this.championChangesPerInstance = o.championChangesPerInstance;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public int maxInInQueue() {
        return tx.getConfig().getMaxInInQueue();
    }

    public int maxNrOfHistory() {
        return tx.getConfig().getMaxNrOfHistory();
    }

    public boolean devMode() {
        return tx.getConfig().isDevMode();
    }

    void completeRun() {
        runCount++;

        int totChan = totalChanges;
        totalChanges = 0;
        int mostObs = mostObservers;
        mostObservers = 0;
        int mostObd = mostObserved;
        mostObserved = 0;
        int mostChpi = mostChangesPerInstance;
        mostChangesPerInstance = 0;

        totalChangesEver += totChan;
        mostTotalChangesEver = Math.max(mostTotalChangesEver, totChan);
        mostObserversEver = Math.max(mostObserversEver, mostObs);
        mostObservedEver = Math.max(mostObservedEver, mostObd);
        mostChangesPerInstanceEver = Math.max(mostChangesPerInstanceEver, mostChpi);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public boolean debugging() {
        return debugging;
    }

    public void setDebugging(boolean on) {
        debugging = on;
    }

    public long runCount() {
        return runCount;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    void bumpForwardCount() {
        forwardCount++;
    }

    public long forwardCount() {
        return forwardCount;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public int maxTotalNrOfChanges() {
        return tx.getConfig().getMaxTotalNrOfChanges();
    }

    public int bumpAndGetTotalChanges() {
        if (!devMode() || totalChanges <= maxTotalNrOfChanges()) {
            return totalChanges++;
        }
        synchronized (tx) {
            return totalChanges++;
        }
    }

    public int totalChanges() {
        return totalChanges;
    }

    public long totalChangesEver() {
        return totalChangesEver;
    }

    public long mostTotalChangesEver() {
        return mostTotalChangesEver;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public int maxNrOfChanges() {
        return tx.getConfig().getMaxNrOfChanges();
    }

    public boolean tooManyChangesPerInstance(int n, Observer<?> observer, Mutable mutable) {
        if (!devMode()) {
            return false;
        }
        if (n <= maxNrOfChanges()) {
            if (mostChangesPerInstance < n) {
                mostChangesPerInstance = n;
                championChangesPerInstance = new ChampionChangesPerInstance(observer, mutable);
            }
            return false;
        }
        synchronized (tx) {
            if (mostChangesPerInstance < n) {
                mostChangesPerInstance = n;
                championChangesPerInstance = new ChampionChangesPerInstance(observer, mutable);
            }
        }
        return true;
    }

    public int mostChangesPerInstance() {
        return mostChangesPerInstance;
    }

    public long mostChangesPerInstanceEver() {
        return mostChangesPerInstanceEver;
    }

    public ChampionChangesPerInstance championChangesPerInstance() {
        return championChangesPerInstance;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public int maxNrOfObservers() {
        return tx.getConfig().getMaxNrOfObservers();
    }

    public boolean tooManyObservers(Observed observed, Object /* always Mutable */ o, DefaultMap<Observer, Set<Mutable>> observers) {
        if (!devMode()) {
            return false;
        }
        int n = LeafTransaction.sizeForConsistency(observers);
        if (n <= maxNrOfObservers()) {
            if (mostObservers < n) {
                mostObservers = n;
                championObservers = new ChampionObserver(observed, o instanceof Mutable ? (Mutable) o : null, observers);
            }
            return false;
        }
        synchronized (tx) {
            if (mostObservers < n) {
                mostObservers = n;
                championObservers = new ChampionObserver(observed, o instanceof Mutable ? (Mutable) o : null, observers);
            }
        }
        return true;
    }

    public int mostObservers() {
        return mostObservers;
    }

    public long mostObserversEver() {
        return mostObserversEver;
    }

    public ChampionObserver championObservers() {
        return championObservers;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public int maxNrOfObserved() {
        return tx.getConfig().getMaxNrOfObserved();
    }

    public boolean tooManyObserved(Observer<?> observer, Mutable mutable, DefaultMap<Observed, Set<Mutable>> observeds) {
        if (!devMode()) {
            return false;
        }
        int n = LeafTransaction.sizeForConsistency(observeds);
        if (n <= maxNrOfObserved()) {
            if (mostObserved < n) {
                mostObserved = n;
                championObserved = new ChampionObserved(observer, mutable, observeds);
            }
            return false;
        }
        synchronized (tx) {
            if (mostObserved < n) {
                mostObserved = n;
                championObserved = new ChampionObserved(observer, mutable, observeds);
            }
        }
        return true;
    }

    public int mostObserved() {
        return mostObserved;
    }

    public long mostObservedEver() {
        return mostObservedEver;
    }

    public ChampionObserved championObserved() {
        return championObserved;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public UniverseTransaction universeTransaction() {
        return tx;
    }

    @Override
    public String toString() {
        return "UniverseStats:\n" //
                + "    debugging                  = " + debugging + "\n" //
                + "    runCount                   = " + runCount + "\n" //
                + "    forwardCount               = " + forwardCount + "\n" //
                + "    totalChanges               = " + totalChanges + "\n" //
                + "    totalChangesEver           = " + totalChangesEver + "\n" //
                + "    mostTotalChangesEver       = " + mostTotalChangesEver + "\n" //
                + "    mostObservers              = " + mostObservers + "\n" //
                + "    mostObserversEver          = " + mostObserversEver + "\n" //
                + "    championObservers          = " + championObservers + "\n" //
                + "    mostObserved               = " + mostObserved + "\n" //
                + "    mostObservedEver           = " + mostObservedEver + "\n" //
                + "    championObserved           = " + championObserved + "\n" //
                + "    mostChangesPerInstance     = " + mostChangesPerInstance + "\n" //
                + "    mostChangesPerInstanceEver = " + mostChangesPerInstanceEver + "\n" //
                + "    championChangesPerInstance = " + championChangesPerInstance + "\n" //
        ;
    }

    public String shortString() {
        return String.format("[debug=%-5s run=%6d forward=%6d changes=%6d/%6d/%6d pInst=%6d/%6d/%s observers=%6d/%6d/%s observed=%6d/%6d/%s]", //
                debugging, runCount, forwardCount, //
                totalChanges, mostTotalChangesEver, totalChangesEver, //
                mostChangesPerInstance, mostChangesPerInstanceEver, championChangesPerInstance, //
                mostObservers, mostObserversEver, championObservers, //
                mostObserved, mostObservedEver, championObserved //
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UniverseStatistics that = (UniverseStatistics) o;
        return tx == that.tx //
                && debugging == that.debugging //
                && runCount == that.runCount //
                && forwardCount == that.forwardCount //
                && totalChanges == that.totalChanges //
                && totalChangesEver == that.totalChangesEver //
                && mostTotalChangesEver == that.mostTotalChangesEver //
                && mostObservers == that.mostObservers //
                && mostObserversEver == that.mostObserversEver //
                && Objects.equals(championObservers, that.championObservers) //
                && mostObserved == that.mostObserved //
                && mostObservedEver == that.mostObservedEver //
                && Objects.equals(championObserved, that.championObserved) //
                && mostChangesPerInstance == that.mostChangesPerInstance //
                && mostChangesPerInstanceEver == that.mostChangesPerInstanceEver //
                && Objects.equals(championChangesPerInstance, that.championChangesPerInstance) //
        ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tx, //
                debugging, //
                runCount, //
                forwardCount, //
                totalChanges, //
                totalChangesEver, //
                mostTotalChangesEver, //
                mostObservers, //
                mostObserversEver, //
                championObservers, //
                mostObserved, //
                mostObservedEver, //
                championObserved, //
                mostChangesPerInstance, //
                mostChangesPerInstanceEver, //
                championChangesPerInstance //
        );
    }
}
