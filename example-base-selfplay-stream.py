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
from httpx import ReadTimeout, get

import melee
import numpy as np
import faulthandler
from melee.gamestate import GameState, PlayerState, Projectile
from ComputableNetwork import ComputableNetwork, sigmoidal, swish, mish
from Configuration import Configuration, EvaluatorConfiguration, processConfiguration
from ControllerHelperBinned import ControllerHelper
from DashHelper import DashHelper
from Evaluator import Evaluator
from InputEmbeder import InputEmbeder

from InputEmbederPacked4 import InputEmbederPacked4
from ModelHelper import ModelHelper

from delayGameState import DelayGameState
from LimitedSizeList import LimitedSizeList


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
                            polling_mode=False,)
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
    framesOnCharacterSelect: int
    # ws: WebSocketClientProtocol or None = None
    simulationRunning: bool
    menuLoadFirstFrame: bool
    gamestate: GameState or None
    lastStockAi: int
    lastStockOpponent: int
    lastGamestate: GameState or None
    cpu_level: int
    cpu_character: melee.Character
    ai_character: melee.Character
    reassign_characters: bool
    character_pool: List[melee.Character]

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


def create_packed_state(gamestate: GameState, player_index: int, opponent_index: int) -> 'list[np.ndarray]':
    positionNormalizer = 100.0
    actionNormalizer = 150.0
    return InputEmbederPacked4(player_index, opponent_index,
                               positionNormalizer, actionNormalizer).embed_input(gamestate)


