package hk.ust.comp3021.replay;


import hk.ust.comp3021.actions.ActionResult;
import hk.ust.comp3021.actions.Exit;
import hk.ust.comp3021.game.AbstractSokobanGame;
import hk.ust.comp3021.game.GameState;
import hk.ust.comp3021.game.InputEngine;
import hk.ust.comp3021.game.RenderingEngine;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.IntStream;

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

        // Added code: initialized array
        this.hasInputEnginesFinished = new boolean[this.inputEngines.size()];
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

    // Helper Enum to represent which type of engine is currently running
    private enum Engine {
        INPUT, RENDERING
    }

    // Concurrency information

    // Index used to select input engine in ROUND_ROBIN mode
    private int inputEngineIndex = 0;
    // List of whether each input engine has finished
    private final boolean[] hasInputEnginesFinished;
    // Current working engine
    private Engine currentEngine = Engine.RENDERING;

    /**
     * @return True when the game should stop running.
     * When all input engines specified to exit the game or the game is won.
     */
    @Override
    protected boolean shouldStop() {
        // Stopping criteria should include all input engines as each input engine should have its own isExitSpecified
        return IntStream.range(0, hasInputEnginesFinished.length).allMatch(i -> hasInputEnginesFinished[i]) || state.isWin();
    }

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

            // Helper lambda function: handles concurrency after processing Actions
            Runnable finishActionProcessingHandler = () -> {
                // TODO: Pass control to other engines
                // TODO: Implement ROUND_ROBIN and FREE_RACE modes
                // If Exit, give control to next input engine instead of rendering engine
                inputEngineIndex = (inputEngineIndex + 1) % inputEngines.size();
                state.notifyAll();
                if (Mode.ROUND_ROBIN.equals(mode)) {
                    try {
                        state.wait();
                    } catch (InterruptedException e) {
                        System.out.printf("InterruptedException caught in input engine Thread %d%n", this.index);
                        throw new RuntimeException(e);
                    }
                }
            };

            // Game loop
            while (true) {
                synchronized (state) {
                    // Wrap entire loop content in try-catch to handle possible Threading Exceptions
                    try {
                        // Await own turn to run
                        // No other engines are allowed to execute concurrently
                        while (!Engine.INPUT.equals(currentEngine) || this.index != inputEngineIndex) {
                            state.wait();
                        }

                        // If input engine has not received Exit object
                        if (!hasInputEnginesFinished[this.index]) {
                            // Fetch and process Action from this player
                            final var action = inputEngine.fetchAction();
                            if (action instanceof Exit) {
                                // Should not continue to fetch actions after first Exit of player
                                hasInputEnginesFinished[this.index] = true;
                            }
                            final var result = processAction(action);
                            // FIXME: DEBUG use, to be deleted
                            if (!(action instanceof Exit)) {
                                System.out.printf("Action from player %d processed%n", this.index);
                            }
                            if (result instanceof ActionResult.Failed failed) {
                                renderingEngine.message(failed.getReason());
                            }
                        }

                        // Check if the game should stop to terminate game loop
                        if (shouldStop()) {
                            finishActionProcessingHandler.run();
                            break;
                        }

                    } catch (InterruptedException e) {
                        System.out.printf("InterruptedException caught in input engine Thread %d%n", this.index);
                        throw new RuntimeException(e);
                    } finally {
                        finishActionProcessingHandler.run();
                    }
                }
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

            // Helper lambda function: render map and undo quota
            Runnable renderMapAndUndo = () -> {
                // Disallow concurrent updates to game state during rendering
                synchronized (state) {
                    final var undoQuotaMessage = state.getUndoQuota()
                            .map(it -> String.format(UNDO_QUOTA_TEMPLATE, it))
                            .orElse(UNDO_QUOTA_UNLIMITED);
                    renderingEngine.message(undoQuotaMessage);
                    renderingEngine.render(state);

                    state.notifyAll();
                }
            };

            // Render game start
            renderingEngine.message(GAME_READY_MESSAGE);
            // Render initial game map
            renderMapAndUndo.run();

            // Compute number of milliseconds to pass to Thread.sleep
            final long sleepTime = 1000 / frameRate;

            // Allow input engines to start processing Actions from players
            currentEngine = Engine.INPUT;

            // Game loop
            do {
                // Wrap entire loop content in try-catch to handle possible Threading Exceptions
                try {
                    // Process frameRate
                    // FIXME: still fails small number of FPS test repetitions
                    //  Test case uses
                    //    final var timeElapsed = renderTimes.get(renderTimes.size() - 1).getTime() - renderTimes.get(0).getTime();
                    //    final var expected = (float) timeElapsed / 1000 * fps;
                    Thread.sleep(sleepTime);

                    // Check if game should stop before rendering current game state
                    // Otherwise terminate game loop
                    if (shouldStop()) {
                        currentEngine = Engine.INPUT;
                        break;
                    }

                    currentEngine = Engine.RENDERING;

                    // Perform rendering
                    renderMapAndUndo.run();

                } catch (InterruptedException e) {
                    System.out.println("InterruptedException caught in rendering engine Thread.");
                    throw new RuntimeException(e);
                } finally {
                    currentEngine = Engine.INPUT;
                }
            } while (true);

            // Render final game state
            renderMapAndUndo.run();
            // Render game exit
            renderingEngine.message(GAME_EXIT_MESSAGE);
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
        // Rendering engine Thread
        final Thread renderingEngineThread = new Thread(new RenderingEngineRunnable());
        renderingEngineThread.setPriority(Thread.MAX_PRIORITY);

        // Spawn new threads for each input engine
        // Array of input engine Threads
        final Thread[] inputEnginesThreads = new Thread[inputEngines.size()];
        for (int i = 0; i < this.inputEngines.size(); ++i) {
            final InputEngineRunnable inputEngineRunnable = new InputEngineRunnable(i, inputEngines.get(i));
            final Thread inputEngineThread = new Thread(inputEngineRunnable);
            inputEnginesThreads[i] = inputEngineThread;
        }

        // Start threads
        renderingEngineThread.start();
        for (final Thread engineThread: inputEnginesThreads) {
            engineThread.start();
        }

        // Wait for all threads to finish before return
        try {
            for (final Thread engineThread: inputEnginesThreads) {
                engineThread.join();
            }
            renderingEngineThread.join();
        } catch (InterruptedException e) {
            System.out.println("Thread Interrupted Exception: " + e);
            throw new RuntimeException(e);
        }
    }

}
