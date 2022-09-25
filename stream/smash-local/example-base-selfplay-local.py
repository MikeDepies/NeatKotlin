#!/usr/bin/python3
from http.server import HTTPServer
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
from InputEmbederPacked import InputEmbederPacked
from ModelHelper import ModelHelper

from multiprocessing.connection import Connection
from multiprocessing.managers import Namespace
from multiprocessing import Pool, Process, Pipe, Manager


def check_port(value):
    ivalue = int(value)
    if ivalue < 1 or ivalue > 4:
        raise argparse.ArgumentTypeError("%s is an invalid controller port. \
                                         Must be 1, 2, 3, or 4." % value)
    return ivalue


# This isn't necessary, but makes it so that Dolphin will get killed when you ^C
fd = melee.framedata.FrameData()


def startConsole():

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
                            slippi_port=args.dolphin_port,
                            blocking_input=False,
                            polling_mode=False)
    controller = melee.Controller(console=console,
                                  port=args.port,
                                  type=melee.ControllerType.STANDARD)
    print("Player port: " + str(args.opponent))
    controller_opponent = melee.Controller(console=console,
                                           port=args.opponent,
                                           type=melee.ControllerType.STANDARD)

    def signal_handler(sig, frame):
        console.stop()
        # stats = pstats.Stats(pr)
        # stats.sort_stats(pstats.SortKey.TIME)
        # stats.dump_stats(filename="profiling" + str(args.dolphin_port) + ".prof")
        # faulthandler.dump_traceback()    
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


def create_packed_state(gamestate: GameState, player_index: int, opponent_index: int) -> np.ndarray:
    positionNormalizer = 100.0
    actionNormalizer = 60.0
    return InputEmbederPacked(player_index, opponent_index,
                           positionNormalizer, actionNormalizer).embed_input(gamestate)

# def create_state(gamestate: GameState, player_index: int, opponent_index: int) -> np.ndarray:
#     positionNormalizer = 100.0
#     actionNormalizer = 60.0
#     state: np.ndarray = np.zeros((1, 1105))
#     player0: PlayerState = gamestate.players[player_index]
#     embeder = InputEmbeder(player_index, opponent_index,
#                            positionNormalizer, actionNormalizer)
#     statePosition = embeder.applyPlayerState(player0, state, 0)

#     player1: PlayerState = gamestate.players[opponent_index]
#     statePosition = embeder.applyPlayerState(player1, state, statePosition)
#     embeder.embedCategory(state, statePosition, gamestate.stage.value, 26)
#     statePosition = statePosition + 26
#     edge = melee.stages.EDGE_GROUND_POSITION[gamestate.stage]
#     leftPlatform = melee.stages.left_platform_position(gamestate.stage)
#     topPlatform = melee.stages.top_platform_position(gamestate.stage)
#     rightPlatform = melee.stages.right_platform_position(gamestate.stage)
#     state[0, statePosition] = edge / positionNormalizer
#     state[0, statePosition + 1] = (edge * -1) / positionNormalizer
#     blastzones: tuple[float, float, float,
#                       float] = melee.stages.BLASTZONES[gamestate.stage]
#     state[0, statePosition +
#           2] = (blastzones[0]) / positionNormalizer
#     state[0, statePosition +
#           3] = (blastzones[1]) / positionNormalizer
#     state[0, statePosition +
#           4] = (blastzones[2]) / positionNormalizer
#     state[0, statePosition +
#           5] = (blastzones[3]) / positionNormalizer
#     statePosition += 6
#     statePosition = embeder.applyPlatform(
#         leftPlatform, state, statePosition)
#     statePosition = embeder.applyPlatform(
#         topPlatform, state, statePosition)
#     statePosition = embeder.applyPlatform(
#         rightPlatform, state, statePosition)
#     state[0, statePosition] = (gamestate.distance) / positionNormalizer
#     statePosition += 1
#     # # state[0, 63] = (gamestate.projectiles) / positionNormalizer
#     for projectile in gamestate.projectiles[:10]:
#         projectile: Projectile
#         embeder.embedCategory(state, statePosition, projectile.owner, 4)
#         statePosition += 4
#         state[0, statePosition] = float(
#             projectile.position.x) / positionNormalizer
#         statePosition += 1
#         state[0, statePosition] = float(
#             projectile.position.y) / positionNormalizer
#         statePosition += 1
#         state[0, statePosition] = float(
#             projectile.speed.x) / positionNormalizer
#         statePosition += 1
#         state[0, statePosition] = float(
#             projectile.speed.y) / positionNormalizer
#         statePosition += 1
#         embeder.embedCategory(state, statePosition, projectile.subtype, 11)
#         statePosition += 11
#     return state


