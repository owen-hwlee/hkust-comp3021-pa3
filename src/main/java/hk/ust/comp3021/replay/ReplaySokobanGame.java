package hk.ust.comp3021.replay;


import hk.ust.comp3021.actions.ActionResult;
import hk.ust.comp3021.actions.Exit;
import hk.ust.comp3021.game.AbstractSokobanGame;
import hk.ust.comp3021.game.GameState;
import hk.ust.comp3021.game.InputEngine;
import hk.ust.comp3021.game.RenderingEngine;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static hk.ust.comp3021.utils.StringResources.*;

/**
 * A thread-safe Sokoban game.
 * The game should be able to run in a separate thread, and games running in parallel should not interfere with each other.
 * <p>
 * The game can run in two modes:
 * 1. {@link Mode#ROUND_ROBIN} mode: all input engines take turns to perform actions, starting from the first specified input engine.
 * Example: suppose there are two input engines, A and B, whose actions are [R, L], [R, L], respectively.
 * In this mode, the game will perform the following actions in order: A.R, B.R, A.L, B.L.
 * 2. {@link Mode#FREE_RACE} mode: all input engines perform actions simultaneously. The actions processed can be in any order.
 * There could be a chance that two runs of the same game process actions in different orders.
 * <p>
 * {@link hk.ust.comp3021.Sokoban#replayGame(int, String, Mode, int, String[])} runs multiple games in parallel.
 */
public class ReplaySokobanGame extends AbstractSokobanGame {
    /**
     * Mode of scheduling actions among input engines.
     */
    public enum Mode {
        /**
         * All input engines take turns to perform actions, starting from the first specified input engine.
         */
        ROUND_ROBIN,

        /**
         * All input engines perform actions concurrently without enforcing the order.
         */
        FREE_RACE,
    }

    protected final Mode mode;
    /**
     * Indicated the frame rate of the rendering engine (in FPS).
     */
    protected final int frameRate;

    /**
     * Default frame rate.
     */
    protected static final int DEFAULT_FRAME_RATE = 60;

    /**
     * The list of input engines to fetch inputs.
     */
    protected final List<? extends InputEngine> inputEngines;

    /**
     * The rendering engine to render the game status.
     */
    protected final RenderingEngine renderingEngine;

    /**
     * Create a new instance of ReplaySokobanGame.
     * Each input engine corresponds to an action file and will produce actions from the action file.
     *
     * @param mode            The mode of the game.
     * @param frameRate       Rendering fps.
     * @param gameState       The game state.
     * @param inputEngines    the input engines.
     * @param renderingEngine the rendering engine.
     * @throws IllegalArgumentException when there are more than two players in the map.
     */
    public ReplaySokobanGame(
            @NotNull Mode mode,
            int frameRate,
            @NotNull GameState gameState,
            @NotNull List<? extends InputEngine> inputEngines,
            @NotNull RenderingEngine renderingEngine
    ) {
        super(gameState);
        if (inputEngines.size() == 0)
            throw new IllegalArgumentException("No input engine specified");
        this.mode = mode;
        this.frameRate = frameRate;
        this.renderingEngine = renderingEngine;
        this.inputEngines = inputEngines;

        // Added code: initialized added locks and conditions
        this.lock = new ReentrantLock();
        this.renderingEngineCondition = lock.newCondition();
        this.inputEnginesConditions = new Condition[inputEngines.size()];
        for (int i = 0; i < inputEnginesConditions.length; i++) {
            final Condition inputEngineCondition = lock.newCondition();
            inputEnginesConditions[i] = inputEngineCondition;
        }
    }

    /**
     * @param gameState       The game state.
     * @param inputEngines    the input engines.
     * @param renderingEngine the rendering engine.
     */
    public ReplaySokobanGame(
            @NotNull GameState gameState,
            @NotNull List<? extends InputEngine> inputEngines,
            @NotNull RenderingEngine renderingEngine) {
        this(Mode.FREE_RACE, DEFAULT_FRAME_RATE, gameState, inputEngines, renderingEngine);
    }

    // TODO: add any method or field you need.
    // Concurrency information

    // Index used to select input engine in ROUND_ROBIN mode
    private volatile int inputEngineIndex = 0;
    // Rendering engine Thread
    private Thread renderingEngineThread;
    // Array of input engine Threads
    private Thread[] inputEnginesThreads;
    // Concurrency lock
    private final ReentrantLock lock;
    // Rendering engine condition
    private final Condition renderingEngineCondition;
    // Array of input engine conditions
    private final Condition[] inputEnginesConditions;

    /**
     * The implementation of the Runnable for each input engine thread.
     * Each input engine should run in a separate thread.
     * <p>
     * Assumption:
     * 1. the last action fetch-able from the input engine is always an {@link Exit} action.
     * <p>
     * Requirements:
     * 1. All actions fetched from input engine should be processed in the order they are fetched.
     * 2. All actions before (including) the first {@link Exit} action should be processed
     * (passed to {@link this#processAction} method).
     * 3. Any actions after the first {@link Exit} action should be ignored
     * (not passed to {@link this#processAction}).
     */
    private class InputEngineRunnable implements Runnable {
        private final int index;
        private final InputEngine inputEngine;

        private InputEngineRunnable(int index, @NotNull InputEngine inputEngine) {
            this.index = index;
            this.inputEngine = inputEngine;
        }

