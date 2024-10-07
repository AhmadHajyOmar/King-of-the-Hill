package com.pseuco.np21;


import com.pseuco.np21.shared.Position;
import com.pseuco.np21.shared.Recorder;
import com.pseuco.np21.shared.Trail.Pheromone;
import com.pseuco.np21.shared.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Representation of an ant with behavior.
 * <p>
 * You may change the code however you see fit.
 */
public class Ant extends com.pseuco.np21.shared.Ant implements Runnable {

    /**
     * Helper class for trail selected to store the indices of the trails and the trails in a pair of two lists
     */
    private static class Pair {
        List<Integer> first;
        List<Trail> second;

        public Pair(List<Integer> first, List<Trail> second) {
            this.first = first;
            this.second = second;
        }
    }

    private static class AntDiedException extends Throwable {
        private final boolean eaten;
        private final Position where;

        private AntDiedException(final boolean eaten, final Position where) {
            this.eaten = eaten;
            this.where = where;
        }

        private boolean wasEaten() {
            return eaten;
        }

        private Position where() {
            return where;
        }
    }

    private final World<Clearing, Trail> world;
    public final Recorder recorder;

    public Clearing position;

    /**
     * Flag indicating that the ant dies on a trail
     * Flag indicating that the ant carries food and is on the way back home
     * Flag representing if an ant is an adventurer
     */
    private boolean eaten = false, goHome = false, adventurous = false, updateFoodHome = true, searchingFood = false;

    /**
     * Number of steps taken since food was picked.
     */
    private int stepsSinceFood = 0;
    private int stepsSinceHill = 0;

    /**
     * Using the class Sequence we simulate the memory of the ant. This structure implements kF (c)
     */
    private final Sequence brain;

    /**
     * Constructs an ant given a basic ant, the world and a recorder.
     *
     * @param ant      the template ant
     * @param world    the ant has to live in
     * @param recorder to log all actions against
     */
    public Ant(final com.pseuco.np21.shared.Ant ant, final World<Clearing, Trail> world, final Recorder recorder) {
        super(ant);
        this.world = world;
        this.recorder = recorder;
        this.brain = new Sequence();
    }