class ModelHandler:
    network: ComputableNetwork
    evaluator: Evaluator
    dashboard_evaluator: Evaluator
    ai_controller_id: int
    model_helper: ModelHelper

    model_index: int
    opponent_index: int
    controller: melee.Controller
    controller_helper: ControllerHelper
    queue: mp.Queue
    evaluator_configuration: EvaluatorConfiguration
    stale_counter: int
    stat_queue: mp.Queue
    max_state: np.ndarray

    def __init__(self, ai_controller_id: int, model_index: int, opponent_index: int, controller: melee.Controller, controller_helper: ControllerHelper, queue: mp.Queue, evaluator_configuration: EvaluatorConfiguration, stat_queue: mp.Queue) -> None:
        self.network = None
        self.evaluator = Evaluator(model_index, opponent_index, evaluator_configuration.attack_time,
                                   evaluator_configuration.max_time, evaluator_configuration.action_limit, None)
        self.dashboard_evaluator = Evaluator(model_index, opponent_index, evaluator_configuration.attack_time,
                                             evaluator_configuration.max_time, evaluator_configuration.action_limit, None)
        self.ai_controller_id = ai_controller_id
        self.model_index = model_index
        self.opponent_index = opponent_index
        self.model_helper = ModelHelper(ai_controller_id, "localhost")
        self.controller = controller
        self.controller_helper = controller_helper
        self.model_id = ""
        self.queue = queue
        self.dash_helper = DashHelper(ai_controller_id)
        self.stale_counter = 0
        self.evaluator_configuration = evaluator_configuration
        self.stat_queue = stat_queue
        self.max_state = None
        self.stateQueue = LimitedSizeList(10)
        self.bias = np.ones((1,1))

    def evaluate(self, game_state: melee.GameState, delayed_game_state: melee.GameState):
        player0: PlayerState = game_state.players[self.model_index]
        player1: PlayerState = game_state.players[self.opponent_index]

        if self.evaluator.previous_frame:
            if self.evaluator.previous_frame.distance == game_state.distance:
                self.stale_counter += 1
            else:
                self.stale_counter = 0

            if self.dashboard_evaluator.previous_frame and self.dashboard_evaluator.player_lost_stock(game_state):
                self.stat_queue.put("death")
                # mp.Process(target=self.dash_helper.updateDeath, daemon=True).start()

            if self.dashboard_evaluator.previous_frame and self.dashboard_evaluator.opponent_lost_stock(game_state) and self.evaluator.opponent_knocked:
                self.stat_queue.put("kill")
                # mp.Process(target=self.dash_helper.updateKill, daemon=True).start()

        if self.network is not None and self.evaluator is not None and self.stale_counter < 60 * 6:
            state = create_packed_state(
                delayed_game_state, self.model_index, self.opponent_index)
            # if (self.max_state is not None):
            #     self.max_state = np.maximum(state, self.max_state)
            # else:
            #     self.max_state = state
            # if game_state.frame % 30 == 0:
            #     print("--------")
            #     print(state)
            # print(self.controller)
            if self.stateQueue.size_limit > 0:
                new_state = state + [self.bias] + self.stateQueue.get_data()
            else:
                new_state = state + [self.bias]
            self.controller_helper.process(
                self.network, self.controller, new_state, player0.controller_state)
            if self.stateQueue.size_limit > 0:
                self.stateQueue.add(state)
            self.evaluator.evaluate_frame(game_state)
            self.dashboard_evaluator.evaluate_frame(game_state)
        else:
            self.controller.release_button(melee.Button.BUTTON_A)
            self.controller.release_button(melee.Button.BUTTON_B)
            self.controller.release_button(melee.Button.BUTTON_Y)
            self.controller.release_button(melee.Button.BUTTON_Z)
            self.controller.release_button(melee.Button.BUTTON_L)
            self.controller.press_shoulder(melee.Button.BUTTON_L, 0)
            self.controller.tilt_analog(melee.Button.BUTTON_MAIN, 0, .5)
        if player0 and player0.stock == 0 or player1 and player1.stock == 0:
            print("no stocks! game over")

            if player0.stock == 0:
                self.stat_queue.put("loss")
                # mp.Process(target=self.dash_helper.updateLoss, daemon=True).start()
            elif player1.stock == 0:
                self.stat_queue.put("win")
                # mp.Process(target=self.dash_helper.updateWin, daemon=True).start()

    def postEvaluate(self, game_state: melee.GameState):
        if self.network is None or self.evaluator is not None and self.evaluator.is_finished(game_state):
            if self.network is not None:
                player0: PlayerState = game_state.players[self.model_index]
                behavior = self.evaluator.score(game_state)
                print(player0.character)
                print(behavior.recovery_sets)
                print(behavior.total_frames_alive)
                # self.model_helper.send_evaluation_result(self.model_id, behavior)
                self.network = None
                self.evaluator = Evaluator(self.model_index, self.opponent_index, self.evaluator_configuration.attack_time,
                                           self.evaluator_configuration.max_time, self.evaluator_configuration.action_limit, None)
            self.reset()

    def reset(self):
        self.model_id, self.network = self.queue.get()
        self.stat_queue.put(self.model_id)
        # print(self.model_id)
        # print("Connections:" + str(self.network.total_number_of_connections) +
        #       "\tCost: " + str(self.network.total_connection_cost))
        # ratio: float = 1
        # if self.network.total_connection_cost != 0:
        #     ratio = self.network.total_number_of_connections / \
        #         self.network.total_connection_cost
        # print("time: " + str(self.evaluator_configuration.max_time *
        #       ratio) + " ratio: " + str(ratio))
        # mp.Process(target=self.dash_helper.updateModel, daemon=True, args=(self.model_id,)).start()
        # print(self.max_state)
        # if (self.max_state is not None):
        #     self.max_state = np.zeros(self.max_state.shape)
        print("creating new evaluator")
        self.stateQueue = LimitedSizeList(len(self.network.input_index)-3)
        self.evaluator = Evaluator(self.model_index, self.opponent_index, self.evaluator_configuration.attack_time,
                                   self.evaluator_configuration.max_time , self.evaluator_configuration.action_limit, None)


