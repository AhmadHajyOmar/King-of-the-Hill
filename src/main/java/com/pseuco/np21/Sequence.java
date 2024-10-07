package com.pseuco.np21;

import java.util.HashSet;
import java.util.Stack;

public class Sequence {
    /**
     * This class implements the brain of ants. It consists of a stack of Pairs. Each pair represents a clearing and its
     * outgoing trails {@link Pair}.
     * <p>
     * As structure for the brain we use a stack because it naturally fits the specification of the description in its
     * character that new clearings are put on top of the last and is removed when returning from a dead end.
     */

    private static class Pair {
        /**
         * This pair-class represents the entities in the "brain" of the ants. Each pair stores the id of the clearing
         * and an array of its outgoing edges. This bool-array is used to mark edges that were already used by the ant.
         * We can use an array without mixing the paths and its viability because the list of outgoing edges is not
         * modified after creating the map. This is guaranteed by the code that we may not modify and the protected
         * field {@link com.pseuco.np21.shared.Clearing# trails}.
         * <p>
         * Viable/Viability in this case means that an ant may traverse a trail from the clearing according to the rules
         * of the specification.
         */

        private final int id;
        private final boolean[] options;

        /**
         * Instantiate a pair holding the id of the clearing and a boolean array of its outgoing edges
         *
         * @param id      id of the clearing
         * @param size    number of outgoing trails from that clearing
         * @param blocked indices of the paths that are known to be blocked. This array can also be empty.
         */
        public Pair(int id, int size, int... blocked) {
            this.id = id;
            this.options = new boolean[size];

            // Initialize all paths as viable ...
            for (int i = 0; i < size; ++i) {
                this.options[i] = true;
            }

            // ... and exclude those that are said to be not viable
            for (int b : blocked) {
                this.options[b] = false;
            }
        }

        /**
         * get the id of the clearing in this entity in the ant-brain.
         *
         * @return id of the clearing associated with this pair.
         */
        public int getId() {
            return this.id;
        }

        /**
         * check if a path in the represented clearing is viable
         *
         * @param path index of the path to check for this clearing
         * @return true if the path is viable
         */
        public boolean viable(int path) {
            return this.options[path];
        }

        /**
         * Add a blockade on the associated clearing, this may result from coming back from a dead end
         *
         * @param path index that is newly blocked
         */
        public void addBlock(int path) {
            this.options[path] = false;
        }

        /**
         * Get all flags for the paths going out from this clearing
         *
         * @return boolean-array according to the
         */
        public boolean[] getOptions() {
            return this.options;
        }
    }


    private final Stack<Pair> seq;
    private final HashSet<Integer> seqHash;
    private final Stack<Trail> trailChain;
    /**
     * Initialize the sequence, i.e. the brain of the ant.
     */
    public Sequence() {
        seq = new Stack<>();
        trailChain = new Stack<>();
        seqHash = new HashSet<>();
    }

    /**
     * Add a new station on the route of the ant to its brain
     *
     * @param id      id of the newly visited clearing
     * @param size    number of outgoing trails from this new clearing
     * @param blocked indices of the initially blocked trails from this edge
     */
    public void addClearing(int id, int size, int... blocked) {
        this.seq.push(new Pair(id, size, blocked));
        this.seqHash.add(id);
    }

    /**
     * Add a new trail to the stack
     * @param trail trail to be added
     */
    public void addTrail(Trail trail){
        this.trailChain.push(trail);
    }

    /**
     * get last used trail in the stack
     * @return last used trail
     */
    public Trail lastTrail(){
        return this.trailChain.peek();
    }

    /**
     * Get information on all trails that leave the last clearing
     *
     * @return boolean array encoding to which clearing the ant may traverse.
     */
    public boolean[] getOptions() {
        return this.seq.peek().getOptions();
    }

    /**
     * Get the id of the (actually) second last clearing. This is needed in case the ant has to return from a dead end
     * and wants to remember to which clearing to go. And also when an adventurous ant brings food home to the ant hill.
     *
     * @return id of clearing to return to
     */
    public int lastId() {
        return this.seq.get(this.seq.size() - 2).getId();
    }

    public boolean full() {
        int trues = 0;
        for (boolean b : this.seq.peek().options) {
            if (b) {
                trues += 1;
            }
        }

        // anthill cannot be full in this way
        return (trues == 1 && this.length() > 1) || (trues == 0 && this.length() == 1);
    }

    /**
     * Remove the last element from the stack if the ant returns from a dead end.
     */
    public void removeLast() {
        this.seqHash.remove(this.seq.peek().getId());
        this.trailChain.pop();
        this.seq.pop();
    }

    /**
     * Clear the brain. This is useful if the ant returns to the anthill.
     */
    public void clear() {
        this.seq.clear();
        this.trailChain.clear();
        this.seqHash.clear();
    }

    /**
     * Check if the clearing with the given id is already part of the current route to prevent circles.
     *
     * @param id ID of the clearing to check
     * @return true if the clearing is already part of the current traverse.
     */
    /*public boolean alreadySeen(int id) {
        for (Pair pair : this.seq) {
            if (pair.getId() == id) {
                return true;
            }
        }
        return false;
    }*/

    public boolean alreadySeen(int id) {
       return seqHash.contains(id);
    }

    /**
     * Add a new blocked-flag to the index path of the latest brain-entity.
     *
     * @param path index of the path that is newly blocked.
     */
    public void addBlock(int path) {
        this.seq.peek().addBlock(path);
    }

    public void addBlock(int id, int path) {
        for (Pair pair : this.seq) {
            if (pair.getId() == id) {
                pair.addBlock(path);
            }
        }
    }

    /**
     * Check if the queried path is viable in the latest brain-entity.
     *
     * @param path index of the path to be checked
     * @return true if the path can be traversed
     */
    public boolean viable(int path) {
        return this.seq.peek().viable(path);
    }

    /**
     * Return the length of the current traversal
     *
     * @return length of the internal sequence of clearings
     */
    public int length() {
        return this.seq.size();
    }
}