        @Override
        public void run() {
            // TODO: modify this method to implement the requirements.

            // Store whether an Exit object has been received
            boolean hasExited = false;

//            // Force input engine Thread to wait for render engine to render first
//            try {
//                inputEnginesConditions[this.index].await();
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }

//            // TODO: Wrap everything in while-loop
            while (!shouldStop()) {
//
//                // Create critical region using ReentrantLock
//                lock.lock();
//
//                // Await own turn to run
//                while (this.index != inputEngineIndex) {
//                    try {
//                        inputEnginesConditions[this.index].await();
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
//                }
//
//                // Make sure no other input engines are allowed to execute concurrently
//                for (int i = 0; i < inputEnginesConditions.length; i++) {
//                    if (i != this.index) {
//                        try {
//                            inputEnginesConditions[i].await();
//                        } catch (InterruptedException e) {
//                            throw new RuntimeException(e);
//                        }
//                    }
//                }

                // Fetch and process Action from this player
                if (!hasExited) {
                    final var action = inputEngine.fetchAction();
                    if (action instanceof Exit) {
                        hasExited = true;
                    }
                    final var result = processAction(action);
                    if (result instanceof ActionResult.Failed failed) {
                        renderingEngine.message(failed.getReason());
                    }
                }

//                // Signal rendering engine after input Action is fetched
//                renderingEngineCondition.signal();
//
//                // Release ReentrantLock
//                // FIXME: put in finally clause
//                lock.unlock();
            }
        }
    }

    /**
     * The implementation of the Runnable for the rendering engine thread.
     * The rendering engine should run in a separate thread.
     * <p>
     * Requirements:
     * 1. The game map should be rendered at least once before any action is processed (the initial state should be rendered).
     * 2. The game map should be rendered after the last action is processed (the final state should be rendered).
     */
    private class RenderingEngineRunnable implements Runnable {
        /**
         * NOTE: You are NOT allowed to use {@link java.util.Timer} or {@link java.util.TimerTask} in this method.
         * Please use a loop with {@link Thread#sleep(long)} instead.
         */
        @Override
        public void run() {
            // TODO: modify this method to implement the requirements.

            // Compute number of milliseconds to pass to Thread.sleep
            final long sleepTime = 1000 / frameRate;

            // TODO: Wrap everything in while-loop
            do {
//                // Create critical region using ReentrantLock
//                lock.lock();
//
//                // Await own turn to run
//                // TODO: Determine while loop run condition
//                while (frameRate > 0) {
//                    try {
//                        renderingEngineCondition.await();
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
//                }

                // Perform rendering
                final var undoQuotaMessage = state.getUndoQuota()
                    .map(it -> String.format(UNDO_QUOTA_TEMPLATE, it))
                    .orElse(UNDO_QUOTA_UNLIMITED);
                renderingEngine.message(undoQuotaMessage);
                renderingEngine.render(state);

                // Process frameRate
                // FIXME: still fails small number of FPS test repetitions
                //  Actual value smaller than expected
                //  Test case uses
                //          final var timeElapsed = renderTimes.get(renderTimes.size() - 1).getTime() - renderTimes.get(0).getTime();
                //          final var expected = (float) timeElapsed / 1000 * fps;
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

//                // Signal input engine(s)
//                // Allow each inputEngine execution by Mode (ROUND_ROBIN vs FREE_RACE)
//                if (Mode.ROUND_ROBIN.equals(mode)) {
//                    // ROUND_ROBIN: allow next input engine to run
//                    inputEngineIndex = (inputEngineIndex + 1) % inputEngines.size();
//                    inputEnginesConditions[inputEngineIndex].signal();
//                } else {
//                    // FREE_RACE: allow all input engines to run
//                    for (final Condition inputEngineCondition: inputEnginesConditions) {
//                        inputEngineCondition.signal();
//                    }
//                }
//
//                // Release ReentrantLock
//                // FIXME: put in finally clause
//                lock.unlock();
            } while (!shouldStop());

            // Render win message
            if (state.isWin()) {
                renderingEngine.message(WIN_MESSAGE);
            }
        }
    }

    /**
     * Start the game.
     * This method should spawn new threads for each input engine and the rendering engine.
     * This method should wait for all threads to finish before return.
     */
    @Override
    public void run() {
        // DONE

        // Spawn new thread for rendering engine
        this.renderingEngineThread = new Thread(new RenderingEngineRunnable());

        // Spawn new threads for each input engine
        this.inputEnginesThreads = new Thread[inputEngines.size()];
        for (int i = 0; i < this.inputEngines.size(); ++i) {
            final InputEngineRunnable inputEngineRunnable = new InputEngineRunnable(i, inputEngines.get(i));
            final Thread inputEngineThread = new Thread(inputEngineRunnable);
            this.inputEnginesThreads[i] = inputEngineThread;
        }

        // Start threads
        renderingEngineThread.start();
        for (final Thread engineThread: this.inputEnginesThreads) {
            engineThread.start();
        }

        // Wait for all threads to finish before return
        try {
            for (final Thread engineThread: this.inputEnginesThreads) {
                engineThread.join();
            }
            renderingEngineThread.join();
        } catch (InterruptedException e) {
            System.out.println("Thread Interrupted Exception: " + e);
            throw new RuntimeException(e);
        }
    }

}
