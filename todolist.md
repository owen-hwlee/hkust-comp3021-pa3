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
  - [ ] For each action file, (and the corresponding `InputEngine`), all actions before (inclusive) the first `Exit` (`E`) should be processed (i.e. fed to the `processAction` method)
    - Assumption: The last action in an action file is always `Exit` (`E`)
  - [ ] After the first `Exit` (`E`) is processed, all other actions in the action file should be ignored (i.e., not fed to the `processAction` method)
    - Assumption: The `InputEngine` passed to `ReplaySokobanGame` is an instance of `StreamInputEngine` and `fetchAction` method will return the next action in the action file no matter whether there are `Exit` in the middle. If there are no more actions, `Exit` will be returned
  - [ ] Actions in the same action file should be processed in the same order as they appear in the action file
    - Assumption: Each action file corresponds to one `InputEngine` instance, and they are passed in the same order as an array to `ReplaySokobanGame`
- [ ] `ReplaySokobanGame.RenderingEngineRunnable`
  - [ ] Game must be rendered at least once before first action is performed (i.e. the initial state of the game must be rendered)
  - [ ] Game must be rendered at least once after the last action is performed (i.e. the final state of the game must be rendered)
    - [ ] Trailing `Exit` action does not count

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

#### `ReplaySokobanGame` class

| Passed?  | Test name  | Test description  | Thread-dependent?  | # repetitions  |
|--- |--- |--- |--- |--- |
| No  | .  | .  | .  | .  |

#### `ReplaySokobanGame::run`

| Passed?  | Test name  | Test description  | Thread-dependent?  | # repetitions  |
|--- |--- |--- |--- |--- |
| Test in development  | testMainThreadLastToTerminate  | Game's run method should wait for all threads to finish before return  | Yes  | 100  |

#### `ReplaySokobanGame.InputEngineRunnable`

| Passed?  | Test name  | Test description  | Thread-dependent?  | # repetitions  |
|--- |--- |--- |--- |--- |
| No  | .  | .  | .  | .  |

#### `ReplaySokobanGame.RenderingEngineRunnable`

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
  - Can refer to `Sokoban::replayGame`
- [ ] `ReplaySokobanGame.InputEngineRunnable`
- [ ] `ReplaySokobanGame.RenderingEngineRunnable`
  - [ ] `Thread.sleep()` is capable of passing FPS test, but requires solid understanding of Java programme execution (verified by TAs in Discussion #156)

## This is the end of this todo list