    /**
     * Primary ant behavior.
     */
    public void run() {
        position = world.anthill();
        recorder.spawn(this);

        // Add anthill to memory
        brain.addClearing(position.id(), position.connectsTo().size());
        this.recorder.enter(this, this.position);

        while (!termination()) {
            /*
             * State in the beginning of a loop:
             * No termination criterion "fired", so the ant lives and can search for food
             * Every time the execution passes this point, the ant is in a clearing that is already added to the brain
             * Each loop-run performs exactly one traversal to a new clearing. The only exceptions are
             *     - the ant picks up food, then the ant does not move
             *     - the ant drops food to the anthill, then the ant does not move
             *     - the ant traverses to a clearing it has already seen, then the ant also goes back to the
             *         start-clearing of this loop-round and traverses twice in one round
             */

            // Check if the ant is heading home carrying some food or searched for food
            if (!goHome) {
                // If the ant is in the anthill, set the anthill counter to 0 and start food-search in case it didn't
                // return with food. If the ant brings food home but hasn't dropped it yet, this is not called because
                // the goHome-flag still is true
                if (position.compareTo(world.anthill()) == 0) {
                    stepsSinceHill = 0;

                    // If the ant isn't searching for food yet, start the search
                    if (!searchingFood) {
                        searchingFood = true;
                        this.recorder.startFoodSearch(this);
                    }
                }
                // search for food, so check if the current clearing has food
                if (position.hasFood()) {
                    // If we found food, pick up some food
                    takeFood();
                } else {
                    // otherwise, select a trail to traverse and go take this trail to a new clearing
                    // this method follows the specifications of kF (a) and kF (b)
                    int index = selectWay();

                    // handle the cases kF (e) and kF (f), for which selectWay doesn't return an index but -1
                    if (index == -1) {
                        stepsSinceHill--;
                        handleDeadEnd();
                        continue;
                    }

                    // mark the trail to traverse as seen in the brain
                    brain.addBlock(index);
                    Trail t = position.connectsTo().get(index);
                    stepsSinceHill++;
                    traverse(t, !brain.alreadySeen(t.to().id()));

                    // Check if the new clearing was already seen in the traversal, handle case kF (d)
                    if (brain.alreadySeen(t.to().id())) {
                        // head back to clearing from where we reached the new clearing
                        this.recorder.select(this, t.reverse(), null, Recorder.SelectionReason.IMMEDIATE_RETURN);
                        traverse(t.reverse(), false);

                        // add blockade in clearing from where we returned
                        brain.addBlock(t.to().id(), selectLastUsedTrail(t.to(), position.id()));


                        // going back on the trace, so the distance is again reduced
                        stepsSinceHill--;
                    } else {
                        // Otherwise, add the clearing to the brain
                        Clearing target = t.to();
                        brain.addClearing(target.id(), target.connectsTo().size());
                        brain.addTrail(t);
                        this.position = target;
                    }
                }
            } else {
                // In case the ant carries food and goes home, check if the ant has reached home
                if (position.compareTo(world.anthill()) == 0) {
                    // drop the food
                    world.foodCollected();
                    recorder.returnedFood(this);

                    // Clear brain and add the anthill to the brain again
                    brain.clear();
                    brain.addClearing(position.id(), position.connectsTo().size());

                    // reset the "statistics" and some control parameters
                    stepsSinceFood = 0;
                    stepsSinceHill = 0;
                    adventurous = false;
                    goHome = false;
                    updateFoodHome = true;
                    searchingFood = false;

                } else if (adventurous) {
                    // otherwise, if the and is an adventurer, go back the path the ant has taken to find food
                    // This behaviour follows kR (a) S1, i.e. find last used trail the ant has to choose now
                    //Trail t = position.connectsTo().get(selectLastUsedTrail(this.position, brain.lastId()));
                    Trail t = brain.lastTrail().reverse();
                    this.recorder.select(this, t, null, Recorder.SelectionReason.RETURN_IN_SEQUENCE);

                    // Traverse this path to come home
                    stepsSinceFood++;
                    traverse(t, updateFoodHome);
                    brain.removeLast();
                    position = t.to();
                } else {
                    // The and is non-adventurous and wants to go home, find a way according to the pheromones
                    Trail t = selectWayHome();
                    // traverse the selected trail
                    stepsSinceFood++;
                    traverse(t, updateFoodHome);
                    position = t.to();
                }
            }
        }
    }

    /**
     * Check the termination criteria of the ant.
     * <p>
     * Correctness:
     * Terminates with the reasons from the specification and as to return no locks as no locks are hold when calling
     * <p>
     * Runtime:
     * Constant
     *
     * @return {@code true} if the ant has to terminate due to one of the specified criteria
     */
    private boolean termination() {
        if (this.eaten) {
            // Ant was eaten and died, don't leave a position, because the ant attracted attention on a trail
            recorder.despawn(this, Recorder.DespawnReason.DISCOVERED_AND_EATEN);
            return true;
        } else if (Thread.currentThread().isInterrupted()) {
            // External termination
            terminate();
            return true;
        } else if (!world.isFoodLeft()) {
            /*
             * Aimed food amount reached or no food left to collect
             * Case f from project description is covered here, as in the case that every path is marked MaP,
             * there cannot be food on the map anymore.
             */
            this.recorder.leave(this, this.position);
            recorder.despawn(this, Recorder.DespawnReason.ENOUGH_FOOD_COLLECTED);
            return true;
        }
        return false;
    }

    /**
     * Just handle the most common case of termination
     */
    public void terminate() {
        recorder.leave(this, position);
        recorder.despawn(this, Recorder.DespawnReason.TERMINATED);
    }

