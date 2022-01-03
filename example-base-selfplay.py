#!/usr/bin/python3

import argparse
from asyncio.tasks import sleep
from asyncio.windows_events import NULL
import json
from multiprocessing.connection import Connection
from multiprocessing.managers import Namespace
import signal
import sys
from typing import Dict
import melee
import random
from melee import gamestate
from melee.enums import Button
from melee.gamestate import GameState, PlayerState, Projectile
from melee.stages import randall_position
import numpy as np
import websockets
import asyncio
import threading
import math
import random
import numpy as np
import math
import requests
import NeatNetwork
import time
from typing import List
from threading import Thread
from websockets.client import WebSocketClientProtocol
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
    parser = argparse.ArgumentParser(description='Example of libmelee in action')
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
                            logger=log)
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
    return (console, controller, controller_opponent, args)


def getActiveNetwork(controllerId):
    res = requests.post("http://" + host + ":8091/model/active", json={
        "controllerId": controllerId
    })
    if res.ok:
        data = res.json()
        return getNetwork(data["controllerId"], data["modelId"])
    else:
        raise Exception("No response for active model for " +
                        str(controllerId))


def getNetwork(controllerId, modelId):
    requestNetwork = True
    network = None
    try:
        res = requests.post("http://" + host + ":8091/model", json={
            "controllerId": controllerId,
            "modelId": modelId,
            
        }, timeout=2)
        if not res.ok:
            raise Exception("No data for request")
        data = res.json()
        id: str = data["id"]
        connections: List[NeatNetwork.ConnectionLocation] = list(map(lambda c: NeatNetwork.ConnectionLocation(
            c[0], c[1], c[2], c[3], c[4], c[5], c[6]), data["connections"]))
        nodes: List[NeatNetwork.ConnectionLocation] = list(
            map(lambda n: NeatNetwork.NodeLocation(n[0], n[1], n[2]), data["nodes"]))
        print(len(connections))
        # print(len(nodes))

        network = NeatNetwork.constructNetwork(nodes, connections, [[1, 1105], [9,9],
                                                                    [9, 9], [9, 9], [7, 7], [5, 5], [1, 9]])
    except requests.Timeout as err:
        
        print("timeout: failed to get " + str(modelId) + " for " + str(controllerId))
        getNetwork(controllerId, modelId)
    requestNetwork = False
    # requests.post("http://" + host + ":8091/start", json={
    #         "controllerId": controllerId,
    #         "modelId": modelId,
            
    #     }, timeout=2)
    return network


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


host = "192.168.0.132"


class Session:
    network0 = None
    network1 = None
    framesOnCharacterSelect = 0
    # ws: WebSocketClientProtocol or None = None
    simulationRunning = False
    menuLoadFirstFrame = True
    gamestate: GameState or None = None
    lastStockAi = 4
    lastStockOpponent = 4
    lastGamestate: GameState or None = None
    cpu_level = random.randint(1, 9)
    cpu_character = melee.Character.FOX
    ai_character = melee.Character.SAMUS
    reassign_characters = True
    character_pool = [melee.Character.FOX, melee.Character.SAMUS, melee.Character.FALCO, melee.Character.MARTH,
                      melee.Character.CPTFALCON, melee.Character.PEACH, melee.Character.PIKACHU, melee.Character.ZELDA, melee.Character.GANONDORF, melee.Character.JIGGLYPUFF,
                      melee.Character.MARIO, melee.Character.DK, melee.Character.KIRBY, melee.Character.BOWSER, melee.Character.LINK, melee.Character.NESS, melee.Character.PEACH, melee.Character.YOSHI,melee.Character.MEWTWO, melee.Character.LUIGI, melee.Character.YLINK, melee.Character.DOC, melee.Character.GAMEANDWATCH, melee.Character.ROY]


