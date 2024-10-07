package com.pseuco.np21;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Representation of a trail.
 * <p>
 * Manages ant and pheromone levels on it.
 * <p>
 * You may change the code however you see fit.
 */
public class Trail extends com.pseuco.np21.shared.Trail<Clearing, Trail> {
    private Pheromone anthill;
    private Pheromone food;
    private int ants;
    public Lock traverse_Lock = new ReentrantLock();

    public ReentrantReadWriteLock food_Pheromone_Lock = new ReentrantReadWriteLock();
    public ReentrantReadWriteLock anthill_Pheromone_Lock = new ReentrantReadWriteLock();

    private Trail(final Trail reverse) {
        super(reverse);

        this.anthill = Pheromone.NOT_A_PHEROMONE;
        this.food = Pheromone.NOT_A_PHEROMONE;
        this.ants = 0;
    }

    /**
     * Constructs a new trail given the connected clearings.
     *
     * @param from clearing the trail leads away from
     * @param to   clearing the trail leads to
     */
    public Trail(final Clearing from, final Clearing to) {
        super(from, to, (reverse) -> new Trail((Trail) reverse));

        this.anthill = Pheromone.NOT_A_PHEROMONE;
        this.food = Pheromone.NOT_A_PHEROMONE;
        this.ants = 0;
    }

    /**
     * Get the anthill pheromone level.
     *
     * @return anthill pheromone level
     */
    public Pheromone anthill() {
        this.anthill_Pheromone_Lock.readLock().lock();
        try {
            return anthill;
        } finally {
            this.anthill_Pheromone_Lock.readLock().unlock();
        }
    }

    /**
     * Get the food pheromone level.
     *
     * @return food pheromone level
     */
    public Pheromone food() {
        this.food_Pheromone_Lock.readLock().lock();
        try {
            return food;
        } finally {
            this.food_Pheromone_Lock.readLock().unlock();
        }
    }

    /**
     * Update the anthill pheromone level.
     *
     * @param p the new pheromone level
     */
    public void updateAnthill(final Pheromone p) {
        this.anthill_Pheromone_Lock.writeLock().lock();
        try {
            if (!p.isInfinite() && anthill.isAPheromone() && PHEROMONE_COMPARATOR.compare(p, anthill) > 0) {
                return;
            }
            anthill = p;
        } finally {
            this.anthill_Pheromone_Lock.writeLock().unlock();
        }

    }

    /**
     * Update the food pheromone level.
     *
     * @param p        the new pheromone level
     * @param explorer {@code true} iff the ant is in exploration mode
     */
    public void updateFood(final Pheromone p, final boolean explorer) {
        this.food_Pheromone_Lock.writeLock().lock();
        try {
            if (explorer && !p.isInfinite() && food.isAPheromone() && PHEROMONE_COMPARATOR.compare(p, food) > 0)
                return;
            food = p;
        } finally {
            this.food_Pheromone_Lock.writeLock().unlock();
        }
    }

    /**
     * Check whether there is still space left on this trail.
     *
     * @return {@code true} iff there is space left
     */
    public boolean isSpaceLeft() {
        return capacity.isInfinite() || ants < capacity.value();
    }

    /**
     * Call this when an ant enters this trail.
     */
    public void enter() {
        ants++;
    }

    /**
     * Call this when an ant leaves this trail.
     */
    public void leave() {
        ants--;
    }
}