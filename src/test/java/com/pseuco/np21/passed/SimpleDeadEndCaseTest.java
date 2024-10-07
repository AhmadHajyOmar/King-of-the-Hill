package com.pseuco.np21.passed;

import com.pseuco.np21.Clearing;
import com.pseuco.np21.Factory;
import com.pseuco.np21.Simulator;
import com.pseuco.np21.Trail;
import com.pseuco.np21.shared.Ant;
import com.pseuco.np21.shared.Recorder;
import com.pseuco.np21.shared.Trail.Pheromone;
import com.pseuco.np21.shared.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

public class SimpleDeadEndCaseTest {

    private World<Clearing, Trail> world;
    private Clearing anthill, C1, C2, C3, C4;
    private Trail trail1, trail2, trail3, trailReverse1, trailReverse2, trailReverse3;
    private Ant ant;

    private Simulator simulator;
    private Recorder recorder;

    public void setFoodPheromone(Trail t, com.pseuco.np21.shared.Trail.Pheromone value) {
        try {
            var food = t.getClass().getDeclaredField("food");
            food.setAccessible(true);
            food.set(t, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @BeforeEach
    void setUp() {
        final var factory = new Factory();

        anthill = factory.createClearing("anthill");
        factory.setAnthill(anthill);
        C1 = factory.createClearing("C1", 1);
        C2 = factory.createClearing("C2", 0);
        C3 = factory.createClearing("C3", 0);
        C4 = factory.createClearing("C4", 0);
        trail1 = factory.createTrail(anthill, C1);
        trail2 = factory.createTrail(anthill, C2);
        trail3 = factory.createTrail(C2, C3);

        trailReverse1 = trail1.reverse();
        trailReverse2 = trail2.reverse();
        trailReverse3 = trail3.reverse();

        ant = factory.createAnt("Anthony", 0, 1000);

        world = factory.finishWorld("TestLine", -1);
        recorder = mock(Recorder.class);

        simulator = new Simulator(world, recorder);

        setFoodPheromone(trail1, Pheromone.get(1));
        setFoodPheromone(trail2, Pheromone.NOT_A_PHEROMONE);
        setFoodPheromone(trail3, Pheromone.INFINITE);
    }

    @Test
    @Timeout(value = 1)
    void Sackgasse() {
        simulator.run();

        final var inOrder = inOrder(recorder);

        inOrder.verify(recorder).start();

        inOrder.verify(recorder).spawn(eq(ant));
        inOrder.verify(recorder).enter(eq(ant), same(anthill));

        inOrder.verify(recorder).startFoodSearch(eq(ant));

        inOrder.verify(recorder).startExploration(eq(ant));
        inOrder.verify(recorder).select(eq(ant), same(trail2), eq(List.of(trail2)), same(Recorder.SelectionReason.EXPLORATION));
        inOrder.verify(recorder).enter(eq(ant), same(trail2));
        inOrder.verify(recorder).leave(eq(ant), same(anthill));
        inOrder.verify(recorder).enter(eq(ant), same(C2));
        inOrder.verify(recorder).leave(eq(ant), same(trail2));
        inOrder.verify(recorder).select(eq(ant), same(trailReverse2), any(), same(Recorder.SelectionReason.NO_FOOD_RETURN));



    }
}