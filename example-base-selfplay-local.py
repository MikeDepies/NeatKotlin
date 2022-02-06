#!/usr/bin/python3
import multiprocessing as mp
import argparse
import json
import math

import pstats
import cProfile
import random
import signal
import sys
import time
from typing import List

import melee
import numpy as np
import faulthandler
from melee.gamestate import GameState, PlayerState, Projectile
from ControllerHelper import ControllerHelper
from Evaluator import Evaluator
from InputEmbeder import InputEmbeder
from ModelHelper import ModelHelper


def check_port(value):
    ivalue = int(value)
    if ivalue < 1 or ivalue > 4:
        raise argparse.ArgumentTypeError("%s is an invalid controller port. \
                                         Must be 1, 2, 3, or 4." % value)
    return ivalue


# This isn't necessary, but makes it so that Dolphin will get killed when you ^C
fd = melee.framedata.FrameData()


def startConsole(port : int):

    # This example program demonstrates how to use the Melee API to run a console,
    #   setup controllers, and send button presses over to a console
    parser = argparse.ArgumentParser(
        description='Example of libmelee in action')
    parser.add_argument('--port', '-p', type=check_port,
                        help='The controller port (1-4) your AI will play on',
                        default=2)
    parser.add_argument('--opponent', '-o', type=check_port,
                        help='The controller port (1-4) the opponent will play on',
                        default=1)
    parser.add_argument('--debug', '-d', action='store_true',
                        help='Debug mode. Creates a CSV of all game states')
    parser.add_argument('--address', '-a', default="127.0.0.1",
                        help='IP address of Slippi/Wii')
    parser.add_argument('--dolphin_port', '-b', default=51441, type=int,
                        help='IP address of Slippi/Wii')                        
    parser.add_argument('--dolphin_executable_path', '-e', default=None,
                        help='The directory where dolphin is')
    parser.add_argument('--connect_code', '-t', default="",
                        help='Direct connect code to connect to in Slippi Online')
    parser.add_argument('--iso', default=None, type=str,
                        help='Path to melee iso.')

    # This logger object is useful for retroactively debugging issues in your bot
    #   You can write things to it each frame, and it will create a CSV file describing the match
    args = parser.parse_args()
    log = None
    if args.debug:
        log = melee.Logger()
    controller = None
    controller_opponent = None
    console = melee.Console(path=args.dolphin_executable_path,
                            logger=log,
                            slippi_port=port,
                            blocking_input=True,
                            polling_mode=False,
                            setup_gecko_codes=True, gfx_backend="Null", use_exi_inputs=True, enable_ffw=True, save_replays=False)
    controller = melee.Controller(console=console,
                                  port=args.port,
                                  type=melee.ControllerType.STANDARD)
    print("Player port: " + str(args.opponent))
    controller_opponent = melee.Controller(console=console,
                                           port=args.opponent,
                                           type=melee.ControllerType.STANDARD)

    def signal_handler(sig, frame):
        console.stop()
        # Session.ws.send(json.dumps(createMessage("simulation.pause", {})))
        if args.debug:
            log.writelog()
            print("")  # because the ^C will be on the terminal
            print("Log file created: " + log.filename)
        print("Shutting down cleanly...")
        sys.exit(0)

    signal.signal(signal.SIGINT, signal_handler)

    # Run the console
    console.run(iso_path=args.iso)

    # Connect to the console
    print("Connecting to console... ")
    if not console.connect():
        print("ERROR: Failed to connect to the console.")
        sys.exit(-1)
    print("Console connected")

    # Plug our controller in
    #   Due to how named pipes work, this has to come AFTER running dolphin
    #   NOTE: If you're loading a movie file, don't connect the controller,
    #   dolphin will hang waiting for input and never receive it
    print("Connecting controller to console...")
    if not controller.connect():
        print("ERROR: Failed to connect the controller.")
        sys.exit(-1)
    print("Controller connected")

    print("Connecting controller to console...")
    if not controller_opponent.connect():
        print("ERROR: Failed to connect the controller.")
        sys.exit(-1)
    print("Controller connected")
    return (console, controller, controller_opponent, args, log)


class NumpyEncoder(json.JSONEncoder):
    """ Special json encoder for numpy types """

    def default(self, obj):
        if isinstance(obj, np.integer):
            value = int(obj)
            return 0 if math.isnan(value) else value
        elif isinstance(obj, np.floating):
            value = float(obj)
            if math.isnan(value):
                return 0
            else:
                return value
        elif isinstance(obj, np.ndarray):
            return obj.tolist()
        return json.JSONEncoder.default(self, obj)