def processMessage(message: Dict, controller: melee.Controller):

    if (message["a"] == True):
        controller.press_button(Button.BUTTON_A)
    else:
        controller.release_button(Button.BUTTON_A)
    if (message["b"] == True):
        controller.press_button(Button.BUTTON_B)
    else:
        controller.release_button(Button.BUTTON_B)
    if (message["y"] == True):
        controller.press_button(Button.BUTTON_Y)
    else:
        controller.release_button(Button.BUTTON_Y)

    if (message["z"] == True):
        controller.press_button(Button.BUTTON_Z)
    else:
        controller.release_button(Button.BUTTON_Z)

    controller.tilt_analog(
        Button.BUTTON_MAIN, message["mainStickX"], message["mainStickY"])
    controller.tilt_analog(
        Button.BUTTON_C, message["cStickX"], message["cStickY"])
    controller.press_shoulder(Button.BUTTON_L, message["leftShoulder"])
    if (message["leftShoulder"] >= .9):
        controller.press_button(Button.BUTTON_L)
    else:
        controller.release_button(Button.BUTTON_L)
    # controller.press_shoulder(Button.BUTTON_R, message["rightShoulder"])

    if (not Session.menuLoadFirstFrame):
        controller.flush()


def actionData(gameState: GameState, port: int):
    player: PlayerState = gameState.players[port]
    rangeForward = fd.range_forward(
        player.character, player.action, player.action_frame)
    rangeBackward = fd.range_backward(
        player.character, player.action, player.action_frame)
    if (math.isnan(rangeBackward)):
        rangeBackward = 0
    if (math.isnan(rangeForward)):
        rangeForward = 0

    return {
        "action": player.action.value,
        "isAttack": bool(fd.is_attack(character=player.character, action=player.action)),
        "isGrab": bool(fd.is_grab(player.character, player.action)),
        "isBMove": bool(fd.is_bmove(player.character, player.action)),
        "isShield": bool(fd.is_shield(player.action)),
        "rangeBackward": rangeBackward,
        "rangeForward": rangeForward,
        "hitBoxCount": fd.hitbox_count(player.character, player.action),
        "attackState": fd.attack_state(player.character, player.action, player.action_frame).value,
        "actionFrame": player.action_frame
    }


def environmentalCollisionBox(player: PlayerState):
    return {
        "left": {
            "x": player.ecb_left[0],
            "y": player.ecb_left[1]
        },
        "top": {
            "x": player.ecb_top[0],
            "y": player.ecb_top[1]
        },
        "right": {
            "x": player.ecb_right[0],
            "y": player.ecb_right[1]
        },
        "bottom": {
            "x": player.ecb_bottom[0],
            "y": player.ecb_bottom[1]
        }
    }


def playerData(gameState: GameState, port: int):
    player: PlayerState = gameState.players[port]
    x = 0
    y = 0

    if gameState.menu_state in [melee.Menu.CHARACTER_SELECT]:
        x = player.cursor.x
        y = player.cursor.y
    else:
        x = player.position.x
        y = player.position.y
    return {
        "character": player.character.value,
        "stock": player.stock,
        "x": x,
        "y": y,
        "speedAirX": player.speed_air_x_self,
        "speedGroundX": player.speed_ground_x_self,
        "speedXAttack": player.speed_x_attack,
        "speedYAttack": player.speed_y_attack,
        "speedY": player.speed_y_self,
        "percent": player.percent,
        "facingRight": bool(player.facing),
        "ecb": environmentalCollisionBox(player),
        "onGround": bool(player.on_ground),
        "hitStun": bool(player.hitlag_left),
        "invulnerable": bool(player.invulnerable),
        "offStage": bool(player.off_stage)
    }


def stageData(gameState: GameState):
    blastzones: tuple[float, float, float,
                      float] = melee.stages.BLASTZONES[gameState.stage]
    leftPlatform: tuple[float, float,
                        float] = melee.stages.left_platform_position(gameState)
    # print(leftPlatform)
    rightPlatform: tuple[float, float,
                         float] = melee.stages.right_platform_position(gameState)
    topPlatform: tuple[float, float,
                       float] = melee.stages.top_platform_position(gameState)
    randall = randall_position(gameState.frame)
    edge = melee.stages.EDGE_GROUND_POSITION[gameState.stage]
    # print(edge)
    data = {
        "stage": gameState.stage.value,
        "leftEdge": edge * -1,
        "rightEdge": edge,
        "randall": {
            "height": randall[0],
            "left": randall[1],
            "right": randall[2],
        },
        "blastzone": {
            "left": blastzones[0],
            "right": blastzones[1],
            "top": blastzones[2],
            "bottom": blastzones[3],
        },
    }
    # if (leftPlatform is not None):
    #     data["platformLeft"] = {
    #         "height": leftPlatform[0],
    #         "left": leftPlatform[1],
    #         "right": leftPlatform[2]
    #     }
    # else:
    data["platformLeft"] = {
        "height": 0,
        "left": 0,
        "right": 0
    }
    # if (topPlatform is not None):
    #     data["platformTop"] = {
    #         "height": topPlatform[0],
    #         "left": topPlatform[1],
    #         "right": topPlatform[2]
    #     }
    # else:
    data["platformTop"] = {
        "height": 0,
        "left": 0,
        "right": 0
    }
    # if (rightPlatform is not None):
    #     data["platformRight"] = {
    #         "height": rightPlatform[0],
    #         "left": rightPlatform[1],
    #         "right": rightPlatform[2]
    #     }
    # else:
    data["platformRight"] = {
        "height": 0,
        "left": 0,
        "right": 0
    }
    return data


