package com.pseuco.np21;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Representation of a clearing.
 * <p>
 * Manages ant and food levels on it.
 * <p>
 * You may change the code however you see fit.
 */
public class Clearing extends com.pseuco.np21.shared.Clearing<Clearing, Trail> {
    private int ants;
    private int food;

    public Lock lock;
    Condition spaceLeft;

    /**
     * Constructs a new clearing.
     *
     * @param name     of the clearing
     * @param food     the clearing starts with
     * @param capacity the clearing has access to
     */
    public Clearing(final String name, final int food, final Capacity capacity) {
        super(name, food, capacity);
        this.food = initialFood;

        this.lock = new ReentrantLock();
        this.spaceLeft = lock.newCondition();
    }

    /**
     * Check whether there is still space left on this clearing.
     *
     * @return {@code true} iff there is space left
     */
    public boolean isSpaceLeft() {
        lock.lock();
        try {
            return capacity.isInfinite() || ants < capacity.value();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Call this when an ant enters this clearing.
     */
    public void enter() {
        lock.lock();
        try {
            ants++;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Call this when an ant leaves this clearing.
     */
    public void leave() {
        lock.lock();
        try {
            ants--;

            // Signal the waiting ants that a place is free on the current clearing
            spaceLeft.signal();

        } catch (Exception ire) {
            ire.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check whether this clearing has food left.
     *
     * @return {@code true} iff there is food left
     */
    public boolean hasFood() {
        lock.lock();
        try {
            return food > 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Call this when an ant picks up food at this clearing.
     */
    public void pickupFood() {
        lock.lock();
        try {
            food--;
        } finally {
            lock.unlock();
        }
    }

    public boolean getAndCheckFood() {
        lock.lock();
        try {
            return --food > 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Call this when an ant places food at this clearing.
     */
    public void placeFood() {
        lock.lock();
        try {
            food++;
        } finally {
            lock.unlock();
        }
    }
}