def console_loop( connection : Connection,  connection2 : Connection, ns : Namespace, model_helper : ModelHelper, model_helper2 : ModelHelper):
    # Main loop
    # if (Session.network0 != None and Session.network1 == None):
    # log = melee.Logger()
    i =0
    console, controller, controller_opponent, args, log = startConsole()
    player_index = args.port
    opponent_index = args.opponent
    
    evaluator = Evaluator(player_index, opponent_index, model_helper, 1, 1)
    evaluator2 = Evaluator( opponent_index,player_index, model_helper2, 1, 1)
    last_time_opponent_not_stuck = time.time()
    
    # model_id = model_list[model_index]
    network = None
    network2 = None
    # model_index += 1
    controller_helper = ControllerHelper()
    t = time.time()
    while True:
        game_state = console.step()
        
        i+=1
        # if i % 240 == 0:
        #         print("still working: " + str(i) + " stage: " + str(game_state.stage))
        if game_state is None:
            # print("We hit this None BS")
            continue

        # The console object keeps track of how long your bot is taking to process frames
        #   And can warn you if it's taking too long
        if console.processingtime * 1000 > 50:
            print("WARNING: Last frame took " +
                  str(console.processingtime*1000) + "ms to process.")
        if game_state.menu_state in [melee.Menu.IN_GAME, melee.Menu.SUDDEN_DEATH]:
            if ns.network is not None:
                network = ns.network
                ns.network = None
            if ns.network2 is not None:
                network2 = ns.network2
                ns.network2 = None
                # print("network null? " +str(network is None))
            player0: PlayerState = game_state.players[player_index]
            player1: PlayerState = game_state.players[opponent_index]
            if network is not None:    
                state = create_packed_state(game_state, player_index, opponent_index)
                controller_helper.process(network, controller, state)
            if network2 is not None:    
                state = create_packed_state(game_state,  opponent_index, player_index)
                controller_helper.process(network2, controller_opponent, state)
            
            evaluator.evaluate_frame(game_state)
            evaluator2.evaluate_frame(game_state)
                
            # session.lastStockAi = player0.stock
            # session.lastStockOpponent = player1.stock
            
            if evaluator.is_finished(game_state):
                behavior = evaluator.score(game_state)
                evaluator = Evaluator(player_index, opponent_index, model_helper, 20, 300)
                # network = None
                connection.send({
                    
                })
            if evaluator2.is_finished(game_state):
                behavior = evaluator2.score(game_state)
                evaluator2 = Evaluator( opponent_index,player_index, model_helper2, 20, 300)
                # network2 = None
                connection2.send({
                    
                })
                    # result = model_helper.testModelId(model_id)
                # print("finished getting new model.")
            if player0 and player0.stock == 0 or player1 and player1.stock == 0:
                print("no stocks! game over")
                if player0.stock == 0:
                    Process(target=updateEndGame, args=(model_helper2, model_helper), daemon=True).start()
                elif player1.stock == 0:
                    Process(target=updateEndGame, args=(model_helper, model_helper2), daemon=True).start()
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
                                                    melee.Character.LINK,
                                                    melee.Stage.FINAL_DESTINATION,
                                                    args.connect_code,
                                                    costume=0,
                                                    autostart=False,
                                                    swag=False,
                                                    cpu_level=0)            
            # if game_state.players and game_state.players[player_index].character == melee.Character.CPTFALCON:
            melee.MenuHelper.menu_helper_simple(game_state,
                                            controller_opponent,
                                            melee.Character.PIKACHU,
                                            melee.Stage.FINAL_DESTINATION,
                                            args.connect_code,
                                            costume=0,
                                            autostart=True,
                                            swag=False,
                                            cpu_level=0)
                

            # counter += 1
            # if counter > 6 * 60:
            #     session.reassign_characters = True
def updateEndGame(winner : ModelHelper, loser : ModelHelper):
    winner.updateWin()
    loser.updateLoss()

def request_loop(read : Connection, model_helper : ModelHelper, ns):
    nextNetwork = model_helper.randomBest()
    while True:
        msg = read.recv()
        try:
            print("get network! 1")
            ns.network = nextNetwork[0]
            model_helper.updateModel(nextNetwork[1])
            nextNetwork = model_helper.randomBest()
        except:
            print("failed to assign a new best network")
def request_loop2(read : Connection, model_helper : ModelHelper, ns):
    nextNetwork = model_helper.randomBest()
    while True:
        msg = read.recv()
        try:
            print("get network! 2")
            ns.network2 = nextNetwork[0]
            model_helper.updateModel(nextNetwork[1])
            nextNetwork = model_helper.randomBest()
        except:
            print("failed to assign a new best network")
    

if __name__ == '__main__':
    mgr = Manager()
    ns = mgr.Namespace()
    ns.network = None
    ns.network2 = None
    ai_controller_id = 0
    ai_controller_id2 = 1
    model_helper = ModelHelper(ai_controller_id, "192.168.0.123")
    model_helper2 = ModelHelper(ai_controller_id2, "192.168.0.123")
    in_send, in_rec = Pipe()
    in_send2, in_rec2 = Pipe()

    Process(target=request_loop, args=(in_rec, model_helper, ns), daemon=True).start()
    Process(target=request_loop2, args=(in_rec2, model_helper2, ns), daemon=True).start()
    
    # faulthandler.enable(True, True)    
    # faulthandler.dump_traceback_later(10, True)
    # with cProfile.Profile() as pr:
    c = Process(target=console_loop, args=(in_send, in_send2, ns, model_helper, model_helper2))
    # console_loop(model_helper, ai_controller_id, in_send)
    c.start()
    c.join()



# Process(target=requestNewNetworks, args=(in_send,)).start()
# loop = Process(target=loop.run_forever, args=(in_send,))
# loop.start()
# loop.join()

    # main.start()
    # loop.run_until_complete(handle_message())
    # main.join()
