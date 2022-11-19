package hk.ust.comp3021.replay;

import hk.ust.comp3021.actions.Action;
import hk.ust.comp3021.actions.Exit;
import hk.ust.comp3021.actions.Move;
import hk.ust.comp3021.game.GameState;
import hk.ust.comp3021.game.InputEngine;
import hk.ust.comp3021.game.RenderingEngine;
import hk.ust.comp3021.utils.TestKind;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


/**
 * Tests ReplaySokobanGame
 * Contains test cases provided by TAs as well as custom test cases
 */
class IntegratedReplaySokobanGameTest {

    /**
     * Number of repetitions set for unit tests that do not test thread safety operations
     */
    private static final int THREAD_SAFE_REPETITIONS = 20;
    /**
     * Number of repetitions set for unit tests that test thread safety operations
     */
    private static final int THREAD_UNSAFE_REPETITIONS = 100;

    /**
     * Contains test cases provided by TAs
     * Each test is set to repeat, unlike in ReplaySokobanGameTest, to ensure deterministic results
     */
    @Nested
    class GivenReplaySokobanGameTest {

        @DisplayName("Game's run method should spawn a new thread for rendering engine")
        @RepeatedTest(THREAD_SAFE_REPETITIONS)
        @Tag(TestKind.PUBLIC)
        void testRenderingEngineThread() {
            final var gameState = mock(GameState.class);
            final var inputEngine = mock(InputEngine.class);
            final var renderingEngine = mock(RenderingEngine.class);
            final var game = new TestGame(gameState, List.of(inputEngine), renderingEngine);

            final var renderThreadIds = new ConcurrentLinkedQueue<Long>();
            doAnswer(invocation -> {
                final var threadID = Thread.currentThread().getId();
                renderThreadIds.add(threadID);
                return null;
            }).when(renderingEngine).render(any());
            when(inputEngine.fetchAction())
                    .thenAnswer(new RandomlyPausedActionProducer(new Move.Right(0), new Exit()));

            game.run();

            assertTrue(renderThreadIds.size() > 0);
            final var renderThreadId = renderThreadIds.poll();
            while (!renderThreadIds.isEmpty()) {
                assertEquals(renderThreadId, renderThreadIds.poll());
            }
        }

        @DisplayName("Game's run method should spawn one thread for each input engine")
        @RepeatedTest(THREAD_SAFE_REPETITIONS)
        @Tag(TestKind.PUBLIC)
        void testInputEngineThread() {
            final var gameState = mock(GameState.class);
            final var inputEngine0 = mock(InputEngine.class);
            final var inputEngine1 = mock(InputEngine.class);
            final var inputEngine2 = mock(InputEngine.class);
            final var renderingEngine = mock(RenderingEngine.class);
            final var game = new TestGame(gameState, List.of(inputEngine0, inputEngine1, inputEngine2), renderingEngine);

            final var threadIds0 = new ConcurrentLinkedQueue<Long>();
            final var threadIds1 = new ConcurrentLinkedQueue<Long>();
            final var threadIds2 = new ConcurrentLinkedQueue<Long>();
            final var actionProducer0 = new RandomlyPausedActionProducer(new Move.Right(0), new Exit());
            final var actionProducer1 = new RandomlyPausedActionProducer(new Move.Right(1), new Exit());
            final var actionProducer2 = new RandomlyPausedActionProducer(new Move.Right(2), new Exit());
            when(inputEngine0.fetchAction()).thenAnswer(invocation -> {
                final var threadID = Thread.currentThread().getId();
                threadIds0.add(threadID);
                return actionProducer0.produce();
            });
            when(inputEngine1.fetchAction()).thenAnswer(invocation -> {
                final var threadID = Thread.currentThread().getId();
                threadIds1.add(threadID);
                return actionProducer1.produce();
            });
            when(inputEngine2.fetchAction()).thenAnswer(invocation -> {
                final var threadID = Thread.currentThread().getId();
                threadIds2.add(threadID);
                return actionProducer2.produce();
            });
            game.run();

            assertTrue(threadIds0.size() > 0);
            assertTrue(threadIds1.size() > 0);
            assertTrue(threadIds2.size() > 0);
            final var threadIds = new HashSet<Long>();
            threadIds.add(Thread.currentThread().getId());
            final var th0 = threadIds0.poll();
            while (!threadIds0.isEmpty()) {
                assertEquals(th0, threadIds0.poll());
            }
            threadIds.add(th0);
            final var th1 = threadIds1.poll();
            while (!threadIds1.isEmpty()) {
                assertEquals(th1, threadIds1.poll());
            }
            threadIds.add(th1);
            final var th2 = threadIds2.poll();
            while (!threadIds2.isEmpty()) {
                assertEquals(th2, threadIds2.poll());
            }
            threadIds.add(th2);
            assertEquals(4, threadIds.size());
        }