def frameData(gameState: GameState, args):
    return {
        "player1": playerData(gameState, args.port),
        "player2": playerData(gameState, args.opponent),
        "action1": actionData(gameState, args.port),
        "action2": actionData(gameState, args.opponent),
        "stage": stageData(gameState),
        "distance": gameState.distance,
        "frame": gameState.frame
    }


async def send_messages(connection: Connection, ws : WebSocketClientProtocol):
    print("waiting on ws")
    while(True):
        value = connection.recv()
        if (value is not None):
            await ws.send(json.dumps(value, cls=NumpyEncoder))
        # print("")


async def handle_message(connection: Connection, ns : Namespace):
    uri = "ws://localhost:8090/ws"
    print("Start websocket")
    async with websockets.connect(uri) as websocket:
        await websocket.send(json.dumps({
            "clientId": "pythonAgent"
        }))
        
        print("send value'd ws")
        # ns.ws = websocket
        asyncio.ensure_future(send_messages(out_rec, websocket))
        # Session.ws = websocket
        while True:
            got_back = await websocket.recv()
            msg: Dict = json.loads(got_back)
            connection.send(msg["data"])
            # if (msg["data"]["controllerId"] == 0):
            # processMessage(msg["data"], controller)
            # controller.release_all()
            # controller.flush()
            # Thread(target=getNetwork0, args=[msg["data"]["controllerId"], msg["data"]["modelId"]], daemon=True).start()
            # elif (msg["data"]["controllerId"] == 1):
            # processMessage(msg["data"], controller_opponent)
            # controller_opponent.release_all()
            # controller_opponent.flush()

            # Thread(target=getNetwork1, args=[msg["data"]["controllerId"], msg["data"]["modelId"]], daemon=True).start()

            # else:
            #     controller.release_all()
            #     controller.flush()


def requestNewNetworks(connection: Connection, ns : Namespace):
    while True:
        msg = connection.recv()
        print(msg)
        controllerId = msg["controllerId"]
        modelId = msg["modelId"]
        try:
            if (msg["controllerId"] == 0):
              ns.network0 = getNetwork(controllerId, modelId)
            elif (msg["controllerId"] == 1):
                ns.network1 = None
                ns.network1 = getNetwork(controllerId, modelId)
        except:
            print("failed to assign network for " + str(controllerId))
# network.input(state)
#         network.compute()
#         output =network.output()


def createMessage(topic: str, data):
    return {
        "topic": topic,
        "playerRef": "guest",
        "data": data
    }


positionNormalizer = 100.0
actionNormalizer = 60.0


def initializeNetworks(ns : Namespace):
    ns.network1 = getActiveNetwork(1)
    ns.network0 =  None # getActiveNetwork(0)
    print("init")


def embedCategory(state, statePosition, embedValue, embedSize):
    for i in range(0, embedSize):
        if (i == embedValue):
            state[0, statePosition + i] = 1
        else:
            state[0, statePosition + i] = 0

def applyPlatform(platform, state, statePosition, positionNormalizer):
    if (platform[0]):
        state[0, statePosition + 0] = platform[0] / positionNormalizer
    if (platform[1]):
        state[0, statePosition + 1] = platform[1] / positionNormalizer
    if (platform[2]):
        state[0, statePosition + 2] = platform[2] / positionNormalizer
    return statePosition + 3
    

