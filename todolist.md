# To-do List for COMP3021 PA3

This list documents the progress of implementation of PA3 `ReplaySokobanGame`, as well as keeps track of custom test cases in `IntegratedReplaySokobanGameTest`.

## Intro

The main content of PA3 is to support multi-threading.

- Only `hk.ust.comp3021.replay` package needs to be implemented
- PA3 uses automatic replay to demonstrate power of multi-threading
- Map and replay actions are stored in map files and action files

### How Replay works

The programme takes a map and a list of actions by different players, and runs replays of the same game in parallel.

Replay game command structure:

```sh
Usage: java -jar Sokoban.jar <repeat> <map_file> <mode> <fps> <action_file> [<action_file> ...]
```

Sample command:

```sh
java -jar Sokoban-PA3.jar 3 map02.map FREE_RACE 60 actions0.txt actions1.txt
```

Explanation:

- `repeat`: number of game instances
  - Each game instance runs in a separate Thread in parallel
  - All game instances replay the exact same game setting
- `map_file`: file path of selected map file
- `mode`: scheduling mode between `InputEngine` threads, can be `FREE_RACE` or `ROUND_ROBIN`
  - `FREE_RACE`: the `InputEngine` threads perform actions concurrently without any scheduling in the order
    - The final order of processed actions are arbitrary and may be different across different runs
  - `ROUND_ROBIN`: all `InputEngine` threads perform actions in a round-robin fashion (turn by turn)
- `fps`: frame per second of the `renderingEngine`, as in how many times `renderingEngine.render()` should be invoked per second
- `action_file`: list of actions of ONE player
  - Can have multiple action files, one for each player
  - First line of action file is player ID
  - Then, each line represents an action of the player

## Multi-threading in this PA

In this PA, there are 2 layers of multithreading.

### Outer layer: Repeat Game

Note: this has been implemented by TAs.

This PA expects concurrent execution of `<REPEAT>` number of the exact same game.

From the main Thread, `REPEAT` number of game Threads are started. There is a corresponding `GameState` for each game Thread. The `Action`s and `GameState` in each game Thread should not interfere with each other. Each game Thread contains an instance of `ReplaySokobanGame`.