class Session:
    framesOnCharacterSelect : int
    # ws: WebSocketClientProtocol or None = None
    simulationRunning : bool
    menuLoadFirstFrame : bool
    gamestate: GameState or None
    lastStockAi : int
    lastStockOpponent : int
    lastGamestate: GameState or None
    cpu_level : int
    cpu_character : melee.Character
    ai_character : melee.Character
    reassign_characters : bool
    character_pool : List[melee.Character]
    def __init__(self) -> None:
        self.framesOnCharacterSelect = 0
        # ws: WebSocketClientProtocol or None = None
        self.simulationRunning = False
        self.menuLoadFirstFrame = True
        self.gamestate: GameState or None = None
        self.lastStockAi = 4
        self.lastStockOpponent = 4
        self.lastGamestate: GameState or None = None
        self.cpu_level = random.randint(1, 9)
        self.cpu_character = melee.Character.FOX
        self.ai_character = melee.Character.SAMUS
        self.reassign_characters = True
        self.character_pool = [melee.Character.FOX, melee.Character.SAMUS, melee.Character.FALCO, melee.Character.MARTH,
                      melee.Character.CPTFALCON, melee.Character.PEACH, melee.Character.PIKACHU, melee.Character.ZELDA, melee.Character.GANONDORF, melee.Character.JIGGLYPUFF,
                      melee.Character.MARIO, melee.Character.DK, melee.Character.KIRBY, melee.Character.BOWSER, melee.Character.LINK, melee.Character.NESS, melee.Character.PEACH, melee.Character.YOSHI, melee.Character.MEWTWO, melee.Character.LUIGI, melee.Character.YLINK, melee.Character.DOC, melee.Character.GAMEANDWATCH, melee.Character.ROY]


def create_state(gamestate: GameState, player_index: int, opponent_index: int) -> np.ndarray:
    positionNormalizer = 100.0
    actionNormalizer = 60.0
    state: np.ndarray = np.zeros((1, 1105))
    player0: PlayerState = gamestate.players[player_index]
    embeder = InputEmbeder(player_index, opponent_index,
                           positionNormalizer, actionNormalizer)
    statePosition = embeder.applyPlayerState(player0, state, 0)

    player1: PlayerState = gamestate.players[opponent_index]
    statePosition = embeder.applyPlayerState(player1, state, statePosition)
    embeder.embedCategory(state, statePosition, gamestate.stage.value, 26)
    statePosition = statePosition + 26
    edge = melee.stages.EDGE_GROUND_POSITION[gamestate.stage]
    leftPlatform = melee.stages.left_platform_position(gamestate.stage)
    topPlatform = melee.stages.top_platform_position(gamestate.stage)
    rightPlatform = melee.stages.right_platform_position(gamestate.stage)
    state[0, statePosition] = edge / positionNormalizer
    state[0, statePosition + 1] = (edge * -1) / positionNormalizer
    blastzones: tuple[float, float, float,
                      float] = melee.stages.BLASTZONES[gamestate.stage]
    state[0, statePosition +
          2] = (blastzones[0]) / positionNormalizer
    state[0, statePosition +
          3] = (blastzones[1]) / positionNormalizer
    state[0, statePosition +
          4] = (blastzones[2]) / positionNormalizer
    state[0, statePosition +
          5] = (blastzones[3]) / positionNormalizer
    statePosition += 6
    statePosition = embeder.applyPlatform(
        leftPlatform, state, statePosition)
    statePosition = embeder.applyPlatform(
        topPlatform, state, statePosition)
    statePosition = embeder.applyPlatform(
        rightPlatform, state, statePosition)
    state[0, statePosition] = (gamestate.distance) / positionNormalizer
    statePosition += 1
    # # state[0, 63] = (gamestate.projectiles) / positionNormalizer
    for projectile in gamestate.projectiles[:10]:
        projectile: Projectile
        embeder.embedCategory(state, statePosition, projectile.owner, 4)
        statePosition += 4
        state[0, statePosition] = float(
            projectile.position.x) / positionNormalizer
        statePosition += 1
        state[0, statePosition] = float(
            projectile.position.y) / positionNormalizer
        statePosition += 1
        state[0, statePosition] = float(
            projectile.speed.x) / positionNormalizer
        statePosition += 1
        state[0, statePosition] = float(
            projectile.speed.y) / positionNormalizer
        statePosition += 1
        embeder.embedCategory(state, statePosition, projectile.subtype, 11)
        statePosition += 11
    return state