def console_loop(queue_1: mp.Queue, queue_2: mp.Queue, configuration: Configuration, stat_queue: mp.Queue, stat_queue2: mp.Queue):
    console, controller, controller_opponent, args, log = startConsole()
    player_index = args.port
    opponent_index = args.opponent
    controller_orig = controller
    controller_opponent_orig = controller_opponent
    # if random.random() >= .5:
    #     player_index = args.opponent
    #     opponent_index = args.port
    #     temp_controller = controller_orig
    #     controller = controller_opponent_orig
    #     controller_opponent = temp_controller
    #     # print(configuration.player_1.character)
    ai_controller_id = 0
    ai_controller_id2 = 1
    hand_counter = 0
    reset = 0
    controller_helper = ControllerHelper()
    controller_helper2 = ControllerHelper()
    model_handler = ModelHandler(ai_controller_id, player_index, opponent_index,
                                 controller, controller_helper, queue_1, configuration.evaluator, stat_queue)
    model_handler.reset()
    model_handler2 = ModelHandler(ai_controller_id2, opponent_index, player_index,
                                  controller_opponent, controller_helper2, queue_2, configuration.evaluator, stat_queue2)
    if configuration.player_2.cpu_level == 0:
        model_handler2.reset()
    frame_delay = configuration.frame_delay
    delay_game_state_provider = DelayGameState(frame_delay)
    while True:
        # print("step")
        game_state = console.step()
        if game_state is None:
            print("We hit this None BS")
            continue

        if game_state.menu_state in [melee.Menu.IN_GAME, melee.Menu.SUDDEN_DEATH]:
            game_state_delayed = delay_game_state_provider.newFrame(game_state)
            if game_state_delayed is None:
                continue
            hand_counter = 0
            reset = 0
            player0: PlayerState = game_state.players[player_index]
            player1: PlayerState = game_state.players[opponent_index]
            model_handler.evaluate(game_state, game_state_delayed)
            if configuration.player_2.cpu_level == 0:
                model_handler2.evaluate(game_state, game_state_delayed)
            model_handler.postEvaluate(game_state)
            if configuration.player_2.cpu_level == 0:
                model_handler2.postEvaluate(game_state)
            if player0 and player0.stock == 0 or player1 and player1.stock == 0:
                print("no stocks! game over")
                # if model_handler.network is None:
                #     model_handler.reset()
                delay_game_state_provider = DelayGameState(frame_delay)
                controller_opponent.release_all()
                controller_opponent.flush()
                controller.release_all()
                controller.flush()
        else:
            if reset == 0:
                evaluator_configuration = configuration.evaluator
                model_handler.dashboard_evaluator = Evaluator(player_index, opponent_index, evaluator_configuration.attack_time,
                                                              evaluator_configuration.max_time, evaluator_configuration.action_limit, None)
                if configuration.player_2.cpu_level == 0:
                    model_handler2.dashboard_evaluator = Evaluator(opponent_index, player_index, evaluator_configuration.attack_time,
                                                                   evaluator_configuration.max_time, evaluator_configuration.action_limit, None)
            #     if random.random() >= .5:
            #         player_index = args.opponent
            #         opponent_index = args.port

            #         controller = controller_opponent_orig
            #         controller_opponent = controller_orig
            #     else:
            #         player_index = args.port
            #         opponent_index =  args.opponent
            #         controller = controller_orig
            #         controller_opponent = controller_opponent_orig
            #         # print(configuration.player_1.character)

            #     model_handler = ModelHandler(ai_controller_id, player_index, opponent_index, controller, controller_helper, queue_1, configuration.evaluator, stat_queue)
            #     model_handler.reset()
                reset += 1
            hand_counter += 1

            if hand_counter < 200:

                melee.MenuHelper.menu_helper_simple(game_state,
                                                    controller,
                                                    configuration.player_1.character,
                                                    configuration.stage,
                                                    args.connect_code,
                                                    costume=0,
                                                    autostart=False,
                                                    swag=False,
                                                    cpu_level=configuration.player_1.cpu_level)
                # if game_state.players and game_state.players[player_index].character == melee.Character.MARIO:
                if game_state.players:
                    player: melee.PlayerState = game_state.players[player_index]
                    player1: melee.PlayerState = game_state.players[opponent_index]
                    if player and player.cpu_level == configuration.player_1.cpu_level and player.character == configuration.player_1.character:
                        melee.MenuHelper.choose_character(
                            character=configuration.player_2.character,
                            gamestate=game_state,
                            controller=controller_opponent,
                            cpu_level=configuration.player_2.cpu_level,
                            costume=0,
                            swag=False,
                            start=True)
                    if game_state.menu_state == melee.Menu.STAGE_SELECT:
                        if player and player.cpu_level == configuration.player_1.cpu_level and player.character == configuration.player_1.character and player1 and player1.cpu_level == configuration.player_2.cpu_level and player1.character == configuration.player_2.character:
                            print("ready")
                            # melee.MenuHelper.
                            melee.MenuHelper.choose_stage(
                                configuration.stage, game_state, controller_opponent)
            # elif hand_counter < 400:
            #     controller_opponent.tilt_analog(melee.Button.BUTTON_MAIN, 0, 0)
            else:
                hand_counter = 0