def applyPlayerState(player0, state, statePosition):
    state[0, statePosition + 0] = player0.speed_air_x_self / positionNormalizer
    state[0, statePosition + 1] = player0.speed_ground_x_self / positionNormalizer
    state[0, statePosition + 2] = player0.speed_x_attack / positionNormalizer
    state[0, statePosition + 3] = player0.speed_y_attack / positionNormalizer
    state[0, statePosition + 4] = player0.speed_y_self / positionNormalizer
    state[0, statePosition + 5] = ((player0.percent / positionNormalizer))
    state[0, statePosition + 6] = ((player0.shield_strength / 100))
    state[0, statePosition + 7] = ((player0.stock / 4))
    state[0, statePosition + 8] = (
        (player0.action_frame / actionNormalizer))
    state[0, statePosition + 9] = (
        (player0.hitstun_frames_left / actionNormalizer))
    embedCategory(state, statePosition + 10, player0.character.value, 26)
    embedCategory(state, statePosition + 36, player0.action.value, 386)
    state[0, statePosition + 412] = ((player0.iasa / actionNormalizer))
    state[0, statePosition + 413] = (
        (player0.invulnerability_left / actionNormalizer))
    state[0, statePosition + 414] = ((player0.jumps_left / 2))
    state[0, statePosition + 415] = ((player0.x / positionNormalizer))
    state[0, statePosition + 416] = ((player0.y / positionNormalizer))

    rangeForward = fd.range_forward(
        player0.character, player0.action, player0.action_frame)
    rangeBackward = fd.range_backward(
        player0.character, player0.action, player0.action_frame)
    if (math.isnan(rangeBackward)):
        rangeBackward = 0
    if (math.isnan(rangeForward)):
        rangeForward = 0
    embedCategory(state, statePosition + 417, fd.attack_state(
        player0.character, player0.action, player0.action_frame).value, 4)
    embedCategory(state, statePosition + 421,
                  fd.hitbox_count(player0.character, player0.action), 6)
    state[0, statePosition + 421] = (
        (fd.hitbox_count(player0.character, player0.action) / 5))
    state[0, statePosition + 427] = ((rangeForward / positionNormalizer))
    state[0, statePosition + 428] = ((rangeBackward / positionNormalizer))

    state[0, statePosition + 429] = 1 if player0.facing else 0
    state[0, statePosition + 430] = 1 if player0.off_stage else 0
    state[0, statePosition + 431] = 1 if player0.on_ground else 0
    state[0, statePosition + 432] = 1 if fd.is_attack(
        player0.character, player0.action) else 0
    state[0, statePosition + 433] = 1 if fd.is_grab(
        player0.character, player0.action) else 0
    state[0, statePosition + 434] = 1 if fd.is_bmove(
        player0.character, player0.action) else 0
    state[0, statePosition + 435] = 1 if fd.is_roll(
        player0.character, player0.action) else 0
    return statePosition + 436