    /**
     * Ant in Clearing decides which trail it wants to take
     * <p>
     * Correctness:
     * Implementation of kF (a) and kF (b). If requirements of kF (e) are met, return -1 and indication of dead-end
     * <p>
     * Runtime:
     * Linear in the number of trails that are connected to the current position.
     * This method (and its called submethods) take the locks for the pheromones ones per trail
     *
     * @return the selected trail from {@link com.pseuco.np21.shared.Clearing#connectsTo()} of the position-field
     * according to kF (a) §3 or -1 if the conditions of kF (e) are fulfilled
     * @implSpec The ant calling this method searches for food and is either adventurous or not. The positon of the ant
     * is always the position of this ant.
     */
    private int selectWay() {
        List<Trail> trailList = this.position.connectsTo();

        // Only select those trails that are valid with respect to kF (a) §3
        List<Pair> trailType = divideIntoTrailTypes(trailList);
        List<Integer> minPheromoneTrailIndices = trailType.get(0).first;
        List<Integer> NAPTrailIndices = trailType.get(1).first;

        if (minPheromoneTrailIndices.isEmpty() && NAPTrailIndices.isEmpty()) {
            // dead end, no viable trail from this clearing. this case is handled in the run-method and indicates by -1
            // initial condition of kF (e), i.e. only MaP or already used trails available
            return -1;
        }

        // select a way to go, if there is no way with valid pheromones, default to -1
        int selectedWayIndex = minPheromoneTrailIndices.isEmpty() ? -1 : minPheromoneTrailIndices.get(getRandomIndex(minPheromoneTrailIndices.size()));

        // select a selection-rule according to kF (a) §2 and §3
        if (!NAPTrailIndices.isEmpty() && (minPheromoneTrailIndices.isEmpty() || trailList.get(selectedWayIndex).food().value() > this.impatience)) {
            // The ant will select an NaP-trail, so set the flag adventurous flag according to kF (b) §1
            if (!adventurous) {
                this.adventurous = true;
                this.recorder.startExploration(this);
            }

            // select an element randomly from the NAP trails
            int index = NAPTrailIndices.get(getRandomIndex(NAPTrailIndices.size()));
            this.recorder.select(this, trailList.get(index), trailType.get(1).second, Recorder.SelectionReason.EXPLORATION);
            return index;
        } else {
            // We are in case kF (a) §1 and a trail is already selected previously;
            this.recorder.select(this, trailList.get(selectedWayIndex), trailType.get(0).second, Recorder.SelectionReason.FOOD_SEARCH);
            return selectedWayIndex;
        }
    }

    /**
     * Handle the case that there are no trails the ant can walk over following the specification of kF (a) and kF (b)
     * <p>
     * Following kF (e), the ant has to return to the previous clearing using the trail it used to reach the current
     * position.
     * <p>
     * Following kF (f), the and has to termite as it has no option to do since there is no way back from the anthill
     * <p>
     * Correctness:
     * Following kF (f), the ant terminates with {@link com.pseuco.np21.shared.Recorder.DespawnReason#TERMINATED}
     * Following kF (e), the ant looks up the last path and traverses the opposite direction
     * <p>
     * Runtime:
     * Linear in the number of trails of the last clearing as we have to find the last used path incase of non-anthill
     * dead-ends
     */
    private void handleDeadEnd() {
        // Terminate in case I'm in the anthill case kF (f)
        if (position.compareTo(world.anthill()) == 0) {
            Thread.currentThread().interrupt();
            return;
        }

        // select the trail according to kF (e)
        //Trail t = position.connectsTo().get(selectLastUsedTrail(this.position, brain.lastId()));
        Trail t = brain.lastTrail().reverse();
        this.recorder.select(this, t, null, Recorder.SelectionReason.NO_FOOD_RETURN);

        //Traverse one step back using the last used trail and update the food pheromone to MaP
        traverse(t, false);
        t.reverse().updateFood(Pheromone.get(-1), this.adventurous);
        this.recorder.updateFood(this, t.reverse(), Pheromone.get(-1));
        this.position = t.to();

        //Remove the last element from the stack because the ant returns from a dead end.
        brain.removeLast();
    }