def queueNetworks(queue: mp.Queue, mgr_dict: DictProxy, ns: Namespace, controller_index: int):
    host = "localhost"
    model_helper = ModelHelper(controller_index, host)
    ns.generation = 0
    while True:
        # try:
        id, builder = model_helper.randomBest()
        network = builder.create_ndarrays(sigmoidal, sigmoidal)
        print(str(controller_index) + ": " + str(id))
        queue.put((id, network))

        # except:
        #     print("failed to get network...")


def httpRequestProcess(queue: mp.Queue, eval_id: int):
    dash_helper = DashHelper(eval_id)
    while True:
        request_data = queue.get()
        if (request_data == "kill"):
            dash_helper.updateKill()
        elif (request_data == "death"):
            dash_helper.updateDeath()
        elif (request_data == "win"):
            dash_helper.updateWin()
        elif (request_data == "loss"):
            dash_helper.updateLoss()
        else:
            dash_helper.updateModel(request_data)


if __name__ == '__main__':
    mgr = mp.Manager()
    mgr_dict = mgr.dict()
    ns = mgr.Namespace()
    # ns = mgr.Namespace()
    # host = "localhost"
    # port = 8095
    process_num = 1
    r = get("http://localhost:8091/configuration")
    data = r.json()
    configuration = processConfiguration(data)
    # configuration.evaluator.max_time = 3 * 60
    # configuration.player_2.cpu_level=9
    # configuration.evaluator.attack_time=5
    processes: List[mp.Process] = []
    queue_1 = mgr.Queue(process_num)
    queue_2 = mgr.Queue(process_num)
    stat_queue = mgr.Queue(20)
    stat_queue2 = mgr.Queue(20)
    p = mp.Process(target=httpRequestProcess,
                   daemon=True, args=(stat_queue, 0))
    processes.append(p)
    p.start()
    if configuration.player_2.cpu_level == 0:
        p = mp.Process(target=httpRequestProcess,
                       daemon=True, args=(stat_queue2, 1))
        processes.append(p)
        p.start()
    for i in range(process_num):
        p = mp.Process(target=console_loop, args=(
            queue_1, queue_2, configuration, stat_queue, stat_queue2))
        processes.append(p)
        p.start()
        p = mp.Process(target=queueNetworks, daemon=True,
                       args=(queue_1, mgr_dict, ns, 0))
        processes.append(p)
        p.start()
        if configuration.player_2.cpu_level == 0:
            p = mp.Process(target=queueNetworks, daemon=True,
                           args=(queue_2, mgr_dict, ns, 1))
            processes.append(p)
            p.start()
    for p in processes:
        p.join()