def console_loop(connection: Connection, ns : Namespace):
    # Main loop
    # if (Session.network0 != None and Session.network1 == None):
    console, controller, controller_opponent, args = startConsole()
    console.step()
    print("START CONSOLE LOOP")
    Session.cpu_level = 3
    costume = 0
    while True:
        # await asyncio.sleep(.001)
        # "step" to the next frame
        gamestate = console.step()
        # gamestate = None
        # Session.gamestate = gamestate
        # print(gamestate.menu_state)
        if gamestate is None:
            # await asyncio.sleep(.001)
            continue
        
        # The console object keeps track of how long your bot is taking to process frames
        #   And can warn you if it's taking too long
        if console.processingtime * 1000 > 50:
            print("WARNING: Last frame took " +
                  str(console.processingtime*1000) + "ms to process.")

        # What menu are we in?
        if gamestate.menu_state in [melee.Menu.IN_GAME, melee.Menu.SUDDEN_DEATH]:
            # await asyncio.sleep(.00001)
            if not Session.reassign_characters:
                Session.reassign_characters = True
                Session.menuLoadFirstFrame = False

            ai: PlayerState = gamestate.players[args.port]

            # if gamestate.frame == 0:
            #     print("new game!")
            #     await Session.ws.send(json.dumps(createMessage("simulation.reset.game", {})))
            #     newGame = False

            
                # if (counter % 10 == 0):
                # print("sending data")
            gameStateFrameData = frameData(gamestate, args)
            message = createMessage(
                "simulation.frame.update", gameStateFrameData)

            connection.send(message)
            # messageString = json.dumps(message, cls=NumpyEncoder)

            # await Session.ws.send(messageString)
            state: np.ndarray = np.zeros((1, 1105))
            player0: PlayerState = gamestate.players[args.port]

            statePosition = applyPlayerState(player0, state, 0)

            player1: PlayerState = gamestate.players[args.opponent]
            statePosition = applyPlayerState(player1, state, statePosition)
            embedCategory(state, statePosition, gamestate.stage.value, 26)
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
            statePosition +=6
            statePosition = applyPlatform(leftPlatform, state, statePosition, positionNormalizer)
            statePosition = applyPlatform(topPlatform, state, statePosition, positionNormalizer)
            statePosition = applyPlatform(rightPlatform, state, statePosition, positionNormalizer)
            state[0, statePosition] = (gamestate.distance) / positionNormalizer
            statePosition += 1
            # # state[0, 63] = (gamestate.projectiles) / positionNormalizer
            for projectile in gamestate.projectiles[:10]:
                projectile: Projectile
                embedCategory(state, statePosition, projectile.owner, 4)
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
                embedCategory(state, statePosition, projectile.subtype, 11)
                statePosition += 11
            network0 = ns.network0
            if network0:
                network0.input(state)
                network0.compute()
                output0 = network0.output()
                # outputUnActivated0 = ns.network0.outputUnActivated()

                # print(player0.action.value)
                # print(player0.action)
                # print(str(state.argmax()) + " - " + str(state.max()))
                # print(ns.network0.outputUnActivated())
                # print(str(state.argmax()) + str(state.max()))
                # print(output0)
                
                processMessage({
                    "a": output0[0, 0] > .5,
                    "b": output0[0, 1] > .5,
                    "y": output0[0, 2] > .5,
                    "z": output0[0, 3] > .5,
                    "mainStickX": math.cos(output0[0, 4] * 2 * math.pi) * output0[0, 5],
                    "mainStickY": math.sin(output0[0, 4] * 2 * math.pi) * output0[0, 5],
                    "cStickX": math.cos(output0[0, 6] * 2 * math.pi) * output0[0, 7],
                    "cStickY": math.sin(output0[0, 6] * 2 * math.pi) * output0[0, 7],
                    "leftShoulder": max(output0[0, 8]-.5, 0) *2,

                }, controller)
            network1 = ns.network1
            if network1:
                network1.input(state)
                network1.compute()
                output1 = network1.output()
                outputUnActivated1 = network1.outputUnActivated()
                # print(state[0,415])
                # print(network1.outputUnActivated())
            #     # print(output1)
                # print("xRaw: " + str(output1[0, 4]) + " magRaw: " + str(output1[0, 5]))
                # print("xStick: " + str(math.cos(output1[0, 4] * 2 * math.pi)) + " magXStick: " + str((((math.cos(output1[0, 4] * 2 * math.pi)  * output1[0, 5]) + 1) / 2)))
                # print("yStick: " + str(math.sin(output1[0, 4] * 2 * math.pi)) + " magYStick: " + str((((math.sin(output1[0, 4] * 2 * math.pi)  * output1[0, 5]) + 1) / 2)))
        
                processMessage({
                    "a": output1[0, 0] > .5,
                    "b": output1[0, 1] > .5,
                    "y": output1[0, 2] > .5,
                    "z": output1[0, 3] > .5,
                    "mainStickX": (((math.cos(outputUnActivated1[0, 4] * 2 * math.pi)  * output1[0, 5]) + 1) / 2),
                    "mainStickY": (((math.sin(outputUnActivated1[0, 4] * 2 * math.pi) * output1[0, 5]) + 1) / 2),
                    "cStickX": (((math.cos(outputUnActivated1[0, 6] * 2 * math.pi)* output1[0, 7]) + 1) / 2),
                    "cStickY": (((math.sin(outputUnActivated1[0, 6] * 2 * math.pi) * output1[0, 7]) + 1) / 2) ,
                    "leftShoulder": max(output1[0, 8]-.5, 0) *2,
                }, controller_opponent)
                # counter += 1
            else:
                controller_opponent.release_all()
                controller_opponent.flush()
                    # print("enemy stocks left: " + str(player2.stock))
            Session.lastStockAi = player0.stock
            Session.lastStockOpponent = player1.stock
            if player0 and player0.stock == 0 or player1 and player1.stock == 0:
                controller_opponent.release_all()
                controller_opponent.flush()
                    # Session.lastGamestate = gamestate

            # else:
            #     # If the discovered port was unsure, reroll our costume for next time
            #     costume = random.randint(0, 4)

        else:
            if Session.reassign_characters:
                counter = 0
                Session.ai_character = random.choice(Session.character_pool)
                Session.cpu_character = random.choice(Session.character_pool)
              
                Session.reassign_characters = False
                # controller_opponent.release_all()
            if not Session.menuLoadFirstFrame:
                Session.menuLoadFirstFrame = True
           
            melee.MenuHelper.menu_helper_simple(gamestate,
                                                controller_opponent,
                                                Session.ai_character,
                                                melee.Stage.RANDOM_STAGE,
                                                args.connect_code,
                                                costume=0,
                                                autostart=False,
                                                swag=False,
                                                cpu_level=0)
            if gamestate.players and gamestate.players[args.opponent].character == Session.ai_character:
                # print(gamestate.players[args.opponent].character)
            # if Session.ai_character == gamestate.players[args.opponent].character_selected:
                melee.MenuHelper.menu_helper_simple(gamestate,
                                                controller,
                                                Session.cpu_character,
                                                melee.Stage.RANDOM_STAGE,
                                                args.connect_code,
                                                costume=0,
                                                autostart=True,
                                                swag=False,
                                                cpu_level=3)

            counter +=1
            if counter > 6* 60:
                Session.reassign_characters = True