    /**
     * Select the last trail that was used to reach
     * <p>
     * Correctness:
     * Trivially?
     * <p>
     * Runtime:
     * Linear in the number of outgoing trails in c
     *
     * @param c        clearing from where the trail starts from
     * @param targetId id of the clearing where the trail has to end in
     * @return index of the trail referring to the list of trails in c,
     * -1 if no matching trail is found, but this case should not occur
     */
    private int selectLastUsedTrail(Clearing c, int targetId) {
        List<Trail> trails = c.connectsTo();

        for (int i = 0; i < trails.size(); ++i) {
            //check if current trail is the incoming trail
            if (trails.get(i).to().id() == targetId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get random index from a list. The integer is received uniform at random from the interval [0, bound).
     *
     * @param bound upper bound for the random number
     * @return random integer in [0, bound)
     */
    private int getRandomIndex(int bound) {
        return new Random().nextInt(bound);
    }

    /**
     * Divide the outgoing trails of the current clearing into three types: First, the trails with valid pheromones and
     * second, the trails marked with NaP
     * <p>
     * Correctness:
     * This method helps to {@link Ant#selectWay()} with the divided ways and only provides those trails that have a
     * valid foodPheromone or a NaP-Pheromone. With this, it only provides trails to {@link Ant#selectWay()} that are
     * valid in the specification of kF (a) §3
     * <p>
     * Runtime:
     * Linear in the trailList and gets a lock for each trail to access the food pheromone
     *
     * @param trailList : list of Trails in the current clearing
     * @return a List with 2 elements: First : A pair of indices and their trails with pheromones (not MaP, not NaP),
     *                                 Second: A pair of indices and NaP trails
     */
    private List<Pair> divideIntoTrailTypes(List<Trail> trailList) {
        List<Integer> minPheromoneTrailIndices = new ArrayList<>(),
                NaPTrailIndices = new ArrayList<>();
        List<Trail> minPheromoneTrails = new ArrayList<>(),
                NaPTrails = new ArrayList<>();
        List<Pair> output = new ArrayList<>();

        Pheromone currentPheromone;
        Trail currentTrail;
        int minPheromone = Integer.MAX_VALUE;

        // Traverse the complete trail list and put each element in the category MaP, NaP or normal Pheromone

        for (int index = 0; index < trailList.size(); index++) {
            currentTrail = trailList.get(index);

            currentPheromone = currentTrail.food();

            // check if current trail is the incoming trail
            if ((position.compareTo(world.anthill()) != 0 && currentTrail.to().id() == brain.lastId()) ||
                    !this.brain.viable(index) || currentPheromone.isInfinite()) {
                continue;
            }

            if (currentPheromone.isAPheromone()) {
                int pValue = currentPheromone.value();
                if (pValue < minPheromone) {
                    minPheromoneTrails.clear();
                    minPheromoneTrailIndices.clear();
                    minPheromoneTrails.add(currentTrail);
                    minPheromoneTrailIndices.add(index);
                    minPheromone = pValue;
                } else if (pValue == minPheromone) {
                    minPheromoneTrails.add(currentTrail);
                    minPheromoneTrailIndices.add(index);
                }
            } else {
                NaPTrails.add(currentTrail);
                NaPTrailIndices.add(index);
            }
        }

        // put all lists in a list
        output.add(new Pair(minPheromoneTrailIndices, minPheromoneTrails));
        output.add(new Pair(NaPTrailIndices, NaPTrails));
        return output;
    }

    /**
     * Select the next trail on the way home with food. This rule is implemented according to kR (a) S2
     * <p>
     * Correctness:
     * Similar to selectWay and getLowestPheromones correct and follows kR (a) S2 in the specification
     * <p>
     * Runtime:
     * Linear in the number of trails and selects the anthill pheromone once per connected trail
     *
     * @return Trail-instance to take on the way home
     * @implSpec The ant calling this are non-adventurous ants carrying food home and want to know the next trail to
     * come home following the lowest anthill pheromones
     */
    private Trail selectWayHome() {
        List<Trail> candidates = new ArrayList<>();
        int minPheromone = Integer.MAX_VALUE;

        for (Trail t : this.position.connectsTo()) {
            // If the anthill Pheromone is invalid, don't consider this way
            Pheromone trailAnthillPheromone = t.anthill();
            if (!trailAnthillPheromone.isAPheromone()) {
                continue;
            }

            int pValue = trailAnthillPheromone.value();

            // Check if the pheromone is not NaP
            if (pValue < 0) {
                continue;
            }

            // Save as co-lowest pheromone or replace the previously lowest
            if (pValue < minPheromone) {
                candidates.clear();
                candidates.add(t);
                minPheromone = pValue;
            } else if (pValue == minPheromone) {
                candidates.add(t);
            }
        }

        // select one of the lowest-pheromone-trails uniform at random
        Trail t = candidates.get(getRandomIndex(candidates.size()));
        this.recorder.select(this, t, candidates, Recorder.SelectionReason.RETURN_FOOD);
        return t;
    }

    /**
     * Ant traverses a trail to arrive in new Clearing
     * <p>
     * Correctness:
     * If the locking is correct. The pheromone updates are implemented internally and based on kF (g) and kR (b).
     * <p>
     * Runtime:
     * Constant, but we take the locks of the current trail and both connected clearings. We also wait on the trail for
     * free space on the target clearing which may take a while
     *
     * @param t                Trail to go in this step
     * @param updatePheromones flag indicating that this method is called after circle detection and the ant has no
     *                         directly returns to its last clearing without updating any pheromones (case d)
     * @implSpec The ant just traverses the provided trail. There is no information whether the ant is adventurous or
     * <p>
     * https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/locks/Condition.html#await(long,%20java.util.concurrent.TimeUnit)
     * not
     */
    private void traverse(Trail t, boolean updatePheromones) {
        Clearing destination = t.to();
        // Ant try to get the trail lock
        t.traverse_Lock.lock();
        try {
            // Go from the current clearing to the trail
            t.enter();
            this.recorder.enter(this, t);
            this.recorder.leave(this, t.from());
            t.from().leave();

            // Wait until on the destination clearing a space is free and hope to not get eaten
            destination.lock.lock();
            while (!destination.isSpaceLeft()) {
                if (!destination.spaceLeft.await(this.disguise(), TimeUnit.MILLISECONDS)) {
                    // if the ant got eaten, notify the recorder and leave the trail
                    this.recorder.attractAttention(this);
                    this.recorder.leave(this, t);
                    this.eaten = true;
                    return;
                }
            }

            // enter the destination clearing
            destination.enter();
            this.recorder.enter(this, t.to());
            this.recorder.leave(this, t);
            t.leave();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // finally, give back all locks
            destination.lock.unlock();
            t.traverse_Lock.unlock();
        }

        // and update the pheromones according to kF (g) and kR (b)
        if (updatePheromones) {
            if (this.goHome) {
                t.reverse().updateFood(Pheromone.get(this.stepsSinceFood), this.adventurous);
                this.recorder.updateFood(this, t.reverse(), t.reverse().food());
            } else {
                t.reverse().updateAnthill(Pheromone.get(this.stepsSinceHill));
                this.recorder.updateAnthill(this, t.reverse(), t.reverse().anthill());
            }
        }
    }

    /**
     * Pick up food from current clearing. This implements kF (h)
     * <p>
     * Correctness:
     * Implementation of kF (h)
     * <p>
     * Runtime:
     * Constant, takes one lock of the clearing
     */
    private void takeFood() {
        // get the food and check in the same round if food is left in the clearing. Locking is done in getAndCheckFood
        updateFoodHome = position.getAndCheckFood();

        // Internally update some statistics to go home now and eventually don't update the pheromone if it's the last
        // food that the ant returns
        goHome = true;

        // Notify the recorder about the new heading of the ant
        this.recorder.pickupFood(this, position);
        this.recorder.startFoodReturn(this);
    }
}