There is no scheduling among rendering/input engine Threads across different `ReplaySokobanGame` instances. (specified by TAs in Discussion #195)

### Game layer: In-game Engine Threads

Within each game (`ReplaySokobanGame`), the game Thread should create:

- `1` Thread for the rendering engine (`RenderingEngineRunnable`)
- `n` Threads for `n` input engines (`InputEngineRunnable`).

The game Thread should then wait for all these engine Threads to terminate.

The engine Threads pertaining to one `GameState` instance should not interfere with other `GameState` instances.

First the game's initial state is rendered. The rendering engine then proceeds to waits until the next FPS frame while handing over control to the input engines. The input engines starts to process Actions. The input engines either process Actions from different players concurrently (`FREE_MODE`) or in a scheduled manner (`ROUND_ROBIN`).

When the rendering engine is due to render, it will first block the input engines from processing Actions (hence not allowing game state to be updated simultaneously during each render). Then, it checks if the game should stop. If the game should not stop, the rendering engine will render the current undo quota and game map. If the game should stop, the rendering engine will render the final state and end the game. The rendering engine then unblocks the input engines to continue processing Actions while waiting for the next render.

There is no need, however, to enforce an ordering of [one processed non-Exit Action -> one render -> ... (repeat)].

An example edge case: suppose the computer is fast and the lists of Actions are short. The rendering engine should render the game initial state before any Actions should be processed. These Actions are processed extremely fast, and hence by the time the rendering engine is allowed to render again (due to FPS requirement), all Actions have been processed. The rendering engine should acknowledge that the game should stop, and only renders the final state of the game. As a result, in this scenario, the game state is only rendered a total of 2 times: the initial state and the final state.

As an example, the following hierarchy demonstrates the Thread structure of a valid execution with `REPEAT` = 3 and 2 action files (i.e. 2 players):

- Main Thread (programme entry point)
  - Game Thread 1
    - Rendering Engine 1
    - Input Engine 1.1
    - Input Engine 1.2
  - Game Thread 2
    - Rendering Engine 2
    - Input Engine 2.1
    - Input Engine 2.2
  - Game Thread 3
    - Rendering Engine 3
    - Input Engine 3.1
    - Input Engine 3.2

To prevent sharing resource among independent games, static data member implementations should be avoided.

## Methodology

This section documents code logic in my implementation.

Thread concurrency is achieved using `sychronized` keyword (invoking implicit lock on `state`, the GameState instance of each game) and several instance variables.

### `ReplaySokobanGame::run` logic

The implementation is simple. Refer to given JavaDoc instructions, or if still in doubt, refer to `Sokoban::replayGame`.

Essentially, this game is started by spawning the required engine Threads, starting them, and waiting for these spawned Threads to terminate.

To better fulfill the strict FPS requirement, the rendering engine Thread is set to maximum priority.

### `ReplaySokobanGame.InputEngineRunnable::run` logic

The implementation of `InputEngineRunnable::run` should fulfill these requirements:

- Wait for rendering engine to render first
- In game loop:
  - Determine which input engine Thread gets to run
    - `ROUND_ROBIN`: set next input engine to run
    - `FREE_RACE`: no need to set which input engine
  - Fetch and process action from selected input engine
  - Any write to the game state instance must be atomic
  - Pass Thread control back to rendering engine when due to render
  - Log the first `Exit` and disable this input engine after handling the `Exit` object
  - Pass Thread control to another input engine, dependent on Mode

### `ReplaySokobanGame.RenderingEngineRunnable::run` logic

The implementation of `RenderingEngineRunnable::run` should fulfill these requirements:

- Render start message once first
- In game loop:
  - Render current game state
  - Any read of the game state instance must be atomic
  - Pass Thread control back to input engines
  - Pass FPS test, i.e. invoke `renderingEngine.render()` `1000 / frameRate` times in every second
    - Perform `Thread.sleep(1000 / frameRate)` after each render
- Render final game map and message once after all other input engines finish execution
- Render winning message if game wins

## Test cases

### Regression test cases

These are test cases provided by TAs to check previously implemented features. These tests ensure the integrity and correctness of the game source code, and are Tagged with `TestKind.REGRESSION`. These test cases should pass by default.

### Public test cases

These are the test cases provided by TAs to rudimentarily test our implementation of `ReplaySokobanGame`. These test cases are provided in `ReplaySokobanGameTest`, but in order to test more deterministically, these tests are copied to `IntegratedReplaySokobanGameTest.GivenReplaySokobanGameTest` and configured to repeat.

| Passed?  | Test name  | Test description  | Thread-dependent?  | # repetitions  |
|--- |--- |--- |--- |--- |
| Yes  | testRenderingEngineThread  | Game's run method should spawn a new thread for rendering engine  | No  | 20  |
| Yes  | testInputEngineThread  | Game's run method should spwan one thread for each input engine  | No  | 20  |
| Yes  | testMovesOrderMultiple  | Moves from the same input engine should be processed in the same order (multiple input engine)  | Yes  | 100  |
| Yes  | testRoundRobinModeEqualLength  | Action order should be enforced in ROUND_ROBIN mode (all input engines have same length of actions  | Yes  | 100  |
| Yes  | testFPS  | FPS parameter should specify the times render method is invoked per second  | Yes  | 100  |

Note that there are no hidden test cases for FPS (verified by TAs in Discussion #202).

### Custom test cases

These are test cases written by ourselves. These test cases are written in `IntegratedReplaySokobanGameTest.CustomReplaySokobanGameTest`.

Note that these test cases only test the concurrency within one single `ReplaySokobanGame` instance. This is because there is no concurrency scheduling involved among different `ReplaySokobanGame` instances, leading to difficulty in testing due to non-deterministic processing sequences.

#### `ReplaySokobanGame` class tests

| Passed?  | Test name  | Test description  | Thread-dependent?  | # repetitions  |
|--- |--- |--- |--- |--- |
| Test unimplemented  | .  | .  | .  | .  |

#### `ReplaySokobanGame::run` tests

| Passed?  | Test name  | Test description  | Thread-dependent?  | # repetitions  |
|--- |--- |--- |--- |--- |
| Test unimplemented  | testMainThreadLastToTerminate  | Game's run method should wait for all threads to finish before return  | Yes  | 100  |

#### `ReplaySokobanGame.InputEngineRunnable` tests

| Passed?  | Test name  | Test description  | Thread-dependent?  | # repetitions  |
|--- |--- |--- |--- |--- |
| Test unimplemented  | testProcessNPlusMActionsForTotalOfNNonExitActionsAndMPlayers  | Game should process n+m Actions for a total of n non-Exit Actions and m players  | Yes  | 100  |

#### `ReplaySokobanGame.RenderingEngineRunnable` tests

| Passed?  | Test name  | Test description  | Thread-dependent?  | # repetitions  |
|--- |--- |--- |--- |--- |
| Test unimplemented  | testRenderInitialStateBeforeFirstAction  | Game must render initial state before first Action  | No  | 20  |
| Test unimplemented  | testRenderFinalWinningStateWhenGameWins  | Game should render final winning state when game wins  | Yes  | 100  |
| Test unimplemented  | testRenderFinalStateAfterAllActionsProcessed  | Game should render final game state after all Actions from all players are processed  | Yes  | 100  |

## Requirements list

- [x] `ReplaySokobanGame` class
  - [x] Correctness: for an arbitrary list of games, running them in parallel should achieve the same result as running them in sequence
  - [x] The game ends when either:
    - [x] The winning condition is satisfied (i.e., all boxes are placed on the destinations)
    - [x] All actions in all action files (before the first `Exit`) have been processed
- [x] `ReplaySokobanGame::run`
  - [x] Starts the game by spawning threads for each `InputEngine` and `RenderingEngine` instance
  - [x] When `run` method returns, all spawned threads should already terminate
- [x] `ReplaySokobanGame.InputEngineRunnable`
  - [x] For each action file, (and the corresponding `InputEngine`), all actions before (inclusive) the first `Exit` (`E`) should be processed (i.e. fed to the `processAction` method)
    - Assumption: The last action in an action file is always `Exit` (`E`)
  - [x] After the first `Exit` (`E`) is processed, all other actions in the action file should be ignored (i.e., not fed to the `processAction` method)
    - Assumption: The `InputEngine` passed to `ReplaySokobanGame` is an instance of `StreamInputEngine` and `fetchAction` method will return the next action in the action file no matter whether there are `Exit` in the middle. If there are no more actions, `Exit` will be returned
  - [x] Actions in the same action file should be processed in the same order as they appear in the action file
    - Assumption: Each action file corresponds to one `InputEngine` instance, and they are passed in the same order as an array to `ReplaySokobanGame`
- [x] `ReplaySokobanGame.RenderingEngineRunnable`
  - [x] Game must be rendered at least once before first action is performed (i.e. the initial state of the game must be rendered)
  - [x] Game must be rendered at least once after the last action is performed (i.e. the final state of the game must be rendered)
    - [x] Trailing `Exit` action does not count

## Non-specified implementations

- [x] `ReplaySokobanGame` class
  - [x] <https://github.com/CastleLab/COMP3021-F22-PA-Student-Version/discussions/195#discussioncomment-4189464>
  - [x] Should make sure that each modification/view on the GameState is atomic and avoid any race on read/write of GameState (verified by TAs in Discussion #199)
- [x] `ReplaySokobanGame::run`
- [x] `ReplaySokobanGame.InputEngineRunnable`
  - [x] It is fine for the input engine thread to either exit early or wait until the game exits (verified by TAs in Discussion #198)
  - [x] The action failure message is actually printed by the input engine, since it is related to the last processed action (verified by TAs in Discussion #195)
  - [x] Input engine associated with the first `action.txt` (specified in the CLI arguments when starting the game) should be the first to process action (verified by TAs in Discussion #198)
    - This has already been implemented by the TAs, note that input engine index is not necessarily equivalent to player ID
  - [x] Implementing round robin for both `ROUND_ROBIN` and `FREE_RACE` modes will result in failure of hidden test case for `FREE_RACE` (verified by TAs in Discussion #199)
- [x] `ReplaySokobanGame.RenderingEngineRunnable`
  - [x] "Last action" refers to the last non-Exit Action before current game instance ends
  - [x] It is not that the renderingEngine runs between inputEngines. It is that the renderingEngine runs in parallel with inputEngines. The schedule could be arbitrary, e.g., extremely, inputEngines finish processing all actions before the renderingEngine get the chance to execute. (verified by TAs in Discussion #195)
  - [x] The rendering engine has no idea 1) whether there are new actions processed since last render, 2) what is the last processed action, or 3) how many actions are processed by now. The rendering engine just keep rendering in a separate thread at a fixed rate (frame per second)  (verified by TAs in Discussion #195)

## Issue board

- [x] `ReplaySokobanGame` class
  - [x] Inherited `AbstractSokobanGame::shouldStop` returns true when only one player finishes their own list of Actions
    - `AbstractSokobanGame::isExitSpecified` is switched to `true` when the first `Exit` object from any player is passed to `AbstractSokobanGame::processAction`
    - But there may still be other players who have not finished their own Action list
    - [x] Solution: Override `AbstractSokobanGame::shouldStop` to check for either:
      - [x] All input engines have finished providing Actions
      - [x] The game is won
- [x] `ReplaySokobanGame::run`
- [x] `ReplaySokobanGame.InputEngineRunnable`
- [x] `ReplaySokobanGame.RenderingEngineRunnable`
  - [ ] `Thread.sleep()` is capable of passing FPS test, but requires solid understanding of Java programme execution (verified by TAs in Discussion #156)

## This is the end of this todo list
