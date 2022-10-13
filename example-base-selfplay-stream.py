#!/usr/bin/python3
import multiprocessing as mp
import argparse
import json
import math
from multiprocessing.managers import DictProxy, Namespace

import pstats
import cProfile
import random
import signal
import sys
import time
from typing import List
from httpx import ReadTimeout

import melee
import numpy as np
import faulthandler
from melee.gamestate import GameState, PlayerState, Projectile
from ComputableNetwork import ComputableNetwork
from ControllerHelper import ControllerHelper
from DashHelper import DashHelper
from Evaluator import Evaluator
from InputEmbeder import InputEmbeder
from InputEmbederPacked import InputEmbederPacked
from ModelHelper import ModelHelper



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

class ModelHandler:
    network : ComputableNetwork
    evaluator : Evaluator
    ai_controller_id: int
    model_helper: ModelHelper
    
    model_index: int
    opponent_index: int
    controller: melee.Controller
    controller_helper : ControllerHelper
    queue : mp.Queue
    
    def __init__(self, ai_controller_id: int, model_index: int, opponent_index: int, controller: melee.Controller, controller_helper : ControllerHelper, queue : mp.Queue) -> None:
        self.network = None
        self.evaluator = Evaluator(model_index, opponent_index, 5, 10, 7 , None)
        self.ai_controller_id = ai_controller_id
        self.model_index = model_index
        self.opponent_index = opponent_index
        self.model_helper = ModelHelper(ai_controller_id, "localhost")
        self.controller = controller
        self.controller_helper = controller_helper
        self.model_id = ""    
        self.queue = queue
        self.dash_helper = DashHelper(ai_controller_id)

    def evaluate(self, game_state : melee.GameState):
        player0: PlayerState = game_state.players[self.model_index]
        player1: PlayerState = game_state.players[self.opponent_index]
        
        if self.evaluator.previous_frame:
            if self.evaluator.player_lost_stock(game_state):
                mp.Process(target=self.dash_helper.updateDeath, daemon=True).start()
            if self.evaluator.opponent_lost_stock(game_state) and self.evaluator.opponent_knocked:
                mp.Process(target=self.dash_helper.updateKill, daemon=True).start()
            
        if self.network is not None and self.evaluator is not None:    
            state = create_packed_state(game_state, self.model_index, self.opponent_index)
            create_packed_state(game_state, self.model_index, self.opponent_index)
            self.controller_helper.process(self.network, self.controller, state)
            self.evaluator.evaluate_frame(game_state)
        if player0 and player0.stock == 0 or player1 and player1.stock == 0:
                    print("no stocks! game over")
                    if player0.stock == 0:
                        mp.Process(target=self.dash_helper.updateLoss, daemon=True).start()
                    elif player1.stock == 0:
                        mp.Process(target=self.dash_helper.updateWin, daemon=True).start()

    def postEvaluate(self, game_state : melee.GameState):
        if self.network is None or self.evaluator.is_finished(game_state):
            if self.network is not None:
                behavior = self.evaluator.score(game_state)
                print(behavior.actions)
                # self.model_helper.send_evaluation_result(self.model_id, behavior)
                self.network = None
            
            self.model_id, self.network = self.queue.get()
            mp.Process(target=self.dash_helper.updateModel, daemon=True, args=(self.model_id,)).start()
            print("creating new evaluator")
            self.evaluator = Evaluator(self.model_index, self.opponent_index, 10, 120, 12, None)


def console_loop(queue_1 : mp.Queue, queue_2 : mp.Queue):
    console, controller, controller_opponent, args, log = startConsole()
    player_index = args.port
    opponent_index = args.opponent
    
    ai_controller_id = 0
    ai_controller_id2 = 1
    
    
    controller_helper = ControllerHelper()
    model_handler = ModelHandler(ai_controller_id, player_index, opponent_index, controller, controller_helper, queue_1)
    # model_handler2 = ModelHandler(ai_controller_id2, opponent_index, player_index, controller_opponent, controller_helper, queue_2)
    while True:
        game_state = console.step()
        if game_state is None:
            print("We hit this None BS")
            continue
        
        if game_state.menu_state in [melee.Menu.IN_GAME, melee.Menu.SUDDEN_DEATH]:

            player0: PlayerState = game_state.players[player_index]
            player1: PlayerState = game_state.players[opponent_index]
            model_handler.evaluate(game_state)
            # model_handler2.evaluate(game_state)
            model_handler.postEvaluate(game_state)
            # model_handler2.postEvaluate(game_state)
            if player0 and player0.stock == 0 or player1 and player1.stock == 0:
                print("no stocks! game over")
                controller_opponent.release_all()
                controller_opponent.flush()
                controller.release_all()
                controller.flush()
        else:
            melee.MenuHelper.menu_helper_simple(game_state,
                                                    controller,
                                                    melee.Character.MARIO,
                                                    melee.Stage.FINAL_DESTINATION,
                                                    args.connect_code,
                                                    costume=0,
                                                    autostart=False,
                                                    swag=False,
                                                    cpu_level=0)            
            # if game_state.players and game_state.players[player_index].character == melee.Character.MARIO:
            if game_state.players:
                player : melee.PlayerState = game_state.players[player_index]
                if player and player.cpu_level == 0 and player.character == melee.Character.MARIO:
                    melee.MenuHelper.menu_helper_simple(game_state,
                                            controller_opponent,
                                            melee.Character.FOX,
                                            melee.Stage.FINAL_DESTINATION,
                                            args.connect_code,
                                            costume=0,
                                            autostart=True,
                                            swag=False,
                                            cpu_level=5)

def queueNetworks(queue : mp.Queue, mgr_dict : DictProxy, ns : Namespace, controller_index: int):
    host = "localhost"
    model_helper = ModelHelper(controller_index, host)
    ns.generation = 0
    while True:
        # try:
        id, builder = model_helper.randomBest()
        network = builder.create_ndarrays()
        
        queue.put((id, network))
        
                
        # except:
        #     print("failed to get network...")



if __name__ == '__main__':
    mgr = mp.Manager()
    mgr_dict = mgr.dict()
    ns = mgr.Namespace()
    # ns = mgr.Namespace()
    # host = "localhost"
    # port = 8095
    process_num = 1
    
    processes: List[mp.Process] = []
    queue_1 = mgr.Queue(process_num * 2)
    queue_2 = mgr.Queue(process_num * 2)
    for i in range(process_num):
        p = mp.Process(target=console_loop, args=(queue_1, queue_2))
        processes.append(p)
        p.start()
        p = mp.Process(target=queueNetworks, daemon=True, args=(queue_1,mgr_dict, ns, 0))
        processes.append(p)
        p.start()
        # p = mp.Process(target=queueNetworks, daemon=True, args=(queue_2,mgr_dict, ns, 1))
        # processes.append(p)
        # p.start()
    for p in processes:
        p.join()