loop = asyncio.get_event_loop()


# def restartGame(resetCounter):
#     if not Session.simulationRunning and gamestate.menu_state in [melee.Menu.IN_GAME, melee.Menu.SUDDEN_DEATH]:
#         if resetCounter == 0:
#             print("release all buttons")
#             controller.release_all()
#             resetCounter = 1
#         elif resetCounter == 1:
#             print("press start to pause")
#             controller.press_button(Button.BUTTON_START)
#             resetCounter = 2
#         elif resetCounter == 2:
#             print("release start")
#             controller.release_all()
#             resetCounter = 3
#         elif resetCounter == 3:
#             print("press buttons to exit match")
#             controller.press_button(Button.BUTTON_START)
#             controller.press_button(Button.BUTTON_L)
#             controller.press_button(Button.BUTTON_R)
#             controller.press_button(Button.BUTTON_A)
#             resetCounter = 4
#         controller.flush()
#     # continue


async def test1(value: str):
    while True:
        print(f'message: {value}')
        await asyncio.sleep(.1)

# console.step()
#
# console_loop()
# asyncio.ensure_future(test1("test"))





if __name__ == '__main__':
    
    mgr = Manager()
    ns = mgr.Namespace()
    ns.ws = None
    initializeNetworks(ns)
    in_send, in_rec = Pipe()
    out_send, out_rec = Pipe()
    ws_send, ws_rec = Pipe()
    # .add_done_callback(callback)
    Process(target=requestNewNetworks, args=(in_send, ns), daemon=True).start()
    
    consoleProcess = Process(target=console_loop, args=(out_send, ns), daemon=True)
    consoleProcess.start()
    # consoleProcess.join()
    # p = Process(target=f, args=(in_rec,))
    # p.start()
    # print("waiting for message")
    # print(in_send.recv())
    # Process(target=requestNewNetworks, args=(in_send,)).start()
    # loop = Process(target=loop.run_forever, args=(in_send,))
    # loop.start()
    # console_loop
    print("Start async io loop")
    loop = asyncio.get_event_loop()
    asyncio.ensure_future(handle_message(in_rec, ns))
    
    loop.run_forever()

# Process(target=requestNewNetworks, args=(in_send,)).start()
# loop = Process(target=loop.run_forever, args=(in_send,))
# loop.start()
# loop.join()

    # main.start()
    # loop.run_until_complete(handle_message())
    # main.join()