        @DisplayName("Moves from the same input engine should be processed in the same order (multiple input engine)")
        @RepeatedTest(THREAD_UNSAFE_REPETITIONS)
        @Tag(TestKind.PUBLIC)
        void testMovesOrderMultiple() {
            final var gameState = mock(GameState.class);
            final var inputEngine0 = mock(StreamInputEngine.class);
            final var inputEngine1 = mock(StreamInputEngine.class);
            final var renderingEngine = mock(RenderingEngine.class);
            final var game = spy(new TestGame(gameState, List.of(inputEngine0, inputEngine1), renderingEngine));

            final var actions0 = Arrays.<Action>asList(new Move.Left(0), new Move.Right(0), new Move.Right(0), new Move.Right(0), new Move.Down(0), new Move.Up(0));
            final var actions1 = Arrays.<Action>asList(new Move.Left(1), new Move.Right(1), new Move.Right(1), new Move.Right(1), new Move.Down(1), new Move.Up(1));
            when(inputEngine0.fetchAction()).thenAnswer(new RandomlyPausedActionProducer(actions0));
            when(inputEngine1.fetchAction()).thenAnswer(new RandomlyPausedActionProducer(actions1));
            final var processedActions = new ActionList();
            doAnswer(invocation -> {
                processedActions.add(invocation.getArgument(0));
                return invocation.callRealMethod();
            }).when(game).processAction(any());

            game.run();

            assertArrayEquals(actions0.toArray(), processedActions.stream().filter(action -> action.getInitiator() == 0).toArray());
            assertArrayEquals(actions1.toArray(), processedActions.stream().filter(action -> action.getInitiator() == 1).toArray());
        }

        @DisplayName("Action order should be enforced in ROUND_ROBIN mode (all input engines have same length of actions")
        @RepeatedTest(THREAD_UNSAFE_REPETITIONS)
        @Tag(TestKind.PUBLIC)
        void testRoundRobinModeEqualLength() {
            final var gameState = mock(GameState.class);
            final var inputEngine0 = mock(StreamInputEngine.class);
            final var inputEngine1 = mock(StreamInputEngine.class);
            final var inputEngine2 = mock(StreamInputEngine.class);
            final var renderingEngine = mock(RenderingEngine.class);
            final var inputEngines = List.of(inputEngine0, inputEngine1, inputEngine2);
            final var game = spy(new TestGame(ReplaySokobanGame.Mode.ROUND_ROBIN, gameState, inputEngines, renderingEngine));

            final var actions0 = Arrays.<Action>asList(new Move.Down(0), new Move.Right(0), new Move.Left(0), new Move.Up(0), new Move.Down(0));
            final var actions1 = Arrays.<Action>asList(new Move.Left(1), new Move.Right(1), new Move.Right(1), new Move.Up(1), new Move.Down(1));
            final var actions2 = Arrays.<Action>asList(new Move.Left(2), new Move.Right(2), new Move.Right(2), new Move.Up(2), new Move.Down(2));
            final var actionsLists = new List[]{actions0, actions1, actions2};
            final var processActions = new ActionList();
            when(inputEngine0.fetchAction()).thenAnswer(new RandomlyPausedActionProducer(actions0));
            when(inputEngine1.fetchAction()).thenAnswer(new RandomlyPausedActionProducer(actions1));
            when(inputEngine2.fetchAction()).thenAnswer(new RandomlyPausedActionProducer(actions2));
            doAnswer(invocation -> {
                final var action = invocation.getArgument(0, Action.class);
                processActions.add(action);
                return invocation.callRealMethod();
            }).when(game).processAction(any());

            game.run();

            int i = 0;
            while (i < actions0.size() && i < actions1.size()) {
                final var round = i % inputEngines.size();
                final var index = i / inputEngines.size();
                final var actionList = actionsLists[round];
                if (index < actionList.size()) {
                    assertEquals(actionList.get(index), processActions.get(i));
                }
                i++;
            }
        }

        @DisplayName("FPS parameter should specify the times render method is invoked per second")
        @RepeatedTest(THREAD_UNSAFE_REPETITIONS)
        @Timeout(5)
        @Tag(TestKind.PUBLIC)
        void testFPS() {
            final var fps = 50;
            final var gameState = mock(GameState.class);
            final var inputEngine = mock(InputEngine.class);
            final var renderingEngine = mock(RenderingEngine.class);
            final var game = new TestGame(ReplaySokobanGame.Mode.FREE_RACE, fps, gameState, List.of(inputEngine), renderingEngine);

            final var actions = Arrays.<Action>asList(
                    new Move.Down(0),
                    new Move.Right(0),
                    new Move.Right(0),
                    new Move.Left(0),
                    new Move.Up(0)
            );
            final var renderTimes = new ArrayList<Date>();
            when(inputEngine.fetchAction()).thenAnswer(new RandomlyPausedActionProducer(90, 110, actions));
            doAnswer(invocation -> {
                renderTimes.add(new Date());
                return null;
            }).when(renderingEngine).render(any());

            game.run();

            assertTrue(renderTimes.size() > 0);
            final var timeElapsed = renderTimes.get(renderTimes.size() - 1).getTime() - renderTimes.get(0).getTime();
            final var expected = (float) timeElapsed / 1000 * fps;
            assertEquals(expected, renderTimes.size(), (float) (expected * 0.1)); // 10% error tolerance
        }

    }