def console_loop(port : int):
    # Main loop
    # if (Session.network0 != None and Session.network1 == None):
    # log = melee.Logger()
    i =0
    console, controller, controller_opponent, args, log = startConsole(port)
    player_index = args.port
    opponent_index = args.opponent
    # console.step()
    # print("START CONSOLE LOOP")
    session = Session()
    session.cpu_level = 3
    ai_controller_id = 0
    evaluator = Evaluator(player_index, opponent_index, 5, 10)
    model_helper = ModelHelper(ai_controller_id, "localhost")
    model_list = model_helper.getModels()
    model_index = 0
    # model_id = model_list[model_index]
    network = None
    # model_index += 1
    controller_helper = ControllerHelper()
    t = time.time()
    while True:
        game_state = console.step()
        i+=1
        # if i % 240 == 0:
        #         print("still working: " + str(i) + " stage: " + str(game_state.stage))
        if game_state is None:
            print("We hit this None BS")
            continue

        # The console object keeps track of how long your bot is taking to process frames
        #   And can warn you if it's taking too long
        # if console.processingtime * 1000 > 50:
        #     print("WARNING: Last frame took " +
        #           str(console.processingtime*1000) + "ms to process.")
        
        # What menu are we in?
        # print(game_state.menu_state)
        # if log is not None:
        #     log.logframe(game_state)
        # log.writelog()
        
        if game_state.menu_state in [melee.Menu.IN_GAME, melee.Menu.SUDDEN_DEATH]:
            # if not session.reassign_characters:
            #     session.reassign_characters = True
            #     session.menuLoadFirstFrame = False

            player0: PlayerState = game_state.players[player_index]
            player1: PlayerState = game_state.players[opponent_index]
            if network is not None:    
                state = create_state(game_state, player_index, opponent_index)
                controller_helper.process(network, controller, state)
                evaluator.evaluate_frame(game_state)
                
            # session.lastStockAi = player0.stock
            # session.lastStockOpponent = player1.stock
            
            if network is None or evaluator.is_finished(game_state):
                if network is not None:
                    behavior = evaluator.score(game_state)
                    print(behavior.recovery_sets)
                    model_helper.send_evaluation_result(model_id, behavior)
                    network = None
                    
                # 
                # while True:
                #     print("trying to aquire new model")
                if model_index < len(model_list):
                    print("model index: " + str(model_index))
                    model_id = model_list[model_index]
                    model_index += 1
                    result = model_helper.testModelId(model_id)
                    # print(result.model_scored)
                    # print(result.model_available)
                    # print(result.model_part_of_generation)
                    if result.model_scored:
                        print("model scored already")
                        network = None
                    elif result.model_available:
                        print("getting network for " + model_id)
                        network = model_helper.getNetwork(
                            ai_controller_id, model_id)
                        print("creating new evaluator")
                        evaluator = Evaluator(player_index, opponent_index, 60, 360, action_limit= 12)
                        # break
                    elif not result.model_part_of_generation:
                        network = None
                        print("model was not part of generation " + model_id)
                        model_list = model_helper.getModels()
                        model_index = 0
                else:
                    # print(time.time() - t)
                    if time.time() - t > 2:
                        t = time.time()
                        network = None
                        model_list = model_helper.getModels()
                        model_index = 0
                    # result = model_helper.testModelId(model_id)
                # print("finished getting new model.")
            if player0 and player0.stock == 0 or player1 and player1.stock == 0:
                print("no stocks! game over")
                controller_opponent.release_all()
                controller_opponent.flush()
        else:
            i = 0
            # if session.reassign_characters:
            #     counter = 0
            #     session.ai_character = random.choice(session.character_pool)
            #     session.cpu_character = random.choice(session.character_pool)
            #     session.reassign_characters = False
            # if not session.menuLoadFirstFrame:
            #     session.menuLoadFirstFrame = True
            melee.MenuHelper.menu_helper_simple(game_state,
                                                    controller,
                                                    melee.Character.MEWTWO,
                                                    melee.Stage.FINAL_DESTINATION,
                                                    args.connect_code,
                                                    costume=0,
                                                    autostart=False,
                                                    swag=False,
                                                    cpu_level=0)            
            # if game_state.players and game_state.players[player_index].character == melee.Character.MARIO:
            melee.MenuHelper.menu_helper_simple(game_state,
                                            controller_opponent,
                                            melee.Character.FOX,
                                            melee.Stage.FINAL_DESTINATION,
                                            args.connect_code,
                                            costume=0,
                                            autostart=True,
                                            swag=False,
                                            cpu_level=5)
                

            # counter += 1
            # if counter > 6 * 60:
            #     session.reassign_characters = True


if __name__ == '__main__':
    processes : List[mp.Process]= []
    for i in range(20):
        p = mp.Process(target=console_loop, args=(i + 51460,), daemon=True)
        processes.append(p)
        p.start()

    for p in processes:
        p.join()

# Process(target=requestNewNetworks, args=(in_send,)).start()
# loop = Process(target=loop.run_forever, args=(in_send,))
# loop.start()
# loop.join()

    # main.start()
    # loop.run_until_complete(handle_message())
    # main.join()
