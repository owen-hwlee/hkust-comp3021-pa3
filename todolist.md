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

## Requirements list

- [ ] `ReplaySokobanGame` class
  - [ ] Correctness: for an arbitrary list of games, running them in parallel should achieve the same result as running them in sequence
  - [ ] The game ends when either:
    - [ ] The winning condition is satisfied (i.e., all boxes are placed on the destinations)
    - [ ] All actions in all action files (before the first `Exit`) have been processed
- [x] `ReplaySokobanGame::run`
  - [x] Starts the game by spawning threads for each `InputEngine` and `RenderingEngine` instance
  - [x] When `run` method returns, all spawned threads should already terminate
- [ ] `ReplaySokobanGame.InputEngineRunnable`
  - [x] For each action file, (and the corresponding `InputEngine`), all actions before (inclusive) the first `Exit` (`E`) should be processed (i.e. fed to the `processAction` method)
    - Assumption: The last action in an action file is always `Exit` (`E`)
  - [x] After the first `Exit` (`E`) is processed, all other actions in the action file should be ignored (i.e., not fed to the `processAction` method)
    - Assumption: The `InputEngine` passed to `ReplaySokobanGame` is an instance of `StreamInputEngine` and `fetchAction` method will return the next action in the action file no matter whether there are `Exit` in the middle. If there are no more actions, `Exit` will be returned
  - [ ] Actions in the same action file should be processed in the same order as they appear in the action file
    - Assumption: Each action file corresponds to one `InputEngine` instance, and they are passed in the same order as an array to `ReplaySokobanGame`
- [ ] `ReplaySokobanGame.RenderingEngineRunnable`
  - [ ] Game must be rendered at least once before first action is performed (i.e. the initial state of the game must be rendered)
  - [ ] Game must be rendered at least once after the last action is performed (i.e. the final state of the game must be rendered)
    - [ ] Trailing `Exit` action does not count

## Multi-threading in this PA

In this PA, there are 2 layers of multithreading.

### Outer layer: Repeat Game

Note: this has been implemented by TAs.

This PA expects concurrent execution of `<REPEAT>` number of the exact same game.

From the main Thread, `REPEAT` number of game Threads are started. There is a corresponding `GameState` for each game Thread. The `Action`s and `GameState` in each game Thread should not interfere with each other. Each game Thread contains an instance of `ReplaySokobanGame`.

### Game layer: In-game Engine Threads

Within each game (`ReplaySokobanGame`), the game Thread should create:

- `1` Thread for the rendering engine (`RenderingEngineRunnable`)
- `n` Threads for `n` input engines (`InputEngineRunnable`).

The game Thread should then wait for all these engine Threads to terminate.

The engine Threads pertaining to one `GameState` instance should not interfere with other `GameState` instances. The execution order of these engine Threads should be carefully planned to demonstrate game flow correctly.

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

Thread concurrency is achieved using a `ReentrantLock` and multiple conditions, one for each engine.

Sequence of execution:
  main Thread (generate engine Threads in `ReplaySokobanGame::run`)
  &darr;
  renderingEngineThread (intro)
  &darr;
  [inputEngineThread -> renderingEngineThread] * n (game loop)
  &darr;
  renderingEngineThread (win message)
  &darr;
  main Thread (wait for all engine Threads to terminate)

### `ReplaySokobanGame::run` logic

The implementation is simple. Refer to given JavaDoc instructions, or if still in doubt, refer to `Sokoban::replayGame`.

Essentially, this game is started by spawning the required engine Threads, starting them, and waiting for these spawned Threads to terminate.

### `ReplaySokobanGame.InputEngineRunnable::run` logic

The implementation of `InputEngineRunnable::run` should fulfill these requirements:

- Wait for rendering engine to render first
- Determine which input engine Thread gets to run
- Fetch and process action from selected input engine
- Pass Thread control back to rendering engine
- Remember to log the first `Exit` and disable this input engine after handling the `Exit` object
- Make sure to signal rendering engine to allow it to render win message

### `ReplaySokobanGame.RenderingEngineRunnable::run` logic

The implementation of `RenderingEngineRunnable::run` should fulfill these requirements:

- Render once first
- Wait for 1 action from any/selected input engine (depending on `Mode`)
- Render result immediately after processing input
- Pass Thread control back to any/selected input engine
- Pass FPS test, i.e. invoke `renderingEngine.render()` `1000 / frameRate` times in every second
  - Perform `Thread.sleep(1000 / frameRate)` after each render
- Make sure rendering engine renders win message once after all other input engines finish execution

## Test cases

### Regression test cases

These are test cases provided by TAs to check previously implemented features. These tests ensure the integrity and correctness of the game source code, and are Tagged with `TestKind.REGRESSION`. These test cases should pass by default.

### Public test cases

These are the test cases provided by TAs to rudimentarily test our implementation of `ReplaySokobanGame`. These test cases are provided in `ReplaySokobanGameTest`, but in order to test more deterministically, these tests are copied to `IntegratedReplaySokobanGameTest.GivenReplaySokobanGameTest` and configured to repeat.

| Passed?  | Test name  | Test description  | Thread-dependent?  | # repetitions  |
|--- |--- |--- |--- |--- |
| Yes  | testRenderingEngineThread  | Game's run method should spawn a new thread for rendering engine  | No  | 20  |
| Yes  | testInputEngineThread  | Game's run method should spwan one thread for each input engine  | No  | 20  |
| No  | testMovesOrderMultiple  | Moves from the same input engine should be processed in the same order (multiple input engine)  | Yes  | 100  |
| No  | testRoundRobinModeEqualLength  | Action order should be enforced in ROUND_ROBIN mode (all input engines have same length of actions  | Yes  | 100  |
| No  | testFPS  | FPS parameter should specify the times render method is invoked per second  | Yes  | 100  |

### Custom test cases

These are test cases written by ourselves. These test cases are written in `IntegratedReplaySokobanGameTest.CustomReplaySokobanGameTest`.

#### `ReplaySokobanGame` class tests

| Passed?  | Test name  | Test description  | Thread-dependent?  | # repetitions  |
|--- |--- |--- |--- |--- |
| No  | .  | .  | .  | .  |

#### `ReplaySokobanGame::run` tests

| Passed?  | Test name  | Test description  | Thread-dependent?  | # repetitions  |
|--- |--- |--- |--- |--- |
| Test unimplemented  | testMainThreadLastToTerminate  | Game's run method should wait for all threads to finish before return  | Yes  | 100  |

#### `ReplaySokobanGame.InputEngineRunnable` tests

| Passed?  | Test name  | Test description  | Thread-dependent?  | # repetitions  |
|--- |--- |--- |--- |--- |
| No  | .  | .  | .  | .  |

#### `ReplaySokobanGame.RenderingEngineRunnable` tests

| Passed?  | Test name  | Test description  | Thread-dependent?  | # repetitions  |
|--- |--- |--- |--- |--- |
| No  | .  | .  | .  | .  |

## Non-specified implementations

- [ ] `ReplaySokobanGame` class
- [ ] `ReplaySokobanGame::run`
- [ ] `ReplaySokobanGame.InputEngineRunnable`
- [ ] `ReplaySokobanGame.RenderingEngineRunnable`

## Issue board

- [ ] `ReplaySokobanGame` class
- [ ] `ReplaySokobanGame::run`
- [ ] `ReplaySokobanGame.InputEngineRunnable`
- [ ] `ReplaySokobanGame.RenderingEngineRunnable`
  - [ ] `Thread.sleep()` is capable of passing FPS test, but requires solid understanding of Java programme execution (verified by TAs in Discussion #156)

## This is the end of this todo list