    /**
     * Contains custom test cases (updated alongside development)
     * Each test is set to repeat to ensure deterministic results
     */
    @Nested
    class CustomReplaySokobanGameTest {

        /**
         * Contains test cases to test behaviour of ReplaySokobanGame class
         */
        @Nested
        class ClassReplaySokobanGameTest {
            // Correctness

            // Game ends with either
            // - Winning condition is satisfied
            // - All actions before the first Exit in all action files have been processed
        }


        /**
         * Contains test cases to test ReplaySokobanGame::run
         */
        @Nested
        class RunTest {

            @DisplayName("Game's run method should wait for all threads to finish before return")
            @RepeatedTest(THREAD_SAFE_REPETITIONS)
            @Tag(TestKind.PUBLIC)
            void testMainThreadLastToTerminate() {
                // TODO: test main thread blocked until all engine threads finish
//                final var mainThread = Thread.currentThread();
//
//                final var gameState = mock(GameState.class);
//                final var inputEngine1 = mock(InputEngine.class);
//                final var inputEngine2 = mock(InputEngine.class);
//                final var renderingEngine = mock(RenderingEngine.class);
//                final var game = new TestGame(gameState, List.of(inputEngine1, inputEngine2), renderingEngine);
//
//                final var engineThreads = new ConcurrentSkipListSet<Thread>();
//
//                doAnswer(invocation -> {
//                    engineThreads.add(Thread.currentThread());
//                    assertTrue(Thread.currentThread().isAlive());
//                    assertTrue(mainThread.isAlive());
//                    assertEquals(Thread.State.WAITING, mainThread.getState());
//                    return null;
//                }).when(renderingEngine).render(any());
//                doAnswer(invocation -> {
//                    assertTrue(Thread.currentThread().isAlive());
//                    assertTrue(mainThread.isAlive());
//                    return null;
//                }).when(inputEngine1).fetchAction();
//                doAnswer(invocation -> {
//                    assertTrue(Thread.currentThread().isAlive());
//                    assertTrue(mainThread.isAlive());
//                    return null;
//                }).when(inputEngine2).fetchAction();
//                when(inputEngine1.fetchAction())
//                        .thenAnswer(new RandomlyPausedActionProducer(new Move.Right(0), new Exit()));
//                when(inputEngine2.fetchAction())
//                        .thenAnswer(new RandomlyPausedActionProducer(new Move.Up(0), new Exit()));
//
//                game.run();
//
////                assertTrue(mainThread.isAlive());
//
//                while (mainThread.isAlive()) ;
//
//                engineThreads.forEach(thread -> assertEquals(Thread.State.TERMINATED, thread.getState()));
//                assertEquals(Thread.State.TERMINATED, mainThread.getState());
            }

        }


        /**
         * Contains test cases to test ReplaySokobanGame::InputEngineRunnable
         */
        @Nested
        class InputEngineRunnableTest {
            // All actions before the first Exit should be processed

            // All actions after the first Exit should be ignored

            // All actions should be processed in the same order as they appear in the action file

            // Test round-robin

            // Test free race
        }


        /**
         * Contains test cases to test ReplaySokobanGame::RenderingEngineRunnable
         */
        @Nested
        class RenderingEngineRunnableTest {

            @DisplayName("Game must render initial state before first Action")
            @RepeatedTest(THREAD_SAFE_REPETITIONS)
            @Tag(TestKind.PUBLIC)
            void testRenderInitialStateBeforeFirstAction() {
                // TODO: Check if start game message is printed
                //  Then check if initial game map is rendered immediately after
            }

            @DisplayName("Game should render final winning state when game wins")
            @RepeatedTest(THREAD_UNSAFE_REPETITIONS)
            @Tag(TestKind.PUBLIC)
            void testRenderFinalWinningStateWhenGameWins() {
                // TODO: Provide a scenario where the game must be won
                //  Then check if end game message followed by win message is printed
            }

            @DisplayName("Game should render final game state after all Actions from all players are processed")
            @RepeatedTest(THREAD_UNSAFE_REPETITIONS)
            @Tag(TestKind.PUBLIC)
            void testRenderFinalStateAfterAllActionsProcessed() {
                // TODO: Provide a scenario where the game cannot be won
                //  Then check if end game message without win message is printed
            }

            @DisplayName("Game should not immediately render game map after Exit Action")
            @RepeatedTest(THREAD_UNSAFE_REPETITIONS)
            @Tag(TestKind.PUBLIC)
            void testGameDoesNotImmediatelyRenderMapAfterExitAction() {
                // TODO: Check when Exit Action is passed,
            }

            @DisplayName("Game should render n+1 times for a total of n non-Exit Actions")
            @RepeatedTest(THREAD_UNSAFE_REPETITIONS)
            @ParameterizedTest
            @Tag(TestKind.PUBLIC)
            void testRenderNPlus1TimesForTotalOfNActionsExcludingExit() {
                // TODO: Check number of invocations of .render(state)
                //  Render map once before fetchAction, then render once after each non-Exit Action, hence n+1 renders for n Actions
            }

        }
        
    }
}
